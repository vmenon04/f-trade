package com.fintechfive.ftrade.repository;

import com.fintechfive.ftrade.entity.AccountTradeStatus;
import com.fintechfive.ftrade.enums.TradeStatus;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface AccountTradeStatusMapper {

    String COLUMNS = "status, trade_time as tradeTime";

    @Select("SELECT status FROM account_trade_status WHERE trade_id = #{tradeId} ORDER BY trade_time DESC LIMIT 1")
    TradeStatus getTradeStatusByTradeId(Long tradeId);

    @Select("SELECT " + COLUMNS + " FROM account_trade_status WHERE trade_id = #{tradeId} ORDER BY trade_time")
    List<AccountTradeStatus> getTradeStatusHistoryByTradeId(Long tradeId);

    @Insert("INSERT INTO account_trade_status (trade_id, status, trade_time) VALUES (#{tradeId}, #{status}, clock_timestamp())")
    void insertTradeStatus(Long tradeId, TradeStatus status);

}
