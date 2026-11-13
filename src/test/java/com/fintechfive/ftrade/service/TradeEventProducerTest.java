package com.fintechfive.ftrade.service;

import com.fintechfive.ftrade.dto.TradeAcceptedDTO;
import com.fintechfive.ftrade.dto.TradeFinishedDTO;
import com.fintechfive.ftrade.dto.TradeRecordedDTO;
import com.fintechfive.ftrade.dto.TradeRejectedDTO;
import com.fintechfive.ftrade.dto.TradeSubmittedDTO;
import com.fintechfive.ftrade.dto.TradeValidatedDTO;
import com.fintechfive.ftrade.enums.RejectionCode;
import com.fintechfive.ftrade.enums.TradeSide;
import com.fintechfive.ftrade.kafka.KafkaTopics;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TradeEventProducerTest {

    private static final Long ACCOUNT_ID = 1L;
    private static final Long INSTRUMENT_ID = 10L;
    private static final Long TRADE_ID = 42L;
    private static final UUID EXTERNAL_ACCOUNT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID EXTERNAL_TRADE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final BigDecimal QUANTITY = new BigDecimal("5");
    private static final BigDecimal PRICE = new BigDecimal("100");

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private TradeEventProducer producer;

    private final CompletableFuture<SendResult<String, Object>> sent = new CompletableFuture<>();

    @Test
    void tradeSubmittedIsKeyedByExternalAccountId() {
        TradeSubmittedDTO event = new TradeSubmittedDTO(
                INSTRUMENT_ID, EXTERNAL_ACCOUNT_ID, TradeSide.BUY, QUANTITY, EXTERNAL_TRADE_ID);
        when(kafkaTemplate.send(KafkaTopics.TRADE_SUBMITTED, EXTERNAL_ACCOUNT_ID.toString(), event)).thenReturn(sent);

        assertSame(sent, producer.publishTradeSubmitted(event));
    }

    @Test
    void tradeValidatedIsKeyedByAccountId() {
        TradeValidatedDTO event = new TradeValidatedDTO(
                INSTRUMENT_ID, ACCOUNT_ID, TradeSide.BUY, QUANTITY, PRICE, EXTERNAL_TRADE_ID);
        when(kafkaTemplate.send(KafkaTopics.TRADE_VALIDATED, "1", event)).thenReturn(sent);

        assertSame(sent, producer.publishTradeValidated(event));
    }

    @Test
    void tradeRecordedIsKeyedByAccountId() {
        TradeRecordedDTO event = new TradeRecordedDTO(
                INSTRUMENT_ID, ACCOUNT_ID, TradeSide.SELL, QUANTITY, PRICE, TRADE_ID, EXTERNAL_TRADE_ID);
        when(kafkaTemplate.send(KafkaTopics.TRADE_RECORDED, "1", event)).thenReturn(sent);

        assertSame(sent, producer.publishTradeRecorded(event));
    }

    @Test
    void tradeAcceptedIsKeyedByTradeId() {
        TradeAcceptedDTO event = new TradeAcceptedDTO(
                TRADE_ID, ACCOUNT_ID, INSTRUMENT_ID, TradeSide.BUY, QUANTITY, PRICE);
        when(kafkaTemplate.send(KafkaTopics.TRADE_ACCEPTED, "42", event)).thenReturn(sent);

        assertSame(sent, producer.publishTradeAccepted(event));
    }

    @Test
    void tradeFinishedIsKeyedByTradeId() {
        TradeFinishedDTO event = new TradeFinishedDTO(
                TRADE_ID, ACCOUNT_ID, INSTRUMENT_ID, TradeSide.BUY, QUANTITY, PRICE, null, EXTERNAL_TRADE_ID, null);
        when(kafkaTemplate.send(KafkaTopics.TRADE_FINISHED, "42", event)).thenReturn(sent);

        assertSame(sent, producer.publishTradeFinished(event));
    }

    // trade.rejected has no internal ids, so it is keyed by the trade
    @Test
    void tradeRejectedIsKeyedByExternalTradeId() {
        TradeRejectedDTO event = new TradeRejectedDTO(EXTERNAL_TRADE_ID, RejectionCode.INSUFFICIENT_FUNDS);
        when(kafkaTemplate.send(KafkaTopics.TRADE_REJECTED, EXTERNAL_TRADE_ID.toString(), event)).thenReturn(sent);

        assertSame(sent, producer.publishTradeRejected(event));
    }
}
