package com.fintechfive.ftrade.service;

import com.fintechfive.ftrade.dto.TradeSubmittedDTO;
import com.fintechfive.ftrade.enums.RejectionCode;
import com.fintechfive.ftrade.enums.TradeSide;
import com.fintechfive.ftrade.enums.TradeStage;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TradeTrackerServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-26T12:00:00Z");
    private static final Duration TTL = Duration.ofHours(1);
    private static final UUID ACCOUNT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TRADE = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String TRADE_KEY = "trade:progress:" + TRADE;
    private static final String ACCOUNT_KEY = "trades:recent:" + ACCOUNT;

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private HashOperations<String, Object, Object> hashes;

    @Mock
    private ZSetOperations<String, String> sortedSets;

    private TradeTrackerService tracker;

    @BeforeEach
    void setUp() {
        when(redis.opsForHash()).thenReturn(hashes);
        when(redis.opsForZSet()).thenReturn(sortedSets);
        tracker = new TradeTrackerService(redis, Clock.fixed(NOW, ZoneOffset.UTC), TTL);
    }

    private static TradeSubmittedDTO submitted() {
        return new TradeSubmittedDTO(10L, ACCOUNT, TradeSide.BUY, new BigDecimal("5"), TRADE);
    }

    // --- writing ----------------------------------------------------------------------------------------------

    @Test
    void submitted_storesTheTradeAndAddsItToTheAccountsList() {
        tracker.submitted(submitted());

        verify(hashes).putAll(TRADE_KEY, Map.of(
                "account", ACCOUNT.toString(),
                "instrumentId", "10",
                "side", "BUY",
                "quantity", "5",
                "submittedAt", NOW.toString()));
        verify(redis).expire(TRADE_KEY, TTL);
        verify(sortedSets).add(ACCOUNT_KEY, TRADE.toString(), NOW.toEpochMilli());
        verify(redis).expire(ACCOUNT_KEY, TTL);
    }

    // so a spammer's list can't grow without end
    @Test
    void submitted_keepsOnlyTheNewest20OfTheAccountsTrades() {
        tracker.submitted(submitted());

        verify(sortedSets).removeRange(ACCOUNT_KEY, 0, -21);
    }

    @Test
    void validated_recordsWhenAndRefreshesTheExpiry() {
        tracker.validated(TRADE);

        verify(hashes).put(TRADE_KEY, "validatedAt", NOW.toString());
        verify(redis).expire(TRADE_KEY, TTL);
    }

    @Test
    void recorded_recordsWhen() {
        tracker.recorded(TRADE);

        verify(hashes).put(TRADE_KEY, "recordedAt", NOW.toString());
        verify(redis).expire(TRADE_KEY, TTL);
    }

    @Test
    void fulfilled_recordsWhenAndThePrice() {
        tracker.fulfilled(TRADE, new BigDecimal("101.50"));

        verify(hashes).putAll(TRADE_KEY, Map.of("fulfilledAt", NOW.toString(), "price", "101.50"));
        verify(redis).expire(TRADE_KEY, TTL);
    }

    @Test
    void rejected_recordsWhenAndTheCode() {
        tracker.rejected(TRADE, RejectionCode.INSUFFICIENT_FUNDS);

        verify(hashes).putAll(TRADE_KEY, Map.of("rejectedAt", NOW.toString(), "rejectionCode", "INSUFFICIENT_FUNDS"));
        verify(redis).expire(TRADE_KEY, TTL);
    }

    @Test
    void rejected_withNoCode_isUnknown() {
        tracker.rejected(TRADE, null);

        verify(hashes).putAll(TRADE_KEY, Map.of("rejectedAt", NOW.toString(), "rejectionCode", "UNKNOWN"));
    }

    // a message from before trades carried their external id
    @Test
    void stepWithoutATradeId_isIgnored() {
        tracker.validated(null);
        tracker.recorded(null);
        tracker.fulfilled(null, BigDecimal.ONE);
        tracker.rejected(null, RejectionCode.UNKNOWN);

        verifyNoInteractions(hashes, sortedSets);
        verify(redis, never()).expire(any(), any(Duration.class));
    }

    // the trade still goes through; it just isn't shown as it moves
    @Test
    void redisDown_writesDontThrow() {
        doThrow(new RedisConnectionFailureException("down")).when(hashes).putAll(any(), any());
        doThrow(new RedisConnectionFailureException("down")).when(hashes).put(any(), any(), any());

        assertDoesNotThrow(() -> tracker.submitted(submitted()));
        assertDoesNotThrow(() -> tracker.validated(TRADE));
        assertDoesNotThrow(() -> tracker.recorded(TRADE));
        assertDoesNotThrow(() -> tracker.fulfilled(TRADE, BigDecimal.ONE));
        assertDoesNotThrow(() -> tracker.rejected(TRADE, RejectionCode.UNKNOWN));
    }

    // --- listing ----------------------------------------------------------------------------------------------

    private void givenTheAccountsTrades(UUID... trades) {
        Set<String> ids = new LinkedHashSet<>();
        for (UUID trade : trades) {
            ids.add(trade.toString());
        }
        when(sortedSets.reverseRange(ACCOUNT_KEY, 0, 19)).thenReturn(ids);
    }

    private static Map<Object, Object> stored(String... fieldsAndValues) {
        Map<Object, Object> fields = new java.util.HashMap<>(Map.of(
                "account", ACCOUNT.toString(), "instrumentId", "10", "side", "SELL", "quantity", "2.5",
                "submittedAt", "2026-10-26T11:59:00Z"));
        for (int i = 0; i < fieldsAndValues.length; i += 2) {
            fields.put(fieldsAndValues[i], fieldsAndValues[i + 1]);
        }
        return fields;
    }

    @Test
    void recentTrades_returnsTheStoredTrade() {
        givenTheAccountsTrades(TRADE);
        when(hashes.entries(TRADE_KEY)).thenReturn(stored());

        List<TrackedTrade> trades = tracker.recentTrades(ACCOUNT);

        assertEquals(1, trades.size());
        TrackedTrade trade = trades.get(0);
        assertEquals(TRADE, trade.externalTradeId());
        assertEquals(10L, trade.instrumentId());
        assertEquals(TradeSide.SELL, trade.side());
        assertEquals(0, trade.quantity().compareTo(new BigDecimal("2.5")));
        assertEquals(TradeStage.SUBMITTED, trade.stage());
        assertEquals(Instant.parse("2026-10-26T11:59:00Z"), trade.submittedAt());
        assertNull(trade.validatedAt());
        assertNull(trade.price());
        assertNull(trade.rejectionCode());
    }

    @Test
    void recentTrades_newestFirst() {
        UUID older = UUID.fromString("33333333-3333-3333-3333-333333333333");
        givenTheAccountsTrades(TRADE, older);
        when(hashes.entries(TRADE_KEY)).thenReturn(stored());
        when(hashes.entries("trade:progress:" + older)).thenReturn(stored());

        List<TrackedTrade> trades = tracker.recentTrades(ACCOUNT);

        assertEquals(List.of(TRADE, older), trades.stream().map(TrackedTrade::externalTradeId).toList());
    }

    @Test
    void recentTrades_stageIsTheLatestStepReached() {
        givenTheAccountsTrades(TRADE);

        when(hashes.entries(TRADE_KEY)).thenReturn(stored("validatedAt", "2026-10-26T11:59:01Z"));
        assertEquals(TradeStage.VALIDATED, tracker.recentTrades(ACCOUNT).get(0).stage());

        when(hashes.entries(TRADE_KEY)).thenReturn(stored("validatedAt", "2026-10-26T11:59:01Z",
                "recordedAt", "2026-10-26T11:59:02Z"));
        assertEquals(TradeStage.PENDING, tracker.recentTrades(ACCOUNT).get(0).stage());

        when(hashes.entries(TRADE_KEY)).thenReturn(stored("validatedAt", "2026-10-26T11:59:01Z",
                "recordedAt", "2026-10-26T11:59:02Z", "fulfilledAt", "2026-10-26T11:59:03Z", "price", "101.50"));
        TrackedTrade fulfilled = tracker.recentTrades(ACCOUNT).get(0);
        assertEquals(TradeStage.FULFILLED, fulfilled.stage());
        assertEquals(Instant.parse("2026-10-26T11:59:03Z"), fulfilled.finishedAt());
        assertEquals(0, fulfilled.price().compareTo(new BigDecimal("101.50")));
    }

    // steps can arrive out of order (they come from different topics); a late one doesn't move the stage back
    @Test
    void recentTrades_lateStepDoesNotMoveTheStageBack() {
        givenTheAccountsTrades(TRADE);
        when(hashes.entries(TRADE_KEY)).thenReturn(stored("recordedAt", "2026-10-26T11:59:02Z",
                "fulfilledAt", "2026-10-26T11:59:03Z", "price", "101.50", "validatedAt", "2026-10-26T11:59:04Z"));

        assertEquals(TradeStage.FULFILLED, tracker.recentTrades(ACCOUNT).get(0).stage());
    }

    @Test
    void recentTrades_rejectedTradeHasItsCode() {
        givenTheAccountsTrades(TRADE);
        when(hashes.entries(TRADE_KEY)).thenReturn(stored("rejectedAt", "2026-10-26T11:59:01Z",
                "rejectionCode", "INSUFFICIENT_SHARES"));

        TrackedTrade rejected = tracker.recentTrades(ACCOUNT).get(0);

        assertEquals(TradeStage.REJECTED, rejected.stage());
        assertEquals(RejectionCode.INSUFFICIENT_SHARES, rejected.rejectionCode());
        assertEquals(Instant.parse("2026-10-26T11:59:01Z"), rejected.finishedAt());
        assertNull(rejected.validatedAt());
    }

    @Test
    void recentTrades_skipsTradesThatHaveExpired() {
        givenTheAccountsTrades(TRADE);
        when(hashes.entries(TRADE_KEY)).thenReturn(Map.of());

        assertTrue(tracker.recentTrades(ACCOUNT).isEmpty());
    }

    // a step stored before Submitted arrived (or after it expired) isn't a whole trade
    @Test
    void recentTrades_skipsTradesWithoutTheirSubmittedStep() {
        givenTheAccountsTrades(TRADE);
        when(hashes.entries(TRADE_KEY)).thenReturn(Map.of("validatedAt", "2026-10-26T11:59:01Z"));

        assertTrue(tracker.recentTrades(ACCOUNT).isEmpty());
    }

    @Test
    void recentTrades_noTrades_isEmpty() {
        when(sortedSets.reverseRange(ACCOUNT_KEY, 0, 19)).thenReturn(Set.of());

        assertTrue(tracker.recentTrades(ACCOUNT).isEmpty());
    }

    @Test
    void recentTrades_redisDown_isEmpty() {
        when(sortedSets.reverseRange(ACCOUNT_KEY, 0, 19)).thenThrow(new RedisConnectionFailureException("down"));

        assertTrue(tracker.recentTrades(ACCOUNT).isEmpty());
    }
}
