package com.fintechfive.ftrade.service;

import com.fintechfive.ftrade.dto.TradeSubmittedDTO;
import com.fintechfive.ftrade.enums.RejectionCode;
import com.fintechfive.ftrade.enums.TradeSide;
import com.fintechfive.ftrade.enums.TradeStage;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Keeps each recent trade's steps through the pipeline in Redis, so the trade page can show it moving. It is only
 * for showing: a trade expires trade.tracker.ttl (1 hour) after its last step, and the account's list keeps its
 * newest 20, so spam can't fill it. Order History in Postgres is the record.
 *
 * Each step writes only its own time, and the stage is worked out from the times there are, so a step that arrives
 * late (they come from different topics) can't move a trade back. If Redis is down nothing is tracked, and the
 * trades still go through.
 */
@Service
public class TradeTrackerService {

    static final int MAX_TRADES = 20;

    private static final Logger LOGGER = LoggerFactory.getLogger(TradeTrackerService.class);

    private static final String ACCOUNT = "account";
    private static final String INSTRUMENT_ID = "instrumentId";
    private static final String SIDE = "side";
    private static final String QUANTITY = "quantity";
    private static final String SUBMITTED_AT = "submittedAt";
    private static final String VALIDATED_AT = "validatedAt";
    private static final String RECORDED_AT = "recordedAt";
    private static final String FULFILLED_AT = "fulfilledAt";
    private static final String PRICE = "price";
    private static final String REJECTED_AT = "rejectedAt";
    private static final String REJECTION_CODE = "rejectionCode";

    private final StringRedisTemplate redis;
    private final Clock clock;
    private final Duration ttl;

    @Autowired
    public TradeTrackerService(StringRedisTemplate redis, @Value("${trade.tracker.ttl:1h}") Duration ttl) {
        this(redis, Clock.systemUTC(), ttl);
    }

    TradeTrackerService(StringRedisTemplate redis, Clock clock, Duration ttl) {
        this.redis = redis;
        this.clock = clock;
        this.ttl = ttl;
    }

    public void submitted(TradeSubmittedDTO trade) {
        UUID externalTradeId = trade.externalTradeId();
        Instant now = clock.instant();
        String accountKey = accountKey(trade.accountId());
        try {
            redis.opsForHash().putAll(tradeKey(externalTradeId), Map.of(
                    ACCOUNT, trade.accountId().toString(),
                    INSTRUMENT_ID, trade.instrumentId().toString(),
                    SIDE, trade.side().name(),
                    QUANTITY, trade.quantity().toPlainString(),
                    SUBMITTED_AT, now.toString()));
            redis.expire(tradeKey(externalTradeId), ttl);
            redis.opsForZSet().add(accountKey, externalTradeId.toString(), now.toEpochMilli());
            // ranks count from the oldest, so this keeps the newest MAX_TRADES
            redis.opsForZSet().removeRange(accountKey, 0, -(MAX_TRADES + 1));
            redis.expire(accountKey, ttl);
        } catch (DataAccessException e) {
            LOGGER.warn("Couldn't track trade {}: {}", externalTradeId, e.getMessage());
        }
    }

    public void validated(UUID externalTradeId) {
        step(externalTradeId, Map.of(VALIDATED_AT, clock.instant().toString()));
    }

    public void recorded(UUID externalTradeId) {
        step(externalTradeId, Map.of(RECORDED_AT, clock.instant().toString()));
    }

    public void fulfilled(UUID externalTradeId, BigDecimal price) {
        step(externalTradeId, Map.of(FULFILLED_AT, clock.instant().toString(), PRICE, price.toPlainString()));
    }

    public void rejected(UUID externalTradeId, RejectionCode rejectionCode) {
        RejectionCode code = rejectionCode == null ? RejectionCode.UNKNOWN : rejectionCode;
        step(externalTradeId, Map.of(REJECTED_AT, clock.instant().toString(), REJECTION_CODE, code.name()));
    }

    // a null id is a message from before trades carried their external id through the pipeline
    private void step(UUID externalTradeId, Map<String, String> fields) {
        if (externalTradeId == null) {
            return;
        }
        String key = tradeKey(externalTradeId);
        try {
            if (fields.size() == 1) {
                Map.Entry<String, String> field = fields.entrySet().iterator().next();
                redis.opsForHash().put(key, field.getKey(), field.getValue());
            } else {
                redis.opsForHash().putAll(key, fields);
            }
            redis.expire(key, ttl);
        } catch (DataAccessException e) {
            LOGGER.warn("Couldn't track trade {}: {}", externalTradeId, e.getMessage());
        }
    }

    /**
     * The account's recent trades, newest first: at most MAX_TRADES, from the last trade.tracker.ttl.
     *
     * @return the trades, or none if Redis is down
     */
    public List<TrackedTrade> recentTrades(UUID externalAccountId) {
        try {
            Set<String> ids = redis.opsForZSet().reverseRange(accountKey(externalAccountId), 0, MAX_TRADES - 1);
            List<TrackedTrade> trades = new ArrayList<>();
            for (String id : ids == null ? Set.<String>of() : ids) {
                UUID externalTradeId = UUID.fromString(id);
                Map<Object, Object> fields = redis.opsForHash().entries(tradeKey(externalTradeId));
                TrackedTrade trade = toTrackedTrade(externalTradeId, fields);
                if (trade != null) {
                    trades.add(trade);
                }
            }
            return trades;
        } catch (DataAccessException e) {
            LOGGER.warn("Couldn't read account {}'s tracked trades: {}", externalAccountId, e.getMessage());
            return List.of();
        }
    }

    // null for a trade that has expired, or has steps stored but not its Submitted one
    private static TrackedTrade toTrackedTrade(UUID externalTradeId, Map<Object, Object> fields) {
        if (fields == null || fields.get(SUBMITTED_AT) == null) {
            return null;
        }
        Instant fulfilledAt = instant(fields, FULFILLED_AT);
        Instant rejectedAt = instant(fields, REJECTED_AT);
        String price = (String) fields.get(PRICE);
        String code = (String) fields.get(REJECTION_CODE);
        return new TrackedTrade(
                externalTradeId,
                Long.valueOf((String) fields.get(INSTRUMENT_ID)),
                TradeSide.valueOf((String) fields.get(SIDE)),
                new BigDecimal((String) fields.get(QUANTITY)),
                stage(fields),
                instant(fields, SUBMITTED_AT),
                instant(fields, VALIDATED_AT),
                instant(fields, RECORDED_AT),
                rejectedAt != null ? rejectedAt : fulfilledAt,
                price == null ? null : new BigDecimal(price),
                code == null ? null : RejectionCode.valueOf(code));
    }

    // the furthest step reached
    private static TradeStage stage(Map<Object, Object> fields) {
        if (fields.containsKey(REJECTED_AT)) {
            return TradeStage.REJECTED;
        }
        if (fields.containsKey(FULFILLED_AT)) {
            return TradeStage.FULFILLED;
        }
        if (fields.containsKey(RECORDED_AT)) {
            return TradeStage.PENDING;
        }
        if (fields.containsKey(VALIDATED_AT)) {
            return TradeStage.VALIDATED;
        }
        return TradeStage.SUBMITTED;
    }

    private static Instant instant(Map<Object, Object> fields, String field) {
        Object value = fields.get(field);
        return value == null ? null : Instant.parse((String) value);
    }

    private static String tradeKey(UUID externalTradeId) {
        return "trade:progress:" + externalTradeId;
    }

    private static String accountKey(UUID externalAccountId) {
        return "trades:recent:" + externalAccountId;
    }
}
