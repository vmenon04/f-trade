package com.fintechfive.ftrade.config;

import tools.jackson.databind.ObjectMapper;
import com.fintechfive.ftrade.dto.TradeRecordedDTO;
import com.fintechfive.ftrade.enums.TradeSide;
import com.fintechfive.ftrade.exception.InsufficientFundsException;
import com.fintechfive.ftrade.exception.InsufficientHoldingsException;
import com.fintechfive.ftrade.exception.PriceUnavailableException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.converter.ConversionException;
import org.springframework.kafka.support.converter.RecordMessageConverter;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import org.springframework.messaging.Message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class KafkaConsumerConfigTest {

    private static final UUID EXTERNAL_TRADE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final RecordMessageConverter converter = new KafkaConsumerConfig().messageConverter();

    private static ConsumerRecord<String, String> consumerRecord(String json) {
        return new ConsumerRecord<>("trade.recorded", 0, 0L, "1", json);
    }

    @Test
    void convertsJsonToTheListenerParameterType() {
        String json = "{\"instrumentId\":10,\"accountId\":1,\"side\":\"BUY\",\"quantity\":5,"
                + "\"quote\":100.25,\"tradeId\":42,\"externalTradeId\":\"22222222-2222-2222-2222-222222222222\"}";

        Message<?> message = converter.toMessage(consumerRecord(json), null, null, TradeRecordedDTO.class);

        TradeRecordedDTO trade = (TradeRecordedDTO) message.getPayload();
        assertEquals(42L, trade.tradeId());
        assertEquals(1L, trade.accountId());
        assertEquals(10L, trade.instrumentId());
        assertEquals(TradeSide.BUY, trade.side());
        assertEquals(0, trade.quantity().compareTo(new BigDecimal("5")));
        assertEquals(0, trade.quote().compareTo(new BigDecimal("100.25")));
        assertEquals(EXTERNAL_TRADE_ID, trade.externalTradeId());
    }

    // what KafkaProducerConfig's JsonSerializer writes must convert back for the listener on the other side
    @Test
    void readsWhatTheProducerWrites() {
        TradeRecordedDTO sent = new TradeRecordedDTO(10L, 1L, TradeSide.BUY, new BigDecimal("5"),
                new BigDecimal("100.25"), 42L, EXTERNAL_TRADE_ID);

        Message<?> message = converter.toMessage(consumerRecord(produce(sent)), null, null, TradeRecordedDTO.class);

        assertEquals(sent, message.getPayload());
    }

    // a DTO turned into a JSON string before sending is encoded twice and can't be read back
    @Test
    void rejectsADoubleEncodedMessage() throws Exception {
        TradeRecordedDTO sent = new TradeRecordedDTO(10L, 1L, TradeSide.BUY, new BigDecimal("5"),
                new BigDecimal("100.25"), 42L, EXTERNAL_TRADE_ID);
        ConsumerRecord<String, String> doubleEncoded = consumerRecord(produce(new ObjectMapper().writeValueAsString(sent)));

        assertThrows(ConversionException.class,
                () -> converter.toMessage(doubleEncoded, null, null, TradeRecordedDTO.class));
    }

    private static String produce(Object value) {
        try (JacksonJsonSerializer<Object> serializer = new JacksonJsonSerializer<>()) {
            byte[] bytes = serializer.serialize("trade.recorded", value);
            return bytes == null ? "" : new String(bytes, StandardCharsets.UTF_8);
        }
    }

    @Test
    void rejectsMessagesThatAreNotJson() {
        ConsumerRecord<String, String> notJson = consumerRecord("not json");
        assertThrows(ConversionException.class,
                () -> converter.toMessage(notJson, null, null, TradeRecordedDTO.class));
    }

    // --- the error handler: handleOne returns true once the record is given up on (logged and skipped) ----------

    private final ConsumerRecordRecoverer recoverer = mock(ConsumerRecordRecoverer.class);
    private final DefaultErrorHandler errorHandler = new KafkaConsumerConfig().kafkaErrorHandler(recoverer);
    private final ConsumerRecord<String, String> failing = consumerRecord("{}");
    private final Consumer<?, ?> consumer = mock(Consumer.class);
    // a mock container isn't running, so the 2 s wait between attempts is skipped
    private final MessageListenerContainer container = mock(MessageListenerContainer.class);

    // the listener's exception reaches the handler wrapped, as it does from a real container
    private boolean fails(Exception cause) {
        return errorHandler.handleOne(new ListenerExecutionFailedException("listener failed", cause),
                failing, consumer, container);
    }

    @Test
    void errorHandler_otherFailure_isRetriedTwiceThenSkipped() {
        IllegalStateException fauxnanceDown = new IllegalStateException("Fauxnance returned 500 for AAPL");

        assertFalse(fails(fauxnanceDown));
        assertFalse(fails(fauxnanceDown));
        assertTrue(fails(fauxnanceDown));
    }

    @Test
    void errorHandler_invalidOrder_isSkippedWithoutARetry() {
        assertTrue(fails(new IllegalArgumentException("Quantity must be positive")));
    }

    @Test
    void errorHandler_unknownAccountOrInstrument_isSkippedWithoutARetry() {
        assertTrue(fails(new NoSuchElementException("No instrument with id 99")));
    }

    // the balance or holding won't change in the 4 s the retries take
    @Test
    void errorHandler_insufficientFunds_isSkippedWithoutARetry() {
        assertTrue(fails(new InsufficientFundsException("Insufficient funds in account 1")));
    }

    @Test
    void errorHandler_insufficientHoldings_isSkippedWithoutARetry() {
        assertTrue(fails(new InsufficientHoldingsException("Insufficient quantity of instrument 10")));
    }

    @Test
    void errorHandler_noPrice_isRetriedTwiceThenSkipped() {
        PriceUnavailableException noPrice = new PriceUnavailableException("Fauxnance returned 503 for AAPL");

        assertFalse(fails(noPrice));
        assertFalse(fails(noPrice));
        assertTrue(fails(noPrice));
    }

    // the recoverer publishes trade.rejected for a trade that failed validation
    @Test
    void errorHandler_givesTheSkippedRecordToTheRecoverer() {
        IllegalStateException fauxnanceDown = new IllegalStateException("Fauxnance returned 500 for AAPL");

        fails(fauxnanceDown);
        fails(fauxnanceDown);
        verify(recoverer, never()).accept(any(), any());

        fails(fauxnanceDown);
        verify(recoverer).accept(eq(failing), any());
    }

    @Test
    void errorHandler_givesARecordSkippedWithoutARetryToTheRecoverer() {
        fails(new InsufficientFundsException("Insufficient funds in account 1"));

        verify(recoverer).accept(eq(failing), any());
    }
}
