package com.fintechfive.ftrade.service;

import com.fintechfive.ftrade.exception.InsufficientFundsException;
import com.fintechfive.ftrade.entity.AccountTrade;
import com.fintechfive.ftrade.enums.TradeSide;
import com.fintechfive.ftrade.enums.TradeStatus;
import com.fintechfive.ftrade.repository.AccountMapper;
import com.fintechfive.ftrade.repository.AccountTradeMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountMapper accountMapper;

    @Mock
    private AccountTradeMapper accountTradeMapper;

    @InjectMocks
    private AccountService accountService;

    // Compares by value, ignoring scale (so 250.5 matches 250.50)
    private static BigDecimal amountEqualTo(String expected) {
        return argThat(actual -> actual != null && actual.compareTo(new BigDecimal(expected)) == 0);
    }

    @Test
    void getBalanceReturnsBalanceFromMapper() {
        when(accountMapper.findBalance(1L)).thenReturn(new BigDecimal("1000.00"));
        assertEquals(new BigDecimal("1000.00"), accountService.getBalance(1L));
    }

    @Test
    void getBalanceThrowsWhenAccountNotFound() {
        when(accountMapper.findBalance(99L)).thenReturn(null);
        assertThrows(NoSuchElementException.class, () -> accountService.getBalance(99L));
    }

    @Test
    void depositPassesAmountToMapper() {
        when(accountMapper.increaseBalance(eq(1L), amountEqualTo("250.50"))).thenReturn(1);
        accountService.deposit(1L, new BigDecimal("250.50"));
        verify(accountMapper).increaseBalance(eq(1L), amountEqualTo("250.50"));
    }

    @Test
    void depositRejectsNonPositiveAmount() {
        BigDecimal negative = new BigDecimal("-5");
        assertThrows(IllegalArgumentException.class, () -> accountService.deposit(1L, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> accountService.deposit(1L, negative));
        verifyNoInteractions(accountMapper);
    }

    @Test
    void depositThrowsWhenAccountNotFound() {
        when(accountMapper.increaseBalance(eq(99L), any())).thenReturn(0);
        BigDecimal amount = new BigDecimal("100");
        assertThrows(NoSuchElementException.class, () -> accountService.deposit(99L, amount));
    }

    @Test
    void withdrawPassesAmountToMapper() {
        when(accountMapper.reduceBalance(eq(1L), amountEqualTo("250.50"))).thenReturn(1);
        accountService.withdraw(1L, new BigDecimal("250.50"));
        verify(accountMapper).reduceBalance(eq(1L), amountEqualTo("250.50"));
    }

    @Test
    void withdrawRejectsNonPositiveAmount() {
        BigDecimal negative = new BigDecimal("-5");
        assertThrows(IllegalArgumentException.class, () -> accountService.withdraw(1L, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> accountService.withdraw(1L, negative));
        verifyNoInteractions(accountMapper);
    }

    @Test
    void withdrawThrowsWhenInsufficientFunds() {
        when(accountMapper.reduceBalance(eq(1L), any())).thenReturn(0);
        when(accountMapper.findBalance(1L)).thenReturn(new BigDecimal("1000.00"));
        BigDecimal amount = new BigDecimal("1000.01");
        assertThrows(InsufficientFundsException.class, () -> accountService.withdraw(1L, amount));
    }

    @Test
    void withdrawThrowsWhenAccountNotFound() {
        when(accountMapper.reduceBalance(eq(99L), any())).thenReturn(0);
        when(accountMapper.findBalance(99L)).thenReturn(null);
        BigDecimal amount = new BigDecimal("100");
        assertThrows(NoSuchElementException.class, () -> accountService.withdraw(99L, amount));
    }

    @Test
    void purchasePassesAmountToMapper() {
        when(accountMapper.reduceBalance(eq(1L), amountEqualTo("250.50"))).thenReturn(1);
        accountService.purchase(1L, new BigDecimal("250.50"));
        verify(accountMapper).reduceBalance(eq(1L), amountEqualTo("250.50"));
    }

    @Test
    void purchaseRejectsNonPositiveAmount() {
        BigDecimal negative = new BigDecimal("-5");
        assertThrows(IllegalArgumentException.class, () -> accountService.purchase(1L, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> accountService.purchase(1L, negative));
        verifyNoInteractions(accountMapper);
    }

    @Test
    void purchaseThrowsWhenInsufficientFunds() {
        when(accountMapper.reduceBalance(eq(1L), any())).thenReturn(0);
        when(accountMapper.findBalance(1L)).thenReturn(new BigDecimal("1000.00"));
        BigDecimal amount = new BigDecimal("1000.01");
        assertThrows(InsufficientFundsException.class, () -> accountService.purchase(1L, amount));
    }

    @Test
    void purchaseThrowsWhenAccountNotFound() {
        when(accountMapper.reduceBalance(eq(99L), any())).thenReturn(0);
        when(accountMapper.findBalance(99L)).thenReturn(null);
        BigDecimal amount = new BigDecimal("100");
        assertThrows(NoSuchElementException.class, () -> accountService.purchase(99L, amount));
    }

    @Test
    void sellPassesAmountToMapper() {
        when(accountMapper.increaseBalance(eq(1L), amountEqualTo("250.50"))).thenReturn(1);
        accountService.sell(1L, new BigDecimal("250.50"));
        verify(accountMapper).increaseBalance(eq(1L), amountEqualTo("250.50"));
    }

    @Test
    void sellRejectsNonPositiveAmount() {
        BigDecimal negative = new BigDecimal("-5");
        assertThrows(IllegalArgumentException.class, () -> accountService.sell(1L, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> accountService.sell(1L, negative));
        verifyNoInteractions(accountMapper);
    }

    @Test
    void sellThrowsWhenAccountNotFound() {
        when(accountMapper.increaseBalance(eq(99L), any())).thenReturn(0);
        BigDecimal amount = new BigDecimal("100");
        assertThrows(NoSuchElementException.class, () -> accountService.sell(99L, amount));
    }

    @Test
    void getAccountIdByExternalAccountIdReturnsIdFromMapper() {
        UUID externalAccountId = UUID.randomUUID();
        when(accountMapper.findAccountIdByExternalAccountId(externalAccountId)).thenReturn(1L);
        assertEquals(1L, accountService.getAccountIdByExternalAccountId(externalAccountId));
    }

    @Test
    void getAccountIdByExternalAccountIdThrowsWhenAccountNotFound() {
        UUID externalAccountId = UUID.randomUUID();
        when(accountMapper.findAccountIdByExternalAccountId(externalAccountId)).thenReturn(null);
        assertThrows(NoSuchElementException.class,
                () -> accountService.getAccountIdByExternalAccountId(externalAccountId));
    }

    @Test
    void rejectsNullAmount() {
        assertThrows(IllegalArgumentException.class, () -> accountService.deposit(1L, null));
        assertThrows(IllegalArgumentException.class, () -> accountService.withdraw(1L, null));
        verifyNoInteractions(accountMapper);
    }

    // --- cost basis: what the account paid for the shares it still holds (average cost) ---

    private static final Long AAPL = 1L;
    private static final Long MSFT = 2L;

    private void tradesAre(AccountTrade... trades) {
        when(accountTradeMapper.findByAccountId(1L)).thenReturn(List.of(trades));
    }

    @Test
    void costBasisWithNoTradesIsZero() {
        tradesAre();
        assertEquals(new BigDecimal("0.00"), accountService.getCostBasis(1L));
    }

    @Test
    void costBasisAddsTheTotalPriceOfBuys() {
        tradesAre(
                fulfilled(AAPL, TradeSide.BUY, "2", "200"),
                fulfilled(AAPL, TradeSide.BUY, "2", "300"),
                fulfilled(MSFT, TradeSide.BUY, "1", "800"));
        assertEquals(new BigDecimal("1300.00"), accountService.getCostBasis(1L));
    }

    @Test
    void costBasisRemovesSoldSharesAtAverageCost() {
        // 4 shares cost 500 (average 125); selling 1 leaves 3 at 125 = 375, whatever it sold for
        tradesAre(
                fulfilled(AAPL, TradeSide.BUY, "2", "200"),
                fulfilled(AAPL, TradeSide.BUY, "2", "300"),
                fulfilled(AAPL, TradeSide.SELL, "1", "999"));
        assertEquals(new BigDecimal("375.00"), accountService.getCostBasis(1L));
    }

    @Test
    void costBasisAfterSellingEverythingIsZero() {
        tradesAre(
                fulfilled(AAPL, TradeSide.BUY, "3", "150"),
                fulfilled(AAPL, TradeSide.SELL, "3", "180"));
        assertEquals(new BigDecimal("0.00"), accountService.getCostBasis(1L));
    }

    @Test
    void costBasisIgnoresPendingRejectedAndUnpricedTrades() {
        tradesAre(
                trade(AAPL, TradeSide.BUY, "5", "500", TradeStatus.PENDING),
                trade(AAPL, TradeSide.BUY, "5", "500", TradeStatus.REJECTED),
                trade(AAPL, TradeSide.BUY, "5", null, TradeStatus.FULFILLED),
                fulfilled(AAPL, TradeSide.BUY, "1", "100"));
        assertEquals(new BigDecimal("100.00"), accountService.getCostBasis(1L));
    }

    private static AccountTrade fulfilled(Long instrumentId, TradeSide side, String quantity, String totalPrice) {
        return trade(instrumentId, side, quantity, totalPrice, TradeStatus.FULFILLED);
    }

    private static AccountTrade trade(Long instrumentId, TradeSide side, String quantity, String totalPrice,
                                      TradeStatus status) {
        AccountTrade trade = new AccountTrade(1L, instrumentId, side, new BigDecimal(quantity), null,
                totalPrice == null ? null : new BigDecimal(totalPrice));
        trade.setTradeStatus(status);
        return trade;
    }
}
