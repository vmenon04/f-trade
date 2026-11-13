package com.fintechfive.ftrade.external;

import java.math.BigDecimal;

// a price from Fauxnance; stale means it is past Fauxnance's freshness window (the last known price), which is
// fine to show but not to trade at. HttpMarketDataClient gives a symbol Fauxnance doesn't know a null price, which
// CachingMarketDataClient remembers and leaves out
public record PriceQuote(BigDecimal price, boolean stale) {
}
