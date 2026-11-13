package com.fintechfive.ftrade.controller;

import com.fintechfive.ftrade.entity.AccountHolding;
import com.fintechfive.ftrade.entity.AccountTrade;
import com.fintechfive.ftrade.entity.Instrument;
import com.fintechfive.ftrade.exception.GlobalExceptionHandler;
import com.fintechfive.ftrade.enums.ActivityStatus;
import com.fintechfive.ftrade.enums.InstrumentType;
import com.fintechfive.ftrade.enums.PerformanceRange;
import com.fintechfive.ftrade.enums.TradeSide;
import com.fintechfive.ftrade.enums.TradeStatus;
import com.fintechfive.ftrade.repository.AccountHoldingMapper;
import com.fintechfive.ftrade.repository.AccountMapper;
import com.fintechfive.ftrade.repository.AccountTradeMapper;
import com.fintechfive.ftrade.service.AccountService;
import com.fintechfive.ftrade.service.InstrumentService;
import com.fintechfive.ftrade.service.PerformancePoint;
import com.fintechfive.ftrade.service.PerformanceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AccountControllerTest {

    private static final Long ACCOUNT_ID = 5L;
    private static final Long OTHER_ACCOUNT_ID = 6L;
    private static final Long INSTRUMENT_ID = 1L;
    private static final Long USER_ID = 42L;

    @Mock
    private AccountService accountService;

    @Mock
    private AccountMapper accountMapper;

    @Mock
    private AccountHoldingMapper accountHoldingMapper;

    @Mock
    private AccountTradeMapper accountTradeMapper;

    @Mock
    private InstrumentService instrumentService;

    @Mock
    private PerformanceService performanceService;

    @InjectMocks
    private AccountController accountController;

    private MockMvc mockMvc;

    private final UUID externalAccountId = UUID.randomUUID();
    private final UUID externalTradeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(accountController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        // @AuthenticationPrincipal reads the logged-in user from the security context
        SecurityContextHolder.getContext().setAuthentication(loggedInAs(USER_ID));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static JwtAuthenticationToken loggedInAs(Long userId) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", userId.toString())
                .build();
        return new JwtAuthenticationToken(jwt);
    }

    private ResultActions postDeposit() throws Exception {
        return mockMvc.perform(post("/api/v1/accounts/{accountId}/deposits", externalAccountId)
                .contextPath("/api")
                .contentType(APPLICATION_JSON)
                .content("""
                        {
                          "amount": 250.50
                        }
                        """));
    }

    @Test
    void deposit_ownedAccount_returnsNoContentAndCreditsIt() throws Exception {
        when(accountService.getAccountIdByExternalAccountId(externalAccountId)).thenReturn(ACCOUNT_ID);

        postDeposit().andExpect(status().isNoContent());

        verify(accountService).deposit(ACCOUNT_ID, new BigDecimal("250.50"));
    }

    // depositing into someone else's account (the @PreAuthorize ownership check) is tested in
    // OwnershipAuthorizationTest: this plain MockMvc setup doesn't apply @PreAuthorize

    @Test
    void deposit_unknownAccount_returns404() throws Exception {
        when(accountService.getAccountIdByExternalAccountId(externalAccountId))
                .thenThrow(new java.util.NoSuchElementException("account not found"));

        postDeposit().andExpect(status().isNotFound());

    }

    @Test
    void deposit_nonPositiveAmount_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/accounts/{accountId}/deposits", externalAccountId)
                        .contextPath("/api")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "amount": 0
                                }
                                """))
                .andExpect(status().isBadRequest());

        verify(accountService, never()).deposit(any(), any());
    }

    @Test
    void getSummary_returnsCashBalance() throws Exception {
        when(accountService.getAccountIdByExternalAccountId(externalAccountId)).thenReturn(ACCOUNT_ID);
        when(accountService.getBalance(ACCOUNT_ID)).thenReturn(new BigDecimal("2500.00"));
        when(accountService.getCostBasis(ACCOUNT_ID)).thenReturn(new BigDecimal("0.00"));

        mockMvc.perform(get("/api/v1/accounts/{accountId}", externalAccountId).contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(externalAccountId.toString()))
                .andExpect(jsonPath("$.cashBalance").value(2500.00))
                .andExpect(jsonPath("$.investmentValue").value(0))
                .andExpect(jsonPath("$.portfolioValue").value(2500.00))
                .andExpect(jsonPath("$.costBasis").value(0));
    }

    @Test
    void getHoldings_returnsOnlyActiveHoldings() throws Exception {
        when(accountService.getAccountIdByExternalAccountId(externalAccountId)).thenReturn(ACCOUNT_ID);
        when(accountHoldingMapper.findByAccountId(ACCOUNT_ID)).thenReturn(List.of(
                new AccountHolding(ACCOUNT_ID, INSTRUMENT_ID, null, new BigDecimal("10"), ActivityStatus.ACTIVE),
                new AccountHolding(ACCOUNT_ID, INSTRUMENT_ID, null, new BigDecimal("4"), ActivityStatus.INACTIVE)));
        when(instrumentService.getInstrumentById(INSTRUMENT_ID)).thenReturn(apple());

        mockMvc.perform(get("/api/v1/accounts/{accountId}/holdings", externalAccountId).contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].accountId").value(externalAccountId.toString()))
                .andExpect(jsonPath("$[0].name").value("Apple Inc"))
                .andExpect(jsonPath("$[0].ticker").value("AAPL"))
                .andExpect(jsonPath("$[0].quantity").value(10))
                .andExpect(jsonPath("$[0].assetClass").value("STOCK"));
    }

    @Test
    void getHoldings_includesCurrentMarketPrice() throws Exception {
        when(accountService.getAccountIdByExternalAccountId(externalAccountId)).thenReturn(ACCOUNT_ID);
        when(accountHoldingMapper.findByAccountId(ACCOUNT_ID)).thenReturn(List.of(
                new AccountHolding(ACCOUNT_ID, INSTRUMENT_ID, null, new BigDecimal("10"), ActivityStatus.ACTIVE)));
        when(instrumentService.getInstrumentById(INSTRUMENT_ID)).thenReturn(apple());
        // one batch of display prices for all the holdings, not a price lookup per holding
        when(instrumentService.getCurrentPrices(List.of("AAPL"))).thenReturn(Map.of("AAPL", new BigDecimal("150.25")));

        mockMvc.perform(get("/api/v1/accounts/{accountId}/holdings", externalAccountId).contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].price").value(150.25));
    }

    @Test
    void getHoldings_priceUnavailable_returnsNullPriceInsteadOfFailing() throws Exception {
        when(accountService.getAccountIdByExternalAccountId(externalAccountId)).thenReturn(ACCOUNT_ID);
        when(accountHoldingMapper.findByAccountId(ACCOUNT_ID)).thenReturn(List.of(
                new AccountHolding(ACCOUNT_ID, INSTRUMENT_ID, null, new BigDecimal("10"), ActivityStatus.ACTIVE)));
        when(instrumentService.getInstrumentById(INSTRUMENT_ID)).thenReturn(apple());
        // getCurrentPrices leaves out an instrument with no price (and returns nothing if Fauxnance is down)
        when(instrumentService.getCurrentPrices(List.of("AAPL"))).thenReturn(Map.of());

        mockMvc.perform(get("/api/v1/accounts/{accountId}/holdings", externalAccountId).contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ticker").value("AAPL"))
                .andExpect(jsonPath("$[0].price").doesNotExist());
    }

    @Test
    void getSummary_investmentValueIsMarketValueOfActiveHoldings() throws Exception {
        when(accountService.getAccountIdByExternalAccountId(externalAccountId)).thenReturn(ACCOUNT_ID);
        when(accountService.getBalance(ACCOUNT_ID)).thenReturn(new BigDecimal("2500.00"));
        when(accountHoldingMapper.findByAccountId(ACCOUNT_ID)).thenReturn(List.of(
                new AccountHolding(ACCOUNT_ID, INSTRUMENT_ID, null, new BigDecimal("10"), ActivityStatus.ACTIVE),
                new AccountHolding(ACCOUNT_ID, INSTRUMENT_ID, null, new BigDecimal("4"), ActivityStatus.INACTIVE)));
        when(instrumentService.getInstrumentById(INSTRUMENT_ID)).thenReturn(apple());
        when(instrumentService.getCurrentPrices(List.of("AAPL"))).thenReturn(Map.of("AAPL", new BigDecimal("150.25")));

        mockMvc.perform(get("/api/v1/accounts/{accountId}", externalAccountId).contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.investmentValue").value(1502.50))
                .andExpect(jsonPath("$.portfolioValue").value(4002.50))
                .andExpect(jsonPath("$.cashBalance").value(2500.00));
    }

    @Test
    void getSummary_costBasisIsWhatWasPaidForTheSharesStillHeld() throws Exception {
        when(accountService.getAccountIdByExternalAccountId(externalAccountId)).thenReturn(ACCOUNT_ID);
        when(accountService.getBalance(ACCOUNT_ID)).thenReturn(new BigDecimal("2500.00"));
        // how it's worked out is tested in AccountServiceTest
        when(accountService.getCostBasis(ACCOUNT_ID)).thenReturn(new BigDecimal("900.00"));

        mockMvc.perform(get("/api/v1/accounts/{accountId}", externalAccountId).contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.costBasis").value(900.00));
    }

    @Test
    void getOrders_withoutSide_returnsAllTrades() throws Exception {
        when(accountService.getAccountIdByExternalAccountId(externalAccountId)).thenReturn(ACCOUNT_ID);
        when(accountTradeMapper.findByAccountId(ACCOUNT_ID)).thenReturn(List.of(
                trade(ACCOUNT_ID, TradeSide.BUY), trade(ACCOUNT_ID, TradeSide.SELL)));
        when(instrumentService.getInstrumentById(INSTRUMENT_ID)).thenReturn(apple());

        mockMvc.perform(get("/api/v1/accounts/{accountId}/orders", externalAccountId).contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].side").value("BUY"))
                .andExpect(jsonPath("$[0].tradeTime").value("2026-10-25T14:30:00Z"))
                .andExpect(jsonPath("$[1].side").value("SELL"));
    }

    @Test
    void getOrders_withSide_filtersCaseInsensitively() throws Exception {
        when(accountService.getAccountIdByExternalAccountId(externalAccountId)).thenReturn(ACCOUNT_ID);
        when(accountTradeMapper.findByAccountId(ACCOUNT_ID)).thenReturn(List.of(
                trade(ACCOUNT_ID, TradeSide.BUY), trade(ACCOUNT_ID, TradeSide.SELL)));
        when(instrumentService.getInstrumentById(INSTRUMENT_ID)).thenReturn(apple());

        mockMvc.perform(get("/api/v1/accounts/{accountId}/orders", externalAccountId)
                        .contextPath("/api")
                        .param("side", "sell"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].side").value("SELL"));
    }

    @Test
    void getTrade_tradeOnAccount_returns200WithTradeDetails() throws Exception {
        when(accountTradeMapper.findTradeIdByExternalTradeId(externalTradeId)).thenReturn(42L);
        when(accountTradeMapper.findById(42L)).thenReturn(trade(ACCOUNT_ID, TradeSide.BUY));
        when(accountService.getAccountIdByExternalAccountId(externalAccountId)).thenReturn(ACCOUNT_ID);
        when(instrumentService.getInstrumentById(INSTRUMENT_ID)).thenReturn(apple());

        mockMvc.perform(get("/api/v1/accounts/{accountId}/trades/{tradeId}", externalAccountId, externalTradeId)
                        .contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instrumentId").value(1))
                .andExpect(jsonPath("$.ticker").value("AAPL"))
                .andExpect(jsonPath("$.tradeId").value(externalTradeId.toString()))
                .andExpect(jsonPath("$.side").value("BUY"))
                .andExpect(jsonPath("$.quantity").value(10))
                .andExpect(jsonPath("$.ppu").value(150.00))
                .andExpect(jsonPath("$.totalPrice").value(1500.00))
                .andExpect(jsonPath("$.status").value("FULFILLED"));
    }

    @Test
    void getTrade_unknownTrade_returns404() throws Exception {
        when(accountTradeMapper.findTradeIdByExternalTradeId(externalTradeId)).thenReturn(null);
        when(accountTradeMapper.findById(null)).thenReturn(null);

        mockMvc.perform(get("/api/v1/accounts/{accountId}/trades/{tradeId}", externalAccountId, externalTradeId)
                        .contextPath("/api"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getTrade_tradeOnAnotherAccount_returns403() throws Exception {
        when(accountTradeMapper.findTradeIdByExternalTradeId(externalTradeId)).thenReturn(42L);
        when(accountTradeMapper.findById(42L)).thenReturn(trade(OTHER_ACCOUNT_ID, TradeSide.BUY));
        when(accountService.getAccountIdByExternalAccountId(externalAccountId)).thenReturn(ACCOUNT_ID);

        mockMvc.perform(get("/api/v1/accounts/{accountId}/trades/{tradeId}", externalAccountId, externalTradeId)
                        .contextPath("/api"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getTask_taskOnAccount_returns200() throws Exception {
        when(accountTradeMapper.findTradeIdByExternalTradeId(externalTradeId)).thenReturn(42L);
        when(accountTradeMapper.findById(42L)).thenReturn(trade(ACCOUNT_ID, TradeSide.SELL));
        when(accountService.getAccountIdByExternalAccountId(externalAccountId)).thenReturn(ACCOUNT_ID);
        when(instrumentService.getInstrumentById(INSTRUMENT_ID)).thenReturn(apple());

        mockMvc.perform(get("/api/v1/accounts/{accountId}/tasks/{tradeId}", externalAccountId, externalTradeId)
                        .contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tradeId").value(externalTradeId.toString()))
                .andExpect(jsonPath("$.side").value("SELL"));
    }

    @Test
    void getTask_unknownTask_returns404() throws Exception {
        when(accountTradeMapper.findTradeIdByExternalTradeId(externalTradeId)).thenReturn(null);
        when(accountTradeMapper.findById(null)).thenReturn(null);

        mockMvc.perform(get("/api/v1/accounts/{accountId}/tasks/{tradeId}", externalAccountId, externalTradeId)
                        .contextPath("/api"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getTask_taskOnAnotherAccount_returns403() throws Exception {
        when(accountTradeMapper.findTradeIdByExternalTradeId(externalTradeId)).thenReturn(42L);
        when(accountTradeMapper.findById(42L)).thenReturn(trade(OTHER_ACCOUNT_ID, TradeSide.BUY));
        when(accountService.getAccountIdByExternalAccountId(externalAccountId)).thenReturn(ACCOUNT_ID);

        mockMvc.perform(get("/api/v1/accounts/{accountId}/tasks/{tradeId}", externalAccountId, externalTradeId)
                        .contextPath("/api"))
                .andExpect(status().isForbidden());
    }

    private static Instrument apple() {
        Instrument instrument = new Instrument("Apple Inc", "AAPL", InstrumentType.STOCK);
        instrument.setInstrumentId(INSTRUMENT_ID);
        return instrument;
    }

    private AccountTrade trade(Long accountId, TradeSide side) {
        AccountTrade trade = new AccountTrade(accountId, INSTRUMENT_ID, side, new BigDecimal("10"),
                new BigDecimal("150.00"), new BigDecimal("1500.00"));
        trade.setExternalTradeId(externalTradeId);
        trade.setTradeStatus(TradeStatus.FULFILLED);
        trade.setTradeTime(OffsetDateTime.parse("2026-10-25T14:30:00Z"));
        return trade;
    }

    @Test
    void getPerformance_returnsTheDailyPoints() throws Exception {
        when(accountService.getAccountIdByExternalAccountId(externalAccountId)).thenReturn(ACCOUNT_ID);
        when(performanceService.getPerformance(ACCOUNT_ID, PerformanceRange.ONE_MONTH)).thenReturn(List.of(
                new PerformancePoint(LocalDate.of(2026, 10, 22), new BigDecimal("200.00"), new BigDecimal("190.00")),
                new PerformancePoint(LocalDate.of(2026, 10, 23), new BigDecimal("210.50"), new BigDecimal("190.00"))));

        mockMvc.perform(get("/api/v1/accounts/{accountId}/performance", externalAccountId)
                        .contextPath("/api")
                        .param("range", "ONE_MONTH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].date").value("2026-10-22"))
                .andExpect(jsonPath("$[0].value").value(200.00))
                .andExpect(jsonPath("$[0].invested").value(190.00))
                .andExpect(jsonPath("$[1].value").value(210.50));
    }

    @Test
    void getPerformance_withoutARange_coversThreeMonths() throws Exception {
        when(accountService.getAccountIdByExternalAccountId(externalAccountId)).thenReturn(ACCOUNT_ID);
        when(performanceService.getPerformance(ACCOUNT_ID, PerformanceRange.THREE_MONTHS)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/accounts/{accountId}/performance", externalAccountId).contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void getPerformance_unknownRange_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{accountId}/performance", externalAccountId)
                        .contextPath("/api")
                        .param("range", "FOREVER"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(performanceService);
    }
}
