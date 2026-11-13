package com.fintechfive.ftrade.service;

import com.fintechfive.ftrade.exception.InsufficientHoldingsException;
import com.fintechfive.ftrade.enums.ActivityStatus;
import com.fintechfive.ftrade.repository.AccountHoldingMapper;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

/**
 * Manages active account holding quantities by creating new holding snapshots for position changes.
 */
@Service
public class AccountHoldingService {

    // Must match the CHECK constraint on account_holdings.status

    private final AccountHoldingMapper accountHoldingMapper;

    /**
     * Creates an account holding service backed by the holding mapper.
     *
     * @param accountHoldingMapper mapper used to query and store holding snapshots
     */
    public AccountHoldingService(AccountHoldingMapper accountHoldingMapper) {
        this.accountHoldingMapper = accountHoldingMapper;
    }

    /**
     * Returns the active quantity held for an instrument in an account.
     *
     * @param accountId account identifier
     * @param instrumentId instrument identifier
     * @return active quantity, or {@link BigDecimal#ZERO} when no active holding exists
     */
    public BigDecimal getQuantity(Long accountId, Long instrumentId) {
        BigDecimal quantity = accountHoldingMapper.findActiveQuantity(accountId, instrumentId);
        return quantity == null ? BigDecimal.ZERO : quantity;
    }

    /**
     * Increases the active quantity held for an instrument in an account.
     *
     * @param accountId account identifier
     * @param instrumentId instrument identifier
     * @param quantity positive quantity to add
     * @throws IllegalArgumentException if the quantity is null or not positive
     */
    public void addQuantity(Long accountId, Long instrumentId, BigDecimal quantity) {
        requirePositive(quantity);
        BigDecimal newQuantity = getQuantity(accountId, instrumentId).add(quantity);
        accountHoldingMapper.deactivateHolding(accountId, instrumentId);
        accountHoldingMapper.insertSnapshot(accountId, instrumentId, newQuantity, ActivityStatus.ACTIVE);
    }

    /**
     * Decreases the active quantity held for an instrument in an account.
     *
     * @param accountId account identifier
     * @param instrumentId instrument identifier
     * @param quantity positive quantity to remove
     * @throws IllegalArgumentException if the quantity is null or not positive
     * @throws IllegalStateException if the account holds less than the requested quantity
     */
    public void removeQuantity(Long accountId, Long instrumentId, BigDecimal quantity) {
        requirePositive(quantity);
        BigDecimal current = getQuantity(accountId, instrumentId);
        if (current.compareTo(quantity) < 0) {
            throw new InsufficientHoldingsException("Insufficient quantity of instrument " + instrumentId
                    + " in account " + accountId + ": holding " + current + ", requested " + quantity);
        }
        BigDecimal newQuantity = current.subtract(quantity);
        // a fully sold position should be marked as inactive
        ActivityStatus status = newQuantity.compareTo(BigDecimal.ZERO) == 0 ? ActivityStatus.INACTIVE : ActivityStatus.ACTIVE;
        accountHoldingMapper.deactivateHolding(accountId, instrumentId);
        accountHoldingMapper.insertSnapshot(accountId, instrumentId, newQuantity, status);
    }

    private static void requirePositive(BigDecimal quantity) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
    }
}
