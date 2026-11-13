package com.fintechfive.ftrade.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Delete;
import com.fintechfive.ftrade.entity.ModelPortfolioHolding;
import java.time.LocalDate;
import java.util.List;

@Mapper
public interface ModelPortfolioHoldingMapper {

    // columns aliased to the ModelPortfolioHolding field names, so MyBatis fills the right fields
    String COLUMNS = "model_portfolio_id AS modelPortfolioId, instrument_id AS instrumentId, effective_date AS effectiveDate, "
            + "target_weight_pct AS targetWeightPct, status";

    @Select("SELECT " + COLUMNS + " FROM model_portfolio_holdings WHERE model_portfolio_id = #{modelPortfolioId} AND instrument_id = #{instrumentId} AND effective_date = #{effectiveDate}")
    ModelPortfolioHolding findByIdAndInstrumentAndEffectiveDate(@Param("modelPortfolioId") Long modelPortfolioId,
                                                                @Param("instrumentId") Long instrumentId,
                                                                @Param("effectiveDate") LocalDate effectiveDate);

    @Select("SELECT " + COLUMNS + " FROM model_portfolio_holdings")
    List<ModelPortfolioHolding> findAll();

    // #{...} are ModelPortfolioHolding field names
    @Insert("INSERT INTO model_portfolio_holdings(model_portfolio_id, instrument_id, effective_date, target_weight_pct, status) VALUES(#{modelPortfolioId}, #{instrumentId}, #{effectiveDate}, #{targetWeightPct}, #{status})")
    void insert(ModelPortfolioHolding modelPortfolioHolding);

    @Update("UPDATE model_portfolio_holdings SET target_weight_pct = #{targetWeightPct}, status = #{status} WHERE model_portfolio_id = #{modelPortfolioId} AND instrument_id = #{instrumentId} AND effective_date = #{effectiveDate}")
    void update(ModelPortfolioHolding modelPortfolioHolding);

    @Delete("DELETE FROM model_portfolio_holdings WHERE model_portfolio_id = #{modelPortfolioId} AND instrument_id = #{instrumentId} AND effective_date = #{effectiveDate}")
    void delete(@Param("modelPortfolioId") Long modelPortfolioId, @Param("instrumentId") Long instrumentId,
                @Param("effectiveDate") LocalDate effectiveDate);
}
