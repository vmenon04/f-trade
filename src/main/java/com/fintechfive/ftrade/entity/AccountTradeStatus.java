package com.fintechfive.ftrade.entity;

import com.fintechfive.ftrade.enums.TradeStatus;

import java.time.OffsetDateTime;
import java.time.ZoneId;

public class AccountTradeStatus {

    private TradeStatus tradeStatus;
    private OffsetDateTime tradeTime;

    public TradeStatus getTradeStatus() {
        return tradeStatus;
    }

    public void setTradeStatus(TradeStatus tradeStatus) {
        this.tradeStatus = tradeStatus;
        this.tradeTime = OffsetDateTime.now(ZoneId.of("GMT")); // Set tradeTime to current time in GMT when tradeStatus is set
    }

    public OffsetDateTime getTradeTime() {
        return tradeTime;
    }

    public void setTradeTime(OffsetDateTime tradeTime) {
        this.tradeTime = tradeTime;
    }
}
