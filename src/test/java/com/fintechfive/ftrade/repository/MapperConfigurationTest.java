package com.fintechfive.ftrade.repository;

import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

// The service tests mock the mappers, so a mapper whose SQL MyBatis can't parse (an unknown #{...} property,
// a parameter type with no type handler) would only show up when the app starts. Adding each mapper to a
// MyBatis Configuration parses its SQL the same way startup does, without needing a database.
class MapperConfigurationTest {

    @ParameterizedTest
    @ValueSource(classes = {
            AccountHoldingMapper.class,
            AccountMapper.class,
            AccountSubscriptionMapper.class,
            AccountTradeMapper.class,
            AccountTradePriceMapper.class,
            AccountTradeStatusMapper.class,
            ClientMapper.class,
            InstrumentMapper.class,
            ModelPortfolioHoldingMapper.class,
            ModelPortfolioMapper.class
    })
    void mapperSqlParses(Class<?> mapper) {
        assertDoesNotThrow(() -> new Configuration().addMapper(mapper));
    }
}
