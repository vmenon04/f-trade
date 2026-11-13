package com.fintechfive.ftrade.controller;

import com.fintechfive.ftrade.entity.Instrument;
import com.fintechfive.ftrade.enums.InstrumentType;
import com.fintechfive.ftrade.enums.RejectionCode;
import com.fintechfive.ftrade.enums.TradeSide;
import com.fintechfive.ftrade.enums.TradeStage;
import com.fintechfive.ftrade.service.InstrumentService;
import com.fintechfive.ftrade.service.TrackedTrade;
import com.fintechfive.ftrade.service.TradeTrackerService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class LiveTradeControllerTest {

    private static final UUID ACCOUNT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TRADE = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant SUBMITTED = Instant.parse("2026-10-26T12:00:00Z");
    private static final Instrument APPLE = new Instrument("Apple Inc", "AAPL", InstrumentType.STOCK);

    @Mock
    private TradeTrackerService tracker;

    @Mock
    private InstrumentService instrumentService;

    @InjectMocks
    private LiveTradeController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // resolves @AuthenticationPrincipal (to null: the ownership check is in OwnershipAuthorizationTest)
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    private static TrackedTrade rejected(UUID id) {
        return new TrackedTrade(id, 10L, TradeSide.BUY, new BigDecimal("5"), TradeStage.REJECTED, SUBMITTED, null, null,
                SUBMITTED.plusSeconds(1), null, RejectionCode.INSUFFICIENT_FUNDS);
    }

    @Test
    void listsTheAccountsTrackedTrades() throws Exception {
        TrackedTrade fulfilled = new TrackedTrade(TRADE, 10L, TradeSide.SELL, new BigDecimal("2.5"),
                TradeStage.FULFILLED, SUBMITTED, SUBMITTED.plusSeconds(1), SUBMITTED.plusSeconds(2),
                SUBMITTED.plusSeconds(3), new BigDecimal("101.50"), null);
        when(tracker.recentTrades(ACCOUNT)).thenReturn(List.of(fulfilled));
        when(instrumentService.getInstrumentById(10L)).thenReturn(APPLE);

        mockMvc.perform(get("/v1/accounts/{accountId}/trades/live", ACCOUNT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].tradeId").value(TRADE.toString()))
                .andExpect(jsonPath("$[0].ticker").value("AAPL"))
                .andExpect(jsonPath("$[0].name").value("Apple Inc"))
                .andExpect(jsonPath("$[0].side").value("SELL"))
                .andExpect(jsonPath("$[0].quantity").value(2.5))
                .andExpect(jsonPath("$[0].stage").value("FULFILLED"))
                .andExpect(jsonPath("$[0].submittedAt").exists())
                .andExpect(jsonPath("$[0].validatedAt").exists())
                .andExpect(jsonPath("$[0].recordedAt").exists())
                .andExpect(jsonPath("$[0].finishedAt").exists())
                .andExpect(jsonPath("$[0].price").value(101.50))
                .andExpect(jsonPath("$[0].rejectionCode").doesNotExist());
    }

    // only the code: the reason staff see has internal ids and balances
    @Test
    void rejectedTradeHasItsCode() throws Exception {
        when(tracker.recentTrades(ACCOUNT)).thenReturn(List.of(rejected(TRADE)));
        when(instrumentService.getInstrumentById(10L)).thenReturn(APPLE);

        mockMvc.perform(get("/v1/accounts/{accountId}/trades/live", ACCOUNT))
                .andExpect(jsonPath("$[0].stage").value("REJECTED"))
                .andExpect(jsonPath("$[0].rejectionCode").value("INSUFFICIENT_FUNDS"))
                .andExpect(jsonPath("$[0].reason").doesNotExist())
                .andExpect(jsonPath("$[0].instrumentId").doesNotExist());
    }

    @Test
    void eachInstrumentIsLookedUpOnce() throws Exception {
        UUID other = UUID.fromString("33333333-3333-3333-3333-333333333333");
        when(tracker.recentTrades(ACCOUNT)).thenReturn(List.of(rejected(TRADE), rejected(other)));
        when(instrumentService.getInstrumentById(10L)).thenReturn(APPLE);

        mockMvc.perform(get("/v1/accounts/{accountId}/trades/live", ACCOUNT))
                .andExpect(jsonPath("$", hasSize(2)));
        verify(instrumentService, times(1)).getInstrumentById(10L);
    }

    // an instrument removed since doesn't fail the whole list
    @Test
    void unknownInstrument_isShownWithoutATicker() throws Exception {
        when(tracker.recentTrades(ACCOUNT)).thenReturn(List.of(rejected(TRADE)));
        when(instrumentService.getInstrumentById(10L))
                .thenThrow(new NoSuchElementException("No instrument with id 10"));

        mockMvc.perform(get("/v1/accounts/{accountId}/trades/live", ACCOUNT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tradeId").value(TRADE.toString()))
                .andExpect(jsonPath("$[0].ticker").doesNotExist());
    }

    @Test
    void noTrades_isAnEmptyList() throws Exception {
        when(tracker.recentTrades(ACCOUNT)).thenReturn(List.of());

        mockMvc.perform(get("/v1/accounts/{accountId}/trades/live", ACCOUNT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
