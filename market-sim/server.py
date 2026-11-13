"""Simulated market data: serves the Fauxnance endpoints HttpMarketDataClient calls, with made-up prices.

Every price is worked out from the symbol and the time, so nothing is stored, a restart changes nothing, and history
never contradicts itself. Markets never close and quotes are never stale, so the demo can trade at any hour.
"""

import hashlib
import hmac
import json
import math
import os
import random
import re
from datetime import date, datetime, timedelta, timezone
from functools import lru_cache
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, unquote, urlsplit

MAX_BATCH = 25
MAX_CANDLE_DAYS = 3660
SECONDS_PER_DAY = 86400

# the symbol types Fauxnance reports, for the asset classes in sample-data.sql
TYPES = {"STOCK": "equity", "ETF": "etf", "FOREX": "fx", "CRYPTO": "crypto"}
DAILY_VOLATILITY = {"equity": 0.015, "etf": 0.008, "fx": 0.004, "crypto": 0.035}
PRICE_RANGE = {"equity": (20, 600), "etf": (30, 700), "crypto": (0.1, 2000)}

# levels for the symbols the demo data trades, so its past trades are in the right range; the rest get a price from
# PRICE_RANGE. Stablecoins sit at 1 and barely move.
KNOWN_PRICES = {
    "AAPL": 340, "MSFT": 480, "NVDA": 210, "VOO": 690, "SPY": 690, "QQQ": 600,
    "X:BTC-USD": 85000, "X:ETH-USD": 3000, "X:USDT-USD": 1, "X:USDC-USD": 1,
}
STABLECOINS = {"X:USDT-USD", "X:USDC-USD"}
# US dollars per unit, for working out the level of any FX: pair
USD_PER_UNIT = {
    "USD": 1, "EUR": 1.17, "GBP": 1.34, "JPY": 0.0066, "CHF": 1.24, "AUD": 0.65, "CAD": 0.72, "NZD": 0.58,
    "SEK": 0.105, "NOK": 0.099, "DKK": 0.157, "PLN": 0.275, "HUF": 0.003, "CZK": 0.048, "TRY": 0.024, "ZAR": 0.057,
    "MXN": 0.054, "BRL": 0.185, "CNH": 0.14, "HKD": 0.128, "SGD": 0.77, "INR": 0.0113, "KRW": 0.0007, "THB": 0.031,
    "IDR": 0.00006, "PHP": 0.017, "ILS": 0.3, "CLP": 0.00105, "AED": 0.272, "SAR": 0.267,
}

INSTRUMENT_ROW = re.compile(r"\('((?:[^']|'')+)',\s*'([^']+)',\s*'(STOCK|ETF|FOREX|CRYPTO)'\)")


def load_symbols(sql_text):
    """The instruments in sample-data.sql, as {symbol: {name, type}}. Crypto is stored as X:{coin}-USD there too."""
    symbols = {}
    for name, ticker, asset_class in INSTRUMENT_ROW.findall(sql_text):
        symbol = f"X:{ticker}-USD" if asset_class == "CRYPTO" else ticker
        symbols[symbol.upper()] = {"name": name.replace("''", "'"), "type": TYPES[asset_class]}
    return symbols


def _unit(*parts):
    """A number in [0, 1) that is always the same for the same parts."""
    digest = hashlib.sha256("|".join(str(part) for part in parts).encode()).digest()
    return int.from_bytes(digest[:8], "big") / 2**64


@lru_cache(maxsize=4096)
def base_price(symbol, symbol_type):
    if symbol in KNOWN_PRICES:
        return float(KNOWN_PRICES[symbol])
    if symbol_type == "fx":
        pair = symbol.removeprefix("FX:")
        base, quote = USD_PER_UNIT.get(pair[:3]), USD_PER_UNIT.get(pair[3:])
        if base and quote:
            return base / quote
    low, high = PRICE_RANGE.get(symbol_type, PRICE_RANGE["equity"])
    return math.exp(math.log(low) + _unit(symbol, "base") * (math.log(high) - math.log(low)))


def _volatility(symbol, symbol_type):
    return 0.0002 if symbol in STABLECOINS else DAILY_VOLATILITY[symbol_type]


@lru_cache(maxsize=200000)
def _shock(symbol, day_number):
    return random.Random(f"{symbol}|{day_number}").gauss(0, 1)


@lru_cache(maxsize=200000)
def _log_close(symbol, symbol_type, day_number):
    """How far a day's close is from the symbol's level, in logs: slow swings plus recent shocks that fade.

    Using no running total keeps prices near their level however long the service runs.
    """
    volatility = _volatility(symbol, symbol_type)
    phase = _unit(symbol, "phase") * 2 * math.pi
    swings = 6 * math.sin(2 * math.pi * day_number / 211 + phase) + 3 * math.sin(2 * math.pi * day_number / 47 + phase * 2)
    fading_shocks = sum(0.85**age * _shock(symbol, day_number - age) for age in range(20))
    return volatility * (swings + fading_shocks)


def _rounded(price, symbol_type):
    if symbol_type == "fx":
        return round(price, 5)
    if price >= 1:
        return round(price, 2)
    # small coins: keep 4 significant digits, within the database's 8 decimal places
    return round(price, min(8, 3 - math.floor(math.log10(price))))


def price_at(symbol, symbol_type, moment):
    """The price at a moment: moves from one day's close to the next, with some wobble that fades by each close."""
    seconds = moment.timestamp()
    day_number = math.floor(seconds / SECONDS_PER_DAY)
    fraction = seconds / SECONDS_PER_DAY - day_number
    start = _log_close(symbol, symbol_type, day_number - 1)
    end = _log_close(symbol, symbol_type, day_number)
    phase = _unit(symbol, "wobble") * 2 * math.pi
    wobble = sum(
        math.sin(2 * math.pi * fraction * cycles + phase * cycles) / cycles for cycles in (7, 31, 113)
    ) * 0.25 * _volatility(symbol, symbol_type) * math.sin(math.pi * fraction)
    return _rounded(base_price(symbol, symbol_type) * math.exp(start + (end - start) * fraction + wobble), symbol_type)


def candles(symbol, symbol_type, first, last, now):
    """Daily closes from first to last, oldest first. Today's is the price now; there are none for the future."""
    today = now.astimezone(timezone.utc).date()
    result = []
    current = first
    while current <= min(last, today):
        if current == today:
            close = price_at(symbol, symbol_type, now)
        else:
            day_number = (current - date(1970, 1, 1)).days
            close = _rounded(base_price(symbol, symbol_type) * math.exp(_log_close(symbol, symbol_type, day_number)),
                             symbol_type)
        result.append({"date": current.isoformat(), "close": close, "synthetic": True})
        current += timedelta(days=1)
    return result


def _now():
    return datetime.now(timezone.utc)


def _timestamp(moment):
    return moment.strftime("%Y-%m-%dT%H:%M:%SZ")


class Handler(BaseHTTPRequestHandler):
    symbols = {}
    api_key = ""

    def do_GET(self):
        url = urlsplit(self.path)
        path = unquote(url.path).removeprefix("/v1")
        if path == "/health":
            return self._send(200, {"status": "UP"})
        if not hmac.compare_digest(self.headers.get("X-Api-Key", "").encode(), self.api_key.encode()):
            return self._error(401, "UNAUTHORIZED")

        query = parse_qs(url.query)
        parts = path.strip("/").split("/")
        if parts == ["quotes"]:
            return self._batch_quotes(query)
        if len(parts) == 2 and parts[0] == "quotes":
            return self._quote(parts[1].upper())
        if len(parts) == 2 and parts[0] == "symbols":
            return self._symbol(parts[1].upper())
        if len(parts) == 2 and parts[0] == "candles":
            return self._candles(parts[1].upper(), query)
        return self._error(404, "NOT_FOUND")

    def _quote(self, symbol):
        info = self.symbols.get(symbol)
        if info is None:
            return self._error(404, "SYMBOL_NOT_FOUND")
        now = _now()
        price = price_at(symbol, info["type"], now)
        self._send(200, {
            "data": {"symbol": symbol, "price": price, "currency": "USD", "asOf": _timestamp(now), "marketState": "open"},
            "meta": {"symbol": symbol, "asOf": _timestamp(now), "source": "simulated", "stale": False},
        })

    def _batch_quotes(self, query):
        requested = [symbol.strip().upper() for symbol in query.get("symbols", [""])[0].split(",") if symbol.strip()]
        if not requested or len(requested) > MAX_BATCH:
            return self._error(400, "INVALID_SYMBOLS")
        now = _now()
        quotes = []
        for symbol in requested:
            info = self.symbols.get(symbol)
            if info is None:
                quotes.append({"symbol": symbol, "error": {"code": "SYMBOL_NOT_FOUND"}})
            else:
                quotes.append({
                    "symbol": symbol, "source": "simulated", "stale": False,
                    "quote": {"symbol": symbol, "price": price_at(symbol, info["type"], now), "currency": "USD",
                              "marketState": "open"},
                })
        self._send(200, {"data": {"quotes": quotes}, "meta": {"asOf": _timestamp(now), "source": "simulated"}})

    def _symbol(self, symbol):
        info = self.symbols.get(symbol)
        if info is None:
            return self._error(404, "SYMBOL_NOT_FOUND")
        self._send(200, {
            "data": {"symbol": symbol, "name": info["name"], "type": info["type"], "currency": "USD", "active": True},
            "meta": {"symbol": symbol, "source": "simulated"},
        })

    def _candles(self, symbol, query):
        info = self.symbols.get(symbol)
        if info is None:
            return self._error(404, "SYMBOL_NOT_FOUND")
        try:
            first = date.fromisoformat(query["from"][0])
            last = date.fromisoformat(query["to"][0])
        except (KeyError, ValueError):
            return self._error(400, "INVALID_DATE_RANGE")
        if query.get("interval", ["1d"])[0] != "1d" or first > last or (last - first).days > MAX_CANDLE_DAYS:
            return self._error(400, "INVALID_DATE_RANGE")
        self._send(200, {
            "data": {"symbol": symbol, "interval": "1d", "currency": "USD",
                     "candles": candles(symbol, info["type"], first, last, _now())},
            "meta": {"source": "simulated", "partial": False},
        })

    def _error(self, status, code):
        self._send(status, {"error": {"code": code, "message": code, "details": {}}})

    def _send(self, status, body):
        payload = json.dumps(body).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def log_message(self, format, *args):
        pass


def main():
    api_key = os.environ.get("MARKET_DATA_API_KEY", "")
    if not api_key:
        raise SystemExit("MARKET_DATA_API_KEY is not set")
    with open(os.environ.get("INSTRUMENTS_SQL", "sample-data.sql"), encoding="utf-8") as sql:
        Handler.symbols = load_symbols(sql.read())
    if not Handler.symbols:
        raise SystemExit("no instruments found")
    Handler.api_key = api_key
    port = int(os.environ.get("PORT", "8090"))
    print(f"simulating {len(Handler.symbols)} symbols on port {port}", flush=True)
    ThreadingHTTPServer(("0.0.0.0", port), Handler).serve_forever()


if __name__ == "__main__":
    main()
