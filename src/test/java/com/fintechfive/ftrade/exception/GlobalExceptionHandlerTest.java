package com.fintechfive.ftrade.exception;

import com.fintechfive.ftrade.dto.ErrorResponseDTO;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/trades");

    // MethodArgumentNotValidException needs a MethodParameter; any method with a parameter will do
    @SuppressWarnings("unused")
    private void target(Object body) {
        // intentionally empty: only its signature is used, to build the MethodParameter
    }

    private static MethodArgumentNotValidException validationFailure(BeanPropertyBindingResult bindingResult)
            throws NoSuchMethodException {
        Method method = GlobalExceptionHandlerTest.class.getDeclaredMethod("target", Object.class);
        return new MethodArgumentNotValidException(new MethodParameter(method, 0), bindingResult);
    }

    @Test
    void validationFailureReturnsBadRequestWithEachFieldError() throws Exception {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "tradeRequest");
        bindingResult.reject("invalid", "trade request is invalid");
        bindingResult.addError(new FieldError(
                "tradeRequest", "quantity", "must be greater than 0"));
        bindingResult.addError(new FieldError(
                "tradeRequest", "side", "must not be null"));

        ResponseEntity<ErrorResponseDTO> response = handler.handleValidation(validationFailure(bindingResult));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        ErrorResponseDTO body = response.getBody();
        assertNotNull(body);
        assertAll(
                () -> assertEquals(HttpStatus.BAD_REQUEST, body.status()),
                () -> assertEquals("Bad Request", body.error()),
                () -> assertEquals("request failed validation", body.message()),
                // the global (non-field) error is not reported as a field error
                () -> assertEquals(2, body.fieldErrors().size()),
                () -> assertEquals(new ErrorResponseDTO.FieldError("quantity", "must be greater than 0"),
                        body.fieldErrors().get(0)),
                () -> assertEquals(new ErrorResponseDTO.FieldError("side", "must not be null"),
                        body.fieldErrors().get(1))
        );
    }

    @Test
    void unexpectedExceptionReturnsGenericInternalServerError() {
        ResponseEntity<ErrorResponseDTO> response =
                handler.handleUnexpected(new RuntimeException("database password is hunter2"), request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        ErrorResponseDTO body = response.getBody();
        assertNotNull(body);
        assertAll(
                () -> assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, body.status()),
                () -> assertEquals("Internal Server Error", body.error()),
                // the exception's own message must not leak to the client
                () -> assertEquals("an unexpected error occurred", body.message()),
                () -> assertTrue(body.fieldErrors().isEmpty())
        );
    }
}
