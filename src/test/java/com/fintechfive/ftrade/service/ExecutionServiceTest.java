package com.fintechfive.ftrade.service;

import com.fintechfive.ftrade.dto.TradeFinishedDTO;
import com.fintechfive.ftrade.dto.TradeRecordedDTO;
import com.fintechfive.ftrade.exception.InsufficientFundsException;
import com.fintechfive.ftrade.exception.InsufficientHoldingsException;
import com.fintechfive.ftrade.exception.PriceUnavailableException;
import java.math.BigDecimal;
import java.util.NoSuchElementException;
import java.util.UUID;

import com.fintechfive.ftrade.enums.RejectionCode;
import com.fintechfive.ftrade.enums.TradeSide;
import com.fintechfive.ftrade.enums.TradeStatus;
import com.fintechfive.ftrade.repository.AccountTradePriceMapper;
import com.fintechfive.ftrade.repository.AccountTradeStatusMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExecutionServiceTest {

    private static final Long ACCOUNT_ID = 1L;
    private static final Long INSTRUMENT_ID = 10L;
    private static final Long TRADE_ID = 42L;
    private static final BigDecimal PRICE = new BigDecimal("100");
    private static final UUID EXTERNAL_TRADE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock
    private AccountService accountService;

    @Mock
    private AccountHoldingService accountHoldingService;

    @Mock
    private InstrumentService instrumentService;

    @Mock
    private AccountTradeStatusMapper accountTradeStatusMapper;

    @Mock
    private AccountTradePriceMapper accountTradePriceMapper;

    @Mock
    private PlatformTransactionManager transactionManager;

    private ExecutionService executionService;

    @BeforeEach
    void setUp() {
        executionService = new ExecutionService(accountService, accountHoldingService, instrumentService,
                accountTradeStatusMapper, accountTradePriceMapper, new TransactionTemplate(transactionManager));
    }

    // Compares by value, ignoring scale (so 500 matches 500.0000)
    private static BigDecimal amountEqualTo(String expected) {
        return argThat(actual -> actual != null && actual.compareTo(new BigDecimal(expected)) == 0);
    }

    private static TradeRecordedDTO recorded(TradeSide side, String quantity) {
        return new TradeRecordedDTO(INSTRUMENT_ID, ACCOUNT_ID, side, new BigDecimal(quantity),
                new BigDecimal("99"), TRADE_ID, EXTERNAL_TRADE_ID);
    }

    @Test
    void recordedBuyIsFulfilledAtTheCurrentPrice() {
        when(accountTradeStatusMapper.getTradeStatusByTradeId(TRADE_ID)).thenReturn(TradeStatus.PENDING);
        when(instrumentService.getCurrentPrice(INSTRUMENT_ID)).thenReturn(PRICE);

        TradeFinishedDTO outcome = executionService.execute(recorded(TradeSide.BUY, "5"));

        InOrder inOrder = inOrder(accountService, accountHoldingService, accountTradePriceMapper,
                accountTradeStatusMapper, transactionManager);
        inOrder.verify(accountService).purchase(eq(ACCOUNT_ID), amountEqualTo("500"));
        inOrder.verify(accountHoldingService).addQuantity(eq(ACCOUNT_ID), eq(INSTRUMENT_ID), amountEqualTo("5"));
        inOrder.verify(accountTradeStatusMapper).insertTradeStatus(TRADE_ID, TradeStatus.ACCEPTED);
        inOrder.verify(accountTradePriceMapper).insertTradePrice(eq(TRADE_ID), amountEqualTo("100"), amountEqualTo("500"));
        inOrder.verify(accountTradeStatusMapper).insertTradeStatus(TRADE_ID, TradeStatus.FULFILLED);
        inOrder.verify(transactionManager).commit(any());
        verify(transactionManager, never()).rollback(any());

        assertEquals(TRADE_ID, outcome.tradeId());
        assertEquals(ACCOUNT_ID, outcome.accountId());
        assertEquals(INSTRUMENT_ID, outcome.instrumentId());
        assertEquals(TradeSide.BUY, outcome.side());
        assertEquals(0, outcome.quantity().compareTo(new BigDecimal("5")));
        assertEquals(EXTERNAL_TRADE_ID, outcome.externalTradeId());
        assertEquals(0, outcome.price().compareTo(PRICE));
        assertNull(outcome.reason());
        assertNull(outcome.rejectionCode());
    }

    @Test
    void recordedSellIsFulfilledAtTheCurrentPrice() {
        when(accountTradeStatusMapper.getTradeStatusByTradeId(TRADE_ID)).thenReturn(TradeStatus.PENDING);
        when(instrumentService.getCurrentPrice(INSTRUMENT_ID)).thenReturn(PRICE);

        TradeFinishedDTO outcome = executionService.execute(recorded(TradeSide.SELL, "5"));

        InOrder inOrder = inOrder(accountHoldingService, accountTradeStatusMapper, accountService);
        inOrder.verify(accountHoldingService).removeQuantity(eq(ACCOUNT_ID), eq(INSTRUMENT_ID), amountEqualTo("5"));
        inOrder.verify(accountService).sell(eq(ACCOUNT_ID), amountEqualTo("500"));
        inOrder.verify(accountTradeStatusMapper).insertTradeStatus(TRADE_ID, TradeStatus.ACCEPTED);
        verify(accountTradePriceMapper).insertTradePrice(eq(TRADE_ID), amountEqualTo("100"), amountEqualTo("500"));
        verify(accountTradeStatusMapper).insertTradeStatus(TRADE_ID, TradeStatus.FULFILLED);
        verify(transactionManager).commit(any());
        assertEquals(0, outcome.price().compareTo(PRICE));
        assertNull(outcome.reason());
    }

    @Test
    void recordedBuyIsRejectedWhenFundsAreInsufficient() {
        when(instrumentService.getCurrentPrice(INSTRUMENT_ID)).thenReturn(PRICE);
        doThrow(new InsufficientFundsException("Insufficient funds"))
                .when(accountService).purchase(eq(ACCOUNT_ID), any());

        TradeFinishedDTO outcome = executionService.execute(recorded(TradeSide.BUY, "5"));

        verifyNoInteractions(accountHoldingService);
        verify(accountTradePriceMapper, never()).insertTradePrice(any(), any(), any());
        verify(accountTradeStatusMapper, never()).insertTradeStatus(TRADE_ID, TradeStatus.ACCEPTED);
        verify(accountTradeStatusMapper, never()).insertTradeStatus(TRADE_ID, TradeStatus.FULFILLED);
        verify(transactionManager).rollback(any());
        verify(transactionManager, never()).commit(any());
        verify(accountTradeStatusMapper).insertTradeStatus(TRADE_ID, TradeStatus.REJECTED);
        assertNull(outcome.price());
        assertEquals("Insufficient funds", outcome.reason());
        assertEquals(RejectionCode.INSUFFICIENT_FUNDS, outcome.rejectionCode());
        assertEquals(EXTERNAL_TRADE_ID, outcome.externalTradeId());
    }

    @Test
    void recordedSellIsRejectedWhenHoldingsAreInsufficient() {
        when(instrumentService.getCurrentPrice(INSTRUMENT_ID)).thenReturn(PRICE);
        doThrow(new InsufficientHoldingsException("Insufficient quantity"))
                .when(accountHoldingService).removeQuantity(eq(ACCOUNT_ID), eq(INSTRUMENT_ID), any());

        TradeFinishedDTO outcome = executionService.execute(recorded(TradeSide.SELL, "5"));

        verifyNoInteractions(accountService);
        verify(accountTradeStatusMapper, never()).insertTradeStatus(TRADE_ID, TradeStatus.ACCEPTED);
        verify(transactionManager).rollback(any());
        verify(accountTradeStatusMapper).insertTradeStatus(TRADE_ID, TradeStatus.REJECTED);
        assertNull(outcome.price());
        assertEquals("Insufficient quantity", outcome.reason());
        assertEquals(RejectionCode.INSUFFICIENT_SHARES, outcome.rejectionCode());
    }

    @Test
    void recordedTradeIsRejectedWhenNoPriceIsAvailable() {
        when(instrumentService.getCurrentPrice(INSTRUMENT_ID))
                .thenThrow(new PriceUnavailableException("No price available for X:BTC-USD"));

        TradeFinishedDTO outcome = executionService.execute(recorded(TradeSide.BUY, "5"));

        verifyNoInteractions(accountService, accountHoldingService, accountTradePriceMapper, transactionManager);
        verify(accountTradeStatusMapper).insertTradeStatus(TRADE_ID, TradeStatus.REJECTED);
        assertNull(outcome.price());
        assertEquals("No price available for X:BTC-USD", outcome.reason());
        assertEquals(RejectionCode.PRICE_UNAVAILABLE, outcome.rejectionCode());
    }

    @Test
    void recordedTradeIsRejectedWhenInstrumentIsUnknown() {
        when(instrumentService.getCurrentPrice(INSTRUMENT_ID))
                .thenThrow(new NoSuchElementException("No instrument with id 10"));

        TradeFinishedDTO outcome = executionService.execute(recorded(TradeSide.BUY, "5"));

        verifyNoInteractions(accountService, accountHoldingService, transactionManager);
        verify(accountTradeStatusMapper).insertTradeStatus(TRADE_ID, TradeStatus.REJECTED);
        assertNull(outcome.price());
        assertEquals("No instrument with id 10", outcome.reason());
        assertEquals(RejectionCode.INVALID_ORDER, outcome.rejectionCode());
    }

    @Test
    void recordedTradeIsRejectedWhenPriceApiFails() {
        when(instrumentService.getCurrentPrice(INSTRUMENT_ID))
                .thenThrow(new RestClientException("Fauxnance unavailable"));

        TradeFinishedDTO outcome = executionService.execute(recorded(TradeSide.BUY, "5"));

        verifyNoInteractions(accountService, accountHoldingService, transactionManager);
        verify(accountTradeStatusMapper).insertTradeStatus(TRADE_ID, TradeStatus.REJECTED);
        assertNull(outcome.price());
        assertEquals("Fauxnance unavailable", outcome.reason());
        assertEquals(RejectionCode.PRICE_UNAVAILABLE, outcome.rejectionCode());
    }

    @Test
    void databaseErrorRollsBackAndPropagates() {
        when(instrumentService.getCurrentPrice(INSTRUMENT_ID)).thenReturn(PRICE);
        doThrow(new DataAccessResourceFailureException("Database down"))
                .when(accountTradePriceMapper).insertTradePrice(any(), any(), any());

        TradeRecordedDTO buy = recorded(TradeSide.BUY, "5");
        assertThrows(DataAccessResourceFailureException.class, () -> executionService.execute(buy));

        verify(transactionManager).rollback(any());
        verify(transactionManager, never()).commit(any());
        // ACCEPTED was written inside the transaction that rolled back
        verify(accountTradeStatusMapper, never()).insertTradeStatus(TRADE_ID, TradeStatus.FULFILLED);
        verify(accountTradeStatusMapper, never()).insertTradeStatus(TRADE_ID, TradeStatus.REJECTED);
    }

    @Test
    void redeliveredFulfilledTradeReturnsStoredPrice() {
        when(accountTradeStatusMapper.getTradeStatusByTradeId(TRADE_ID)).thenReturn(TradeStatus.FULFILLED);
        when(accountTradePriceMapper.getTradePriceByTradeId(TRADE_ID)).thenReturn(new BigDecimal("100.0"));

        TradeFinishedDTO outcome = executionService.execute(recorded(TradeSide.BUY, "5"));

        verifyNoInteractions(instrumentService, accountService, accountHoldingService, transactionManager);
        verify(accountTradeStatusMapper, never()).insertTradeStatus(any(), any());
        verify(accountTradePriceMapper, never()).insertTradePrice(any(), any(), any());
        assertEquals(TRADE_ID, outcome.tradeId());
        assertEquals(0, outcome.price().compareTo(PRICE));
        assertNull(outcome.rejectionCode());
    }

    @Test
    void redeliveredRejectedTradeReturnsNoPrice() {
        when(accountTradeStatusMapper.getTradeStatusByTradeId(TRADE_ID)).thenReturn(TradeStatus.REJECTED);
        // a rejected trade has no account_trade_price row
        when(accountTradePriceMapper.getTradePriceByTradeId(TRADE_ID)).thenReturn(null);

        TradeFinishedDTO outcome = executionService.execute(recorded(TradeSide.BUY, "5"));

        verifyNoInteractions(instrumentService, accountService, accountHoldingService, transactionManager);
        verify(accountTradeStatusMapper, never()).insertTradeStatus(any(), any());
        assertNull(outcome.price());
        // the first rejection's reason isn't stored
        assertEquals(RejectionCode.UNKNOWN, outcome.rejectionCode());
        assertEquals(EXTERNAL_TRADE_ID, outcome.externalTradeId());
    }

    @Test
    void recordedTradeWithoutASideIsRejected() {
        when(instrumentService.getCurrentPrice(INSTRUMENT_ID)).thenReturn(PRICE);

        TradeFinishedDTO outcome = executionService.execute(recorded(null, "5"));

        verifyNoInteractions(accountService, accountHoldingService);
        verify(transactionManager).rollback(any());
        verify(accountTradeStatusMapper).insertTradeStatus(TRADE_ID, TradeStatus.REJECTED);
        assertNull(outcome.price());
        assertEquals("Side must be BUY or SELL, got null", outcome.reason());
        assertEquals(RejectionCode.INVALID_ORDER, outcome.rejectionCode());
    }
}
