package com.fintechfive.ftrade.entity;

import com.fintechfive.ftrade.enums.ActivityStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

class AccountHoldingTest {

    @Test
    void noArgsConstructorLeavesFieldsAtJavaDefaults() {
        AccountHolding holding = new AccountHolding();

        assertAll(
                () -> assertNull(holding.getAccountId()),
                () -> assertNull(holding.getInstrumentId()),
                () -> assertNull(holding.getAsOfDate()),
                () -> assertNull(holding.getQuantity()),
                () -> assertNull(holding.getStatus())
        );
    }

    @Test
    void parameterizedConstructorSetsProvidedValues() {
        OffsetDateTime date = OffsetDateTime.now();
        AccountHolding holding = new AccountHolding(101L, 1L, date, new BigDecimal("100.5"), ActivityStatus.ACTIVE);

        assertAll(
                () -> assertEquals(101L, holding.getAccountId()),
                () -> assertEquals(1L, holding.getInstrumentId()),
                () -> assertEquals(date, holding.getAsOfDate()),
                () -> assertEquals(BigDecimal.valueOf(100.5), holding.getQuantity()),
                () -> assertEquals(ActivityStatus.ACTIVE, holding.getStatus())
        );
    }

    @Test
    void settersUpdateFields() {
        AccountHolding holding = new AccountHolding();
        OffsetDateTime date = OffsetDateTime.now();

        holding.setAccountId(200L);
        holding.setInstrumentId(300L);
        holding.setAsOfDate(date);
        holding.setQuantity(new BigDecimal("55.75"));
        holding.setStatus(ActivityStatus.INACTIVE);

        assertAll(
                () -> assertEquals(200L, holding.getAccountId()),
                () -> assertEquals(300L, holding.getInstrumentId()),
                () -> assertEquals(date, holding.getAsOfDate()),
                () -> assertEquals(BigDecimal.valueOf(55.75), holding.getQuantity()),
                () -> assertEquals(ActivityStatus.INACTIVE, holding.getStatus())
        );
    }
}