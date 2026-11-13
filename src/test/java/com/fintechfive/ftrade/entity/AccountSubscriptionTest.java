package com.fintechfive.ftrade.entity;

import com.fintechfive.ftrade.enums.ActivityStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.Month;

import static org.junit.jupiter.api.Assertions.*;

class AccountSubscriptionTest {

    Month month = Month.JANUARY;

    @Test
    void noArgsConstructorLeavesFieldsAtDefaults() {
        AccountSubscription subscription = new AccountSubscription();

        assertAll(
                () -> assertNull(subscription.getAccountId()),
                () -> assertNull(subscription.getModelPortfolioId()),
                () -> assertNull(subscription.getSubscriptionDate()),
                () -> assertNull(subscription.getStatus())
        );
    }

    @Test
    void parameterizedConstructorSetsIdsAndDateAndDefaultsStatusToActive() {
        LocalDate date = LocalDate.of(2026, month, 25);
        AccountSubscription subscription = new AccountSubscription(101L, 7L, date);

        assertAll(
                () -> assertEquals(101, subscription.getAccountId()),
                () -> assertEquals(7, subscription.getModelPortfolioId()),
                () -> assertEquals(date, subscription.getSubscriptionDate()),
                () -> assertEquals(ActivityStatus.ACTIVE, subscription.getStatus())
        );
    }

    @Test
    void settersUpdateAllMutableFields() {
        AccountSubscription subscription = new AccountSubscription();
        LocalDate date = LocalDate.of(2025, month, 30);

        subscription.setAccountId(11L);
        subscription.setModelPortfolioId(22L);
        subscription.setSubscriptionDate(date);
        subscription.setStatus(ActivityStatus.INACTIVE);

        assertAll(
                () -> assertEquals(11, subscription.getAccountId()),
                () -> assertEquals(22, subscription.getModelPortfolioId()),
                () -> assertEquals(date, subscription.getSubscriptionDate()),
                () -> assertEquals(ActivityStatus.INACTIVE, subscription.getStatus())
        );
    }
}
