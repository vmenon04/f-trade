package com.fintechfive.ftrade.external;

import com.fintechfive.ftrade.exception.PriceUnavailableException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

// use a fake HTTP server
class HttpMarketDataClientTest {

    private static final String BASE_URL = "https://fauxnance.test/v1";
    private static final String API_KEY = "test-key";

    private MockRestServiceServer server;
    private HttpMarketDataClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new HttpMarketDataClient(builder, BASE_URL, API_KEY);
    }

    // Shape of a GET /quotes/{symbol} response, from the API spec
    private static String quoteJson(String symbol, String price, boolean stale) {
        return """
                {
                  "data": {
                    "symbol": "%s", "price": %s, "bid": 313.5, "ask": 313.56, "spreadBps": 1.8587,
                    "currency": "USD", "change": 3.63, "changePercent": 1.1713, "previousClose": 309.9,
                    "asOf": "2026-08-26T16:13:24Z", "marketState": "open"
                  },
                  "meta": {
                    "asOf": "2026-08-26T16:13:24Z", "disclaimer": "Educational data. Not for investment use.",
                    "symbol": "%s", "source": "cache", "stale": %s, "spreadSource": "modelled"
                  }
                }
                """.formatted(symbol, price, symbol, stale);
    }

    private static String errorJson(String code) {
        return """
                {"error": {"code": "%s", "message": "Error", "details": {}}}
                """.formatted(code);
    }

    @Test
    void returnsPriceFromQuote() {
        server.expect(requestTo(BASE_URL + "/quotes/AAPL"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Api-Key", API_KEY))
                .andRespond(withSuccess(quoteJson("AAPL", "313.53", false), MediaType.APPLICATION_JSON));

        assertEquals(0, new BigDecimal("313.53").compareTo(client.getPrice("AAPL")));
        server.verify();
    }

    // getPrice prices trades, which must not use a price past Fauxnance's freshness window
    @Test
    void returnsNullForStaleQuote() {
        server.expect(requestTo(BASE_URL + "/quotes/AAPL"))
                .andRespond(withSuccess(quoteJson("AAPL", "313.53", true), MediaType.APPLICATION_JSON));

        assertNull(client.getPrice("AAPL"));
    }

    @Test
    void returnsNullWhenSymbolNotFound() {
        server.expect(requestTo(BASE_URL + "/quotes/NOPE"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON).body(errorJson("SYMBOL_NOT_FOUND")));

        assertNull(client.getPrice("NOPE"));
    }

    @Test
    void returnsNullWhileBackfillInProgress() {
        server.expect(requestTo(BASE_URL + "/quotes/AAPL"))
                .andRespond(withStatus(HttpStatus.ACCEPTED)
                        .contentType(MediaType.APPLICATION_JSON).body(errorJson("BACKFILL_IN_PROGRESS")));

        assertNull(client.getPrice("AAPL"));
    }

    @Test
    void throwsWhenApiKeyRejected() {
        server.expect(requestTo(BASE_URL + "/quotes/AAPL"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThrows(PriceUnavailableException.class, () -> client.getPrice("AAPL"));
    }

    @Test
    void throwsWhenQuotaExhausted() {
        server.expect(requestTo(BASE_URL + "/quotes/AAPL"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON).body(errorJson("RATE_LIMITED")));

        assertThrows(PriceUnavailableException.class, () -> client.getPrice("AAPL"));
    }

    @Test
    void throwsOnServerError() {
        server.expect(requestTo(BASE_URL + "/quotes/AAPL"))
                .andRespond(withServiceUnavailable());

        assertThrows(PriceUnavailableException.class, () -> client.getPrice("AAPL"));
    }

    @Test
    void requestsCryptoSymbolUnchanged() {
        // The ':' is percent-encoded in the URL (%3A); the API decodes it back to X:BTC-USD
        server.expect(requestTo(BASE_URL + "/quotes/X%3ABTC-USD"))
                .andRespond(withSuccess(quoteJson("X:BTC-USD", "64250.12", false), MediaType.APPLICATION_JSON));

        assertEquals(0, new BigDecimal("64250.12").compareTo(client.getPrice("X:BTC-USD")));
    }

    @Test
    void returnsNullForEmptyResponse() {
        server.expect(requestTo(BASE_URL + "/quotes/AAPL"))
                .andRespond(withSuccess());

        assertNull(client.getPrice("AAPL"));
    }

    // Shape of a GET /quotes?symbols= response, as returned by the live API
    private static String batchJson(String... entries) {
        return """
                {"data": {"quotes": [%s]}, "meta": {"asOf": "2026-10-21T15:24:39Z", "spreadSource": "modelled"}}
                """.formatted(String.join(",", entries));
    }

    private static String batchQuote(String symbol, String price, boolean stale) {
        return """
                {"symbol": "%s", "source": "cache", "stale": %s,
                 "quote": {"symbol": "%s", "price": %s, "currency": "USD", "marketState": "open"}}
                """.formatted(symbol, stale, symbol, price);
    }

    private static String batchError(String symbol, String code) {
        return """
                {"symbol": "%s", "error": {"code": "%s", "message": "Error", "details": {}}}
                """.formatted(symbol, code);
    }

    @Test
    void getPrices_returnsPricesFromOneBatchRequest() {
        server.expect(requestTo(startsWith(BASE_URL + "/quotes?")))
                .andExpect(queryParam("symbols", "AAPL,MSFT"))
                .andExpect(header("X-Api-Key", API_KEY))
                .andRespond(withSuccess(batchJson(batchQuote("AAPL", "344.07", false), batchQuote("MSFT", "530.45", false)),
                        MediaType.APPLICATION_JSON));

        Map<String, PriceQuote> prices = client.getPrices(List.of("AAPL", "MSFT"));

        assertEquals(0, new BigDecimal("344.07").compareTo(prices.get("AAPL").price()));
        assertEquals(0, new BigDecimal("530.45").compareTo(prices.get("MSFT").price()));
        server.verify();
    }

    // prices for display: a stale quote keeps its last known price, marked stale
    @Test
    void getPrices_keepsStaleQuotesMarkedStale() {
        server.expect(requestTo(startsWith(BASE_URL + "/quotes?")))
                .andRespond(withSuccess(batchJson(batchQuote("AAPL", "344.07", false), batchQuote("MSFT", "530.45", true)),
                        MediaType.APPLICATION_JSON));

        Map<String, PriceQuote> prices = client.getPrices(List.of("AAPL", "MSFT"));

        assertFalse(prices.get("AAPL").stale());
        assertTrue(prices.get("MSFT").stale());
    }

    // an unknown symbol comes back with no price, so the cache can remember it; a symbol that is only
    // being fetched right now (or failed another way) is left out, to be asked for again
    @Test
    void getPrices_unknownSymbolHasNoPriceAndOtherErrorsAreLeftOut() {
        server.expect(requestTo(startsWith(BASE_URL + "/quotes?")))
                .andRespond(withSuccess(batchJson(batchQuote("AAPL", "344.07", false),
                        batchError("NOPE", "SYMBOL_NOT_FOUND"), batchError("NEWCO", "BACKFILL_IN_PROGRESS")),
                        MediaType.APPLICATION_JSON));

        Map<String, PriceQuote> prices = client.getPrices(List.of("AAPL", "NOPE", "NEWCO"));

        assertEquals(Set.of("AAPL", "NOPE"), prices.keySet());
        assertNull(prices.get("NOPE").price());
    }

    @Test
    void getPrices_splitsIntoBatchesOf25() {
        List<String> tickers = IntStream.range(0, 30).mapToObj(i -> "T" + i).toList();
        server.expect(requestTo(startsWith(BASE_URL + "/quotes?")))
                .andExpect(queryParam("symbols", String.join(",", tickers.subList(0, 25))))
                .andRespond(withSuccess(batchJson(batchQuote("T0", "1", false)), MediaType.APPLICATION_JSON));
        server.expect(requestTo(startsWith(BASE_URL + "/quotes?")))
                .andExpect(queryParam("symbols", String.join(",", tickers.subList(25, 30))))
                .andRespond(withSuccess(batchJson(batchQuote("T29", "2", false)), MediaType.APPLICATION_JSON));

        Map<String, PriceQuote> prices = client.getPrices(tickers);

        assertEquals(Set.of("T0", "T29"), prices.keySet());
        server.verify();
    }

    @Test
    void getSymbol_returnsNameAndType() {
        server.expect(requestTo(BASE_URL + "/symbols/ORCL"))
                .andExpect(header("X-Api-Key", API_KEY))
                .andRespond(withSuccess("""
                        {"data": {"symbol": "ORCL", "name": "Oracle Corporation", "type": "equity", "exchange": "US",
                                  "currency": "USD", "active": true},
                         "meta": {"symbol": "ORCL", "source": "stored"}}
                        """, MediaType.APPLICATION_JSON));

        assertEquals(new SymbolInfo("ORCL", "Oracle Corporation", "equity"), client.getSymbol("ORCL"));
    }

    @Test
    void getSymbol_unknownSymbol_returnsNull() {
        server.expect(requestTo(BASE_URL + "/symbols/NOPE"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON).body(errorJson("SYMBOL_NOT_FOUND")));

        assertNull(client.getSymbol("NOPE"));
    }

    // not "unknown": it will be known once Fauxnance has fetched it, so the cache mustn't remember it as unknown
    @Test
    void getSymbol_stillBeingFetched_throws() {
        server.expect(requestTo(BASE_URL + "/symbols/NEWCO"))
                .andRespond(withStatus(HttpStatus.ACCEPTED));

        assertThrows(IllegalStateException.class, () -> client.getSymbol("NEWCO"));
    }

    @Test
    void getSymbol_throwsWhenFauxnanceFails() {
        server.expect(requestTo(BASE_URL + "/symbols/ORCL"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThrows(IllegalStateException.class, () -> client.getSymbol("ORCL"));
    }

    @Test
    void getPrices_throwsWhenFauxnanceFails() {
        server.expect(requestTo(startsWith(BASE_URL + "/quotes?")))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThrows(IllegalStateException.class, () -> client.getPrices(List.of("AAPL")));
    }

    // --- getCandles: daily closes, for the performance chart -------------------------------------------

    private static final String CANDLES_URL = BASE_URL + "/candles/AAPL?from=2026-10-21&to=2026-10-23&interval=1d";

    @Test
    void getCandles_returnsTheDailyClosesOldestFirst() {
        server.expect(requestTo(CANDLES_URL))
                .andExpect(header("X-Api-Key", API_KEY))
                .andRespond(withSuccess("""
                        {"data": {"symbol": "AAPL", "interval": "1d", "currency": "USD", "candles": [
                           {"date": "2026-10-21", "open": 99, "high": 101, "low": 98, "close": 100.5, "adjclose": 100.1,
                            "volume": 1000, "synthetic": false},
                           {"date": "2026-10-22", "open": 100, "high": 103, "low": 99, "close": 102.25, "adjclose": 101.9,
                            "volume": null, "synthetic": true},
                           {"date": "2026-10-23", "open": 102, "high": 104, "low": 101, "close": null, "adjclose": null,
                            "volume": null, "synthetic": false}]},
                         "meta": {"source": "stored", "partial": false}}
                        """, MediaType.APPLICATION_JSON));

        // the raw close (we value shares actually held), and a day without one is left out
        assertEquals(List.of(
                        new DailyClose(LocalDate.of(2026, 10, 21), new BigDecimal("100.5")),
                        new DailyClose(LocalDate.of(2026, 10, 22), new BigDecimal("102.25"))),
                client.getCandles("AAPL", LocalDate.of(2026, 10, 21), LocalDate.of(2026, 10, 23)));
        server.verify();
    }

    @Test
    void getCandles_unknownSymbol_returnsNoCloses() {
        server.expect(requestTo(CANDLES_URL))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON).body(errorJson("SYMBOL_NOT_FOUND")));

        assertEquals(List.of(), client.getCandles("AAPL", LocalDate.of(2026, 10, 21), LocalDate.of(2026, 10, 23)));
    }

    // 202: Fauxnance is still backfilling the history, so there is none to show yet
    @Test
    void getCandles_stillBeingFetched_returnsNoCloses() {
        server.expect(requestTo(CANDLES_URL)).andRespond(withStatus(HttpStatus.ACCEPTED));

        assertEquals(List.of(), client.getCandles("AAPL", LocalDate.of(2026, 10, 21), LocalDate.of(2026, 10, 23)));
    }

    @Test
    void getCandles_throwsWhenFauxnanceFails() {
        server.expect(requestTo(CANDLES_URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        LocalDate from = LocalDate.of(2026, 10, 21);
        LocalDate to = LocalDate.of(2026, 10, 23);
        assertThrows(IllegalStateException.class, () -> client.getCandles("AAPL", from, to));
    }
}
