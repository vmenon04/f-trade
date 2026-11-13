package com.fintechfive.ftrade.service;

import com.fintechfive.ftrade.entity.Instrument;
import com.fintechfive.ftrade.enums.InstrumentType;
import com.fintechfive.ftrade.external.MarketDataClient;
import com.fintechfive.ftrade.external.SymbolInfo;
import com.fintechfive.ftrade.repository.InstrumentMapper;
import com.fintechfive.ftrade.service.InstrumentCatalog.CatalogInstrument;
import com.fintechfive.ftrade.service.InstrumentCatalog.Page;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InstrumentCatalogTest {

    @Mock
    private MarketDataClient marketDataClient;

    @Mock
    private InstrumentMapper instrumentMapper;

    @InjectMocks
    private InstrumentCatalog catalog;

    private static Instrument instrument(Long id, String ticker, String name, InstrumentType type) {
        Instrument instrument = new Instrument(name, ticker, type);
        instrument.setInstrumentId(id);
        return instrument;
    }

    @Test
    void lookup_inTheDatabase_doesNotCallTheMarketDataApi() {
        when(instrumentMapper.findByTicker("ORCL"))
                .thenReturn(instrument(22L, "ORCL", "Oracle Corporation", InstrumentType.STOCK));

        assertEquals(new CatalogInstrument(22L, "ORCL", "Oracle Corporation", "STOCK"), catalog.lookup(" orcl "));
        verifyNoInteractions(marketDataClient);
    }

    @Test
    void lookup_newSymbol_isAddedToTheDatabase() {
        when(instrumentMapper.findByTicker("ORCL"))
                .thenReturn(null)
                .thenReturn(instrument(400L, "ORCL", "Oracle Corporation", InstrumentType.STOCK));
        when(marketDataClient.getSymbol("ORCL")).thenReturn(new SymbolInfo("ORCL", "Oracle Corporation", "equity"));
        when(instrumentMapper.insertIfAbsent(any(Instrument.class))).thenReturn(1);

        assertEquals(new CatalogInstrument(400L, "ORCL", "Oracle Corporation", "STOCK"), catalog.lookup("ORCL"));

        ArgumentCaptor<Instrument> inserted = ArgumentCaptor.forClass(Instrument.class);
        verify(instrumentMapper).insertIfAbsent(inserted.capture());
        assertEquals("ORCL", inserted.getValue().getTicker());
        assertEquals("Oracle Corporation", inserted.getValue().getName());
        assertEquals(InstrumentType.STOCK, inserted.getValue().getInstrumentType());
    }

    // two lookups of a new symbol at once: the second insert does nothing (UNIQUE ticker), and both get the one row
    @Test
    void lookup_addedByAnotherRequestMeanwhile_usesTheExistingRow() {
        when(instrumentMapper.findByTicker("ORCL"))
                .thenReturn(null)
                .thenReturn(instrument(22L, "ORCL", "Oracle Corporation", InstrumentType.STOCK));
        when(marketDataClient.getSymbol("ORCL")).thenReturn(new SymbolInfo("ORCL", "Oracle Corporation", "equity"));
        when(instrumentMapper.insertIfAbsent(any(Instrument.class))).thenReturn(0);

        assertEquals(22L, catalog.lookup("ORCL").instrumentId());
    }

    @Test
    void lookup_noNameFromTheApi_usesTheTicker() {
        when(instrumentMapper.findByTicker("X:BTC-USD"))
                .thenReturn(null)
                .thenReturn(instrument(5L, "X:BTC-USD", "X:BTC-USD", InstrumentType.CRYPTO));
        when(marketDataClient.getSymbol("X:BTC-USD")).thenReturn(new SymbolInfo("X:BTC-USD", " ", "crypto"));

        catalog.lookup("x:btc-usd");

        ArgumentCaptor<Instrument> inserted = ArgumentCaptor.forClass(Instrument.class);
        verify(instrumentMapper).insertIfAbsent(inserted.capture());
        assertEquals("X:BTC-USD", inserted.getValue().getName());
        assertEquals(InstrumentType.CRYPTO, inserted.getValue().getInstrumentType());
    }

    @Test
    void lookup_unknownSymbol_returnsNullAndAddsNothing() {
        when(instrumentMapper.findByTicker("NOPE")).thenReturn(null);
        when(marketDataClient.getSymbol("NOPE")).thenReturn(null);

        assertNull(catalog.lookup("NOPE"));
        verify(instrumentMapper, never()).insertIfAbsent(any());
    }

    // e.g. still being fetched, quota used up or the API down: nothing to add right now
    @Test
    void lookup_apiFailure_returnsNull() {
        when(instrumentMapper.findByTicker("ORCL")).thenReturn(null);
        when(marketDataClient.getSymbol("ORCL")).thenThrow(new IllegalStateException("Fauxnance returned 429"));

        assertNull(catalog.lookup("ORCL"));
        verify(instrumentMapper, never()).insertIfAbsent(any());
    }

    @Test
    void lookup_blankSymbol_returnsNull() {
        assertNull(catalog.lookup("  "));
        verifyNoInteractions(marketDataClient, instrumentMapper);
    }

    @Test
    void search_findsTickersAndNamesContainingTheQuery() {
        when(instrumentMapper.search("%apple%", null, "APPLE", 50, 0)).thenReturn(List.of(
                instrument(1L, "AAPL", "Apple Inc", InstrumentType.STOCK)));
        when(instrumentMapper.countMatching("%apple%", null)).thenReturn(1L);

        assertEquals(new Page(List.of(new CatalogInstrument(1L, "AAPL", "Apple Inc", "STOCK")), 1),
                catalog.search(" apple ", null, 50, 0));
        verifyNoInteractions(marketDataClient);
    }

    @Test
    void search_countsTheMatchesOnEveryPage() {
        when(instrumentMapper.search("%%", null, "", 50, 300)).thenReturn(List.of(
                instrument(334L, "ZTS", "Zoetis Inc", InstrumentType.STOCK)));
        when(instrumentMapper.countMatching("%%", null)).thenReturn(334L);

        assertEquals(334, catalog.search("", null, 50, 300).total());
    }

    @Test
    void search_blankQuery_matchesEveryInstrument() {
        when(instrumentMapper.search("%%", null, "", 50, 100)).thenReturn(List.of());

        assertEquals(new Page(List.of(), 0), catalog.search(null, null, 50, 100));
        verify(instrumentMapper).search("%%", null, "", 50, 100);
    }

    @Test
    void search_assetClass_onlyMatchesThatClass() {
        when(instrumentMapper.search("%%", "CRYPTO", "", 20, 0)).thenReturn(List.of(
                instrument(7L, "X:BTC-USD", "Bitcoin", InstrumentType.CRYPTO)));
        when(instrumentMapper.countMatching("%%", "CRYPTO")).thenReturn(150L);

        assertEquals(new Page(List.of(new CatalogInstrument(7L, "X:BTC-USD", "Bitcoin", "CRYPTO")), 150),
                catalog.search("", InstrumentType.CRYPTO, 20, 0));
    }

    @Test
    void search_wildcardCharacters_areMatchedLiterally() {
        when(instrumentMapper.search("%50\\%\\_x\\\\%", null, "50%_X\\", 50, 0)).thenReturn(List.of());

        catalog.search("50%_x\\", null, 50, 0);

        verify(instrumentMapper).search("%50\\%\\_x\\\\%", null, "50%_X\\", 50, 0);
    }

    @Test
    void assetClass_mapsFauxnanceTypes() {
        assertEquals(InstrumentType.STOCK, InstrumentCatalog.assetClass("equity"));
        assertEquals(InstrumentType.ETF, InstrumentCatalog.assetClass("etf"));
        assertEquals(InstrumentType.FOREX, InstrumentCatalog.assetClass("fx"));
        assertEquals(InstrumentType.CRYPTO, InstrumentCatalog.assetClass("crypto"));
        assertEquals(InstrumentType.STOCK, InstrumentCatalog.assetClass(null));
    }
}
