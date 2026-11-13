package com.fintechfive.ftrade.repository;

import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import com.fintechfive.ftrade.entity.ModelPortfolio;
import java.util.List;


@Mapper
public interface ModelPortfolioMapper {
    // columns aliased to the ModelPortfolio field names, so MyBatis fills the right fields
    String COLUMNS = "model_portfolio_id AS modelPortfolioId, name";

    @Select ("SELECT " + COLUMNS + " FROM model_portfolios WHERE model_portfolio_id = #{modelPortfolioId}")
    ModelPortfolio findById(Long modelPortfolioId);


    @Select("SELECT " + COLUMNS + " FROM model_portfolios")
    List<ModelPortfolio> findAll();

    @Select("SELECT " + COLUMNS + " FROM model_portfolios WHERE name = #{name}")
    ModelPortfolio findByName(String name);

    // #{...} are ModelPortfolio field names
    @Insert("INSERT INTO model_portfolios(name) VALUES(#{name})")
    @Options(useGeneratedKeys = true, keyProperty = "modelPortfolioId", keyColumn = "model_portfolio_id")
    void insert(ModelPortfolio modelPortfolio);

    @Update("UPDATE model_portfolios SET name = #{name} WHERE model_portfolio_id = #{modelPortfolioId}")
    void update(ModelPortfolio modelPortfolio);

    @Delete("DELETE FROM model_portfolios WHERE model_portfolio_id = #{modelPortfolioId}")
    void delete(Long modelPortfolioId);
}
