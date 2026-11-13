package com.fintechfive.ftrade.dto;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.UUID;

@DisplayName("ClientDTO Tests")
class ClientDTOTest {

    Month month = Month.JANUARY;

    @Test
    @DisplayName("Should create ClientDTO with valid data")
    void testValidClientDTO() {
        List<AccountDTO> accounts = List.of(
            new AccountDTO(UUID.fromString("123e4567-e89b-12d3-a456-426614174000")));
        ClientDTO dto = new ClientDTO(1L, "John", "Doe", LocalDate.of(1990, month, 15), accounts);
        
        assertEquals(1L, dto.clientId());
        assertEquals("John", dto.firstName());
        assertEquals("Doe", dto.lastName());
        assertEquals(LocalDate.of(1990, month, 15), dto.birthDate());
        assertEquals(1, dto.clientAccounts().size());
    }

    @Test
    @DisplayName("Should support record equality")
    void testRecordEquality() {
        List<AccountDTO> accounts1 = List.of(
            new AccountDTO(UUID.fromString("123e4567-e89b-12d3-a456-426614174000")));
        List<AccountDTO> accounts2 = List.of(
            new AccountDTO(UUID.fromString("123e4567-e89b-12d3-a456-426614174000")));
        LocalDate birthDate = LocalDate.of(1990, month, 15);
        
        ClientDTO dto1 = new ClientDTO(1L, "John", "Doe", birthDate, accounts1);
        ClientDTO dto2 = new ClientDTO(1L, "John", "Doe", birthDate, accounts2);
        ClientDTO dto3 = new ClientDTO(1L, "Jane", "Doe", birthDate, accounts1);
        
        assertEquals(dto1, dto2);
        assertNotEquals(dto1, dto3);
    }

    @Test
    @DisplayName("Should support record immutability")
    void testRecordImmutability() {
        List<AccountDTO> accounts = List.of(
            new AccountDTO(UUID.fromString("123e4567-e89b-12d3-a456-426614174000")));
        ClientDTO dto = new ClientDTO(1L, "John", "Doe", LocalDate.of(1990, month, 15), accounts);
        
        assertEquals("John", dto.firstName());
        // Records are immutable, no setters exist
    }

    @Test
    @DisplayName("Should support multiple account types")
    void testMultipleAccountTypes() {
        List<AccountDTO> accounts = List.of(
            new AccountDTO(UUID.fromString("123e4567-e89b-12d3-a456-426614174000")),
            new AccountDTO(UUID.fromString("223e4567-e89b-12d3-a456-426614174001"))
        );
        ClientDTO dto = new ClientDTO(1L, "John", "Doe", LocalDate.of(1990, month, 15), accounts);
        
        assertEquals(2, dto.clientAccounts().size());
        assertEquals(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"), dto.clientAccounts().get(0).accountId());
        assertEquals(UUID.fromString("223e4567-e89b-12d3-a456-426614174001"), dto.clientAccounts().get(1).accountId());
    }
}

