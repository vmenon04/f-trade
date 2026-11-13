package com.fintechfive.ftrade.external;

import java.time.Instant;

// a news story to show: its headline and summary, who published it and when, and links to it and its picture
public record NewsArticle(long id, String headline, String summary, String source, String url, String image,
                          Instant publishedAt) {
}
