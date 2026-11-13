package com.fintechfive.ftrade.dto;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import java.util.stream.Stream;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

@DisplayName("ErrorResponseDTO Tests")
class ErrorResponseDTOTest {
    private Validator validator;

    @BeforeEach
    void setUp() {
        try( ValidatorFactory factory = Validation.byDefaultProvider()
            .configure()
            .messageInterpolator(new ParameterMessageInterpolator())
            .buildValidatorFactory()) {
            validator = factory.getValidator();
        } catch (Exception e) {
            fail("Failed to set up validator: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Should create ErrorResponseDTO with valid data")
    void testValidErrorResponseDTO() {
        ErrorResponseDTO dto = new ErrorResponseDTO(HttpStatus.BAD_REQUEST, "INSUFFICIENT_FUNDS", "Account does not have sufficient balance", null);
        
        assertEquals("INSUFFICIENT_FUNDS", dto.error());
        assertEquals("Account does not have sufficient balance", dto.message());
    }

    static Stream<Arguments> missingFields() {
        return Stream.of(
            Arguments.of("   ", "Account does not have sufficient balance", "error"),
            Arguments.of(null, "Account does not have sufficient balance", "error"),
            Arguments.of("INSUFFICIENT_FUNDS", "", "message"),
            Arguments.of("INSUFFICIENT_FUNDS", null, "message"));
    }

    @ParameterizedTest(name = "error={0}, message={1} -> violation on {2}")
    @MethodSource("missingFields")
    @DisplayName("Should validate non-blank error and message fields")
    void testValidateBlankOrNullField(String error, String message, String invalidField) {
        ErrorResponseDTO dto = new ErrorResponseDTO(HttpStatus.BAD_REQUEST, error, message, null);
        
        Set<ConstraintViolation<ErrorResponseDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
            .anyMatch(v -> v.getPropertyPath().toString().equals(invalidField)));
    }

    @Test
    @DisplayName("Should validate both fields when both are blank")
    void testValidateBothBlank() {
        ErrorResponseDTO dto = new ErrorResponseDTO(HttpStatus.BAD_REQUEST, "  ", "   ", null);
        
        Set<ConstraintViolation<ErrorResponseDTO>> violations = validator.validate(dto);
        assertEquals(2, violations.size());
    }

    @Test
    @DisplayName("Should support record equality")
    void testRecordEquality() {
        ErrorResponseDTO dto1 = new ErrorResponseDTO(HttpStatus.BAD_REQUEST, "INSUFFICIENT_FUNDS", "Low balance", null);
        ErrorResponseDTO dto2 = new ErrorResponseDTO(HttpStatus.BAD_REQUEST, "INSUFFICIENT_FUNDS", "Low balance", null);
        ErrorResponseDTO dto3 = new ErrorResponseDTO(HttpStatus.BAD_REQUEST, "INVALID_ACCOUNT", "Account not found", null);
        
        assertEquals(dto1, dto2);
        assertNotEquals(dto1, dto3);
    }

    @Test
    @DisplayName("Should support record hashCode")
    void testRecordHashCode() {
        ErrorResponseDTO dto1 = new ErrorResponseDTO(HttpStatus.BAD_REQUEST, "INSUFFICIENT_FUNDS", "Low balance", null);
        ErrorResponseDTO dto2 = new ErrorResponseDTO(HttpStatus.BAD_REQUEST, "INSUFFICIENT_FUNDS", "Low balance", null);
        
        assertEquals(dto1.hashCode(), dto2.hashCode());
    }

    @Test
    @DisplayName("Should support different error codes")
    void testDifferentErrorCodes() {
        ErrorResponseDTO invalidAccount = new ErrorResponseDTO(HttpStatus.BAD_REQUEST, "INVALID_ACCOUNT", "Account not found", null);
        ErrorResponseDTO insufficientFunds = new ErrorResponseDTO(HttpStatus.BAD_REQUEST, "INSUFFICIENT_FUNDS", "Low balance", null);
        ErrorResponseDTO invalidInstrument = new ErrorResponseDTO(HttpStatus.BAD_REQUEST, "INVALID_INSTRUMENT", "Instrument not available", null);
        
        assertNotEquals(invalidAccount, insufficientFunds);
        assertNotEquals(insufficientFunds, invalidInstrument);
        assertNotEquals(invalidAccount, invalidInstrument);
    }

    @Test
    @DisplayName("Should preserve immutability of record")
    void testRecordImmutability() {
        ErrorResponseDTO dto = new ErrorResponseDTO(HttpStatus.BAD_REQUEST, "ERROR_CODE", "Error message", null);
        
        assertEquals("ERROR_CODE", dto.error());
        assertEquals("Error message", dto.message());
        // Records are immutable, accessor methods return the record components
    }
}

