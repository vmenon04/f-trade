package com.fintechfive.ftrade.service;

import com.fintechfive.ftrade.exception.InsufficientFundsException;
import com.fintechfive.ftrade.entity.AccountTrade;
import com.fintechfive.ftrade.enums.TradeSide;
import com.fintechfive.ftrade.enums.TradeStatus;
import com.fintechfive.ftrade.repository.AccountMapper;
import com.fintechfive.ftrade.repository.AccountTradeMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.springframework.stereotype.Service;

/**
 * Provides account cash balance lookups and balance adjustments for cash movements and trade execution.
 */
@Service
public class AccountService {

    private final AccountMapper accountMapper;
    private final AccountTradeMapper accountTradeMapper;

    /**
     * Creates an account service backed by the account and trade mappers.
     *
     * @param accountMapper mapper used to query and update account balances
     * @param accountTradeMapper mapper used to read the account's trades (for the cost basis)
     */
    public AccountService(AccountMapper accountMapper, AccountTradeMapper accountTradeMapper) {
        this.accountMapper = accountMapper;
        this.accountTradeMapper = accountTradeMapper;
    }


    public Long getAccountIdByExternalAccountId(UUID externalAccountId) {
        Long accountId = accountMapper.findAccountIdByExternalAccountId(externalAccountId);
        if (accountId == null) {
            throw new NoSuchElementException("No account with external account id " + externalAccountId);
        }
        return accountId;
    }

    /**
     * Returns the current balance for an account.
     *
     * @param accountId account identifier
     * @return current account balance
     * @throws NoSuchElementException if the account does not exist
     */
    public BigDecimal getBalance(Long accountId) {
        BigDecimal balance = accountMapper.findBalance(accountId);
        if (balance == null) {
            throw new NoSuchElementException("No account with id " + accountId);
        }
        return balance;
    }

    /**
     * Adds client cash to an account.
     *
     * @param accountId account identifier
     * @param amount positive amount to deposit
     * @throws IllegalArgumentException if the amount is null or not positive
     * @throws NoSuchElementException if the account does not exist
     */
    public void deposit(Long accountId, BigDecimal amount) {
        increaseBalance(accountId, amount);
    }

    /**
     * Removes client cash from an account.
     *
     * @param accountId account identifier
     * @param amount positive amount to withdraw
     * @throws IllegalArgumentException if the amount is null or not positive
     * @throws IllegalStateException if the account has insufficient funds
     */
    public void withdraw(Long accountId, BigDecimal amount) {
        reduceBalance(accountId, amount);
    }

    /**
     * Deducts cash from an account to settle a purchase.
     *
     * @param accountId account identifier
     * @param amount positive cash amount to remove
     * @throws IllegalArgumentException if the amount is null or not positive
     * @throws IllegalStateException if the account has insufficient funds
     */
    public void purchase(Long accountId, BigDecimal amount) {
        reduceBalance(accountId, amount);
    }

    /**
     * Credits cash to an account after a sale.
     *
     * @param accountId account identifier
     * @param amount positive cash amount to add
     * @throws IllegalArgumentException if the amount is null or not positive
     * @throws NoSuchElementException if the account does not exist
     */
    public void sell(Long accountId, BigDecimal amount) {
        increaseBalance(accountId, amount);
    }

    private void increaseBalance(Long accountId, BigDecimal amount) {
        requirePositive(amount);
        if (accountMapper.increaseBalance(accountId, amount) == 0) {
            throw new NoSuchElementException("No account with id " + accountId);
        }
    }

    private void reduceBalance(Long accountId, BigDecimal amount) {
        requirePositive(amount);
        if (accountMapper.reduceBalance(accountId, amount) == 0) {
            BigDecimal balance = getBalance(accountId);
            throw new InsufficientFundsException(
                    "Insufficient funds in account " + accountId + ": balance " + balance + ", requested " + amount);
        }
    }

    /**
     * Returns what the account paid for the shares it still holds, by the average cost method: a buy adds its total
     * price to the instrument's cost, and a sell removes the sold shares' share of that cost. Only FULFILLED trades
     * with a price count, so holdings with no priced trades (e.g. sample data) add no cost.
     *
     * @param accountId account identifier
     * @return the cost basis of the shares still held, to the cent
     */
    public BigDecimal getCostBasis(Long accountId) {
        Map<Long, Position> positions = new HashMap<>();
        // oldest first, so each sell takes the average cost of the buys before it
        for (AccountTrade trade : accountTradeMapper.findByAccountId(accountId)) {
            if (trade.getStatus() != TradeStatus.FULFILLED || trade.getTotalPrice() == null) {
                continue;
            }
            Position position = positions.computeIfAbsent(trade.getInstrumentId(), id -> new Position());
            if (trade.getTradeSide() == TradeSide.BUY) {
                position.buy(trade.getQuantity(), trade.getTotalPrice());
            } else {
                position.sell(trade.getQuantity());
            }
        }
        return positions.values().stream()
                .map(position -> position.cost)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    // shares held of one instrument and what they cost in total
    private static final class Position {
        private BigDecimal quantity = BigDecimal.ZERO;
        private BigDecimal cost = BigDecimal.ZERO;

        void buy(BigDecimal shares, BigDecimal totalPrice) {
            quantity = quantity.add(shares);
            cost = cost.add(totalPrice);
        }

        void sell(BigDecimal shares) {
            if (quantity.signum() <= 0) {
                return;
            }
            if (shares.compareTo(quantity) >= 0) {
                quantity = BigDecimal.ZERO;
                cost = BigDecimal.ZERO;
                return;
            }
            // the sold shares take their average cost with them
            BigDecimal soldCost = cost.multiply(shares).divide(quantity, 10, RoundingMode.HALF_UP);
            cost = cost.subtract(soldCost);
            quantity = quantity.subtract(shares);
        }
    }

    private static void requirePositive(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
    }
}
