package com.fintechfive.ftrade.service;

import com.fintechfive.ftrade.dto.TradeRecordedDTO;
import com.fintechfive.ftrade.dto.TradeValidatedDTO;
import com.fintechfive.ftrade.enums.TradeSide;
import com.fintechfive.ftrade.enums.TradeStatus;
import com.fintechfive.ftrade.exception.InvalidTradeException;
import com.fintechfive.ftrade.repository.AccountTradeMapper;
import com.fintechfive.ftrade.repository.AccountTradeStatusMapper;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TradeRecordingServiceTest {

    private static final UUID EXTERNAL_TRADE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock
    private AccountTradeMapper accountTradeMapper;

    @Mock
    private AccountTradeStatusMapper accountTradeStatusMapper;

    private TradeRecordingService tradeRecordingService;

    private final TradeValidatedDTO validatedDTO = new TradeValidatedDTO(
            1L,
            2L,
            TradeSide.BUY,
            new BigDecimal("100"),
            new BigDecimal("10.0"),
            EXTERNAL_TRADE_ID);

    @BeforeEach
    void setUp() {
        tradeRecordingService = new TradeRecordingService(
                accountTradeMapper,
                accountTradeStatusMapper);
    }

    @Test
    void recordsTradeAsPendingAndReturnsIt() {
        when(accountTradeMapper.insert(validatedDTO)).thenReturn(4L);

        TradeRecordedDTO tradeRecordedDTO = tradeRecordingService.recordTrade(validatedDTO);

        verify(accountTradeMapper).insert(validatedDTO);
        verify(accountTradeStatusMapper).insertTradeStatus(4L, TradeStatus.PENDING);
        assertEquals(1L, tradeRecordedDTO.instrumentId());
        assertEquals(2L, tradeRecordedDTO.accountId());
        assertEquals(TradeSide.BUY, tradeRecordedDTO.side());
        assertEquals(new BigDecimal("100"), tradeRecordedDTO.quantity());
        assertEquals(new BigDecimal("10.0"), tradeRecordedDTO.quote());
        assertEquals(4L, tradeRecordedDTO.tradeId());
        assertEquals(EXTERNAL_TRADE_ID, tradeRecordedDTO.externalTradeId());
    }

    @Test
    void alreadyRecordedTradeReturnsExistingTradeWithoutRecordingAgain() {
        when(accountTradeMapper.insert(validatedDTO)).thenReturn(null);
        when(accountTradeMapper.findTradeIdByExternalTradeId(EXTERNAL_TRADE_ID)).thenReturn(4L);

        TradeRecordedDTO tradeRecordedDTO = tradeRecordingService.recordTrade(validatedDTO);

        verify(accountTradeStatusMapper, never()).insertTradeStatus(any(), any());
        assertEquals(4L, tradeRecordedDTO.tradeId());
        assertEquals(EXTERNAL_TRADE_ID, tradeRecordedDTO.externalTradeId());
    }

    @Test
    void tradeNeitherInsertedNorFoundThrowsInvalidTradeException() {
        when(accountTradeMapper.insert(validatedDTO)).thenReturn(null);
        when(accountTradeMapper.findTradeIdByExternalTradeId(EXTERNAL_TRADE_ID)).thenReturn(null);

        assertThrows(InvalidTradeException.class, () -> tradeRecordingService.recordTrade(validatedDTO));

        verify(accountTradeStatusMapper, never()).insertTradeStatus(any(), any());
    }
}
