"""Checks the simulated API against the response shapes HttpMarketDataClient reads. Run: python -m unittest"""

import json
import threading
import unittest
from datetime import date, datetime, timedelta, timezone
from http.server import ThreadingHTTPServer
from pathlib import Path
from urllib.error import HTTPError
from urllib.request import Request, urlopen

import server

KEY = "test-key"
SEED = Path(__file__).parent.parent / "src/main/resources/sample-data.sql"


class SimulatedApiTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        server.Handler.symbols = server.load_symbols(SEED.read_text(encoding="utf-8"))
        server.Handler.api_key = KEY
        cls.httpd = ThreadingHTTPServer(("127.0.0.1", 0), server.Handler)
        threading.Thread(target=cls.httpd.serve_forever, daemon=True).start()
        cls.base = f"http://127.0.0.1:{cls.httpd.server_port}/v1"

    @classmethod
    def tearDownClass(cls):
        cls.httpd.shutdown()

    def get(self, path, key=KEY):
        request = Request(self.base + path, headers={"X-Api-Key": key})
        try:
            with urlopen(request) as response:
                return response.status, json.load(response)
        except HTTPError as error:
            return error.code, json.load(error)

    def test_a_wrong_key_is_refused(self):
        self.assertEqual(self.get("/quotes/AAPL", key="wrong")[0], 401)

    def test_quote(self):
        status, body = self.get("/quotes/AAPL")
        self.assertEqual(status, 200)
        self.assertGreater(body["data"]["price"], 0)
        self.assertIs(body["meta"]["stale"], False)

    def test_an_encoded_crypto_symbol_is_understood(self):
        status, body = self.get("/quotes/X%3ABTC-USD")
        self.assertEqual(status, 200)
        self.assertEqual(body["data"]["symbol"], "X:BTC-USD")

    def test_an_unknown_symbol_is_a_404(self):
        for path in ("/quotes/NOPE", "/symbols/NOPE", "/candles/NOPE?from=2026-01-01&to=2026-01-02&interval=1d"):
            self.assertEqual(self.get(path)[0], 404, path)
        # a bare BTC is not the coin
        self.assertEqual(self.get("/quotes/BTC")[0], 404)

    def test_batch_marks_only_unknown_symbols_as_errors(self):
        status, body = self.get("/quotes?symbols=AAPL,NOPE,FX:EURUSD")
        self.assertEqual(status, 200)
        entries = {entry["symbol"]: entry for entry in body["data"]["quotes"]}
        self.assertGreater(entries["AAPL"]["quote"]["price"], 0)
        self.assertIs(entries["AAPL"]["stale"], False)
        self.assertEqual(entries["NOPE"]["error"]["code"], "SYMBOL_NOT_FOUND")
        self.assertGreater(entries["FX:EURUSD"]["quote"]["price"], 0)

    def test_batch_size_is_limited_like_fauxnance(self):
        symbols = ",".join(f"T{i}" for i in range(server.MAX_BATCH + 1))
        self.assertEqual(self.get(f"/quotes?symbols={symbols}")[0], 400)

    def test_symbol_types_match_the_catalog(self):
        for symbol, expected in (("AAPL", "equity"), ("VOO", "etf"), ("FX:EURUSD", "fx"), ("X:BTC-USD", "crypto")):
            status, body = self.get(f"/symbols/{symbol}")
            self.assertEqual((status, body["data"]["type"]), (200, expected), symbol)
            self.assertTrue(body["data"]["name"])

    def test_candles_are_daily_oldest_first_and_stop_today(self):
        today = datetime.now(timezone.utc).date()
        first = today - timedelta(days=30)
        status, body = self.get(f"/candles/AAPL?from={first}&to={today + timedelta(days=5)}&interval=1d")
        self.assertEqual(status, 200)
        dates = [date.fromisoformat(candle["date"]) for candle in body["data"]["candles"]]
        self.assertEqual(dates, [first + timedelta(days=i) for i in range(31)])

    def test_every_seed_symbol_has_a_price_the_database_can_store(self):
        now = datetime.now(timezone.utc)
        self.assertGreaterEqual(len(server.Handler.symbols), 334)
        for symbol, info in server.Handler.symbols.items():
            price = server.price_at(symbol, info["type"], now)
            self.assertGreater(price, 0, symbol)
            self.assertLess(price, 10**10, symbol)
            self.assertEqual(round(price, 8), price, symbol)


class PriceModelTest(unittest.TestCase):
    def test_prices_are_repeatable(self):
        moment = datetime(2026, 6, 1, 15, 30, tzinfo=timezone.utc)
        self.assertEqual(server.price_at("AAPL", "equity", moment), server.price_at("AAPL", "equity", moment))

    def test_the_price_moves_smoothly_over_a_minute(self):
        moment = datetime(2026, 6, 1, 15, 30, tzinfo=timezone.utc)
        before = server.price_at("AAPL", "equity", moment)
        after = server.price_at("AAPL", "equity", moment + timedelta(minutes=1))
        self.assertLess(abs(after - before) / before, 0.005)

    def test_a_past_close_matches_the_price_at_midnight(self):
        close = server.candles("AAPL", "equity", date(2026, 3, 1), date(2026, 3, 1),
                               datetime(2026, 6, 1, tzinfo=timezone.utc))[0]["close"]
        self.assertAlmostEqual(close, server.price_at("AAPL", "equity", datetime(2026, 3, 2, tzinfo=timezone.utc)), 2)

    def test_prices_stay_near_their_level_for_years(self):
        for symbol, symbol_type in (("AAPL", "equity"), ("FX:EURUSD", "fx"), ("X:BTC-USD", "crypto")):
            level = server.base_price(symbol, symbol_type)
            for day in range(0, 3650, 7):
                moment = datetime(2026, 1, 1, tzinfo=timezone.utc) + timedelta(days=day)
                ratio = server.price_at(symbol, symbol_type, moment) / level
                self.assertTrue(0.3 < ratio < 3, f"{symbol} {moment}: {ratio}")

    def test_stablecoins_stay_near_one(self):
        price = server.price_at("X:USDT-USD", "crypto", datetime(2026, 6, 1, tzinfo=timezone.utc))
        self.assertAlmostEqual(price, 1, delta=0.05)


if __name__ == "__main__":
    unittest.main()
