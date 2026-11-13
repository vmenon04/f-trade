package com.fintechfive.ftrade.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import com.fintechfive.ftrade.entity.Account;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;

//an account can be created, all accounts need to be found for a client, and an account can be updated or deleted.
@Mapper
public interface AccountMapper {

    // columns aliased to the Account field names, so MyBatis fills the right fields
    // (accounts has no client_id column, so clientId is not filled here; the link is in client_accounts)
    String COLUMNS = "account_id AS accountId, balance";

    @Select("SELECT " + COLUMNS + " FROM accounts WHERE account_id = #{accountId}")
    Account findById(@Param("accountId") Long accountId);

    @Select("SELECT " + COLUMNS + " FROM accounts")
    List<Account> findAll();

    @Select("SELECT account_id as accountId FROM accounts WHERE external_account_id = #{externalAccountId}")
    Long findAccountIdByExternalAccountId(@Param("externalAccountId") UUID externalAccountId);

    // #{...} are Account field names
    @Insert("INSERT INTO accounts(balance) VALUES(#{balance})")
    @Options(useGeneratedKeys = true, keyProperty = "accountId", keyColumn = "account_id")
    void insert(Account account);

    @Update("UPDATE accounts SET balance = #{balance} WHERE account_id = #{accountId}")
    void update(Account account);

    @Delete("DELETE FROM accounts WHERE account_id = #{accountId}")
    void delete(@Param("accountId") Long accountId);

    // Vasu's additions for accountmapper

    @Select("SELECT balance FROM accounts WHERE account_id = #{accountId}")
    BigDecimal findBalance(@Param("accountId") Long accountId);

    //returns rows updated: 0 = no such account
    @Update("UPDATE accounts SET balance = balance + #{amount} WHERE account_id = #{accountId}")
    int increaseBalance(@Param("accountId") Long accountId, @Param("amount") BigDecimal amount);

    // returns rows updated: 0 = no such account OR insufficient funds (we check and update in one statement)
    @Update("UPDATE accounts SET balance = balance - #{amount} "
            + "WHERE account_id = #{accountId} AND balance >= #{amount}")
    int reduceBalance(@Param("accountId") Long accountId, @Param("amount") BigDecimal amount);
}
