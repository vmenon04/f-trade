package com.fintechfive.ftrade.controller;

import com.fintechfive.ftrade.external.NewsArticle;
import com.fintechfive.ftrade.external.NewsClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/v1/news")
public class NewsController {

    static final int MAX_LIMIT = 50;

    private final NewsClient newsClient;

    public NewsController(NewsClient newsClient) {
        this.newsClient = newsClient;
    }

    // The newest `limit` stories (from Finnhub, cached for a few minutes): general market news, or with symbol=AAPL
    // news about that company from the last week. An empty list when there is none (or no Finnhub key is set)
    @GetMapping
    public List<NewsArticle> getNews(@RequestParam(required = false) String symbol,
                                     @RequestParam(defaultValue = "10") int limit) {
        List<NewsArticle> articles = symbol == null || symbol.isBlank()
                ? newsClient.getMarketNews()
                : newsClient.getCompanyNews(symbol.trim().toUpperCase(Locale.ROOT));
        return articles.stream().limit(Math.max(1, Math.min(limit, MAX_LIMIT))).toList();
    }
}
