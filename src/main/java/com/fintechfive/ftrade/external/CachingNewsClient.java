package com.fintechfive.ftrade.external;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

// Caches what another NewsClient (Finnhub) returns in Redis as JSON, so every dashboard view doesn't call the API
// (Finnhub allows 60 calls a minute). Errors aren't cached. If Redis is down, news comes straight from the API.
//
//   news:market     general market news, for news.cache-ttl (e.g. 10m)
//   news:company:{S} news about one company, for the same TTL
public class CachingNewsClient implements NewsClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(CachingNewsClient.class);

    private static final TypeReference<List<NewsArticle>> ARTICLES = new TypeReference<>() {
    };

    private final NewsClient delegate;
    private final StringRedisTemplate redis;
    private final JsonMapper json;
    private final Duration ttl;

    public CachingNewsClient(NewsClient delegate, StringRedisTemplate redis, JsonMapper json, Duration ttl) {
        this.delegate = delegate;
        this.redis = redis;
        this.json = json;
        this.ttl = ttl;
    }

    @Override
    public List<NewsArticle> getMarketNews() {
        return cached("news:market", delegate::getMarketNews);
    }

    @Override
    public List<NewsArticle> getCompanyNews(String symbol) {
        return cached("news:company:" + symbol, () -> delegate.getCompanyNews(symbol));
    }

    private List<NewsArticle> cached(String key, Supplier<List<NewsArticle>> fetch) {
        List<NewsArticle> articles = read(key);
        if (articles != null) {
            return articles;
        }
        articles = fetch.get();
        write(key, articles);
        return articles;
    }

    // null when it isn't cached, Redis is down, or the cached JSON can't be read
    private List<NewsArticle> read(String key) {
        try {
            String value = redis.opsForValue().get(key);
            return value == null ? null : json.readValue(value, ARTICLES);
        } catch (DataAccessException | JacksonException e) {
            LOGGER.warn("News cache read failed for {}, using the API: {}", key, e.getMessage());
            return null;
        }
    }

    private void write(String key, List<NewsArticle> articles) {
        try {
            redis.opsForValue().set(key, json.writeValueAsString(articles), ttl);
        } catch (DataAccessException | JacksonException e) {
            LOGGER.warn("News cache write failed for {}: {}", key, e.getMessage());
        }
    }
}
