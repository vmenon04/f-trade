package com.fintechfive.ftrade.service;

import com.fintechfive.ftrade.entity.AccountTrade;
import com.fintechfive.ftrade.enums.PerformanceRange;
import com.fintechfive.ftrade.enums.TradeSide;
import com.fintechfive.ftrade.enums.TradeStatus;
import com.fintechfive.ftrade.repository.AccountTradeMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

// An account's performance over time, for the dashboard chart: each day, what its holdings were worth at that day's
// close and what was paid for them. Holdings come from replaying the fulfilled trades; prices from the market data
// API's daily closes (today: the current price, like the dashboard total).
@Service
public class PerformanceService {

    // history is fetched from a week before the chart starts, so a chart starting on a weekend or holiday still has
    // the last close before it
    static final int CARRY_BACK_DAYS = 7;

    private final AccountTradeMapper accountTradeMapper;
    private final InstrumentService instrumentService;
    private final Clock clock;

    @Autowired
    public PerformanceService(AccountTradeMapper accountTradeMapper, InstrumentService instrumentService) {
        this(accountTradeMapper, instrumentService, Clock.systemUTC());
    }

    PerformanceService(AccountTradeMapper accountTradeMapper, InstrumentService instrumentService, Clock clock) {
        this.accountTradeMapper = accountTradeMapper;
        this.instrumentService = instrumentService;
        this.clock = clock;
    }

    /**
     * Returns one point a day, from the start of the range (or the first fulfilled trade, if later) to today.
     *
     * @param accountId account identifier
     * @param range how far back to go
     * @return the points, oldest first; none if the account has no fulfilled trades
     * @throws IllegalStateException if the price history can't be fetched right now (quota, outage)
     */
    public List<PerformancePoint> getPerformance(Long accountId, PerformanceRange range) {
        List<AccountTrade> fills = accountTradeMapper.findByAccountId(accountId).stream()
                .filter(trade -> trade.getStatus() == TradeStatus.FULFILLED && trade.getTotalPrice() != null
                        && trade.getTradeTime() != null)
                .sorted(Comparator.comparing(AccountTrade::getTradeTime))
                .toList();
        if (fills.isEmpty()) {
            return List.of();
        }

        LocalDate today = LocalDate.now(clock);
        LocalDate firstFill = dayOf(fills.get(0));
        LocalDate rangeStart = range.startFrom(today);
        LocalDate start = rangeStart == null || rangeStart.isBefore(firstFill) ? firstFill : rangeStart;

        // only instruments held at some point on the chart need prices; one sold before it starts costs no quota
        Map<Long, String> tickers = new LinkedHashMap<>();
        Map<Long, Position> atStart = new HashMap<>();
        for (AccountTrade fill : fills) {
            if (dayOf(fill).isAfter(start)) {
                tickers.computeIfAbsent(fill.getInstrumentId(), this::tickerOf);
            } else {
                atStart.computeIfAbsent(fill.getInstrumentId(), id -> new Position()).apply(fill);
            }
        }
        atStart.forEach((instrumentId, position) -> {
            if (position.quantity.signum() > 0) {
                tickers.computeIfAbsent(instrumentId, this::tickerOf);
            }
        });

        Map<String, NavigableMap<LocalDate, BigDecimal>> closes = new HashMap<>();
        for (String ticker : tickers.values()) {
            closes.put(ticker, instrumentService.getDailyCloses(ticker, start.minusDays(CARRY_BACK_DAYS), today));
        }
        Map<String, BigDecimal> currentPrices = tickers.isEmpty()
                ? Map.of()
                : instrumentService.getCurrentPrices(List.copyOf(tickers.values()));

        List<PerformancePoint> points = new ArrayList<>();
        Map<Long, Position> positions = new HashMap<>();
        int next = 0;
        for (LocalDate day = start; !day.isAfter(today); day = day.plusDays(1)) {
            // a trade counts from the day it was filled
            while (next < fills.size() && !dayOf(fills.get(next)).isAfter(day)) {
                AccountTrade fill = fills.get(next++);
                positions.computeIfAbsent(fill.getInstrumentId(), id -> new Position()).apply(fill);
            }
            BigDecimal value = BigDecimal.ZERO;
            BigDecimal invested = BigDecimal.ZERO;
            for (Map.Entry<Long, Position> entry : positions.entrySet()) {
                Position position = entry.getValue();
                if (position.quantity.signum() <= 0) {
                    continue;
                }
                String ticker = tickers.get(entry.getKey());
                value = value.add(position.quantity.multiply(priceOn(day, today, ticker, position, closes,
                        currentPrices)));
                invested = invested.add(position.cost);
            }
            points.add(new PerformancePoint(day, value.setScale(2, RoundingMode.HALF_UP),
                    invested.setScale(2, RoundingMode.HALF_UP)));
        }
        return points;
    }

    // today: the current price; other days: that day's close, or the last one before it (weekends, holidays); with
    // no history at all (e.g. a symbol the market data API is still backfilling), the price it was last filled at
    private static BigDecimal priceOn(LocalDate day, LocalDate today, String ticker, Position position,
                                      Map<String, NavigableMap<LocalDate, BigDecimal>> closes,
                                      Map<String, BigDecimal> currentPrices) {
        if (day.equals(today) && currentPrices.containsKey(ticker)) {
            return currentPrices.get(ticker);
        }
        NavigableMap<LocalDate, BigDecimal> history = closes.get(ticker);
        Map.Entry<LocalDate, BigDecimal> close = history == null ? null : history.floorEntry(day);
        return close == null ? position.lastPrice : close.getValue();
    }

    private String tickerOf(Long instrumentId) {
        return instrumentService.getInstrumentById(instrumentId).getTicker();
    }

    private static LocalDate dayOf(AccountTrade trade) {
        return trade.getTradeTime().atZoneSameInstant(ZoneOffset.UTC).toLocalDate();
    }

    // shares held of one instrument, what they cost in total (average cost, as AccountService.getCostBasis), and the
    // unit price it was last filled at
    private static final class Position {
        private BigDecimal quantity = BigDecimal.ZERO;
        private BigDecimal cost = BigDecimal.ZERO;
        private BigDecimal lastPrice = BigDecimal.ZERO;

        void apply(AccountTrade fill) {
            lastPrice = fill.getTotalPrice().divide(fill.getQuantity(), 8, RoundingMode.HALF_UP);
            if (fill.getTradeSide() == TradeSide.BUY) {
                quantity = quantity.add(fill.getQuantity());
                cost = cost.add(fill.getTotalPrice());
            } else {
                sell(fill.getQuantity());
            }
        }

        private void sell(BigDecimal shares) {
            if (quantity.signum() <= 0) {
                return;
            }
            if (shares.compareTo(quantity) >= 0) {
                quantity = BigDecimal.ZERO;
                cost = BigDecimal.ZERO;
                return;
            }
            // the sold shares take their average cost with them
            BigDecimal soldCost = cost.multiply(shares).divide(quantity, 10, RoundingMode.HALF_UP);
            cost = cost.subtract(soldCost);
            quantity = quantity.subtract(shares);
        }
    }
}
