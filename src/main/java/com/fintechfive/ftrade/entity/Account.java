package com.fintechfive.ftrade.entity;

import java.math.BigDecimal;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Account {

    private Long accountId;
    private UUID externalAccountId;
    private BigDecimal balance;
    Map<Instrument, AccountHolding> holdings;

    public Account() {
        this.accountId = null;
        this.externalAccountId = UUID.randomUUID();
        this.holdings = new HashMap<>();
        this.balance = BigDecimal.ZERO;
    }

    public Long getAccountId() {
        return accountId;
    }

    public  void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public UUID getExternalAccountId() {
        return externalAccountId;
    }

    public void setExternalAccountId(UUID externalAccountId) {
        this.externalAccountId = externalAccountId;
    }

    public Map<Instrument, AccountHolding> getHoldings() {
        return holdings;
    }

    public void setHoldings(HashMap<Instrument, AccountHolding> holdings) {
        this.holdings = holdings;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) { this.balance = balance; }

}
