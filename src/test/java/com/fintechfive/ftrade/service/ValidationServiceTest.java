package com.fintechfive.ftrade.service;

import com.fintechfive.ftrade.exception.InsufficientHoldingsException;
import com.fintechfive.ftrade.exception.InsufficientFundsException;
import com.fintechfive.ftrade.dto.TradeSubmittedDTO;
import com.fintechfive.ftrade.dto.TradeValidatedDTO;
import com.fintechfive.ftrade.entity.Instrument;
import com.fintechfive.ftrade.enums.InstrumentType;
import com.fintechfive.ftrade.enums.TradeSide;
import java.math.BigDecimal;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ValidationServiceTest {

    private static final UUID EXTERNAL_ACCOUNT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Long ACCOUNT_ID = 1L;
    private static final Long INSTRUMENT_ID = 10L;
    private static final BigDecimal PRICE = new BigDecimal("100");
    private static final String BALANCE = "1000";
    private static final String TICKER = "AAPL";
    private static final UUID EXTERNAL_TRADE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock
    private InstrumentService instrumentService;

    @Mock
    private AccountService accountService;

    @Mock
    private AccountHoldingService accountHoldingService;

    @InjectMocks
    private ValidationService validationService;

    private static TradeSubmittedDTO order(String side, String quantity) {
        return new TradeSubmittedDTO(
                INSTRUMENT_ID,
                EXTERNAL_ACCOUNT_ID,
                TradeSide.valueOf(side),
                new BigDecimal(quantity),
                EXTERNAL_TRADE_ID
        );
    }

    private void givenInstrumentAndBalance() {
        when(instrumentService.getInstrumentById(INSTRUMENT_ID))
                .thenReturn(new Instrument("Apple Inc", TICKER, InstrumentType.STOCK));
        when(instrumentService.getCurrentPrice(INSTRUMENT_ID)).thenReturn(PRICE);
        when(accountService.getAccountIdByExternalAccountId(EXTERNAL_ACCOUNT_ID)).thenReturn(ACCOUNT_ID);
        when(accountService.getBalance(ACCOUNT_ID)).thenReturn(new BigDecimal(BALANCE));
    }

    // 10 is exactly the cash available; 0.12345678 has the maximum 8 decimals
    @ParameterizedTest
    @ValueSource(strings = {"5", "10", "0.12345678"})
    void validBuyPasses(String quantity) {
        givenInstrumentAndBalance();
        TradeSubmittedDTO buy = order("BUY", quantity);
        assertDoesNotThrow(() -> validationService.validate(buy));
    }

    @Test
    void buyRejectsInsufficientCash() {
        givenInstrumentAndBalance();
        TradeSubmittedDTO buy = order("BUY", "11");
        assertThrows(InsufficientFundsException.class, () -> validationService.validate(buy));
    }

    @Test
    void validSellPasses() {
        givenInstrumentAndBalance();
        when(accountHoldingService.getQuantity(ACCOUNT_ID, INSTRUMENT_ID)).thenReturn(new BigDecimal("10"));
        TradeSubmittedDTO sell = order("SELL", "5");
        assertDoesNotThrow(() -> validationService.validate(sell));
    }

    @Test
    void sellRejectsInsufficientHoldings() {
        givenInstrumentAndBalance();
        when(accountHoldingService.getQuantity(ACCOUNT_ID, INSTRUMENT_ID)).thenReturn(new BigDecimal("10"));
        TradeSubmittedDTO sell = order("SELL", "11");
        assertThrows(InsufficientHoldingsException.class, () -> validationService.validate(sell));
    }

    @Test
    void rejectsUnknownSide() {
        TradeSubmittedDTO invalidOrder = new TradeSubmittedDTO(
                INSTRUMENT_ID,
                EXTERNAL_ACCOUNT_ID,
                null,
                new BigDecimal("5"),
                EXTERNAL_TRADE_ID
        );

        assertThrows(IllegalArgumentException.class, () -> validationService.validate(invalidOrder));
        verifyNoInteractions(instrumentService, accountService, accountHoldingService);
    }

    @Test
    void rejectsNonPositiveQuantity() {
        TradeSubmittedDTO zero = order("BUY", "0");
        TradeSubmittedDTO negative = order("BUY", "-5");
        assertThrows(IllegalArgumentException.class, () -> validationService.validate(zero));
        assertThrows(IllegalArgumentException.class, () -> validationService.validate(negative));
        verifyNoInteractions(instrumentService, accountService, accountHoldingService);
    }

    @Test
    void rejectsQuantityWithMoreThanEightDecimals() {
        TradeSubmittedDTO buy = order("BUY", "0.000000001");
        assertThrows(IllegalArgumentException.class, () -> validationService.validate(buy));
        verifyNoInteractions(instrumentService, accountService, accountHoldingService);
    }

    @Test
    void rejectsNullOrder() {
        assertThrows(IllegalArgumentException.class, () -> validationService.validate(null));
        verifyNoInteractions(instrumentService, accountService, accountHoldingService);
    }

    @Test
    void rejectsUnknownInstrument() {
        when(accountService.getAccountIdByExternalAccountId(EXTERNAL_ACCOUNT_ID)).thenReturn(ACCOUNT_ID);
        when(instrumentService.getInstrumentById(INSTRUMENT_ID)).thenThrow(new NoSuchElementException());
        TradeSubmittedDTO buy = order("BUY", "5");
        assertThrows(NoSuchElementException.class, () -> validationService.validate(buy));
    }

    @Test
    void rejectsUnknownAccount() {
        when(accountService.getAccountIdByExternalAccountId(EXTERNAL_ACCOUNT_ID)).thenThrow(new NoSuchElementException());
        TradeSubmittedDTO buy = order("BUY", "5");
        assertThrows(NoSuchElementException.class, () -> validationService.validate(buy));
    }

    @Test
    void validateSubmittedTradeReturnsValidatedDtoWithInternalAccountId() {
        givenInstrumentAndBalance();

        TradeValidatedDTO validatedDTO = validationService.validateSubmittedTrade(order("BUY", "5"));

        assertAll(
                () -> assertEquals(INSTRUMENT_ID, validatedDTO.instrumentId()),
                () -> assertEquals(ACCOUNT_ID, validatedDTO.accountId()),
                () -> assertEquals(TradeSide.BUY, validatedDTO.side()),
                () -> assertEquals(new BigDecimal("5"), validatedDTO.quantity()),
                () -> assertEquals(PRICE, validatedDTO.quote()),
                () -> assertEquals(EXTERNAL_TRADE_ID, validatedDTO.externalTradeId())
        );
    }

    @Test
    void rejectsNullQuantity() {
        TradeSubmittedDTO invalidOrder = new TradeSubmittedDTO(
                INSTRUMENT_ID,
                EXTERNAL_ACCOUNT_ID,
                TradeSide.BUY,
                null,
                EXTERNAL_TRADE_ID
        );

        assertThrows(IllegalArgumentException.class, () -> validationService.validate(invalidOrder));
        verifyNoInteractions(instrumentService, accountService, accountHoldingService);
    }

    private void givenInstrumentBalanceAndQuote(BigDecimal quote) {
        when(accountService.getAccountIdByExternalAccountId(EXTERNAL_ACCOUNT_ID)).thenReturn(ACCOUNT_ID);
        when(accountService.getBalance(ACCOUNT_ID)).thenReturn(new BigDecimal(BALANCE));
        when(instrumentService.getCurrentPrice(INSTRUMENT_ID)).thenReturn(quote);
    }

    @Test
    void rejectsMissingQuote() {
        givenInstrumentBalanceAndQuote(null);
        TradeSubmittedDTO buy = order("BUY", "5");
        assertThrows(IllegalArgumentException.class, () -> validationService.validate(buy));
    }

    @Test
    void rejectsNonPositiveQuote() {
        givenInstrumentBalanceAndQuote(BigDecimal.ZERO);
        TradeSubmittedDTO buy = order("BUY", "5");
        assertThrows(IllegalArgumentException.class, () -> validationService.validate(buy));
        verifyNoInteractions(accountHoldingService);
    }
}
