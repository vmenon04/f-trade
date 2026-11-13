package com.fintechfive.ftrade.service;

import com.fintechfive.ftrade.exception.InsufficientHoldingsException;
import com.fintechfive.ftrade.exception.InsufficientFundsException;
import java.math.BigDecimal;
import java.util.UUID;

import com.fintechfive.ftrade.dto.TradeSubmittedDTO;
import com.fintechfive.ftrade.dto.TradeValidatedDTO;
import com.fintechfive.ftrade.enums.TradeSide;
import org.springframework.stereotype.Service;

@Service
public class ValidationService {

    private final InstrumentService instrumentService;
    private final AccountService accountService;
    private final AccountHoldingService accountHoldingService;

    public ValidationService(InstrumentService instrumentService, AccountService accountService,
                             AccountHoldingService accountHoldingService) {
        this.instrumentService = instrumentService;
        this.accountService = accountService;
        this.accountHoldingService = accountHoldingService;
    }

    public TradeValidatedDTO validateSubmittedTrade(TradeSubmittedDTO tradeSubmittedDTO) {
        validate(tradeSubmittedDTO);

        return new TradeValidatedDTO(
                tradeSubmittedDTO.instrumentId(),
                accountService.getAccountIdByExternalAccountId(tradeSubmittedDTO.accountId()),
                tradeSubmittedDTO.side(),
                tradeSubmittedDTO.quantity(),
                instrumentService.getCurrentPrice(tradeSubmittedDTO.instrumentId()),
                tradeSubmittedDTO.externalTradeId()
        );
    }

    public void validate(TradeSubmittedDTO tradeSubmittedDTO) {
        checkOrderNotNull(tradeSubmittedDTO);
        TradeSide side = checkSideValid(tradeSubmittedDTO);
        BigDecimal quantity = checkQuantityPositive(tradeSubmittedDTO);
        checkDecimalsValid(quantity);

        UUID externalAccountId = tradeSubmittedDTO.accountId();
        Long accountId = accountService.getAccountIdByExternalAccountId(externalAccountId);

        Long instrumentId = tradeSubmittedDTO.instrumentId();
        instrumentService.getInstrumentById(instrumentId);
        BigDecimal balance = accountService.getBalance(accountId);

        BigDecimal quote = instrumentService.getCurrentPrice(instrumentId);
        checkPricePositive(quote);

        if (TradeSide.BUY.equals(side)) {
            checkSufficientFunds(quote, quantity, balance, accountId);
        } else {
            checkSufficientInstrumentQuantity(accountId, instrumentId, quantity);
        }
    }

    private void checkSufficientInstrumentQuantity(Long accountId, Long instrumentId, BigDecimal quantity) {
        BigDecimal held = accountHoldingService.getQuantity(accountId, instrumentId);
        if (held.compareTo(quantity) < 0) {
            throw new InsufficientHoldingsException("Insufficient quantity of instrument " + instrumentId
                    + " in account " + accountId + ": holding " + held + ", order quantity " + quantity);
        }
    }

    private void checkSufficientFunds(BigDecimal quote, BigDecimal quantity, BigDecimal balance, Long accountId) {
        BigDecimal cost = quote.multiply(quantity);
        if (balance.compareTo(cost) < 0) {
            throw new InsufficientFundsException("Insufficient funds in account " + accountId
                    + ": balance " + balance + ", order cost " + cost);
        }
    }

    private void checkDecimalsValid(BigDecimal quantity) {
        if (quantity.stripTrailingZeros().scale() > 8) {
            throw new IllegalArgumentException("Quantity can have at most 8 decimal places, got " + quantity);
        }
    }

    private void checkPricePositive(BigDecimal price) {
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Price must be positive");
        }
    }

    private BigDecimal checkQuantityPositive(TradeSubmittedDTO order) {
        BigDecimal quantity = order.quantity();
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        return quantity;
    }

    private TradeSide checkSideValid(TradeSubmittedDTO order) {
        TradeSide side = order.side();
        if (!TradeSide.BUY.equals(side) && !TradeSide.SELL.equals(side)) {
            throw new IllegalArgumentException("Side must be BUY or SELL, got " + side);
        }
        return side;
    }

    private void checkOrderNotNull(TradeSubmittedDTO order) {
        if (order == null) {
            throw new IllegalArgumentException("Order must not be null");
        }
    }
}
