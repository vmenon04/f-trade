package com.fintechfive.ftrade.exception;

public class InsufficientFundsException extends IllegalStateException {

    public InsufficientFundsException(String message) {
        super(message);
    }
}
