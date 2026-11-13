package com.fintechfive.ftrade.entity;

import com.fintechfive.ftrade.enums.ActivityStatus;

import java.time.LocalDate;

public class AccountSubscription {

    private Long accountId;
    private Long modelPortfolioId;
    private LocalDate subscriptionDate;
    private ActivityStatus status;

    public AccountSubscription() {
    }

    public AccountSubscription(Long accountId, Long modelPortfolioId, LocalDate subscriptionDate) {
        this.accountId = accountId;
        this.modelPortfolioId = modelPortfolioId;
        this.subscriptionDate = subscriptionDate;
        this.status = ActivityStatus.ACTIVE;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public Long getModelPortfolioId() {
        return modelPortfolioId;
    }

    public void setModelPortfolioId(Long modelPortfolioId) {
        this.modelPortfolioId = modelPortfolioId;
    }

    public LocalDate getSubscriptionDate() {
        return subscriptionDate;
    }

    public void setSubscriptionDate(LocalDate subscriptionDate) {
        this.subscriptionDate = subscriptionDate;
    }

    public ActivityStatus getStatus() {
        return status;
    }

    public void setStatus(ActivityStatus status) {
        this.status = status;
    }
}
