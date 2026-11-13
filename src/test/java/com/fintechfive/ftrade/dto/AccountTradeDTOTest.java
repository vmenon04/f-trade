package com.fintechfive.ftrade.dto;

import static org.junit.jupiter.api.Assertions.*;

import com.fintechfive.ftrade.enums.TradeSide;
import com.fintechfive.ftrade.enums.TradeStatus;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.util.Set;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AccountTradeDTO Tests")
class AccountTradeDTOTest {
    private Validator validator;

    @BeforeEach
    void setUp() {
        try (ValidatorFactory factory = Validation.byDefaultProvider()
            .configure()
            .messageInterpolator(new ParameterMessageInterpolator())
            .buildValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    @DisplayName("Should create AccountTradeDTO with valid data")
    void testValidAccountTradeDTO() {
        AccountTradeDTO dto = new AccountTradeDTO(1L, "2026-09-25T10:30:00", 2L, 3L, TradeSide.BUY, 
            BigDecimal.valueOf(100), BigDecimal.valueOf(150), BigDecimal.valueOf(15000), TradeStatus.FULFILLED);
        
        assertEquals(1L, dto.tradeId());
        assertEquals("2026-09-25T10:30:00", dto.tradeTime());
        assertEquals(2L, dto.accountId());
        assertEquals(3L, dto.instrumentId());
        assertEquals(TradeSide.BUY, dto.tradeSide());
        assertEquals(BigDecimal.valueOf(100), dto.quantity());
        assertEquals(BigDecimal.valueOf(150), dto.ppu());
        assertEquals(BigDecimal.valueOf(15000), dto.totalPrice());
        assertEquals(TradeStatus.FULFILLED, dto.status());
    }

    @Test
    @DisplayName("Should validate positive tradeId")
    void testValidateTradeId() {
        AccountTradeDTO dto = new AccountTradeDTO(invalidTradeId(), "2026-09-25T10:30:00", 2L, 3L, TradeSide.BUY, 
            BigDecimal.valueOf(100), BigDecimal.valueOf(150), BigDecimal.valueOf(15000), TradeStatus.FULFILLED);
        
        Set<ConstraintViolation<AccountTradeDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
            .anyMatch(v -> v.getPropertyPath().toString().equals("tradeId")));
    }

    @Test
    @DisplayName("Should validate non-blank tradeTime")
    void testValidateBlankTradeTime() {
        AccountTradeDTO dto = new AccountTradeDTO(1L, "   ", 2L, 3L, TradeSide.BUY, 
            BigDecimal.valueOf(100), BigDecimal.valueOf(150), BigDecimal.valueOf(15000), TradeStatus.FULFILLED);
        
        Set<ConstraintViolation<AccountTradeDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
            .anyMatch(v -> v.getPropertyPath().toString().equals("tradeTime")));
    }

    @Test
    @DisplayName("Should validate positive accountId")
    void testValidateAccountId() {
        AccountTradeDTO dto = new AccountTradeDTO(1L, "2026-09-25T10:30:00", invalidAccountId(), 3L, TradeSide.BUY, 
            BigDecimal.valueOf(100), BigDecimal.valueOf(150), BigDecimal.valueOf(15000), TradeStatus.FULFILLED);
        
        Set<ConstraintViolation<AccountTradeDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
            .anyMatch(v -> v.getPropertyPath().toString().equals("accountId")));
    }

    @Test
    @DisplayName("Should validate positive instrumentId")
    void testValidateInstrumentId() {
        AccountTradeDTO dto = new AccountTradeDTO(1L, "2026-09-25T10:30:00", 2L, invalidInstrumentId(), TradeSide.BUY, 
            BigDecimal.valueOf(100), BigDecimal.valueOf(150), BigDecimal.valueOf(15000), TradeStatus.FULFILLED);
        
        Set<ConstraintViolation<AccountTradeDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
            .anyMatch(v -> v.getPropertyPath().toString().equals("instrumentId")));
    }

    @Test
    @DisplayName("Should validate non-blank tradeSide")
    void testValidateBlankTradeType() {
        AccountTradeDTO dto = new AccountTradeDTO(1L, "2026-09-25T10:30:00", 2L, 3L, null,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150), BigDecimal.valueOf(15000), TradeStatus.FULFILLED);
        
        Set<ConstraintViolation<AccountTradeDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
            .anyMatch(v -> v.getPropertyPath().toString().equals("tradeSide")));
    }

    @Test
    @DisplayName("Should validate positive quantity")
    void testValidateQuantity() {
        AccountTradeDTO dto = new AccountTradeDTO(1L, "2026-09-25T10:30:00", 2L, 3L, TradeSide.BUY, 
            BigDecimal.valueOf(-100), BigDecimal.valueOf(150), BigDecimal.valueOf(15000), TradeStatus.FULFILLED);
        
        Set<ConstraintViolation<AccountTradeDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
            .anyMatch(v -> v.getPropertyPath().toString().equals("quantity")));
    }

    @Test
    @DisplayName("Should validate positive ppu")
    void testValidatePPU() {
        AccountTradeDTO dto = new AccountTradeDTO(1L, "2026-09-25T10:30:00", 2L, 3L, TradeSide.BUY, 
            BigDecimal.valueOf(100), BigDecimal.valueOf(-150), BigDecimal.valueOf(15000), TradeStatus.FULFILLED);
        
        Set<ConstraintViolation<AccountTradeDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
            .anyMatch(v -> v.getPropertyPath().toString().equals("ppu")));
    }

    @Test
    @DisplayName("Should validate non-blank status")
    void testValidateBlankStatus() {
        AccountTradeDTO dto = new AccountTradeDTO(1L, "2026-09-25T10:30:00", 2L, 3L, TradeSide.BUY, 
            BigDecimal.valueOf(100), BigDecimal.valueOf(150), BigDecimal.valueOf(15000), null);
        
        Set<ConstraintViolation<AccountTradeDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
            .anyMatch(v -> v.getPropertyPath().toString().equals("status")));
    }

    @Test
    @DisplayName("Should support record equality")
    void testRecordEquality() {
        AccountTradeDTO dto1 = new AccountTradeDTO(1L, "2026-09-25T10:30:00", 2L, 3L, TradeSide.BUY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150), BigDecimal.valueOf(15000), TradeStatus.FULFILLED);
        AccountTradeDTO dto2 = new AccountTradeDTO(1L, "2026-09-25T10:30:00", 2L, 3L, TradeSide.BUY, 
            BigDecimal.valueOf(100), BigDecimal.valueOf(150), BigDecimal.valueOf(15000), TradeStatus.FULFILLED);
        AccountTradeDTO dto3 = new AccountTradeDTO(1L, "2026-09-25T10:30:00", 2L, 3L, TradeSide.SELL, 
            BigDecimal.valueOf(100), BigDecimal.valueOf(150), BigDecimal.valueOf(15000), TradeStatus.FULFILLED);
        
        assertEquals(dto1, dto2);
        assertNotEquals(dto1, dto3);
    }

    private Long invalidTradeId() {
        return Long.parseLong("-1");
    }

    private Long invalidAccountId() {
        return Long.parseLong("-2");
    }

    private Long invalidInstrumentId() {
        return Long.parseLong("-3");
    }
}
