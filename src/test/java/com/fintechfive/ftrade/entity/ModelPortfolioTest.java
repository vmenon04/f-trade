package com.fintechfive.ftrade.entity;

import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

class ModelPortfolioTest {

    @Test
    void noArgsConstructorInitializesEmptyHoldings() {
        ModelPortfolio modelPortfolio = new ModelPortfolio();

        assertAll(
                () -> assertNull(modelPortfolio.getModelPortfolioId()),
                () -> assertNull(modelPortfolio.getName()),
                () -> assertNotNull(modelPortfolio.getHoldings()),
                () -> assertTrue(modelPortfolio.getHoldings().isEmpty())
        );
    }

    @Test
    void settersUpdateAllMutableFields() {
        ModelPortfolio modelPortfolio = new ModelPortfolio();
        HashMap<ModelPortfolioHolding, Double> holdings = new HashMap<>();

        modelPortfolio.setModelPortfolioId(1L);
        modelPortfolio.setName("Conservative Income");
        modelPortfolio.setHoldings(holdings);

        assertAll(
                () -> assertEquals(1L, modelPortfolio.getModelPortfolioId()),
                () -> assertEquals("Conservative Income", modelPortfolio.getName()),
                () -> assertSame(holdings, modelPortfolio.getHoldings())
        );
    }
}