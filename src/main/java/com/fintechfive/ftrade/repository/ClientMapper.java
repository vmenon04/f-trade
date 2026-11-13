package com.fintechfive.ftrade.repository;

import org.apache.ibatis.annotations.*;
import com.fintechfive.ftrade.entity.Client;

import java.util.List;
import java.util.UUID;

@Mapper
public interface ClientMapper {

    // columns aliased to the Client field names, so MyBatis fills the right fields
    String COLUMNS = "client_id AS clientId, external_client_id AS externalClientId, first_name AS firstName, last_name AS lastName, email, birth_date AS birthDate";

    // #{...} are Client field names
    @Insert("INSERT INTO clients(first_name, last_name, email, birth_date) VALUES(#{firstName}, #{lastName}, #{email}, #{birthDate})")
    @Options(useGeneratedKeys = true, keyProperty = "clientId", keyColumn = "client_id")
    void insert(Client client);

    @Select("SELECT " + COLUMNS + " FROM clients WHERE client_id = #{clientId}")
    Client findById(Long clientId);

    @Select("SELECT client_id as clientId FROM clients WHERE external_client_id = #{externalClientId}")
    Long findInternalIdByExternalClientId(@Param("externalClientId") UUID externalClientId);

    // null when the user has no client yet
    @Select("SELECT clients.external_client_id FROM clients "
            + "JOIN users ON users.client_id = clients.client_id "
            + "WHERE users.user_id = #{userId}")
    UUID findExternalClientIdByUserId(@Param("userId") Long userId);

    @Select("SELECT accounts.external_account_id FROM accounts "
            + "JOIN client_accounts ON accounts.account_id = client_accounts.account_id "
            + "WHERE client_accounts.client_id = #{clientId} "
            + "ORDER BY accounts.external_account_id")
    List<UUID> findExternalAccountIdsByClientId(@Param("clientId") Long clientId);

    @Select("SELECT " + COLUMNS + " FROM clients")
    List<Client> findAll();

    @Update("UPDATE clients SET first_name = #{firstName}, last_name = #{lastName}, email = #{email}, birth_date = #{birthDate} WHERE client_id = #{clientId}")
    void update(Client client);

    @Delete("DELETE FROM clients WHERE client_id = #{clientId}")
    void delete(Long clientId);
}
