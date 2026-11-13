package com.fintechfive.ftrade.entity;

import com.fintechfive.ftrade.enums.ActivityStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public class AccountHolding {

    private Long accountId;
    private Long instrumentId;
    private OffsetDateTime asOfDate;
    private BigDecimal quantity;
    private ActivityStatus status;

    public AccountHolding() {
    }

    public AccountHolding(Long accountId, Long instrumentId, OffsetDateTime asOfDate, BigDecimal quantity, ActivityStatus status) {
        this.accountId = accountId;
        this.instrumentId = instrumentId;
        this.asOfDate = asOfDate;
        this.quantity = quantity;
        this.status = status;
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

    public OffsetDateTime getAsOfDate() {
        return asOfDate;
    }

    public void setAsOfDate(OffsetDateTime asOfDate) {
        this.asOfDate = asOfDate;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public ActivityStatus getStatus() {
        return status;
    }

    public void setStatus(ActivityStatus status) {
        this.status = status;
    }
}
