package com.fintechfive.ftrade.exception;

public class ClientDetailsRequiredException extends RuntimeException {

    public ClientDetailsRequiredException(String message) {
        super(message);
    }
}
