package com.fintechfive.ftrade.kafka;

public final class KafkaTopics {

    public static final String TRADE_SUBMITTED = "trade.submitted";
    public static final String TRADE_VALIDATED = "trade.validated";
    public static final String TRADE_RECORDED = "trade.recorded";
    public static final String TRADE_ACCEPTED = "trade.accepted";
    public static final String TRADE_FINISHED = "trade.finished";
    public static final String TRADE_REJECTED = "trade.rejected";

    private KafkaTopics() {
    }
}