package com.fintechfive.ftrade.repository;

import com.fintechfive.ftrade.dto.TradeValidatedDTO;
import com.fintechfive.ftrade.enums.TradeStatus;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import com.fintechfive.ftrade.entity.*;

// to keep history we make each status change a new row with the same trade_id.
// the row with the latest trade_time is the current status.
// we use clock_timestamp() rather than NOW() since NOW() is fixed for a whole transaction
@Mapper
public interface AccountTradeMapper {

    // columns aliased to the AccountTrade field names, so MyBatis fills the right fields
    // (the status column goes into the tradeStatus field)
    String TRADE_COLUMNS = "account_trades.trade_id AS tradeId, external_trade_id AS externalTradeId, "
            + "account_id AS accountId, instrument_id AS instrumentId, "
            + "trade_side AS tradeSide, quantity";

    String STATUS_COLUMNS = "status AS tradeStatus, trade_time AS tradeTime";

    String PRICE_COLUMNS = "price_per_unit AS ppu, total_price AS totalPrice";

    // current state of a trade (its latest row)
    @Select("SELECT " + TRADE_COLUMNS + "," +  STATUS_COLUMNS + "," + PRICE_COLUMNS + " FROM account_trades " +
            "LEFT JOIN account_trade_status ON account_trades.trade_id = account_trade_status.trade_id " +
            "LEFT JOIN account_trade_price ON account_trades.trade_id = account_trade_price.trade_id " +
            "WHERE account_trades.trade_id = #{tradeId} ORDER BY account_trade_status.trade_time DESC LIMIT 1")
    AccountTrade findById(@Param("tradeId") Long tradeId);

    // current state of each of the account's trades (one row per trade)
    @Select("SELECT DISTINCT ON (account_trades.trade_id) " + TRADE_COLUMNS + "," +  STATUS_COLUMNS + "," + PRICE_COLUMNS + " FROM account_trades " +
            "LEFT JOIN account_trade_status ON account_trades.trade_id = account_trade_status.trade_id " +
            "LEFT JOIN account_trade_price ON account_trades.trade_id = account_trade_price.trade_id " +
            "WHERE account_trades.account_id = #{accountId} " +
            "ORDER BY account_trades.trade_id, account_trade_status.trade_time DESC")
    List<AccountTrade> findByAccountId(@Param("accountId") Long accountId);

    // every status a trade went through, oldest first
    @Select("SELECT " + TRADE_COLUMNS + "," +  STATUS_COLUMNS + "," + PRICE_COLUMNS + " FROM account_trades " +
            "LEFT JOIN account_trade_status ON account_trades.trade_id = account_trade_status.trade_id " +
            "LEFT JOIN account_trade_price ON account_trades.trade_id = account_trade_price.trade_id " +
            "WHERE account_trades.trade_id = #{tradeId} ORDER BY account_trade_status.trade_time")
    List<AccountTrade> findHistoryById(@Param("tradeId") Long tradeId);

    // Inserts the trade and returns its generated trade_id. #{...} are TradeValidatedDTO fields.
    // The insert ... returning goes through @Select so MyBatis returns the trade_id rather than the row count;
    // flushCache stops MyBatis from answering a repeat of the same insert from its cache.
    // Returns null if a trade with this external_trade_id was already recorded (a redelivered message).
    // MyBatis has no UUID type handler; ObjectTypeHandler hands the UUID to the Postgres driver, which maps it to uuid.
    @Select("INSERT INTO account_trades (external_trade_id, account_id, instrument_id, trade_side, quantity) "
            + "VALUES (#{externalTradeId, typeHandler=org.apache.ibatis.type.ObjectTypeHandler}, #{accountId}, "
            + "#{instrumentId}, #{side}, #{quantity}) "
            + "ON CONFLICT (external_trade_id) DO NOTHING "
            + "RETURNING trade_id")
    @Options(flushCache = Options.FlushCachePolicy.TRUE)
    Long insert(TradeValidatedDTO tradeValidatedDTO);

    @Select("SELECT trade_id FROM account_trades WHERE external_trade_id = #{externalTradeId}")
    Long findTradeIdByExternalTradeId(@Param("externalTradeId") UUID externalTradeId);

    // record the status change: copies the trade's latest row with the new status and the current time
    @Insert("INSERT INTO account_trade_status "
            + "(trade_id, trade_time, status) "
            + "SELECT trade_id, clock_timestamp(), #{status} "
            + "FROM account_trades WHERE trade_id = #{tradeId} ")
    void insertStatus(@Param("tradeId") Long tradeId, @Param("status") TradeStatus status);
}

