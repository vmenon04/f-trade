package com.fintechfive.ftrade.dto;

import static org.junit.jupiter.api.Assertions.*;

import com.fintechfive.ftrade.enums.TradeSide;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("OrderRequestDTO Tests")
class TradeRequestDTOTest {

    private Validator validator;

    private TradeRequestDTO initialDTO;

    private UUID externalAccountId;

    @BeforeEach
    void setUp() {
        try (ValidatorFactory factory = Validation.byDefaultProvider()
            .configure()
            .messageInterpolator(new ParameterMessageInterpolator())
            .buildValidatorFactory()) {
            validator = factory.getValidator();
        }
        externalAccountId = UUID.randomUUID();
        initialDTO = new TradeRequestDTO(externalAccountId, 2L, TradeSide.BUY, new BigDecimal("150.50"));
    }

    @Test
    void testValidOrderRequestDTO() {
        
        assertEquals(externalAccountId, initialDTO.accountId());
        assertEquals(2L, initialDTO.instrumentId());
        assertEquals(TradeSide.BUY, initialDTO.side());
        assertEquals(new BigDecimal("150.50"), initialDTO.quantity());
    }

    @Test
    void testValidatePositiveInstrumentId() {
        TradeRequestDTO invalidInstrumentDto = new TradeRequestDTO(
                externalAccountId, invalidInstrumentId(), TradeSide.BUY, new BigDecimal("150.50"));
        Set<ConstraintViolation<TradeRequestDTO>> violations = validator.validate(invalidInstrumentDto);
        assertFalse(violations.isEmpty());
    }

    @Test
    void testValidateNullSide() {
        TradeRequestDTO dto = new TradeRequestDTO(
                externalAccountId, 2L, null, new BigDecimal("150.50"));
        Set<ConstraintViolation<TradeRequestDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
    }

    @Test
    void testValidatePositiveQuantity() {
        TradeRequestDTO dto = new TradeRequestDTO(
                externalAccountId, 2L, TradeSide.BUY, new BigDecimal("-150.50"));
        Set<ConstraintViolation<TradeRequestDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
    }

    @Test
    void testValidateNonNullQuantity() {
        TradeRequestDTO dto = new TradeRequestDTO(
                externalAccountId, 2L, TradeSide.BUY, null);
        Set<ConstraintViolation<TradeRequestDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
    }

    @Test
    void testValidateZeroQuantity() {
        TradeRequestDTO dto = new TradeRequestDTO(
                externalAccountId, 2L, TradeSide.BUY, BigDecimal.ZERO);
        Set<ConstraintViolation<TradeRequestDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
    }

    @Test
    void testRecordEquality() {
        TradeRequestDTO dto1 = new TradeRequestDTO(
                externalAccountId, 2L, TradeSide.BUY, new BigDecimal("150.50"));
        TradeRequestDTO dto2 = new TradeRequestDTO(
                externalAccountId, 2L, TradeSide.BUY, new BigDecimal("150.50"));
        TradeRequestDTO dto3 = new TradeRequestDTO(
                externalAccountId, 2L, TradeSide.SELL, new BigDecimal("150.50"));
        
        assertEquals(dto1, dto2);
        assertNotEquals(dto1, dto3);
    }

    @Test
    void testSupportsBuyAndSellSides() {
        TradeRequestDTO buyOrder = new TradeRequestDTO(
                externalAccountId, 2L, TradeSide.BUY, new BigDecimal("100"));
        TradeRequestDTO sellOrder = new TradeRequestDTO(
                externalAccountId, 2L, TradeSide.SELL, new BigDecimal("100"));
        
        assertEquals(TradeSide.BUY, buyOrder.side());
        assertEquals(TradeSide.SELL, sellOrder.side());
        assertNotEquals(buyOrder, sellOrder);
    }

    private Long invalidInstrumentId() {
        return Long.parseLong("-1");
    }
}

