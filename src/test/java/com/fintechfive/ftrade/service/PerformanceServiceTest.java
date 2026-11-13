package com.fintechfive.ftrade.service;

import com.fintechfive.ftrade.entity.AccountTrade;
import com.fintechfive.ftrade.entity.Instrument;
import com.fintechfive.ftrade.enums.PerformanceRange;
import com.fintechfive.ftrade.enums.TradeSide;
import com.fintechfive.ftrade.enums.TradeStatus;
import com.fintechfive.ftrade.repository.AccountTradeMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// lenient: not every test prices every instrument it sets up
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PerformanceServiceTest {

    private static final Long ACCOUNT_ID = 1L;
    private static final Long AAPL = 10L;
    private static final Long MSFT = 11L;
    private static final Long NEWCO = 12L;
    private static final Long BTC = 13L;
    // Monday 26 October 2026, mid-afternoon UTC
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 26);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-26T15:00:00Z"), ZoneOffset.UTC);

    @Mock
    private AccountTradeMapper accountTradeMapper;

    @Mock
    private InstrumentService instrumentService;

    private PerformanceService performanceService;

    @BeforeEach
    void setUp() {
        performanceService = new PerformanceService(accountTradeMapper, instrumentService, CLOCK);
        instrument(AAPL, "AAPL");
        instrument(MSFT, "MSFT");
        instrument(NEWCO, "NEWCO");
        instrument(BTC, "X:BTC-USD");
        // no history or current price unless a test gives one
        when(instrumentService.getDailyCloses(anyString(), any(), any())).thenReturn(new TreeMap<>());
        when(instrumentService.getCurrentPrices(anyList())).thenReturn(Map.of());
    }

    @Test
    void noFulfilledTrades_noPoints() {
        tradesAre();
        assertEquals(List.of(), performanceService.getPerformance(ACCOUNT_ID, PerformanceRange.ALL));
        verifyNoInteractions(instrumentService);
    }

    @Test
    void oneBuy_valuesTheSharesAtEachDaysCloseFromTheFillDateToToday() {
        tradesAre(fulfilled(AAPL, TradeSide.BUY, "2", "200", "2026-10-22"));
        closes("AAPL", "2026-10-22", "100", "2026-10-23", "110", "2026-10-26", "120");
        currentPrices(Map.of("AAPL", "125"));

        List<PerformancePoint> points = performanceService.getPerformance(ACCOUNT_ID, PerformanceRange.ALL);

        assertEquals(LocalDate.of(2026, 10, 22), points.get(0).date());
        assertEquals(TODAY, points.get(points.size() - 1).date());
        assertPoint(points, "2026-10-22", "200.00", "200.00");
        assertPoint(points, "2026-10-23", "220.00", "200.00");
        // today is valued at the current price, like the dashboard total
        assertPoint(points, "2026-10-26", "250.00", "200.00");
    }

    @Test
    void oneMonthRange_startsAMonthAgo_butCountsWhatWasBoughtBefore() {
        tradesAre(fulfilled(AAPL, TradeSide.BUY, "2", "200", "2026-06-01"));
        closes("AAPL", "2026-09-25", "150", "2026-09-28", "160");

        List<PerformancePoint> points = performanceService.getPerformance(ACCOUNT_ID, PerformanceRange.ONE_MONTH);

        assertEquals(LocalDate.of(2026, 9, 26), points.get(0).date());
        assertEquals(TODAY, points.get(points.size() - 1).date());
        // a Saturday: Friday's close carries forward
        assertPoint(points, "2026-09-26", "300.00", "200.00");
        assertPoint(points, "2026-09-28", "320.00", "200.00");
    }

    @Test
    void allRange_startsOnTheFirstFill() {
        tradesAre(
                fulfilled(AAPL, TradeSide.BUY, "1", "100", "2026-03-02"),
                fulfilled(MSFT, TradeSide.BUY, "1", "300", "2026-05-04"));

        List<PerformancePoint> points = performanceService.getPerformance(ACCOUNT_ID, PerformanceRange.ALL);

        assertEquals(LocalDate.of(2026, 3, 2), points.get(0).date());
    }

    @Test
    void rangeLongerThanTheHistory_startsOnTheFirstFill() {
        tradesAre(fulfilled(AAPL, TradeSide.BUY, "1", "100", "2026-10-20"));

        List<PerformancePoint> points = performanceService.getPerformance(ACCOUNT_ID, PerformanceRange.ONE_YEAR);

        assertEquals(LocalDate.of(2026, 10, 20), points.get(0).date());
    }

    @Test
    void sell_lowersTheSharesFromItsFillDate_andInvestedByTheAverageCost() {
        // 4 shares cost 500 (average 125); selling 1 leaves 3 at 125 = 375, as in AccountService.getCostBasis
        tradesAre(
                fulfilled(AAPL, TradeSide.BUY, "2", "200", "2026-10-19"),
                fulfilled(AAPL, TradeSide.BUY, "2", "300", "2026-10-19"),
                fulfilled(AAPL, TradeSide.SELL, "1", "999", "2026-10-21"));
        closes("AAPL", "2026-10-19", "100", "2026-10-20", "100", "2026-10-21", "100");

        List<PerformancePoint> points = performanceService.getPerformance(ACCOUNT_ID, PerformanceRange.ALL);

        assertPoint(points, "2026-10-20", "400.00", "500.00");
        assertPoint(points, "2026-10-21", "300.00", "375.00");
    }

    @Test
    void ignoresTradesThatWereNotFulfilled() {
        tradesAre(
                trade(AAPL, TradeSide.BUY, "5", "500", "2026-10-20", TradeStatus.PENDING),
                trade(AAPL, TradeSide.BUY, "5", "500", "2026-10-20", TradeStatus.ACCEPTED),
                trade(AAPL, TradeSide.BUY, "5", "500", "2026-10-20", TradeStatus.REJECTED),
                fulfilled(AAPL, TradeSide.BUY, "1", "100", "2026-10-22"));
        closes("AAPL", "2026-10-22", "100");

        List<PerformancePoint> points = performanceService.getPerformance(ACCOUNT_ID, PerformanceRange.ALL);

        assertEquals(LocalDate.of(2026, 10, 22), points.get(0).date());
        assertPoint(points, "2026-10-22", "100.00", "100.00");
    }

    @Test
    void cryptoTradesEveryDay_soEachDayUsesItsOwnClose() {
        tradesAre(fulfilled(BTC, TradeSide.BUY, "1", "60000", "2026-10-23"));
        closes("X:BTC-USD", "2026-10-23", "60000", "2026-10-24", "61000", "2026-10-25", "59000");

        List<PerformancePoint> points = performanceService.getPerformance(ACCOUNT_ID, PerformanceRange.ALL);

        assertPoint(points, "2026-10-24", "61000.00", "60000.00");
        assertPoint(points, "2026-10-25", "59000.00", "60000.00");
    }

    @Test
    void instrumentWithNoHistory_isValuedAtItsLastFillPrice() {
        tradesAre(
                fulfilled(NEWCO, TradeSide.BUY, "10", "990", "2026-10-20"),
                fulfilled(NEWCO, TradeSide.BUY, "10", "1000", "2026-10-22"));

        List<PerformancePoint> points = performanceService.getPerformance(ACCOUNT_ID, PerformanceRange.ALL);

        // 99 a unit, then 100 once the second buy fills
        assertPoint(points, "2026-10-21", "990.00", "990.00");
        assertPoint(points, "2026-10-23", "2000.00", "1990.00");
    }

    @Test
    void severalInstruments_addUpEachDay() {
        tradesAre(
                fulfilled(AAPL, TradeSide.BUY, "2", "200", "2026-10-22"),
                fulfilled(MSFT, TradeSide.BUY, "1", "300", "2026-10-23"));
        closes("AAPL", "2026-10-22", "100", "2026-10-23", "110");
        closes("MSFT", "2026-10-23", "310");

        List<PerformancePoint> points = performanceService.getPerformance(ACCOUNT_ID, PerformanceRange.ALL);

        assertPoint(points, "2026-10-22", "200.00", "200.00");
        assertPoint(points, "2026-10-23", "530.00", "500.00");
    }

    @Test
    void doesNotFetchHistoryForInstrumentsSoldBeforeTheRange() {
        tradesAre(
                fulfilled(MSFT, TradeSide.BUY, "1", "300", "2026-01-05"),
                fulfilled(MSFT, TradeSide.SELL, "1", "320", "2026-02-02"),
                fulfilled(AAPL, TradeSide.BUY, "1", "100", "2026-03-02"));

        performanceService.getPerformance(ACCOUNT_ID, PerformanceRange.ONE_MONTH);

        verify(instrumentService).getDailyCloses(eq("AAPL"), any(), any());
        verify(instrumentService, never()).getDailyCloses(eq("MSFT"), any(), any());
    }

    @Test
    void failedHistoryLookup_propagates() {
        tradesAre(fulfilled(AAPL, TradeSide.BUY, "1", "100", "2026-10-22"));
        when(instrumentService.getDailyCloses(eq("AAPL"), any(), any()))
                .thenThrow(new IllegalStateException("Fauxnance returned 503"));

        assertThrows(IllegalStateException.class,
                () -> performanceService.getPerformance(ACCOUNT_ID, PerformanceRange.ALL));
    }

    // --- helpers --------------------------------------------------------------------------------------

    private void instrument(Long instrumentId, String ticker) {
        Instrument instrument = mock(Instrument.class);
        when(instrument.getTicker()).thenReturn(ticker);
        when(instrumentService.getInstrumentById(instrumentId)).thenReturn(instrument);
    }

    private void tradesAre(AccountTrade... trades) {
        when(accountTradeMapper.findByAccountId(ACCOUNT_ID)).thenReturn(List.of(trades));
    }

    // pairs of date and close
    private void closes(String ticker, String... dateAndClose) {
        NavigableMap<LocalDate, BigDecimal> closes = new TreeMap<>();
        for (int i = 0; i < dateAndClose.length; i += 2) {
            closes.put(LocalDate.parse(dateAndClose[i]), new BigDecimal(dateAndClose[i + 1]));
        }
        when(instrumentService.getDailyCloses(eq(ticker), any(), any())).thenReturn(closes);
    }

    private void currentPrices(Map<String, String> prices) {
        Map<String, BigDecimal> converted = new java.util.HashMap<>();
        prices.forEach((ticker, price) -> converted.put(ticker, new BigDecimal(price)));
        when(instrumentService.getCurrentPrices(anyList())).thenReturn(converted);
    }

    private static void assertPoint(List<PerformancePoint> points, String date, String value, String invested) {
        PerformancePoint point = points.stream()
                .filter(p -> p.date().equals(LocalDate.parse(date)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no point for " + date));
        assertEquals(new BigDecimal(value), point.value(), "value on " + date);
        assertEquals(new BigDecimal(invested), point.invested(), "invested on " + date);
    }

    private static AccountTrade fulfilled(Long instrumentId, TradeSide side, String quantity, String totalPrice,
                                          String date) {
        return trade(instrumentId, side, quantity, totalPrice, date, TradeStatus.FULFILLED);
    }

    private static AccountTrade trade(Long instrumentId, TradeSide side, String quantity, String totalPrice,
                                      String date, TradeStatus status) {
        AccountTrade trade = new AccountTrade();
        trade.setAccountId(ACCOUNT_ID);
        trade.setInstrumentId(instrumentId);
        trade.setTradeSide(side);
        trade.setQuantity(new BigDecimal(quantity));
        trade.setTotalPrice(new BigDecimal(totalPrice));
        trade.setPPU(new BigDecimal(totalPrice).divide(new BigDecimal(quantity), 8, java.math.RoundingMode.HALF_UP));
        trade.setTradeStatus(status);
        trade.setTradeTime(OffsetDateTime.parse(date + "T14:30:00Z"));
        return trade;
    }
}
