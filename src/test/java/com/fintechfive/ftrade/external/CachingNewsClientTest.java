package com.fintechfive.ftrade.external;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CachingNewsClientTest {

    private static final Duration TTL = Duration.ofMinutes(10);
    private static final NewsArticle ARTICLE = new NewsArticle(7, "Stocks rise", "What happened today.", "Reuters",
            "https://example.com/7", null, Instant.parse("2026-10-23T12:00:00Z"));
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Mock
    private NewsClient delegate;

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> values;

    private CachingNewsClient client;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
        client = new CachingNewsClient(delegate, redis, JSON, TTL);
    }

    @Test
    void cachedMarketNews_doesNotCallTheApi() {
        when(values.get("news:market")).thenReturn(JSON.writeValueAsString(List.of(ARTICLE)));

        assertEquals(List.of(ARTICLE), client.getMarketNews());
        verifyNoInteractions(delegate);
    }

    @Test
    void uncachedMarketNews_comesFromTheApiAndIsCached() {
        when(delegate.getMarketNews()).thenReturn(List.of(ARTICLE));

        assertEquals(List.of(ARTICLE), client.getMarketNews());
        verify(values).set("news:market", JSON.writeValueAsString(List.of(ARTICLE)), TTL);
    }

    @Test
    void companyNews_isCachedPerSymbol() {
        when(delegate.getCompanyNews("AAPL")).thenReturn(List.of(ARTICLE));

        assertEquals(List.of(ARTICLE), client.getCompanyNews("AAPL"));
        verify(values).get("news:company:AAPL");
        verify(values).set(eq("news:company:AAPL"), anyString(), eq(TTL));
    }

    @Test
    void anApiError_isNotCached() {
        when(delegate.getMarketNews()).thenThrow(new IllegalStateException("Finnhub returned 429 for market news"));

        assertThrows(IllegalStateException.class, client::getMarketNews);
        verify(values, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void withRedisDown_newsComesStraightFromTheApi() {
        when(values.get("news:market")).thenThrow(new RedisConnectionFailureException("down"));
        doThrow(new RedisConnectionFailureException("down")).when(values).set(anyString(), anyString(), any(Duration.class));
        when(delegate.getMarketNews()).thenReturn(List.of(ARTICLE));

        assertEquals(List.of(ARTICLE), client.getMarketNews());
    }

    @Test
    void unreadableCachedJson_isTreatedAsAMiss() {
        when(values.get("news:market")).thenReturn("not json");
        when(delegate.getMarketNews()).thenReturn(List.of(ARTICLE));

        assertEquals(List.of(ARTICLE), client.getMarketNews());
    }
}
