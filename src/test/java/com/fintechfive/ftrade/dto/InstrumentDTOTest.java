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

@DisplayName("InstrumentDTO Tests")
class InstrumentDTOTest {
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
    void testValidInstrumentDTO() {
        InstrumentDTO dto = new InstrumentDTO(1L, "Apple", "AAPL");
        assertEquals(1L, dto.instrumentId());
        assertEquals("Apple", dto.name());
    }

    @Test
    void testValidateInstrumentId() {
        InstrumentDTO dto = new InstrumentDTO(invalidInstrumentId(), "Apple", "AAPL");
        Set<ConstraintViolation<InstrumentDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
    }

    @Test
    void testValidateBlankName() {
        InstrumentDTO dto = new InstrumentDTO(1L, "   ", "AAPL");
        Set<ConstraintViolation<InstrumentDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
    }

    @Test
    void testValidateBlankTicker() {
        InstrumentDTO dto = new InstrumentDTO(1L, "Apple", "");
        Set<ConstraintViolation<InstrumentDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
    }

    @Test
    void testRecordEquality() {
        InstrumentDTO dto1 = new InstrumentDTO(1L, "Apple", "AAPL");
        InstrumentDTO dto2 = new InstrumentDTO(1L, "Apple", "AAPL");
        InstrumentDTO dto3 = new InstrumentDTO(2L, "Microsoft", "MSFT");
        
        assertEquals(dto1, dto2);
        assertNotEquals(dto1, dto3);
    }

    private Long invalidInstrumentId() {
        return Long.parseLong("-1");
    }
}

