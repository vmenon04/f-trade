package com.fintechfive.ftrade.dto;

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

@DisplayName("OrderResponseDTO Tests")
class OrderResponseDTOTest {
    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.byDefaultProvider()
            .configure()
            .messageInterpolator(new ParameterMessageInterpolator())
            .buildValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("Should create OrderResponseDTO with valid data")
    void testValidOrderResponseDTO() {
        OrderResponseDTO dto = new OrderResponseDTO(1L, "EXECUTED", 
            BigDecimal.valueOf(150.50), BigDecimal.valueOf(100.00), "Order executed");
        
        assertEquals(1L, dto.tradeId());
        assertEquals("EXECUTED", dto.status());
        assertEquals(BigDecimal.valueOf(150.50), dto.executedPrice());
        assertEquals(BigDecimal.valueOf(100.00), dto.executedQuantity());
        assertEquals("Order executed", dto.reason());
    }

    @Test
    @DisplayName("Should validate positive tradeId")
    void testValidateNegativeTradeId() {
        OrderResponseDTO dto = new OrderResponseDTO(-1L, "EXECUTED", BigDecimal.valueOf(150), BigDecimal.valueOf(100), "Success");
        
        Set<ConstraintViolation<OrderResponseDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
            .anyMatch(v -> v.getPropertyPath().toString().equals("tradeId")));
    }

    @Test
    @DisplayName("Should validate zero tradeId is invalid")
    void testValidateZeroTradeId() {
        OrderResponseDTO dto = new OrderResponseDTO(0L, "EXECUTED", 
            BigDecimal.valueOf(150), BigDecimal.valueOf(100), "Success");
        
        Set<ConstraintViolation<OrderResponseDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
    }

    @Test
    @DisplayName("Should validate non-blank status")
    void testValidateBlankStatus() {
        OrderResponseDTO dto = new OrderResponseDTO(1L, "   ", 
            BigDecimal.valueOf(150), BigDecimal.valueOf(100), "Success");
        
        Set<ConstraintViolation<OrderResponseDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
            .anyMatch(v -> v.getPropertyPath().toString().equals("status")));
    }

    @Test
    @DisplayName("Should validate positive executedPrice")
    void testValidateNegativePrice() {
        OrderResponseDTO dto = new OrderResponseDTO(1L, "EXECUTED", 
            BigDecimal.valueOf(-150), BigDecimal.valueOf(100), "Success");
        
        Set<ConstraintViolation<OrderResponseDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
            .anyMatch(v -> v.getPropertyPath().toString().equals("executedPrice")));
    }

    @Test
    @DisplayName("Should validate positive executedQuantity")
    void testValidateNegativeQuantity() {
        OrderResponseDTO dto = new OrderResponseDTO(1L, "EXECUTED", 
            BigDecimal.valueOf(150), BigDecimal.valueOf(-100), "Success");
        
        Set<ConstraintViolation<OrderResponseDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
            .anyMatch(v -> v.getPropertyPath().toString().equals("executedQuantity")));
    }

    @Test
    @DisplayName("Should validate non-blank reason")
    void testValidateBlankReason() {
        OrderResponseDTO dto = new OrderResponseDTO(1L, "EXECUTED", 
            BigDecimal.valueOf(150), BigDecimal.valueOf(100), "");
        
        Set<ConstraintViolation<OrderResponseDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
            .anyMatch(v -> v.getPropertyPath().toString().equals("reason")));
    }

    @Test
    @DisplayName("Should validate all fields when invalid")
    void testValidateAllFieldsInvalid() {
        OrderResponseDTO dto = new OrderResponseDTO(-1L, "   ", 
            BigDecimal.valueOf(-150), BigDecimal.valueOf(-100), "");
        
        Set<ConstraintViolation<OrderResponseDTO>> violations = validator.validate(dto);
        assertEquals(5, violations.size());
    }

    @Test
    @DisplayName("Should support different order statuses")
    void testDifferentStatuses() {
        OrderResponseDTO executed = new OrderResponseDTO(1L, "EXECUTED", 
            BigDecimal.valueOf(150), BigDecimal.valueOf(100), "Filled");
        OrderResponseDTO pending = new OrderResponseDTO(2L, "PENDING", 
            BigDecimal.valueOf(150), BigDecimal.valueOf(100), "Awaiting");
        OrderResponseDTO cancelled = new OrderResponseDTO(3L, "CANCELLED", 
            BigDecimal.valueOf(150), BigDecimal.valueOf(100), "Cancelled");
        
        assertEquals("EXECUTED", executed.status());
        assertEquals("PENDING", pending.status());
        assertEquals("CANCELLED", cancelled.status());
    }

    @Test
    @DisplayName("Should support record equality")
    void testRecordEquality() {
        OrderResponseDTO dto1 = new OrderResponseDTO(1L, "EXECUTED", 
            BigDecimal.valueOf(150), BigDecimal.valueOf(100), "Success");
        OrderResponseDTO dto2 = new OrderResponseDTO(1L, "EXECUTED", 
            BigDecimal.valueOf(150), BigDecimal.valueOf(100), "Success");
        OrderResponseDTO dto3 = new OrderResponseDTO(2L, "CANCELLED", 
            BigDecimal.valueOf(150), BigDecimal.valueOf(100), "Cancelled");
        
        assertEquals(dto1, dto2);
        assertNotEquals(dto1, dto3);
    }

    @Test
    @DisplayName("Should support record hashCode")
    void testRecordHashCode() {
        OrderResponseDTO dto1 = new OrderResponseDTO(1L, "EXECUTED", 
            BigDecimal.valueOf(150), BigDecimal.valueOf(100), "Success");
        OrderResponseDTO dto2 = new OrderResponseDTO(1L, "EXECUTED", 
            BigDecimal.valueOf(150), BigDecimal.valueOf(100), "Success");
        
        assertEquals(dto1.hashCode(), dto2.hashCode());
    }
}

