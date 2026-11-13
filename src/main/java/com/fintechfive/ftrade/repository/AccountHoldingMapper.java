package com.fintechfive.ftrade.repository;

import com.fintechfive.ftrade.enums.ActivityStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Delete;
import com.fintechfive.ftrade.entity.AccountHolding;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AccountHoldingMapper {

    // columns aliased to the AccountHolding field names, so MyBatis fills the right fields
    String COLUMNS = "account_id AS accountId, instrument_id AS instrumentId, as_of_date AS asOfDate, quantity, status";

    @Select("SELECT " + COLUMNS + " FROM account_holdings WHERE account_id = #{accountId} AND instrument_id = #{instrumentId} AND as_of_date = #{asOfDate}")
    AccountHolding findByAccountInstrumentAndDate(@Param("accountId") Long accountId,
                                                  @Param("instrumentId") Long instrumentId,
                                                  @Param("asOfDate") LocalDateTime asOfDate);

    // every snapshot for the account, including INACTIVE history rows
    @Select("SELECT " + COLUMNS + " FROM account_holdings WHERE account_id = #{accountId}")
    List<AccountHolding> findByAccountId(@Param("accountId") Long accountId);

    // #{...} are AccountHolding field names
    @Update("UPDATE account_holdings SET quantity = #{quantity}, status = #{status} WHERE account_id = #{accountId} AND instrument_id = #{instrumentId} AND as_of_date = #{asOfDate}")
    void update(AccountHolding accountHolding);

    @Delete("DELETE FROM account_holdings WHERE account_id = #{accountId} AND instrument_id = #{instrumentId} AND as_of_date = #{asOfDate}")
    void delete(@Param("accountId") Long accountId, @Param("instrumentId") Long instrumentId,
                @Param("asOfDate") LocalDateTime asOfDate);

    //vasus additions for accountholdingmapper
    // The 'ACTIVE' row is the current holding; older rows are 'INACTIVE' history
    // we use clock_timestamp() rather than NOW() since NOW() is fixed for a whole transaction
    // get quantity of the current (active) holding; null if there is none
    @Select("SELECT quantity FROM account_holdings "
            + "WHERE account_id = #{accountId} AND instrument_id = #{instrumentId} AND status = 'ACTIVE'")
    BigDecimal findActiveQuantity(@Param("accountId") Long accountId, @Param("instrumentId") Long instrumentId);

    // marks the current holding as inactive before we replace it
    @Update("UPDATE account_holdings SET status = 'INACTIVE' "
            + "WHERE account_id = #{accountId} AND instrument_id = #{instrumentId} AND status = 'ACTIVE'")
    void deactivateHolding(@Param("accountId") Long accountId, @Param("instrumentId") Long instrumentId);

    @Insert("INSERT INTO account_holdings (account_id, instrument_id, as_of_date, quantity, status) "
            + "VALUES (#{accountId}, #{instrumentId}, clock_timestamp(), #{quantity}, #{status})")
    void insertSnapshot(@Param("accountId") Long accountId, @Param("instrumentId") Long instrumentId,
                        @Param("quantity") BigDecimal quantity, @Param("status") ActivityStatus status);
}
