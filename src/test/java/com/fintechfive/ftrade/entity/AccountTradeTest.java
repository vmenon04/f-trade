package com.fintechfive.ftrade.entity;

import com.fintechfive.ftrade.enums.TradeStatus;
import com.fintechfive.ftrade.enums.TradeSide;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AccountTradeTest {

    @Test
    void noArgsConstructorLeavesFieldsAtDefaults() {
        AccountTrade trade = new AccountTrade();

        assertAll(
                () -> assertNull(trade.getTradeId()),
                () -> assertNull(trade.getAccountId()),
                () -> assertNull(trade.getTradeTime()),
                () -> assertNull(trade.getInstrumentId()),
                () -> assertNull(trade.getTradeSide()),
                () -> assertNull(trade.getQuantity()),
                () -> assertNull(trade.getPPU()),
                () -> assertNull(trade.getTotalPrice()),
                () -> assertNull(trade.getStatus())
        );
    }

    @Test
    void parameterizedConstructorSetsProvidedValuesAndDefaultsStatusToSubmitted() {
        OffsetDateTime start = OffsetDateTime.now();
        AccountTrade trade = new AccountTrade(101L, 7L, TradeSide.BUY,
            new BigDecimal("10.5"), new BigDecimal("55.25"), new BigDecimal("579.375"));
        OffsetDateTime end = OffsetDateTime.now();

        assertAll(
                () -> assertNull(trade.getTradeId()),
                () -> assertEquals(101L, trade.getAccountId()),
                () -> assertTrue(start.isBefore(trade.getTradeTime()) || start.isEqual(trade.getTradeTime())),
                () -> assertTrue(end.isAfter(trade.getTradeTime()) || end.isEqual(trade.getTradeTime())),
                () -> assertEquals(7L, trade.getInstrumentId()),
                () -> assertEquals(TradeSide.BUY, trade.getTradeSide()),
                () -> assertEquals(BigDecimal.valueOf(10.5), trade.getQuantity()),
                () -> assertEquals(BigDecimal.valueOf(55.25), trade.getPPU()),
                () -> assertEquals(new BigDecimal("579.375"), trade.getTotalPrice()),
                () -> assertEquals(TradeStatus.PENDING, trade.getStatus())
        );
    }

    @Test
    void settersUpdateAllMutableFields() {
        AccountTrade trade = new AccountTrade();

        trade.setTradeId(1L);
        trade.setAccountId(2L);
        trade.setInstrumentId(3L);
        trade.setTradeSide(TradeSide.SELL);
        trade.setQuantity(BigDecimal.valueOf(4.5));
        trade.setPPU(BigDecimal.valueOf(6.75));
        trade.setTotalPrice(BigDecimal.valueOf(30.375));
        trade.setTradeStatus(TradeStatus.ACCEPTED);

        assertAll(
                () -> assertEquals(1L, trade.getTradeId()),
                () -> assertEquals(2, trade.getAccountId()),
                () -> assertEquals(3, trade.getInstrumentId()),
                () -> assertEquals(TradeSide.SELL, trade.getTradeSide()),
                () -> assertEquals(BigDecimal.valueOf(4.5), trade.getQuantity()),
                () -> assertEquals(BigDecimal.valueOf(6.75), trade.getPPU()),
                () -> assertEquals(BigDecimal.valueOf(30.375), trade.getTotalPrice()),
                () -> assertEquals(TradeStatus.ACCEPTED, trade.getStatus())
        );
    }

    @Test
    void settersUpdateExternalTradeIdAndTradeTime() {
        AccountTrade trade = new AccountTrade();
        UUID externalTradeId = UUID.randomUUID();
        OffsetDateTime tradeTime = OffsetDateTime.parse("2026-08-26T16:13:24Z");

        trade.setExternalTradeId(externalTradeId);
        trade.setTradeTime(tradeTime);

        assertAll(
                () -> assertEquals(externalTradeId, trade.getExternalTradeId()),
                () -> assertEquals(tradeTime, trade.getTradeTime())
        );
    }
}
