package com.fintechfive.ftrade.repository;

import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.fintechfive.ftrade.entity.Instrument;
import java.util.List;

@Mapper
public interface InstrumentMapper {

    // columns aliased to the Instrument field names, so MyBatis fills the right fields
    // (asset_class holds the InstrumentType name, e.g. 'STOCK')
    String COLUMNS = "instrument_id AS instrumentId, name, ticker, asset_class AS instrumentType";

    @Select("SELECT " + COLUMNS + " FROM instruments WHERE instrument_id = #{instrumentId}")
    Instrument findById(Long instrumentId);

    @Select("SELECT " + COLUMNS + " FROM instruments")
    List<Instrument> findAll();

    @Select("SELECT " + COLUMNS + " FROM instruments WHERE ticker = #{ticker}")
    Instrument findByTicker(String ticker);

    // the instruments search and countMatching look at: ticker or name matching the ILIKE pattern (case-insensitive),
    // and only one asset class when assetClass isn't null (the CAST tells Postgres the type of a null parameter)
    String MATCHING = " FROM instruments WHERE (ticker ILIKE #{pattern} OR name ILIKE #{pattern}) "
            + "AND (CAST(#{assetClass} AS TEXT) IS NULL OR asset_class = #{assetClass})";

    // a page of the matching instruments: an exact ticker match first, then by ticker
    @Select("SELECT " + COLUMNS + MATCHING + " ORDER BY ticker = #{ticker} DESC, ticker LIMIT #{limit} OFFSET #{offset}")
    List<Instrument> search(@Param("pattern") String pattern, @Param("assetClass") String assetClass,
                            @Param("ticker") String ticker, @Param("limit") int limit, @Param("offset") int offset);

    // how many instruments search matches in all, across every page
    @Select("SELECT count(*)" + MATCHING)
    long countMatching(@Param("pattern") String pattern, @Param("assetClass") String assetClass);

    @Insert("INSERT INTO instruments(ticker, name, asset_class) VALUES(#{ticker}, #{name}, #{instrumentType})")
    @Options(useGeneratedKeys = true, keyProperty = "instrumentId", keyColumn = "instrument_id")
    void insert(Instrument instrument);

    // adds the instrument unless its ticker is already there (tickers are unique); returns how many rows were added
    @Insert("INSERT INTO instruments(ticker, name, asset_class) VALUES(#{ticker}, #{name}, #{instrumentType}) "
            + "ON CONFLICT (ticker) DO NOTHING")
    int insertIfAbsent(Instrument instrument);

    @Update("UPDATE instruments SET name = #{name}, ticker = #{ticker}, asset_class = #{instrumentType} WHERE instrument_id = #{instrumentId}")
    void update(Instrument instrument);

    @Delete("DELETE FROM instruments WHERE instrument_id = #{instrumentId}")
    void delete(Long instrumentId);
}
