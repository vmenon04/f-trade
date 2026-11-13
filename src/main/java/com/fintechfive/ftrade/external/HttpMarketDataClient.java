package com.fintechfive.ftrade.external;

import com.fintechfive.ftrade.exception.PriceUnavailableException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.client.RestClient;

// MarketDataClient backed by the Fauxnance API (GET /quotes/{symbol}, GET /quotes?symbols= for batches, and
// GET /candles/{symbol} for daily history)
public class HttpMarketDataClient implements MarketDataClient {

    // Fauxnance allows up to 25 symbols per batch request, and a batch counts as one request against the quota
    static final int MAX_BATCH = 25;

    private final RestClient restClient;

    public HttpMarketDataClient(RestClient.Builder builder, String baseUrl, String apiKey) {
        this.restClient = builder
                .baseUrl(baseUrl)
                .defaultHeader("X-Api-Key", apiKey)
                .build();
    }

    @Override
    public BigDecimal getPrice(String ticker) {
        return restClient.get()
                .uri("/quotes/{symbol}", ticker)
                .exchange((request, response) -> {
                    int status = response.getStatusCode().value();
                    // 404: unknown symbol; 202: data still being fetched. but either way, no price right now.
                    if (status == 404 || status == 202) {
                        return null;
                    }
                    // 401/403: bad or expired key; 429: daily quota used up; 5xx: API unavailable
                    if (response.getStatusCode().isError()) {
                        throw new PriceUnavailableException("Fauxnance returned " + status + " for " + ticker);
                    }
                    QuoteResponse body = response.bodyTo(QuoteResponse.class);
                    // this prices trades, so a stale quote (past Fauxnance's freshness window) means no price
                    if (body == null || body.data() == null || body.meta() == null || body.meta().stale()) {
                        return null;
                    }
                    return body.data().price();
                });
    }

    // Prices for showing: a stale quote keeps its last known price, marked stale; a symbol Fauxnance doesn't know gets
    // a quote with no price (so the cache can remember it); a symbol with another error (e.g. still being fetched) is
    // left out. The request fails as a whole only if Fauxnance itself does (bad key, quota, outage).
    @Override
    public Map<String, PriceQuote> getPrices(Collection<String> tickers) {
        List<String> symbols = List.copyOf(tickers);
        Map<String, PriceQuote> prices = new HashMap<>();
        for (int start = 0; start < symbols.size(); start += MAX_BATCH) {
            String batch = String.join(",", symbols.subList(start, Math.min(start + MAX_BATCH, symbols.size())));
            BatchQuoteResponse body = restClient.get()
                    .uri(uri -> uri.path("/quotes").queryParam("symbols", batch).build())
                    .exchange((request, response) -> {
                        if (response.getStatusCode().isError()) {
                            throw new IllegalStateException("Fauxnance returned " + response.getStatusCode().value()
                                    + " for a batch quote");
                        }
                        return response.bodyTo(BatchQuoteResponse.class);
                    });
            if (body == null || body.data() == null || body.data().quotes() == null) {
                continue;
            }
            for (BatchEntry entry : body.data().quotes()) {
                if (entry.quote() != null && entry.quote().price() != null) {
                    prices.put(entry.symbol(), new PriceQuote(entry.quote().price(), Boolean.TRUE.equals(entry.stale())));
                } else if (entry.error() != null && "SYMBOL_NOT_FOUND".equals(entry.error().code())) {
                    prices.put(entry.symbol(), new PriceQuote(null, false));
                }
            }
        }
        return prices;
    }

    // GET /symbols/{symbol}; 404 means Fauxnance doesn't know the symbol (null). 202 means it is still being fetched,
    // which isn't "unknown", so it is an error the caller can try again after
    @Override
    public SymbolInfo getSymbol(String symbol) {
        return restClient.get()
                .uri("/symbols/{symbol}", symbol)
                .exchange((request, response) -> {
                    int status = response.getStatusCode().value();
                    if (status == 404) {
                        return null;
                    }
                    if (status == 202) {
                        throw new IllegalStateException(symbol + " is still being fetched by Fauxnance; try again soon");
                    }
                    if (response.getStatusCode().isError()) {
                        throw new IllegalStateException("Fauxnance returned " + status + " for symbol " + symbol);
                    }
                    SymbolResponse body = response.bodyTo(SymbolResponse.class);
                    return body == null ? null : body.data();
                });
    }

    // GET /candles/{symbol}, daily: the raw close of each day (we value shares actually held, so not adjclose). 404
    // (unknown symbol) and 202 (history still being backfilled) both mean no history to show yet
    @Override
    public List<DailyClose> getCandles(String ticker, LocalDate from, LocalDate to) {
        CandlesResponse body = restClient.get()
                .uri(uri -> uri.path("/candles/{symbol}")
                        .queryParam("from", from)
                        .queryParam("to", to)
                        .queryParam("interval", "1d")
                        .build(ticker))
                .exchange((request, response) -> {
                    int status = response.getStatusCode().value();
                    if (status == 404 || status == 202) {
                        return null;
                    }
                    if (response.getStatusCode().isError()) {
                        throw new IllegalStateException("Fauxnance returned " + status + " for candles of " + ticker);
                    }
                    return response.bodyTo(CandlesResponse.class);
                });
        if (body == null || body.data() == null || body.data().candles() == null) {
            return List.of();
        }
        return body.data().candles().stream()
                .filter(candle -> candle.date() != null && candle.close() != null)
                .map(candle -> new DailyClose(candle.date(), candle.close()))
                .toList();
    }

    // take the fields we use from the JSON response
    record QuoteResponse(Quote data, Meta meta) {
    }

    record Meta(boolean stale) {
    }

    record SymbolResponse(SymbolInfo data) {
    }

    // GET /quotes?symbols=: {"data": {"quotes": [{"symbol", "stale", "quote": {"price"}} or {"symbol", "error": {"code"}}]}}
    record BatchQuoteResponse(BatchData data) {
    }

    record BatchData(List<BatchEntry> quotes) {
    }

    // an entry for a symbol that errored has no stale flag (or quote), so stale can be missing
    record BatchEntry(String symbol, Boolean stale, Quote quote, BatchError error) {
    }

    record BatchError(String code) {
    }

    record Quote(BigDecimal price) {
    }

    // GET /candles/{symbol}: {"data": {"candles": [{"date", "close", ...}]}}, oldest first
    record CandlesResponse(CandlesData data) {
    }

    record CandlesData(List<Candle> candles) {
    }

    record Candle(LocalDate date, BigDecimal close) {
    }
}
