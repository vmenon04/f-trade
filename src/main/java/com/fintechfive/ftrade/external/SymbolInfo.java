package com.fintechfive.ftrade.external;

// what Fauxnance knows about a symbol (GET /symbols/{symbol}); type is equity, etf, fx or crypto
public record SymbolInfo(String symbol, String name, String type) {
}
