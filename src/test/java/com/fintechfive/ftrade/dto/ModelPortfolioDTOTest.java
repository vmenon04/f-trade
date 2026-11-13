package com.fintechfive.ftrade.dto;

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

@DisplayName("ModelPortfolioDTO Tests")
class ModelPortfolioDTOTest {
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
    void testValidModelPortfolioDTO() {
        ModelPortfolioDTO dto = new ModelPortfolioDTO(1L, "Growth Portfolio");
        assertEquals(1L, dto.modelPortfolioId());
        assertEquals("Growth Portfolio", dto.name());
    }

    @Test
    void testValidatePositiveId() {
        ModelPortfolioDTO dto = new ModelPortfolioDTO(invalidModelPortfolioId(), "Growth Portfolio");
        Set<ConstraintViolation<ModelPortfolioDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
    }

    @Test
    void testValidateNonBlankName() {
        ModelPortfolioDTO dto = new ModelPortfolioDTO(1L, "   ");
        Set<ConstraintViolation<ModelPortfolioDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
    }

    @Test
    void testValidateNullName() {
        ModelPortfolioDTO dto = new ModelPortfolioDTO(1L, null);
        Set<ConstraintViolation<ModelPortfolioDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
    }

    @Test
    void testRecordEquality() {
        ModelPortfolioDTO dto1 = new ModelPortfolioDTO(1L, "Growth Portfolio");
        ModelPortfolioDTO dto2 = new ModelPortfolioDTO(1L, "Growth Portfolio");
        ModelPortfolioDTO dto3 = new ModelPortfolioDTO(2L, "Value Portfolio");
        
        assertEquals(dto1, dto2);
        assertNotEquals(dto1, dto3);
    }

    private Long invalidModelPortfolioId() {
        return Long.parseLong("-1");
    }
}

