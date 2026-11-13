package com.fintechfive.ftrade.dto;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.UUID;

@DisplayName("AccountDTO Tests")
class AccountDTOTest {
    @Test
    @DisplayName("Should create AccountDTO with valid data")
    void testValidAccountDTO() {
        UUID accountId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        AccountDTO dto = new AccountDTO(accountId);
        
        assertAll(
            () -> assertEquals(accountId, dto.accountId()),
            () -> assertEquals(accountId, dto.accountId())
        );
    }

    @Test
    @DisplayName("Should support record equality")
    void testRecordEquality() {
        UUID accountId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        AccountDTO dto1 = new AccountDTO(accountId);
        AccountDTO dto2 = new AccountDTO(accountId);
        AccountDTO dto3 = new AccountDTO(UUID.fromString("223e4567-e89b-12d3-a456-426614174001"));
        
        assertEquals(dto1, dto2);
        assertNotEquals(dto1, dto3);
    }

    @Test
    @DisplayName("Should support record hashCode")
    void testRecordHashCode() {
        UUID accountId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        AccountDTO dto1 = new AccountDTO(accountId);
        AccountDTO dto2 = new AccountDTO(accountId);
        
        assertEquals(dto1.hashCode(), dto2.hashCode());
    }
}

