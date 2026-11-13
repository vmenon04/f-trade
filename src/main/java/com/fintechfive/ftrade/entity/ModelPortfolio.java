package com.fintechfive.ftrade.entity;

import java.util.HashMap;
import java.util.Map;

public class ModelPortfolio {
    private Long modelPortfolioId;
    private String name;
    private Map<ModelPortfolioHolding, Double> holdings;

    public ModelPortfolio() {
        this.holdings = new HashMap<>();
    }

    public Long getModelPortfolioId() {
        return modelPortfolioId;
    }


    public void  setModelPortfolioId(Long modelPortfolioId) {
        this.modelPortfolioId = modelPortfolioId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<ModelPortfolioHolding, Double> getHoldings() {
        return holdings;
    }

    public void setHoldings(Map<ModelPortfolioHolding, Double> holdings) {
        this.holdings = holdings;
    }
}
