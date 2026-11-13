package com.fintechfive.ftrade.controller;

import com.fintechfive.ftrade.dto.LiveTradeDTO;
import com.fintechfive.ftrade.entity.Instrument;
import com.fintechfive.ftrade.service.InstrumentService;
import com.fintechfive.ftrade.service.TrackedTrade;
import com.fintechfive.ftrade.service.TradeTrackerService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// The trade page's live list: the account's trades from the last hour as they go through the pipeline, from the
// trade tracker in Redis. Order History (GET /{accountId}/orders) is the record
@RestController
@RequestMapping("/v1/accounts")
public class LiveTradeController {

    private final TradeTrackerService tracker;
    private final InstrumentService instrumentService;

    public LiveTradeController(TradeTrackerService tracker, InstrumentService instrumentService) {
        this.tracker = tracker;
        this.instrumentService = instrumentService;
    }

    @PreAuthorize("@accessControlService.userOwnsAccount(#accountId, #jwt.getSubject())")
    @GetMapping("/{accountId}/trades/live")
    public ResponseEntity<List<LiveTradeDTO>> getLiveTrades(@PathVariable UUID accountId,
                                                            @AuthenticationPrincipal Jwt jwt) {
        Map<Long, Optional<Instrument>> instruments = new HashMap<>();
        List<LiveTradeDTO> trades = tracker.recentTrades(accountId).stream()
                .map(trade -> toLiveTrade(trade, instruments.computeIfAbsent(trade.instrumentId(), this::instrument)))
                .toList();
        return ResponseEntity.ok(trades);
    }

    // an instrument removed since the trade doesn't fail the whole list
    private Optional<Instrument> instrument(Long instrumentId) {
        try {
            return Optional.ofNullable(instrumentService.getInstrumentById(instrumentId));
        } catch (NoSuchElementException e) {
            return Optional.empty();
        }
    }

    private static LiveTradeDTO toLiveTrade(TrackedTrade trade, Optional<Instrument> instrument) {
        return new LiveTradeDTO(trade.externalTradeId(),
                instrument.map(Instrument::getTicker).orElse(null),
                instrument.map(Instrument::getName).orElse(null),
                trade.side(), trade.quantity(), trade.stage(), trade.submittedAt(), trade.validatedAt(),
                trade.recordedAt(), trade.finishedAt(), trade.price(), trade.rejectionCode());
    }
}
