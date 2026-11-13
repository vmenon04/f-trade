package com.fintechfive.ftrade.external;

import com.fintechfive.ftrade.exception.PriceUnavailableException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// lenient: most tests don't touch the quota counter or the hash operations they share
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CachingMarketDataClientTest {

    private static final String TICKER = "AAPL";
    private static final String KEY = "price:AAPL";
    private static final Duration TTL = Duration.ofMinutes(5);
    private static final Duration LAST_PRICE_TTL = Duration.ofHours(24);
    private static final Duration UNKNOWN_TTL = Duration.ofHours(1);
    private static final int DAILY_LIMIT = 2000;
    // 15:00 UTC on 22 October, so today's counter expires at midnight UTC
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-22T15:00:00Z"), ZoneOffset.UTC);
    private static final String CALLS = "fauxnance:calls:2026-10-22";
    private static final Instant MIDNIGHT = Instant.parse("2026-10-23T00:00:00Z");

    @Mock
    private MarketDataClient delegate;

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> values;

    @Mock
    private HashOperations<String, Object, Object> hashes;

    private CachingMarketDataClient client;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
        when(redis.<Object, Object>opsForHash()).thenReturn(hashes);
        // nothing used from today's quota yet; incrementing counts the calls made
        when(values.get(CALLS)).thenReturn(null);
        when(values.increment(eq(CALLS), anyLong())).thenAnswer(invocation -> invocation.getArgument(1));
        client = new CachingMarketDataClient(delegate, redis, TTL, DAILY_LIMIT, CLOCK);
    }

    private void callsUsedToday(int calls) {
        when(values.get(CALLS)).thenReturn(String.valueOf(calls));
        when(values.increment(eq(CALLS), anyLong())).thenAnswer(invocation -> calls + (long) invocation.getArgument(1));
    }

    private static PriceQuote fresh(String price) {
        return new PriceQuote(new BigDecimal(price), false);
    }

    private static PriceQuote stale(String price) {
        return new PriceQuote(new BigDecimal(price), true);
    }

    // --- getPrice: prices trades, from fresh prices only ---------------------------------------------

    @Test
    void cachedPriceIsReturnedWithoutCallingTheApi() {
        when(values.get(KEY)).thenReturn("189.25");
        assertEquals(new BigDecimal("189.25"), client.getPrice(TICKER));
        verifyNoInteractions(delegate);
        verify(values, never()).increment(anyString(), anyLong());
    }

    @Test
    void cacheMissCallsTheApiAndCachesThePriceAndTheLastKnownPrice() {
        when(values.get(KEY)).thenReturn(null);
        when(delegate.getPrice(TICKER)).thenReturn(new BigDecimal("189.25"));

        assertEquals(new BigDecimal("189.25"), client.getPrice(TICKER));
        verify(values).set(KEY, "189.25", TTL);
        verify(values).set("price:last:AAPL", "189.25", LAST_PRICE_TTL);
    }

    @Test
    void cachedPriceKeepsItsScale() {
        when(values.get(KEY)).thenReturn("189.50");
        // assertEquals on BigDecimal compares scale too, so 189.5 would fail
        assertEquals(new BigDecimal("189.50"), client.getPrice(TICKER));
    }

    @Test
    void missingPriceIsNotCached() {
        when(values.get(KEY)).thenReturn(null);
        when(delegate.getPrice(TICKER)).thenReturn(null);

        assertNull(client.getPrice(TICKER));
        verify(values, never()).set(startsWith("price:"), anyString(), any(Duration.class));
    }

    @Test
    void getPrice_symbolFauxnanceDoesNotKnow_returnsNullWithoutCallingTheApi() {
        when(values.get(KEY)).thenReturn(null);
        when(values.get("unknown:AAPL")).thenReturn("1");

        assertNull(client.getPrice(TICKER));
        verifyNoInteractions(delegate);
        verify(values, never()).increment(anyString(), anyLong());
    }

    @Test
    void apiErrorPropagatesAndNothingIsCached() {
        when(values.get(KEY)).thenReturn(null);
        when(delegate.getPrice(TICKER)).thenThrow(new IllegalStateException("Fauxnance returned 429 for AAPL"));

        assertThrows(IllegalStateException.class, () -> client.getPrice(TICKER));
        verify(values, never()).set(startsWith("price:"), anyString(), any(Duration.class));
    }

    @Test
    void redisReadFailureFallsBackToTheApi() {
        when(values.get(anyString())).thenThrow(new RedisConnectionFailureException("Redis down"));
        when(values.increment(anyString(), anyLong())).thenThrow(new RedisConnectionFailureException("Redis down"));
        when(delegate.getPrice(TICKER)).thenReturn(new BigDecimal("189.25"));

        // without Redis there is no quota count either, so the call goes ahead
        assertEquals(new BigDecimal("189.25"), client.getPrice(TICKER));
    }

    @Test
    void redisWriteFailureStillReturnsTheApiPrice() {
        when(values.get(KEY)).thenReturn(null);
        when(delegate.getPrice(TICKER)).thenReturn(new BigDecimal("189.25"));
        doThrow(new RedisConnectionFailureException("Redis down"))
                .when(values).set(KEY, "189.25", TTL);

        assertEquals(new BigDecimal("189.25"), client.getPrice(TICKER));
    }

    // --- the daily quota ----------------------------------------------------------------------------

    @Test
    void eachApiCallIsCountedAndTheFirstOfTheDayExpiresAtMidnightUtc() {
        when(values.get(KEY)).thenReturn(null);
        when(delegate.getPrice(TICKER)).thenReturn(new BigDecimal("189.25"));

        client.getPrice(TICKER);

        verify(values).increment(CALLS, 1L);
        verify(redis).expireAt(CALLS, MIDNIGHT);
    }

    @Test
    void laterCallsDoNotResetTheCountersExpiry() {
        callsUsedToday(10);
        when(values.get(KEY)).thenReturn(null);
        when(delegate.getPrice(TICKER)).thenReturn(new BigDecimal("189.25"));

        client.getPrice(TICKER);

        verify(values).increment(CALLS, 1L);
        verify(redis, never()).expireAt(anyString(), any(Instant.class));
    }

    @Test
    void getPrice_quotaUsedUp_throwsWithoutCallingTheApi() {
        callsUsedToday(DAILY_LIMIT);
        when(values.get(KEY)).thenReturn(null);

        PriceUnavailableException e = assertThrows(PriceUnavailableException.class, () -> client.getPrice(TICKER));
        assertTrue(e.getMessage().contains("quota"));
        verifyNoInteractions(delegate);
    }

    // the last 10% of the day's calls are kept for pricing trades
    @Test
    void getPrice_inTheReserveForTrades_stillCallsTheApi() {
        callsUsedToday(1900);
        when(values.get(KEY)).thenReturn(null);
        when(delegate.getPrice(TICKER)).thenReturn(new BigDecimal("189.25"));

        assertEquals(new BigDecimal("189.25"), client.getPrice(TICKER));
    }

    // --- getPrices: prices to show, falling back to the last known price ---------------------------

    @Test
    void getPrices_usesRedisFirstAndOnlyAsksTheApiForMisses() {
        when(values.multiGet(List.of("price:AAPL", "price:MSFT", "price:NVDA"))).thenReturn(Arrays.asList("189.25", null, null));
        when(values.multiGet(List.of("unknown:MSFT", "unknown:NVDA"))).thenReturn(Arrays.asList(null, null));
        when(delegate.getPrices(List.of("MSFT", "NVDA"))).thenReturn(Map.of("MSFT", fresh("410.10")));
        when(values.multiGet(List.of("price:last:NVDA"))).thenReturn(Arrays.asList((String) null));

        Map<String, PriceQuote> prices = client.getPrices(List.of("AAPL", "MSFT", "NVDA"));

        // a cached price is fresh: only fresh prices go in price:
        assertEquals(Map.of("AAPL", fresh("189.25"), "MSFT", fresh("410.10")), prices);
        verify(values).set("price:MSFT", "410.10", TTL);
        verify(values).set("price:last:MSFT", "410.10", LAST_PRICE_TTL);
        verify(values, never()).set(eq("price:NVDA"), anyString(), any(Duration.class));
        verify(values).increment(CALLS, 1L);
    }

    // getPrice prices trades from price:, so a stale price only goes in price:last:
    @Test
    void getPrices_staleQuoteIsKeptAsTheLastKnownPriceOnly() {
        when(values.multiGet(List.of("price:MSFT"))).thenReturn(Arrays.asList((String) null));
        when(values.multiGet(List.of("unknown:MSFT"))).thenReturn(Arrays.asList((String) null));
        when(delegate.getPrices(List.of("MSFT"))).thenReturn(Map.of("MSFT", stale("410.10")));

        assertEquals(Map.of("MSFT", stale("410.10")), client.getPrices(List.of("MSFT")));
        verify(values).set("price:last:MSFT", "410.10", LAST_PRICE_TTL);
        verify(values, never()).set(eq("price:MSFT"), anyString(), any(Duration.class));
    }

    // a stale price won't be fresh again soon (e.g. the market is closed), so it's kept for the cache TTL too,
    // in its own key: otherwise every page view would ask the API for it again
    @Test
    void getPrices_staleQuoteIsAlsoKeptForTheCacheTtl() {
        when(values.multiGet(List.of("price:MSFT"))).thenReturn(Arrays.asList((String) null));
        when(delegate.getPrices(List.of("MSFT"))).thenReturn(Map.of("MSFT", stale("410.10")));

        client.getPrices(List.of("MSFT"));

        verify(values).set("price:stale:MSFT", "410.10", TTL);
    }

    @Test
    void getPrices_recentStaleQuote_isShownWithoutCallingTheApi() {
        when(values.multiGet(List.of("price:MSFT"))).thenReturn(Arrays.asList((String) null));
        when(values.multiGet(List.of("price:stale:MSFT"))).thenReturn(List.of("410.10"));

        assertEquals(Map.of("MSFT", stale("410.10")), client.getPrices(List.of("MSFT")));
        verifyNoInteractions(delegate);
    }

    // one failed batch (e.g. Fauxnance's 500s) mustn't throw away the prices Redis already had
    @Test
    void getPrices_apiFails_keepsCachedPricesAndFallsBackToTheLastKnownOnes() {
        when(values.multiGet(List.of("price:AAPL", "price:MSFT"))).thenReturn(Arrays.asList("189.25", null));
        when(values.multiGet(List.of("unknown:MSFT"))).thenReturn(Arrays.asList((String) null));
        when(delegate.getPrices(List.of("MSFT"))).thenThrow(new IllegalStateException("Fauxnance returned 500"));
        when(values.multiGet(List.of("price:last:MSFT"))).thenReturn(List.of("405.00"));

        assertEquals(Map.of("AAPL", fresh("189.25"), "MSFT", stale("405.00")),
                client.getPrices(List.of("AAPL", "MSFT")));
    }

    @Test
    void getPrices_noPriceFromTheApi_fallsBackToTheLastKnownPriceMarkedStale() {
        when(values.multiGet(List.of("price:AAPL"))).thenReturn(Arrays.asList((String) null));
        when(values.multiGet(List.of("unknown:AAPL"))).thenReturn(Arrays.asList((String) null));
        when(delegate.getPrices(List.of("AAPL"))).thenReturn(Map.of());
        when(values.multiGet(List.of("price:last:AAPL"))).thenReturn(List.of("188.00"));

        assertEquals(Map.of("AAPL", stale("188.00")), client.getPrices(List.of("AAPL")));
    }

    @Test
    void getPrices_unknownSymbolIsRememberedAndLeftOut() {
        when(values.multiGet(List.of("price:NOPE"))).thenReturn(Arrays.asList((String) null));
        when(values.multiGet(List.of("unknown:NOPE"))).thenReturn(Arrays.asList((String) null));
        // the HTTP client reports a symbol Fauxnance doesn't know as a quote with no price
        when(delegate.getPrices(List.of("NOPE"))).thenReturn(Map.of("NOPE", new PriceQuote(null, false)));

        assertEquals(Map.of(), client.getPrices(List.of("NOPE")));
        verify(values).set("unknown:NOPE", "1", UNKNOWN_TTL);
    }

    @Test
    void getPrices_symbolKnownToBeUnknown_isNotAskedForAgain() {
        when(values.multiGet(List.of("price:NOPE"))).thenReturn(Arrays.asList((String) null));
        when(values.multiGet(List.of("unknown:NOPE"))).thenReturn(List.of("1"));

        assertEquals(Map.of(), client.getPrices(List.of("NOPE")));
        verifyNoInteractions(delegate);
    }

    @Test
    void getPrices_allCached_doesNotCallTheApi() {
        when(values.multiGet(List.of("price:AAPL"))).thenReturn(List.of("189.25"));

        assertEquals(Map.of("AAPL", fresh("189.25")), client.getPrices(List.of("AAPL")));
        verifyNoInteractions(delegate);
        verify(values, never()).increment(anyString(), anyLong());
    }

    @Test
    void getPrices_countsOneCallPerBatchOf25() {
        List<String> tickers = IntStream.range(0, 30).mapToObj(i -> "T" + i).toList();
        when(values.multiGet(anyList())).thenAnswer(invocation -> Arrays.asList(new String[30]));
        when(delegate.getPrices(tickers)).thenReturn(Map.of());

        client.getPrices(tickers);

        verify(values).increment(CALLS, 2L);
    }

    // display stops at 90% of the day's calls, leaving the rest for trades
    @Test
    void getPrices_displayShareOfTheQuotaUsedUp_showsLastKnownPricesWithoutCallingTheApi() {
        callsUsedToday(1800);
        when(values.multiGet(List.of("price:AAPL"))).thenReturn(Arrays.asList((String) null));
        when(values.multiGet(List.of("unknown:AAPL"))).thenReturn(Arrays.asList((String) null));
        when(values.multiGet(List.of("price:last:AAPL"))).thenReturn(List.of("188.00"));

        assertEquals(Map.of("AAPL", stale("188.00")), client.getPrices(List.of("AAPL")));
        verifyNoInteractions(delegate);
    }

    @Test
    void getPrices_redisDown_fetchesEverythingFromTheApi() {
        when(values.multiGet(anyList())).thenThrow(new RedisConnectionFailureException("Redis down"));
        when(values.get(anyString())).thenThrow(new RedisConnectionFailureException("Redis down"));
        when(values.increment(anyString(), anyLong())).thenThrow(new RedisConnectionFailureException("Redis down"));
        when(delegate.getPrices(List.of("AAPL"))).thenReturn(Map.of("AAPL", fresh("189.25")));

        assertEquals(Map.of("AAPL", fresh("189.25")), client.getPrices(List.of("AAPL")));
    }

    // --- getSymbol: name and type, cached for a day ------------------------------------------------

    @Test
    void getSymbol_cached_doesNotCallTheApi() {
        when(hashes.entries("symbol:ORCL")).thenReturn(Map.of("name", "Oracle Corporation", "type", "equity"));

        assertEquals(new SymbolInfo("ORCL", "Oracle Corporation", "equity"), client.getSymbol("ORCL"));
        verifyNoInteractions(delegate);
    }

    @Test
    void getSymbol_fromTheApi_isCachedForADay() {
        when(hashes.entries("symbol:ORCL")).thenReturn(Map.of());
        when(values.get("unknown:ORCL")).thenReturn(null);
        when(delegate.getSymbol("ORCL")).thenReturn(new SymbolInfo("ORCL", "Oracle Corporation", "equity"));

        assertEquals(new SymbolInfo("ORCL", "Oracle Corporation", "equity"), client.getSymbol("ORCL"));
        verify(hashes).putAll("symbol:ORCL", Map.of("name", "Oracle Corporation", "type", "equity"));
        verify(redis).expire("symbol:ORCL", Duration.ofHours(24));
        verify(values).increment(CALLS, 1L);
    }

    @Test
    void getSymbol_unknownSymbol_isRememberedForAnHour() {
        when(hashes.entries("symbol:NOPE")).thenReturn(Map.of());
        when(values.get("unknown:NOPE")).thenReturn(null);
        when(delegate.getSymbol("NOPE")).thenReturn(null);

        assertNull(client.getSymbol("NOPE"));
        verify(values).set("unknown:NOPE", "1", UNKNOWN_TTL);
    }

    @Test
    void getSymbol_knownToBeUnknown_doesNotCallTheApi() {
        when(hashes.entries("symbol:NOPE")).thenReturn(Map.of());
        when(values.get("unknown:NOPE")).thenReturn("1");

        assertNull(client.getSymbol("NOPE"));
        verifyNoInteractions(delegate);
    }

    // e.g. 202 (still being fetched) or 429: try again next time
    @Test
    void getSymbol_apiError_propagatesAndNothingIsCached() {
        when(hashes.entries("symbol:ORCL")).thenReturn(Map.of());
        when(values.get("unknown:ORCL")).thenReturn(null);
        when(delegate.getSymbol("ORCL")).thenThrow(new IllegalStateException("ORCL is still being fetched"));

        assertThrows(IllegalStateException.class, () -> client.getSymbol("ORCL"));
        verify(hashes, never()).putAll(anyString(), anyMap());
        verify(values, never()).set(startsWith("unknown:"), anyString(), any(Duration.class));
    }

    @Test
    void getSymbol_displayShareOfTheQuotaUsedUp_throwsWithoutCallingTheApi() {
        callsUsedToday(1800);
        when(hashes.entries("symbol:ORCL")).thenReturn(Map.of());
        when(values.get("unknown:ORCL")).thenReturn(null);

        assertThrows(IllegalStateException.class, () -> client.getSymbol("ORCL"));
        verifyNoInteractions(delegate);
    }

    // --- getCandles: daily closes, cached until midnight UTC (Fauxnance adds a day's candle once a day) ------

    private static final LocalDate FROM = LocalDate.of(2026, 10, 19);
    private static final LocalDate TO = LocalDate.of(2026, 10, 22);
    private static final String CANDLES_KEY = "candles:AAPL:2026-10-19:2026-10-22";
    private static final List<DailyClose> CLOSES = List.of(
            new DailyClose(LocalDate.of(2026, 10, 19), new BigDecimal("100.50")),
            new DailyClose(LocalDate.of(2026, 10, 20), new BigDecimal("101.25")));

    @Test
    void getCandles_cached_doesNotCallTheApi() {
        when(values.get(CANDLES_KEY)).thenReturn("2026-10-19=100.50,2026-10-20=101.25");

        assertEquals(CLOSES, client.getCandles(TICKER, FROM, TO));
        verifyNoInteractions(delegate);
    }

    @Test
    void getCandles_fromTheApi_isCachedUntilMidnightUtcAndCounted() {
        when(values.get(CANDLES_KEY)).thenReturn(null);
        when(delegate.getCandles(TICKER, FROM, TO)).thenReturn(CLOSES);

        assertEquals(CLOSES, client.getCandles(TICKER, FROM, TO));
        verify(values).set(CANDLES_KEY, "2026-10-19=100.50,2026-10-20=101.25", Duration.ofHours(9));
        verify(values).increment(CALLS, 1L);
    }

    // e.g. still being backfilled: ask again next time
    @Test
    void getCandles_noHistory_isNotCached() {
        when(values.get(CANDLES_KEY)).thenReturn(null);
        when(delegate.getCandles(TICKER, FROM, TO)).thenReturn(List.of());

        assertEquals(List.of(), client.getCandles(TICKER, FROM, TO));
        verify(values, never()).set(eq(CANDLES_KEY), anyString(), any(Duration.class));
    }

    @Test
    void getCandles_symbolKnownToBeUnknown_doesNotCallTheApi() {
        when(values.get(CANDLES_KEY)).thenReturn(null);
        when(values.get("unknown:AAPL")).thenReturn("1");

        assertEquals(List.of(), client.getCandles(TICKER, FROM, TO));
        verifyNoInteractions(delegate);
    }

    @Test
    void getCandles_displayShareOfTheQuotaUsedUp_throwsWithoutCallingTheApi() {
        callsUsedToday(1800);
        when(values.get(CANDLES_KEY)).thenReturn(null);

        assertThrows(IllegalStateException.class, () -> client.getCandles(TICKER, FROM, TO));
        verifyNoInteractions(delegate);
    }

    @Test
    void getCandles_redisDown_fetchesFromTheApi() {
        when(values.get(CANDLES_KEY)).thenThrow(new RedisConnectionFailureException("down"));
        when(values.get("unknown:AAPL")).thenThrow(new RedisConnectionFailureException("down"));
        when(delegate.getCandles(TICKER, FROM, TO)).thenReturn(CLOSES);

        assertEquals(CLOSES, client.getCandles(TICKER, FROM, TO));
    }
}
