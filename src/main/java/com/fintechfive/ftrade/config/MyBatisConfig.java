package com.fintechfive.ftrade.config;

import com.fintechfive.ftrade.repository.UuidTypeHandler;
import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

/**
 * MyBatis settings that apply to every mapper.
 */
@Configuration
public class MyBatisConfig {

    // read and write UUID fields as Postgres uuid columns everywhere, e.g. external ids and trade ids
    @Bean
    public ConfigurationCustomizer uuidTypeHandler() {
        return configuration -> configuration.getTypeHandlerRegistry().register(UUID.class, new UuidTypeHandler());
    }
}
