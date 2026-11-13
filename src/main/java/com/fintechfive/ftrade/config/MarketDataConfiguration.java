package com.fintechfive.ftrade.config;

import com.fintechfive.ftrade.external.CachingMarketDataClient;
import com.fintechfive.ftrade.external.HttpMarketDataClient;
import com.fintechfive.ftrade.external.MarketDataClient;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.web.client.RestClient;

@Configuration
@EnableKafka
public class MarketDataConfiguration {

    // Fauxnance, cached in Redis (fresh prices for market.data.cache-ttl, e.g. 5m) and kept within
    // market.data.daily-limit calls a day, Fauxnance's quota for the key
    @Bean
    public MarketDataClient marketDataClient(RestClient.Builder builder, StringRedisTemplate redis,
                                             @Value("${market.data.base-url}") String baseUrl,
                                             @Value("${market.data.api-key}") String apiKey,
                                             @Value("${market.data.cache-ttl}") Duration cacheTtl,
                                             @Value("${market.data.daily-limit:2000}") long dailyLimit) {
        return new CachingMarketDataClient(new HttpMarketDataClient(builder, baseUrl, apiKey), redis, cacheTtl,
                dailyLimit, Clock.systemUTC());
    }
}
