package com.fintechfive.ftrade.controller;

import com.fintechfive.ftrade.external.NewsArticle;
import com.fintechfive.ftrade.external.NewsClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class NewsControllerTest {

    @Mock
    private NewsClient newsClient;

    @InjectMocks
    private NewsController newsController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(newsController).build();
    }

    private static List<NewsArticle> articles(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(id -> new NewsArticle(id, "Headline " + id, "Summary", "Reuters", "https://example.com/" + id,
                        null, Instant.parse("2026-10-23T12:00:00Z")))
                .toList();
    }

    @Test
    void getNews_listsMarketNews() throws Exception {
        when(newsClient.getMarketNews()).thenReturn(articles(1));

        mockMvc.perform(get("/v1/news"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].headline").value("Headline 1"))
                .andExpect(jsonPath("$[0].source").value("Reuters"))
                .andExpect(jsonPath("$[0].url").value("https://example.com/1"))
                .andExpect(jsonPath("$[0].publishedAt").exists());
        verify(newsClient, never()).getCompanyNews(anyString());
    }

    @Test
    void getNews_withASymbol_listsThatCompanysNews() throws Exception {
        when(newsClient.getCompanyNews("AAPL")).thenReturn(articles(2));

        mockMvc.perform(get("/v1/news").param("symbol", " aapl "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void getNews_returnsTenByDefault() throws Exception {
        when(newsClient.getMarketNews()).thenReturn(articles(30));

        mockMvc.perform(get("/v1/news"))
                .andExpect(jsonPath("$", hasSize(10)));
    }

    @Test
    void getNews_keepsTheLimitBetweenOneAndTheMaximum() throws Exception {
        when(newsClient.getMarketNews()).thenReturn(articles(60));

        mockMvc.perform(get("/v1/news").param("limit", "0"))
                .andExpect(jsonPath("$", hasSize(1)));
        mockMvc.perform(get("/v1/news").param("limit", "500"))
                .andExpect(jsonPath("$", hasSize(NewsController.MAX_LIMIT)));
    }
}
