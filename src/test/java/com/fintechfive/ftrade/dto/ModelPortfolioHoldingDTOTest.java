package com.fintechfive.ftrade.dto;

import com.fintechfive.ftrade.enums.ActivityStatus;
import static org.junit.jupiter.api.Assertions.*;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ModelPortfolioHoldingDTO Tests")
class ModelPortfolioHoldingDTOTest {
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
    void testValidModelPortfolioHoldingDTO() {
        ModelPortfolioHoldingDTO dto = new ModelPortfolioHoldingDTO(
            1L, 2L, "2026-09-25", 25.5, ActivityStatus.ACTIVE);
        
        assertEquals(1L, dto.modelPortfolioId());
        assertEquals(2L, dto.instrumentId());
        assertEquals("2026-09-25", dto.effectiveDate());
        assertEquals(25.5, dto.targetWeightPct(), 0.01);
        assertEquals(ActivityStatus.ACTIVE, dto.status());
    }

    @Test
    void testValidateNonBlankEffectiveDate() {
        ModelPortfolioHoldingDTO dto = new ModelPortfolioHoldingDTO(
            1L, 2L, "", 25.5, ActivityStatus.ACTIVE);
        Set<ConstraintViolation<ModelPortfolioHoldingDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
    }

    @Test
    void testValidatePositiveOrZeroWeightPct() {
        ModelPortfolioHoldingDTO dto = new ModelPortfolioHoldingDTO(
            1L, 2L, "2026-09-25", -5.0, ActivityStatus.ACTIVE);
        Set<ConstraintViolation<ModelPortfolioHoldingDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
    }

    @Test
    void testValidateZeroWeightPctAllowed() {
        ModelPortfolioHoldingDTO dto = new ModelPortfolioHoldingDTO(
            1L, 2L, "2026-09-25", 0.0, ActivityStatus.ACTIVE);
        Set<ConstraintViolation<ModelPortfolioHoldingDTO>> violations = validator.validate(dto);
        assertTrue(violations.isEmpty());
    }

    @Test
    void testValidateStatusRequired() {
        ModelPortfolioHoldingDTO dto = new ModelPortfolioHoldingDTO(
            1L, 2L, "2026-09-25", 25.5, null);
        Set<ConstraintViolation<ModelPortfolioHoldingDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
    }

    @Test
    void testRecordEquality() {
        ModelPortfolioHoldingDTO dto1 = new ModelPortfolioHoldingDTO(
            1L, 2L, "2026-09-25", 25.5, ActivityStatus.ACTIVE);
        ModelPortfolioHoldingDTO dto2 = new ModelPortfolioHoldingDTO(
            1L, 2L, "2026-09-25", 25.5, ActivityStatus.ACTIVE);
        ModelPortfolioHoldingDTO dto3 = new ModelPortfolioHoldingDTO(
            1L, 2L, "2026-09-25", 30.0, ActivityStatus.ACTIVE);
        
        assertEquals(dto1, dto2);
        assertNotEquals(dto1, dto3);
    }
}

