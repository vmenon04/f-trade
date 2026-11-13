package com.fintechfive.ftrade.repository;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

@Mapper
public interface AccountTradePriceMapper {

    @Select("SELECT total_price FROM account_trade_price WHERE trade_id = #{tradeId}")
    BigDecimal getTradePriceByTradeId(@Param("tradeId") Long tradeId);

    @Select("SELECT price_per_unit FROM account_trade_price WHERE trade_id = #{tradeId}")
    BigDecimal getPricePerUnitByTradeId(@Param("tradeId") Long tradeId);

    @Insert("INSERT INTO account_trade_price (trade_id, price_per_unit, total_price) VALUES (#{tradeId}, #{pricePerUnit}, #{totalPrice})")
    void insertTradePrice(@Param("tradeId") Long tradeId, @Param("pricePerUnit") BigDecimal pricePerUnit, @Param("totalPrice") BigDecimal totalPrice);

}
