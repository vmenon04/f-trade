package com.fintechfive.ftrade.external;

import com.fintechfive.ftrade.exception.PriceUnavailableException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.client.RestClientException;

// Caches what another MarketDataClient (Fauxnance) returns in Redis, to save its daily quota, and counts that quota.
// Redis is only a cache: if it is down, everything comes straight from the API (and calls aren't counted).
//
//   price:{T}                     fresh price, for market.data.cache-ttl (e.g. 5m); the only price trades use
//   price:stale:{T}               a stale price, for the same TTL, so showing it doesn't ask the API on every view
//   price:last:{T}                last known price, fresh or stale, for a day; shown when there's no fresh one
//   symbol:{S}                    name and type (a hash), for a day
//   unknown:{S}                   Fauxnance doesn't know the symbol, for an hour
//   candles:{T}:{from}:{to}       daily closes ("2026-10-19=100.50,..."), until midnight UTC, when a new day's is added
//   fauxnance:calls:{yyyy-mm-dd}  API calls made today; expires at midnight UTC, when Fauxnance's quota resets
public class CachingMarketDataClient implements MarketDataClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(CachingMarketDataClient.class);

    static final Duration LAST_PRICE_TTL = Duration.ofHours(24);
    static final Duration SYMBOL_TTL = Duration.ofHours(24);
    static final Duration UNKNOWN_TTL = Duration.ofHours(1);
    // showing prices and looking up symbols stop at this share of the day's calls; the rest is kept for trades
    static final double DISPLAY_SHARE = 0.9;

    private final MarketDataClient delegate;
    private final StringRedisTemplate redis;
    private final Duration ttl;
    private final long dailyLimit;
    private final Clock clock;

    public CachingMarketDataClient(MarketDataClient delegate, StringRedisTemplate redis, Duration ttl,
                                   long dailyLimit, Clock clock) {
        this.delegate = delegate;
        this.redis = redis;
        this.ttl = ttl;
        this.dailyLimit = dailyLimit;
        this.clock = clock;
    }

    // prices trades, so only a fresh price: cached, or from the API while today's quota lasts
    @Override
    public BigDecimal getPrice(String ticker) {
        BigDecimal cached = readPrice(priceKey(ticker));
        if (cached != null) {
            return cached;
        }
        if (isUnknown(ticker)) {
            return null;
        }
        if (!trySpend(1, dailyLimit)) {
            throw new PriceUnavailableException("Fauxnance's daily quota is used up; prices are available again after "
                    + "midnight UTC");
        }
        BigDecimal price = delegate.getPrice(ticker);
        // no price (unknown symbol, stale quote) isn't cached, so the next lookup asks the API again
        if (price != null) {
            writePrice(priceKey(ticker), price, ttl);
            writePrice(lastPriceKey(ticker), price, LAST_PRICE_TTL);
        }
        return price;
    }

    // Prices to show. Redis first (fresh, then recently stale); the misses go to the API in batches (leaving out
    // symbols Fauxnance doesn't know), unless the display share of today's quota is used up; anything still without a
    // price shows its last known one, marked stale. Only fresh prices go in price:, because getPrice prices trades
    // from it.
    @Override
    public Map<String, PriceQuote> getPrices(Collection<String> tickers) {
        List<String> symbols = List.copyOf(tickers);
        Map<String, PriceQuote> prices = new HashMap<>();

        List<String> cached = readMany(symbols.stream().map(CachingMarketDataClient::priceKey).toList());
        List<String> notFresh = new ArrayList<>();
        for (int i = 0; i < symbols.size(); i++) {
            String value = valueAt(cached, i);
            if (value == null) {
                notFresh.add(symbols.get(i));
            } else {
                prices.put(symbols.get(i), new PriceQuote(new BigDecimal(value), false));
            }
        }
        if (notFresh.isEmpty()) {
            return prices;
        }

        List<String> recentlyStale = readMany(notFresh.stream().map(CachingMarketDataClient::stalePriceKey).toList());
        List<String> misses = new ArrayList<>();
        for (int i = 0; i < notFresh.size(); i++) {
            String value = valueAt(recentlyStale, i);
            if (value == null) {
                misses.add(notFresh.get(i));
            } else {
                prices.put(notFresh.get(i), new PriceQuote(new BigDecimal(value), true));
            }
        }
        if (misses.isEmpty()) {
            return prices;
        }

        List<String> unknown = readMany(misses.stream().map(CachingMarketDataClient::unknownKey).toList());
        List<String> toFetch = new ArrayList<>();
        for (int i = 0; i < misses.size(); i++) {
            if (valueAt(unknown, i) == null) {
                toFetch.add(misses.get(i));
            }
        }

        Set<String> newlyUnknown = new HashSet<>();
        if (!toFetch.isEmpty() && trySpend(batches(toFetch.size()), displayLimit())) {
            fetchForDisplay(toFetch).forEach((ticker, quote) -> {
                if (quote.price() == null) {
                    markUnknown(ticker);
                    newlyUnknown.add(ticker);
                    return;
                }
                prices.put(ticker, quote);
                writePrice(lastPriceKey(ticker), quote.price(), LAST_PRICE_TTL);
                writePrice(quote.stale() ? stalePriceKey(ticker) : priceKey(ticker), quote.price(), ttl);
            });
        }

        List<String> withoutPrice = toFetch.stream()
                .filter(ticker -> !prices.containsKey(ticker) && !newlyUnknown.contains(ticker))
                .toList();
        if (!withoutPrice.isEmpty()) {
            List<String> last = readMany(withoutPrice.stream().map(CachingMarketDataClient::lastPriceKey).toList());
            for (int i = 0; i < withoutPrice.size(); i++) {
                String value = valueAt(last, i);
                if (value != null) {
                    prices.put(withoutPrice.get(i), new PriceQuote(new BigDecimal(value), true));
                }
            }
        }
        return prices;
    }

    // the API's prices for showing; if it fails (an outage, a timeout), none, so the prices Redis already had and the
    // last known ones are still shown
    private Map<String, PriceQuote> fetchForDisplay(List<String> tickers) {
        try {
            return delegate.getPrices(tickers);
        } catch (IllegalStateException | RestClientException e) {
            LOGGER.warn("No prices from Fauxnance for {} symbols, showing the last known ones: {}", tickers.size(),
                    e.getMessage());
            return Map.of();
        }
    }

    // name and type, cached for a day; a symbol Fauxnance doesn't know is remembered for an hour
    @Override
    public SymbolInfo getSymbol(String symbol) {
        SymbolInfo cached = readSymbol(symbol);
        if (cached != null) {
            return cached;
        }
        if (isUnknown(symbol)) {
            return null;
        }
        if (!trySpend(1, displayLimit())) {
            throw new IllegalStateException("Fauxnance's daily quota is nearly used up; symbol lookups resume after "
                    + "midnight UTC");
        }
        SymbolInfo info = delegate.getSymbol(symbol);
        if (info == null) {
            markUnknown(symbol);
        } else {
            writeSymbol(symbol, info);
        }
        return info;
    }

    // daily closes for a chart, cached until midnight UTC; asking the API counts against the display share of the
    // quota. No history (still being backfilled) isn't cached, so the next look asks again
    @Override
    public List<DailyClose> getCandles(String ticker, LocalDate from, LocalDate to) {
        String key = candlesKey(ticker, from, to);
        List<DailyClose> cached = readCandles(key);
        if (cached != null) {
            return cached;
        }
        if (isUnknown(ticker)) {
            return List.of();
        }
        if (!trySpend(1, displayLimit())) {
            throw new IllegalStateException("Fauxnance's daily quota is nearly used up; price history is available "
                    + "again after midnight UTC");
        }
        List<DailyClose> closes = delegate.getCandles(ticker, from, to);
        if (!closes.isEmpty()) {
            writeCandles(key, closes, Duration.between(clock.instant(), nextMidnightUtc()));
        }
        return closes;
    }

    // --- the daily quota -------------------------------------------------------------------------------

    private long displayLimit() {
        return (long) Math.floor(dailyLimit * DISPLAY_SHARE);
    }

    // one HTTP call per batch of symbols
    private static int batches(int symbols) {
        return (symbols + HttpMarketDataClient.MAX_BATCH - 1) / HttpMarketDataClient.MAX_BATCH;
    }

    // Counts `calls` against today's quota, unless that would go past `limit`: then nothing is counted and it returns
    // false. Without Redis nothing can be counted, so the calls go ahead.
    private boolean trySpend(int calls, long limit) {
        String key = callsKey();
        try {
            String used = redis.opsForValue().get(key);
            if (used != null && Long.parseLong(used) + calls > limit) {
                return false;
            }
            Long total = redis.opsForValue().increment(key, calls);
            // the first calls of the day start the counter, which resets when Fauxnance's quota does
            if (total != null && total == calls) {
                redis.expireAt(key, nextMidnightUtc());
            }
        } catch (DataAccessException e) {
            LOGGER.warn("Quota count failed for {}: {}", key, e.getMessage());
        }
        return true;
    }

    private String callsKey() {
        return "fauxnance:calls:" + LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private Instant nextMidnightUtc() {
        return LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    // --- keys ----------------------------------------------------------------------------------------

    private static String priceKey(String ticker) {
        return "price:" + ticker;
    }

    private static String stalePriceKey(String ticker) {
        return "price:stale:" + ticker;
    }

    private static String lastPriceKey(String ticker) {
        return "price:last:" + ticker;
    }

    private static String symbolKey(String symbol) {
        return "symbol:" + symbol;
    }

    private static String unknownKey(String symbol) {
        return "unknown:" + symbol;
    }

    private static String candlesKey(String ticker, LocalDate from, LocalDate to) {
        return "candles:" + ticker + ":" + from + ":" + to;
    }

    // --- Redis reads and writes: a failure is logged and treated as a cache miss -------------------------

    private BigDecimal readPrice(String key) {
        try {
            String value = redis.opsForValue().get(key);
            // stored as a plain string so the scale survives (189.50 stays 189.50)
            return value == null ? null : new BigDecimal(value);
        } catch (DataAccessException e) {
            LOGGER.warn("Price cache read failed for {}, using the API: {}", key, e.getMessage());
            return null;
        }
    }

    // the i-th value of a readMany, or null if there is none (Redis down, or the key isn't set)
    private static String valueAt(List<String> values, int i) {
        return values == null || i >= values.size() ? null : values.get(i);
    }

    // one round trip for all the keys; null when Redis is down
    private List<String> readMany(List<String> keys) {
        try {
            return redis.opsForValue().multiGet(keys);
        } catch (DataAccessException e) {
            LOGGER.warn("Cache read failed for {} keys: {}", keys.size(), e.getMessage());
            return null;
        }
    }

    private void writePrice(String key, BigDecimal price, Duration expiry) {
        try {
            redis.opsForValue().set(key, price.toPlainString(), expiry);
        } catch (DataAccessException e) {
            LOGGER.warn("Price cache write failed for {}: {}", key, e.getMessage());
        }
    }

    private List<DailyClose> readCandles(String key) {
        try {
            String value = redis.opsForValue().get(key);
            if (value == null) {
                return null;
            }
            List<DailyClose> closes = new ArrayList<>();
            for (String day : value.split(",")) {
                String[] parts = day.split("=");
                closes.add(new DailyClose(LocalDate.parse(parts[0]), new BigDecimal(parts[1])));
            }
            return closes;
        } catch (DataAccessException e) {
            LOGGER.warn("Cache read failed for {}, using the API: {}", key, e.getMessage());
            return null;
        }
    }

    private void writeCandles(String key, List<DailyClose> closes, Duration expiry) {
        StringBuilder value = new StringBuilder();
        for (DailyClose close : closes) {
            if (!value.isEmpty()) {
                value.append(',');
            }
            value.append(close.date()).append('=').append(close.close().toPlainString());
        }
        try {
            redis.opsForValue().set(key, value.toString(), expiry);
        } catch (DataAccessException e) {
            LOGGER.warn("Cache write failed for {}: {}", key, e.getMessage());
        }
    }

    private boolean isUnknown(String symbol) {
        try {
            return redis.opsForValue().get(unknownKey(symbol)) != null;
        } catch (DataAccessException e) {
            LOGGER.warn("Cache read failed for {}: {}", unknownKey(symbol), e.getMessage());
            return false;
        }
    }

    private void markUnknown(String symbol) {
        try {
            redis.opsForValue().set(unknownKey(symbol), "1", UNKNOWN_TTL);
        } catch (DataAccessException e) {
            LOGGER.warn("Cache write failed for {}: {}", unknownKey(symbol), e.getMessage());
        }
    }

    private SymbolInfo readSymbol(String symbol) {
        try {
            Map<Object, Object> fields = redis.opsForHash().entries(symbolKey(symbol));
            if (fields == null || fields.isEmpty()) {
                return null;
            }
            return new SymbolInfo(symbol, (String) fields.get("name"), (String) fields.get("type"));
        } catch (DataAccessException e) {
            LOGGER.warn("Cache read failed for {}: {}", symbolKey(symbol), e.getMessage());
            return null;
        }
    }

    private void writeSymbol(String symbol, SymbolInfo info) {
        Map<String, String> fields = new LinkedHashMap<>();
        if (info.name() != null) {
            fields.put("name", info.name());
        }
        if (info.type() != null) {
            fields.put("type", info.type());
        }
        if (fields.isEmpty()) {
            return;
        }
        try {
            redis.opsForHash().putAll(symbolKey(symbol), fields);
            redis.expire(symbolKey(symbol), SYMBOL_TTL);
        } catch (DataAccessException e) {
            LOGGER.warn("Cache write failed for {}: {}", symbolKey(symbol), e.getMessage());
        }
    }
}
