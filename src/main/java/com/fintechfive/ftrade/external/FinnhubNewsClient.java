package com.fintechfive.ftrade.external;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import org.springframework.web.client.RestClient;

// NewsClient backed by Finnhub (GET /news?category=general and GET /company-news). Without an API key there is no
// news, rather than an error, so the app runs without one
public class FinnhubNewsClient implements NewsClient {

    // how far back company news goes
    static final int COMPANY_NEWS_DAYS = 7;

    private final RestClient restClient;
    private final boolean hasKey;
    private final Clock clock;

    public FinnhubNewsClient(RestClient.Builder builder, String baseUrl, String apiKey, Clock clock) {
        this.restClient = builder
                .baseUrl(baseUrl)
                .defaultHeader("X-Finnhub-Token", apiKey)
                .build();
        this.hasKey = apiKey != null && !apiKey.isBlank();
        this.clock = clock;
    }

    @Override
    public List<NewsArticle> getMarketNews() {
        if (!hasKey) {
            return List.of();
        }
        return fetch(restClient.get().uri(uri -> uri.path("/news").queryParam("category", "general").build()),
                "market news");
    }

    @Override
    public List<NewsArticle> getCompanyNews(String symbol) {
        if (!hasKey) {
            return List.of();
        }
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        return fetch(restClient.get().uri(uri -> uri.path("/company-news")
                .queryParam("symbol", symbol)
                .queryParam("from", today.minusDays(COMPANY_NEWS_DAYS))
                .queryParam("to", today)
                .build()), "news for " + symbol);
    }

    // 401/403: bad key or a premium-only request; 429: over Finnhub's calls a minute; 5xx: API unavailable
    private static List<NewsArticle> fetch(RestClient.RequestHeadersSpec<?> request, String what) {
        Item[] items = request.exchange((req, response) -> {
            if (response.getStatusCode().isError()) {
                throw new IllegalStateException("Finnhub returned " + response.getStatusCode().value() + " for " + what);
            }
            return response.bodyTo(Item[].class);
        });
        if (items == null) {
            return List.of();
        }
        return Arrays.stream(items)
                .filter(item -> item.headline() != null && !item.headline().isBlank() && item.url() != null)
                .map(item -> new NewsArticle(item.id(), item.headline(), item.summary(), item.source(), item.url(),
                        item.image() == null || item.image().isBlank() ? null : item.image(),
                        Instant.ofEpochSecond(item.datetime())))
                .toList();
    }

    // an article in Finnhub's JSON; datetime is in seconds since 1970
    record Item(long id, String headline, String summary, String source, String url, String image, long datetime) {
    }
}
