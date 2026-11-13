package com.fintechfive.ftrade.service;

import com.fintechfive.ftrade.exception.PriceUnavailableException;
import com.fintechfive.ftrade.entity.Instrument;
import com.fintechfive.ftrade.external.DailyClose;
import com.fintechfive.ftrade.external.MarketDataClient;
import com.fintechfive.ftrade.repository.InstrumentMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NoSuchElementException;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

/**
 * Provides instrument lookups and current market price retrieval.
 */
@Service
public class InstrumentService {

    private static final Logger LOGGER = LoggerFactory.getLogger(InstrumentService.class);

    private final InstrumentMapper instrumentMapper;
    private final MarketDataClient marketDataClient;

    /**
     * Creates an instrument service backed by instrument persistence and market data providers.
     *
     * @param instrumentMapper mapper used to query instrument reference data
     * @param marketDataClient client used to retrieve current prices
     */
    public InstrumentService(InstrumentMapper instrumentMapper, MarketDataClient marketDataClient) {
        this.instrumentMapper = instrumentMapper;
        this.marketDataClient = marketDataClient;
    }

    /**
     * Returns an instrument by its identifier.
     *
     * @param instrumentId instrument identifier
     * @return matching instrument
     * @throws NoSuchElementException if the instrument does not exist
     */
    public Instrument getInstrumentById(Long instrumentId) {
        Instrument instrument = instrumentMapper.findById(instrumentId);
        if (instrument == null) {
            throw new NoSuchElementException("No instrument with id " + instrumentId);
        }
        return instrument;
    }

    /**
     * Returns an instrument by its ticker symbol.
     *
     * @param ticker instrument ticker symbol
     * @return matching instrument
     * @throws IllegalArgumentException if the ticker is blank
     * @throws NoSuchElementException if the instrument does not exist
     */
    public Instrument getInstrumentByTicker(String ticker) {
        if (ticker == null || ticker.isBlank()) {
            throw new IllegalArgumentException("Ticker must not be blank");
        }
        Instrument instrument = instrumentMapper.findByTicker(ticker);
        if (instrument == null) {
            throw new NoSuchElementException("No instrument with ticker " + ticker);
        }
        return instrument;
    }

    /**
     * Returns all known instruments.
     *
     * @return list of instrument reference records
     */
    public List<Instrument> getAllInstruments() {
        return instrumentMapper.findAll();
    }

    /**
     * Returns the current price for an instrument ticker.
     *
     * @param ticker instrument ticker symbol
     * @return positive current market price
     * @throws IllegalArgumentException if the ticker is blank
     * @throws NoSuchElementException if the ticker does not map to a known instrument
     * @throws IllegalStateException if no valid market price is available
     */
    public BigDecimal getCurrentPrice(String ticker) {
        getInstrumentByTicker(ticker);
        BigDecimal price = marketDataClient.getPrice(ticker);
        if (price == null) {
            throw new PriceUnavailableException("No price available for " + ticker);
        }
        if (price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new PriceUnavailableException("Invalid price " + price + " for " + ticker);
        }
        return price;
    }

    /**
     * Returns the current price for an instrument identifier.
     *
     * @param instrumentId instrument identifier
     * @return positive current market price
     * @throws NoSuchElementException if the instrument does not exist
     * @throws IllegalStateException if no valid market price is available
     */
    public BigDecimal getCurrentPrice(Long instrumentId) {
        return getCurrentPrice(getInstrumentById(instrumentId).getTicker());
    }

    /**
     * Returns an instrument's daily closing prices, for charts.
     *
     * @param ticker instrument ticker symbol
     * @param from first day, inclusive
     * @param to last day, inclusive
     * @return closes by day, oldest first; empty if the market data API has no history for it (yet)
     * @throws IllegalStateException if the market data API can't say right now (quota, outage)
     */
    public NavigableMap<LocalDate, BigDecimal> getDailyCloses(String ticker, LocalDate from, LocalDate to) {
        NavigableMap<LocalDate, BigDecimal> closes = new TreeMap<>();
        for (DailyClose close : marketDataClient.getCandles(ticker, from, to)) {
            closes.put(close.date(), close.close());
        }
        return closes;
    }

    /**
     * Returns current prices for many instruments at once, for showing: cached prices from Redis, and the rest from
     * the market data API in batches. A stale quote keeps its last known price (trades use getCurrentPrice, which
     * rejects stale quotes). Instruments with no valid price right now are left out.
     *
     * @param tickers tickers to price
     * @return positive prices by ticker; empty if the market data API is unavailable
     */
    public Map<String, BigDecimal> getCurrentPrices(List<String> tickers) {
        tickers = tickers.stream().distinct().toList();
        try {
            Map<String, BigDecimal> prices = new HashMap<>();
            marketDataClient.getPrices(tickers).forEach((ticker, quote) -> {
                if (quote.price() != null && quote.price().compareTo(BigDecimal.ZERO) > 0) {
                    prices.put(ticker, quote.price());
                }
            });
            return prices;
        } catch (IllegalStateException | RestClientException e) {
            // listing instruments still works without prices (bad key, quota used up, API down)
            LOGGER.warn("No market prices for {} instruments: {}", tickers.size(), e.getMessage());
            return Map.of();
        }
    }
}
