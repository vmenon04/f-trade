package com.fintechfive.ftrade.external;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

// use a fake HTTP server
class FinnhubNewsClientTest {

    private static final String BASE_URL = "https://finnhub.test/api/v1";
    private static final String API_KEY = "test-key";
    // 23 October 2026, so company news goes back to 16 October
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-23T15:00:00Z"), ZoneOffset.UTC);

    private MockRestServiceServer server;
    private FinnhubNewsClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new FinnhubNewsClient(builder, BASE_URL, API_KEY, CLOCK);
    }

    // Shape of an article in Finnhub's /news and /company-news responses
    private static String articleJson(long id, String headline, String image) {
        return """
                {"category": "top news", "datetime": 1792771200, "headline": "%s", "id": %d, "image": "%s",
                 "related": "", "source": "Reuters", "summary": "What happened today.",
                 "url": "https://example.com/%d"}
                """.formatted(headline, id, image, id);
    }

    @Test
    void getMarketNews_readsTheArticlesWithTheKeyInAHeader() {
        server.expect(requestTo(BASE_URL + "/news?category=general"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Finnhub-Token", API_KEY))
                .andRespond(withSuccess("[" + articleJson(7, "Stocks rise", "https://example.com/7.jpg") + "]",
                        MediaType.APPLICATION_JSON));

        List<NewsArticle> news = client.getMarketNews();

        assertEquals(List.of(new NewsArticle(7, "Stocks rise", "What happened today.", "Reuters",
                "https://example.com/7", "https://example.com/7.jpg", Instant.ofEpochSecond(1792771200))), news);
        server.verify();
    }

    @Test
    void getCompanyNews_asksForTheLastWeekOfTheSymbol() {
        server.expect(requestTo(BASE_URL + "/company-news?symbol=AAPL&from=2026-10-16&to=2026-10-23"))
                .andExpect(header("X-Finnhub-Token", API_KEY))
                .andRespond(withSuccess("[" + articleJson(1, "Apple news", "") + "]", MediaType.APPLICATION_JSON));

        List<NewsArticle> news = client.getCompanyNews("AAPL");

        assertEquals(1, news.size());
        assertEquals("Apple news", news.get(0).headline());
        server.verify();
    }

    @Test
    void anArticleWithoutAPicture_hasANullImage() {
        server.expect(requestTo(BASE_URL + "/news?category=general"))
                .andRespond(withSuccess("[" + articleJson(1, "No picture", "") + "]", MediaType.APPLICATION_JSON));

        assertNull(client.getMarketNews().get(0).image());
    }

    @Test
    void anArticleWithoutAHeadline_isLeftOut() {
        server.expect(requestTo(BASE_URL + "/news?category=general"))
                .andRespond(withSuccess("[" + articleJson(1, "", "") + "," + articleJson(2, "Kept", "") + "]",
                        MediaType.APPLICATION_JSON));

        List<NewsArticle> news = client.getMarketNews();

        assertEquals(1, news.size());
        assertEquals("Kept", news.get(0).headline());
    }

    @Test
    void anEmptyResponse_isNoNews() {
        server.expect(requestTo(BASE_URL + "/news?category=general"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertEquals(List.of(), client.getMarketNews());
    }

    @Test
    void aBadKey_throws() {
        server.expect(requestTo(BASE_URL + "/news?category=general"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThrows(IllegalStateException.class, client::getMarketNews);
    }

    @Test
    void tooManyCalls_throws() {
        server.expect(requestTo(BASE_URL + "/company-news?symbol=AAPL&from=2026-10-16&to=2026-10-23"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThrows(IllegalStateException.class, () -> client.getCompanyNews("AAPL"));
    }

    @Test
    void withoutAKey_thereIsNoNewsAndFinnhubIsNotCalled() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer noCalls = MockRestServiceServer.bindTo(builder).build();
        FinnhubNewsClient keyless = new FinnhubNewsClient(builder, BASE_URL, "", CLOCK);

        assertEquals(List.of(), keyless.getMarketNews());
        assertEquals(List.of(), keyless.getCompanyNews("AAPL"));
        noCalls.verify();
    }
}
