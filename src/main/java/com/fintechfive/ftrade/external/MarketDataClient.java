package com.fintechfive.ftrade.external;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;

// wraps our external market data API
public interface MarketDataClient {

    BigDecimal getPrice(String ticker);

    // prices for several tickers at once, for showing (stale quotes included and marked stale);
    // a ticker with no price right now is left out of the map
    Map<String, PriceQuote> getPrices(Collection<String> tickers);

    // name and type of a symbol, or null if the market data API doesn't know it; throws if it can't say right now
    // (e.g. the symbol is still being fetched)
    SymbolInfo getSymbol(String symbol);

    // daily closing prices from `from` to `to` (inclusive), oldest first; none if the API has no history for the
    // symbol (yet); throws if it can't say right now
    List<DailyClose> getCandles(String ticker, LocalDate from, LocalDate to);
}
