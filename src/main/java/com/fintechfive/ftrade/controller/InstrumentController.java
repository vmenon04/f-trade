package com.fintechfive.ftrade.controller;

import com.fintechfive.ftrade.dto.InstrumentResponseDTO;
import com.fintechfive.ftrade.enums.InstrumentType;
import com.fintechfive.ftrade.service.InstrumentCatalog;
import com.fintechfive.ftrade.service.InstrumentCatalog.CatalogInstrument;
import com.fintechfive.ftrade.service.InstrumentCatalog.Page;
import com.fintechfive.ftrade.service.InstrumentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/instruments")
public class InstrumentController {

    private final InstrumentService instrumentService;
    private final InstrumentCatalog instrumentCatalog;

    public InstrumentController(InstrumentService instrumentService, InstrumentCatalog instrumentCatalog) {
        this.instrumentService = instrumentService;
        this.instrumentCatalog = instrumentCatalog;
    }

    // each instrument listed is priced, so a page is kept small enough to price in a few Fauxnance batches
    static final int MAX_LIMIT = 100;

    // the header with how many instruments match in all, so the UI can page through them
    static final String TOTAL_COUNT = "X-Total-Count";

    // Instruments with their price to show (cached in Redis, then the API in batches; the last known price if there's
    // no fresh one). Without search: a page (limit, offset) of the instruments in the database whose ticker or name
    // contains q, or of all of them without q, and only of one assetClass (e.g. CRYPTO) when it's given. With
    // search=ORCL: that symbol, looked up in the database and then the market data API (which adds it to the
    // database), or an empty list if it's unknown. Either way X-Total-Count says how many match in all.
    @GetMapping
    public ResponseEntity<List<InstrumentResponseDTO>> getInstruments(@RequestParam(required = false) String search,
                                                                      @RequestParam(required = false) String q,
                                                                      @RequestParam(required = false) InstrumentType assetClass,
                                                                      @RequestParam(defaultValue = "50") int limit,
                                                                      @RequestParam(defaultValue = "0") int offset) {
        List<CatalogInstrument> instruments;
        long total;
        if (search == null || search.isBlank()) {
            Page page = instrumentCatalog.search(q, assetClass, Math.max(1, Math.min(limit, MAX_LIMIT)),
                    Math.max(0, offset));
            instruments = page.instruments();
            total = page.total();
        } else {
            CatalogInstrument found = instrumentCatalog.lookup(search);
            instruments = found == null ? List.of() : List.of(found);
            total = instruments.size();
        }

        Map<String, BigDecimal> prices = instrumentService.getCurrentPrices(
                instruments.stream().map(CatalogInstrument::ticker).toList());
        return ResponseEntity.ok().header(TOTAL_COUNT, String.valueOf(total)).body(instruments.stream()
                .map(instrument -> new InstrumentResponseDTO(instrument.instrumentId(), instrument.ticker(),
                        instrument.name(), prices.get(instrument.ticker()), instrument.assetClass()))
                .toList());
    }
}
