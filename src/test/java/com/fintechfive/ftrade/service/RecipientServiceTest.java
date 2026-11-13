package com.fintechfive.ftrade.service;

import com.fintechfive.ftrade.dto.TradeRequestDTO;
import com.fintechfive.ftrade.dto.TradeSubmittedDTO;
import com.fintechfive.ftrade.entity.Instrument;
import com.fintechfive.ftrade.enums.TradeSide;
import com.fintechfive.ftrade.external.MarketDataClient;
import com.fintechfive.ftrade.repository.AccountTradeMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecipientServiceTest {

    @Mock
    private TradeEventProducer tradeEventProducer;

    @Mock
    private InstrumentService instrumentService;

    @Mock
    private MarketDataClient marketDataClient;

    @Mock
    private AccountTradeMapper accountTradeMapper;

    @InjectMocks
    private RecipientService recipientService;

    @BeforeEach
    void setUp() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/accounts/123/trades");
        request.setScheme("http");
        request.setServerName("localhost");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void publishOrderRegeneratesTradeIdWhenGeneratedIdAlreadyExists() {
        UUID existingTradeId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID regeneratedTradeId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        TradeRequestDTO dto = new TradeRequestDTO(
                UUID.randomUUID(),
                10L,
                TradeSide.BUY,
                new BigDecimal("25.5")
        );

        Instrument instrument = new Instrument();
        instrument.setTicker("AAPL");
        when(instrumentService.getInstrumentById(10L)).thenReturn(instrument);
        when(marketDataClient.getPrice("AAPL")).thenReturn(new BigDecimal("101.25"));
        when(accountTradeMapper.findTradeIdByExternalTradeId(existingTradeId)).thenReturn(1L);
        when(accountTradeMapper.findTradeIdByExternalTradeId(regeneratedTradeId)).thenReturn(null);

        try (MockedStatic<UUID> mockedUuid = Mockito.mockStatic(UUID.class)) {
            mockedUuid.when(UUID::randomUUID)
                    .thenReturn(existingTradeId, regeneratedTradeId)
                    .thenThrow(new AssertionError("Trade ID generation should stop after finding an unused ID"));
            recipientService.publishOrder(dto, 1L);
        }

        ArgumentCaptor<TradeSubmittedDTO> captor = ArgumentCaptor.forClass(TradeSubmittedDTO.class);
        verify(tradeEventProducer).publishTradeSubmitted(captor.capture());
        assertEquals(regeneratedTradeId, captor.getValue().externalTradeId());
        verify(accountTradeMapper).findTradeIdByExternalTradeId(existingTradeId);
        verify(accountTradeMapper).findTradeIdByExternalTradeId(regeneratedTradeId);
    }

    @Test
    void publishOrder_returnsAcceptedWithTradeLocation() {
        when(accountTradeMapper.findTradeIdByExternalTradeId(any(UUID.class))).thenReturn(null);
        TradeRequestDTO dto = new TradeRequestDTO(
                UUID.randomUUID(),
                10L,
                TradeSide.BUY,
                new BigDecimal("25.5")
        );

        Instrument instrument = new Instrument();
        instrument.setTicker("AAPL");
        when(instrumentService.getInstrumentById(10L)).thenReturn(instrument);
        when(marketDataClient.getPrice("AAPL")).thenReturn(new BigDecimal("101.25"));

        ResponseEntity<Void> response = recipientService.publishOrder(dto, 1L);

        assertEquals(202, response.getStatusCode().value());
        assertNotNull(response.getHeaders().getLocation());
        ArgumentCaptor<TradeSubmittedDTO> captor = ArgumentCaptor.forClass(TradeSubmittedDTO.class);
        verify(tradeEventProducer).publishTradeSubmitted(captor.capture());
        assertEquals("/api/accounts/123/trades/" + captor.getValue().externalTradeId(),
                response.getHeaders().getLocation().getPath());
    }

    // the global handler turns this into the generic 500
    @Test
    void publishOrder_noMarketPrice_throwsAndPublishesNothing() {
        TradeRequestDTO dto = new TradeRequestDTO(UUID.randomUUID(), 22L, TradeSide.BUY, new BigDecimal("1"));
        // a new trade id (an unstubbed Long would be 0, which reads as "already exists" and never ends)
        when(accountTradeMapper.findTradeIdByExternalTradeId(any(UUID.class))).thenReturn(null);
        Instrument instrument = new Instrument();
        instrument.setTicker("ORCL");
        when(instrumentService.getInstrumentById(22L)).thenReturn(instrument);
        when(marketDataClient.getPrice("ORCL")).thenReturn(null);

        assertThrows(IllegalStateException.class, () -> recipientService.publishOrder(dto, 1L));

        verifyNoInteractions(tradeEventProducer);
    }

    @Test
    void publishOrder_publishesTradeSubmittedWithExpectedPayload() {
        when(accountTradeMapper.findTradeIdByExternalTradeId(any(UUID.class))).thenReturn(null);
        UUID accountId = UUID.randomUUID();
        TradeRequestDTO dto = new TradeRequestDTO(
                accountId,
                44L,
                TradeSide.SELL,
                new BigDecimal("7")
        );

        Instrument instrument = new Instrument();
        instrument.setTicker("MSFT");
        when(instrumentService.getInstrumentById(44L)).thenReturn(instrument);
        when(marketDataClient.getPrice("MSFT")).thenReturn(new BigDecimal("91.10"));

        recipientService.publishOrder(dto, 2L);

        ArgumentCaptor<TradeSubmittedDTO> captor = ArgumentCaptor.forClass(TradeSubmittedDTO.class);
        verify(tradeEventProducer).publishTradeSubmitted(captor.capture());

        TradeSubmittedDTO submitted = captor.getValue();
        assertEquals(44, submitted.instrumentId());
        assertEquals(accountId, submitted.accountId());
        assertEquals(TradeSide.SELL, submitted.side());
        assertEquals(0, submitted.quantity().compareTo(new BigDecimal("7")));
        assertNotNull(submitted.externalTradeId());
    }
}
