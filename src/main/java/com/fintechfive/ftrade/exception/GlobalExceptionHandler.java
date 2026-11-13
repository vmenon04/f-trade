package com.fintechfive.ftrade.exception;

import com.fintechfive.ftrade.dto.ErrorResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;
import java.util.NoSuchElementException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);


    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDTO> handleValidation(MethodArgumentNotValidException ex) {
        List<ErrorResponseDTO.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErrorResponseDTO.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        ErrorResponseDTO body = ErrorResponseDTO.withFieldErrors(
                HttpStatus.BAD_REQUEST, "Bad Request", "request failed validation", fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }


    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> handleUnexpected(Exception ex, jakarta.servlet.http.HttpServletRequest request) {
        LOGGER.error("Unhandled exception for {} {}", request.getMethod(), request.getRequestURI(), ex);
        ErrorResponseDTO body = ErrorResponseDTO.of(
                HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", "an unexpected error occurred");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

        @ExceptionHandler(NoSuchElementException.class)
        public ResponseEntity<ErrorResponseDTO> handleNotFound(NoSuchElementException ex) {
                ErrorResponseDTO body = ErrorResponseDTO.of(
                                HttpStatus.NOT_FOUND, "Not Found", ex.getMessage());
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
        }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponseDTO> handleAccessDenied(AccessDeniedException ex) {
        ErrorResponseDTO body = ErrorResponseDTO.of(
                HttpStatus.FORBIDDEN, "Forbidden", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(InvalidTradeException.class)
    public ResponseEntity<ErrorResponseDTO> handleInvalidTrade(InvalidTradeException ex,
                                                               jakarta.servlet.http.HttpServletRequest request) {
        LOGGER.warn("Invalid trade for {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        ErrorResponseDTO body = ErrorResponseDTO.of(
                HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage());
        return ResponseEntity.badRequest().body(body);
    }

    // e.g. a path id that isn't a UUID; without this the catch-all above would answer 500
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponseDTO> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        ErrorResponseDTO body = ErrorResponseDTO.of(
                HttpStatus.BAD_REQUEST, "Bad Request", "invalid value for " + ex.getName());
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(ClientDetailsRequiredException.class)
    public ResponseEntity<ErrorResponseDTO> handleClientDetailsRequired(ClientDetailsRequiredException ex) {
        ErrorResponseDTO body = ErrorResponseDTO.of(
                HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage());
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(AccountConflictException.class)
    public ResponseEntity<ErrorResponseDTO> handleAccountConflict(AccountConflictException ex) {
        ErrorResponseDTO body = ErrorResponseDTO.of(
                HttpStatus.CONFLICT, "Conflict", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }
}