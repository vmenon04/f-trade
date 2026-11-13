package com.fintechfive.ftrade.dto;

import com.fintechfive.ftrade.enums.ActivityStatus;
import static org.junit.jupiter.api.Assertions.*;

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

@DisplayName("AccountHoldingDTO Tests")
class AccountHoldingDTOTest {
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
    @DisplayName("Should create AccountHoldingDTO with valid data")
    void testValidAccountHoldingDTO() {
        AccountHoldingDTO dto = new AccountHoldingDTO(1L, 2L, "2026-09-25", BigDecimal.valueOf(100), ActivityStatus.ACTIVE);
        
        assertEquals(1L, dto.accountId());
        assertEquals(2L, dto.instrumentId());
        assertEquals("2026-09-25", dto.asOfDate());
        assertEquals(BigDecimal.valueOf(100), dto.quantity());
        assertEquals(ActivityStatus.ACTIVE, dto.status());
    }

    @Test
    @DisplayName("Should validate positive accountId")
    void testValidateAccountId() {
        AccountHoldingDTO dto = new AccountHoldingDTO(invalidAccountId(), 2L, "2026-09-25", BigDecimal.valueOf(100), ActivityStatus.ACTIVE);
        
        Set<ConstraintViolation<AccountHoldingDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
            .anyMatch(v -> v.getPropertyPath().toString().equals("accountId")));
    }

    @Test
    @DisplayName("Should validate positive instrumentId")
    void testValidateInstrumentId() {
        AccountHoldingDTO dto = new AccountHoldingDTO(1L, invalidInstrumentId(), "2026-09-25", BigDecimal.valueOf(100), ActivityStatus.ACTIVE);
        
        Set<ConstraintViolation<AccountHoldingDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
            .anyMatch(v -> v.getPropertyPath().toString().equals("instrumentId")));
    }

    @Test
    @DisplayName("Should validate non-blank asOfDate")
    void testValidateBlankAsOfDate() {
        AccountHoldingDTO dto = new AccountHoldingDTO(1L, 2L, "   ", BigDecimal.valueOf(100), ActivityStatus.ACTIVE);
        
        Set<ConstraintViolation<AccountHoldingDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
            .anyMatch(v -> v.getPropertyPath().toString().equals("asOfDate")));
    }

    @Test
    @DisplayName("Should validate positive quantity")
    void testValidateQuantity() {
        AccountHoldingDTO dto = new AccountHoldingDTO(1L, 2L, "2026-09-25", BigDecimal.valueOf(-100), ActivityStatus.ACTIVE);
        
        Set<ConstraintViolation<AccountHoldingDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
            .anyMatch(v -> v.getPropertyPath().toString().equals("quantity")));
    }

    @Test
    @DisplayName("Should require a status")
    void testValidateStatusRequired() {
        AccountHoldingDTO dto = new AccountHoldingDTO(1L, 2L, "2026-09-25", BigDecimal.valueOf(100), null);
        
        Set<ConstraintViolation<AccountHoldingDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
            .anyMatch(v -> v.getPropertyPath().toString().equals("status")));
    }

    @Test
    @DisplayName("Should support record equality")
    void testRecordEquality() {
        AccountHoldingDTO dto1 = new AccountHoldingDTO(1L, 2L, "2026-09-25", BigDecimal.valueOf(100), ActivityStatus.ACTIVE);
        AccountHoldingDTO dto2 = new AccountHoldingDTO(1L, 2L, "2026-09-25", BigDecimal.valueOf(100), ActivityStatus.ACTIVE);
        AccountHoldingDTO dto3 = new AccountHoldingDTO(1L, 2L, "2026-09-25", BigDecimal.valueOf(100), ActivityStatus.INACTIVE);
        
        assertEquals(dto1, dto2);
        assertNotEquals(dto1, dto3);
    }

    private Long invalidAccountId() {
        return Long.parseLong("-1");
    }

    private Long invalidInstrumentId() {
        return Long.parseLong("-2");
    }
}

