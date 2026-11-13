package com.fintechfive.ftrade.external;

import java.util.List;

// news from a market news API (Finnhub), newest first
public interface NewsClient {

    // general market news
    List<NewsArticle> getMarketNews();

    // news about one company, e.g. AAPL, from the last few days
    List<NewsArticle> getCompanyNews(String symbol);
}
