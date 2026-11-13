package com.fintechfive.ftrade.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AccountTest {

    @Test
    void noArgsConstructorInitializesDefaults() {
        Account account = new Account();

        assertAll(
                () -> assertNull(account.getAccountId()),
                () -> assertEquals(BigDecimal.ZERO, account.getBalance()),
                () -> assertNotNull(account.getHoldings()),
                () -> assertNotNull(account.getExternalAccountId()),
                () -> assertTrue(account.getHoldings().isEmpty())
        );
    }

    @Test
    void settersUpdateAllMutableFields() {
        Account account = new Account();
        HashMap<Instrument, AccountHolding> holdings = new HashMap<>();

        account.setAccountId(10L);
        account.setBalance(BigDecimal.valueOf(99.5));
        account.setHoldings(holdings);

        assertAll(
                () -> assertEquals(10L, account.getAccountId()),
                () -> assertEquals(BigDecimal.valueOf(99.5), account.getBalance()),
                () -> assertSame(holdings, account.getHoldings())
        );
    }

    @Test
    void setExternalAccountIdReplacesGeneratedId() {
        Account account = new Account();
        UUID externalAccountId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        account.setExternalAccountId(externalAccountId);

        assertEquals(externalAccountId, account.getExternalAccountId());
    }
}
