package com.fintechfive.ftrade.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ClientAccountMapper {

    @Select("SELECT client_id as clientId FROM client_accounts WHERE account_id = #{accountId}")
    List<Long> getClientIdByAccountId(@Param("accountId") Long accountId);

    @Select("SELECT EXISTS (SELECT 1 FROM client_accounts "
            + "WHERE client_id = #{clientId} AND account_id = #{accountId})")
    boolean existsByClientIdAndAccountId(@Param("clientId") Long clientId,
                                         @Param("accountId") Long accountId);
}
