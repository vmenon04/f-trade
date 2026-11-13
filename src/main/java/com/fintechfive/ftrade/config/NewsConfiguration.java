package com.fintechfive.ftrade.config;

import com.fintechfive.ftrade.external.CachingNewsClient;
import com.fintechfive.ftrade.external.FinnhubNewsClient;
import com.fintechfive.ftrade.external.NewsClient;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class NewsConfiguration {

    // Finnhub, cached in Redis for news.cache-ttl. news.api-key is optional: without it there is no news, and that
    // isn't cached, so news shows up as soon as a key is set rather than when the cached "no news" expires
    @Bean
    public NewsClient newsClient(RestClient.Builder builder, StringRedisTemplate redis, JsonMapper json,
                                 @Value("${news.base-url:https://finnhub.io/api/v1}") String baseUrl,
                                 @Value("${news.api-key:}") String apiKey,
                                 @Value("${news.cache-ttl:10m}") Duration cacheTtl) {
        FinnhubNewsClient finnhub = new FinnhubNewsClient(builder, baseUrl, apiKey, Clock.systemUTC());
        if (apiKey.isBlank()) {
            return finnhub;
        }
        return new CachingNewsClient(finnhub, redis, json, cacheTtl);
    }
}
