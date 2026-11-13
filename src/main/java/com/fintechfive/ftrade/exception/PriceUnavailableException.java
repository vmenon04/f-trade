package com.fintechfive.ftrade.exception;

public class PriceUnavailableException extends IllegalStateException {

    public PriceUnavailableException(String message) {
        super(message);
    }
}
