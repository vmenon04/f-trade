package com.fintechfive.ftrade.entity;

import com.fintechfive.ftrade.enums.TradeSide;
import com.fintechfive.ftrade.enums.TradeStatus;

import java.math.BigDecimal;

import java.time.OffsetDateTime;
import java.util.UUID;

public class AccountTrade {

    private UUID externalTradeId;
    private Long tradeId;
    private AccountTradeStatus accountTradeStatus;
    private Long accountId;
    private Long instrumentId;
    private TradeSide tradeSide;
    private BigDecimal quantity;
    private BigDecimal ppu;
    private BigDecimal totalPrice;

    public AccountTrade() {
        this.accountTradeStatus = new AccountTradeStatus();
    }

    public AccountTrade(Long accountId, Long instrumentId, TradeSide tradeSide, BigDecimal quantity,
                        BigDecimal ppu, BigDecimal totalPrice) {
        this.tradeId = null;
        this.accountId = accountId;
        this.instrumentId = instrumentId;
        this.tradeSide = tradeSide;
        this.quantity = quantity;
        this.ppu = ppu;
        this.totalPrice = totalPrice;
        this.accountTradeStatus = new AccountTradeStatus();
        accountTradeStatus.setTradeStatus(TradeStatus.PENDING);
    }

    public UUID getExternalTradeId() {
        return externalTradeId;
    }

    public void setExternalTradeId(UUID externalTradeId) {
        this.externalTradeId = externalTradeId;
    }

    public Long getTradeId() {
        return tradeId;
    }

    public void setTradeId(Long tradeId) {
        this.tradeId = tradeId;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public Long getInstrumentId() {
        return instrumentId;
    }

    public void setInstrumentId(Long instrumentId) {
        this.instrumentId = instrumentId;
    }

    public TradeSide getTradeSide() {
        return tradeSide;
    }

    public void setTradeSide(TradeSide tradeSide) {
        this.tradeSide = tradeSide;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getPPU() {
        return ppu;
    }

    public void setPPU(BigDecimal ppu) {
        this.ppu = ppu;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(BigDecimal totalPrice) {
        this.totalPrice = totalPrice;
    }

    public TradeStatus getStatus() { return accountTradeStatus.getTradeStatus();}

    public void setTradeStatus(TradeStatus tradeStatus) {
        accountTradeStatus.setTradeStatus(tradeStatus);
    }

    public OffsetDateTime getTradeTime() {
        return accountTradeStatus.getTradeTime();
    }

    public void setTradeTime(OffsetDateTime tradeTime) {
        accountTradeStatus.setTradeTime(tradeTime);
    }
}
