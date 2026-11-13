package com.fintechfive.ftrade.exception;

public class InsufficientHoldingsException extends IllegalStateException {

    public InsufficientHoldingsException(String message) {
        super(message);
    }
}
