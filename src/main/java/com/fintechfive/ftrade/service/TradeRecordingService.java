package com.fintechfive.ftrade.service;

import com.fintechfive.ftrade.dto.TradeRecordedDTO;
import com.fintechfive.ftrade.dto.TradeValidatedDTO;
import com.fintechfive.ftrade.enums.TradeStatus;
import com.fintechfive.ftrade.exception.InvalidTradeException;
import com.fintechfive.ftrade.repository.AccountTradeMapper;
import com.fintechfive.ftrade.repository.AccountTradeStatusMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TradeRecordingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TradeRecordingService.class);

    private final AccountTradeMapper accountTradeMapper;
    private final AccountTradeStatusMapper accountTradeStatusMapper;

    public TradeRecordingService(AccountTradeMapper accountTradeMapper,
                                 AccountTradeStatusMapper accountTradeStatusMapper) {
        this.accountTradeMapper = accountTradeMapper;
        this.accountTradeStatusMapper = accountTradeStatusMapper;
    }

    // commits before the caller publishes, so trade.recorded only ever carries a trade that is in the database.
    // A redelivered message finds the trade by its external_trade_id and returns it again instead of inserting it twice.
    @Transactional
    public TradeRecordedDTO recordTrade(TradeValidatedDTO tradeValidatedDTO) {
        Long tradeId = accountTradeMapper.insert(tradeValidatedDTO);
        if (tradeId == null) {
            // a redelivered message: the trade is already recorded, so carry on with its existing ID
            tradeId = accountTradeMapper.findTradeIdByExternalTradeId(tradeValidatedDTO.externalTradeId());
            if (tradeId != null) {
                LOGGER.info("Trade {} was already recorded with ID: {}", tradeValidatedDTO.externalTradeId(), tradeId);
                return createTradeRecordedDTO(tradeValidatedDTO, tradeId);
            }
            LOGGER.warn("Trade failed for account {} for instrument {}, side {}, quantity {}, quote {}.",
                    tradeValidatedDTO.accountId(),
                    tradeValidatedDTO.instrumentId(),
                    tradeValidatedDTO.side(),
                    tradeValidatedDTO.quantity(),
                    tradeValidatedDTO.quote());
            throw new InvalidTradeException("Failed to record trade");
        }
        accountTradeStatusMapper.insertTradeStatus(tradeId, TradeStatus.PENDING);
        LOGGER.info("Created database entry for trade with ID: {}", tradeId);
        return createTradeRecordedDTO(tradeValidatedDTO, tradeId);
    }

    private TradeRecordedDTO createTradeRecordedDTO(TradeValidatedDTO tradeValidatedDTO, long tradeId) {
        return new TradeRecordedDTO(
                tradeValidatedDTO.instrumentId(),
                tradeValidatedDTO.accountId(),
                tradeValidatedDTO.side(),
                tradeValidatedDTO.quantity(),
                tradeValidatedDTO.quote(),
                tradeId,
                tradeValidatedDTO.externalTradeId());
    }
}
