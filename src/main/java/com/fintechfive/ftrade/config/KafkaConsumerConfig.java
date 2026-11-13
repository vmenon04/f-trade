package com.fintechfive.ftrade.config;

import com.fintechfive.ftrade.exception.InsufficientFundsException;
import com.fintechfive.ftrade.exception.InsufficientHoldingsException;
import java.util.NoSuchElementException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.converter.JacksonJsonMessageConverter;
import org.springframework.kafka.support.converter.RecordMessageConverter;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConsumerConfig {

    static final long RETRY_INTERVAL_MS = 2000;
    static final long RETRIES = 2;

    // Spring Boot attaches this to the default listener container factory, so every @KafkaListener gets the
    // JSON message converted to the type of its parameter (TradeSubmittedDTO, TradeValidatedDTO, ...).
    // A message that can't be converted is logged and skipped rather than retried.
    @Bean
    public RecordMessageConverter messageConverter() {
        return new JacksonJsonMessageConverter();
    }

    // Also attached by Spring Boot. A listener that throws is retried twice, 2 s apart (Spring's default is 9 retries
    // with no wait, each one able to spend a Fauxnance call), so a passing Fauxnance or database error has time to
    // clear; after that the message is logged and skipped. A bad order (invalid quantity or side, unknown account or
    // instrument) or one the account can't cover (not enough cash or shares) fails the same way every time, so it is
    // skipped at once. A skipped message goes to the recoverer (TradeRejectionRecoverer).
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(ConsumerRecordRecoverer recoverer) {
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(RETRY_INTERVAL_MS, RETRIES));
        handler.addNotRetryableExceptions(IllegalArgumentException.class, NoSuchElementException.class,
                InsufficientFundsException.class, InsufficientHoldingsException.class);
        return handler;
    }
}
