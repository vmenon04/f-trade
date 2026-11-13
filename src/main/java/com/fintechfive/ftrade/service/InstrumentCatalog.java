package com.fintechfive.ftrade.service;

import com.fintechfive.ftrade.entity.Instrument;
import com.fintechfive.ftrade.enums.InstrumentType;
import com.fintechfive.ftrade.external.MarketDataClient;
import com.fintechfive.ftrade.external.SymbolInfo;
import com.fintechfive.ftrade.repository.InstrumentMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Locale;

/**
 * The instruments users can trade. The instruments table is the record of them: a symbol is looked up there first;
 * on a miss its details come from the market data API (Fauxnance, which the caching client caches) and it gets a row
 * and id in the table, which trades and holdings reference.
 */
@Service
public class InstrumentCatalog {

    private static final Logger LOGGER = LoggerFactory.getLogger(InstrumentCatalog.class);

    private final MarketDataClient marketDataClient;
    private final InstrumentMapper instrumentMapper;

    public InstrumentCatalog(MarketDataClient marketDataClient, InstrumentMapper instrumentMapper) {
        this.marketDataClient = marketDataClient;
        this.instrumentMapper = instrumentMapper;
    }

    // assetClass is the InstrumentType name, e.g. STOCK
    public record CatalogInstrument(Long instrumentId, String ticker, String name, String assetClass) {
    }

    // one page of a search, and how many instruments match in all
    public record Page(List<CatalogInstrument> instruments, long total) {
    }

    /**
     * Finds an instrument by symbol: the database first, then the market data API.
     *
     * @param symbol a Fauxnance symbol, e.g. ORCL or X:BTC-USD (case-insensitive)
     * @return the instrument with its database id, or null if the symbol is blank, unknown or can't be looked up now
     */
    public CatalogInstrument lookup(String symbol) {
        String ticker = symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
        if (ticker.isEmpty()) {
            return null;
        }
        Instrument existing = instrumentMapper.findByTicker(ticker);
        if (existing != null) {
            return toCatalogInstrument(existing);
        }

        SymbolInfo info;
        try {
            info = marketDataClient.getSymbol(ticker);
        } catch (IllegalStateException | RestClientException e) {
            // still being fetched, bad key, quota used up or API down: nothing to add right now
            LOGGER.warn("Symbol lookup failed for {}: {}", ticker, e.getMessage());
            return null;
        }
        if (info == null) {
            return null;
        }

        String name = info.name() == null || info.name().isBlank() ? ticker : info.name();
        // does nothing if another request added the symbol meanwhile (tickers are unique); either way, read the row
        instrumentMapper.insertIfAbsent(new Instrument(name, ticker, assetClass(info.type())));
        return toCatalogInstrument(instrumentMapper.findByTicker(ticker));
    }

    /**
     * A page of the instruments in the database whose ticker or name contains the query (case-insensitive), an
     * exact ticker match first, then by ticker. Only the database is searched; {@link #lookup} adds new symbols.
     *
     * @param query text to look for; blank matches every instrument
     * @param assetClass only instruments of this class, or null for every class
     * @param limit the most instruments to return
     * @param offset how many matches to skip
     * @return the page of matching instruments, with the number of matches across every page
     */
    public Page search(String query, InstrumentType assetClass, int limit, int offset) {
        String text = query == null ? "" : query.trim();
        // a typed % or _ is matched as itself, not as a wildcard (backslash is ILIKE's escape character)
        String pattern = "%" + text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        String type = assetClass == null ? null : assetClass.name();
        List<CatalogInstrument> instruments = instrumentMapper.search(pattern, type, text.toUpperCase(Locale.ROOT),
                limit, offset).stream().map(InstrumentCatalog::toCatalogInstrument).toList();
        return new Page(instruments, instrumentMapper.countMatching(pattern, type));
    }

    // Fauxnance types to the asset classes the instruments table allows
    static InstrumentType assetClass(String fauxnanceType) {
        if (fauxnanceType == null) {
            return InstrumentType.STOCK;
        }
        return switch (fauxnanceType.toLowerCase(Locale.ROOT)) {
            case "etf" -> InstrumentType.ETF;
            case "fx", "forex" -> InstrumentType.FOREX;
            case "crypto" -> InstrumentType.CRYPTO;
            default -> InstrumentType.STOCK;
        };
    }

    private static CatalogInstrument toCatalogInstrument(Instrument instrument) {
        return new CatalogInstrument(instrument.getInstrumentId(), instrument.getTicker(), instrument.getName(),
                instrument.getInstrumentType().name());
    }
}
