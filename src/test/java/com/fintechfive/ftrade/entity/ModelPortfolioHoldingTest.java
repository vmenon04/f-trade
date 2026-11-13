package com.fintechfive.ftrade.entity;

import com.fintechfive.ftrade.enums.ActivityStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;

import static org.junit.jupiter.api.Assertions.*;

class ModelPortfolioHoldingTest {

    Month month = Month.JANUARY;

    @Test
    void noArgsConstructorLeavesFieldsAtDefaults() {
        ModelPortfolioHolding holding = new ModelPortfolioHolding();

        assertAll(
                () -> assertNull(holding.getModelPortfolioId()),
                () -> assertNull(holding.getInstrumentId()),
                () -> assertNull(holding.getEffectiveDate()),
                () -> assertNull(holding.getTargetWeightPct()),
                () -> assertNull(holding.getStatus())
        );
    }

    @Test
    void parameterizedConstructorSetsProvidedValuesAndDefaultsStatusToActive() {
        LocalDate effectiveDate = LocalDate.of(2026, 9, 25);
        ModelPortfolioHolding holding = new ModelPortfolioHolding(1L, 2L, effectiveDate, new BigDecimal("35.5"));

        assertAll(
                () -> assertEquals(1L, holding.getModelPortfolioId()),
                () -> assertEquals(2L, holding.getInstrumentId()),
                () -> assertEquals(effectiveDate, holding.getEffectiveDate()),
                () -> assertEquals(new BigDecimal("35.5"), holding.getTargetWeightPct()),
                () -> assertEquals(ActivityStatus.ACTIVE, holding.getStatus())
        );
    }

    @Test
    void settersUpdateAllMutableFields() {
        ModelPortfolioHolding holding = new ModelPortfolioHolding();
        LocalDate effectiveDate = LocalDate.of(2024, 12, 31);

        holding.setModelPortfolioId(10L);
        holding.setInstrumentId(20L);
        holding.setEffectiveDate(effectiveDate);
        holding.setTargetWeightPct(new BigDecimal("60.0"));
        holding.setStatus(ActivityStatus.INACTIVE);

        assertAll(
                () -> assertEquals(10L, holding.getModelPortfolioId()),
                () -> assertEquals(20L, holding.getInstrumentId()),
                () -> assertEquals(effectiveDate, holding.getEffectiveDate()),
                () -> assertEquals(new BigDecimal("60.0"), holding.getTargetWeightPct()),
                () -> assertEquals(ActivityStatus.INACTIVE, holding.getStatus())
        );
    }
}