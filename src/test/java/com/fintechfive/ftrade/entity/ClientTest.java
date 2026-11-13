package com.fintechfive.ftrade.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ClientTest {

    Month month = Month.JANUARY;

    @Test
    void noArgsConstructorInitializesClientAccounts() {
        Client client = new Client();

        assertAll(
                () -> assertNull(client.getClientId()),
                () -> assertNull(client.getFirstName()),
                () -> assertNull(client.getLastName()),
                () -> assertNull(client.getBirthDate()),
                () -> assertNotNull(client.getClientAccounts()),
                () -> assertTrue(client.getClientAccounts().isEmpty())
        );
    }

    @Test
    void parameterizedConstructorSetsProvidedValues() {
        LocalDate birthDate = LocalDate.of(1990, month, 15);
        Client client = new Client("John", "Doe", birthDate);

        assertAll(
                () -> assertNull(client.getClientId()),
                () -> assertEquals("John", client.getFirstName()),
                () -> assertEquals("Doe", client.getLastName()),
                () -> assertEquals(birthDate, client.getBirthDate()),
                () -> assertNotNull(client.getClientAccounts()),
                () -> assertTrue(client.getClientAccounts().isEmpty())
        );
    }

    @Test
    void settersUpdateAllMutableFields() {
        Client client = new Client();
        LocalDate birthDate = LocalDate.of(1988, month, 20);
        ArrayList<Account> accounts = new ArrayList<>();

        client.setClientId(5L);
        client.setFirstName("Jane");
        client.setLastName("Smith");
        client.setBirthDate(birthDate);
        client.setClientAccounts(accounts);

        assertAll(
                () -> assertEquals(5L, client.getClientId()),
                () -> assertEquals("Jane", client.getFirstName()),
                () -> assertEquals("Smith", client.getLastName()),
                () -> assertEquals(birthDate, client.getBirthDate()),
                () -> assertSame(accounts, client.getClientAccounts())
        );
    }

    @Test
    void constructorWithEmailSetsEmail() {
        LocalDate birthDate = LocalDate.of(1990, month, 15);
        Client client = new Client("John", "Doe", "john.doe@example.com", birthDate);

        assertAll(
                () -> assertEquals("John", client.getFirstName()),
                () -> assertEquals("Doe", client.getLastName()),
                () -> assertEquals("john.doe@example.com", client.getEmail()),
                () -> assertEquals(birthDate, client.getBirthDate())
        );
    }

    @Test
    void settersUpdateEmailAndExternalAccountId() {
        Client client = new Client();
        UUID externalAccountId = UUID.randomUUID();

        client.setEmail("jane.smith@example.com");
        client.setExternalAccountId(externalAccountId);

        assertAll(
                () -> assertEquals("jane.smith@example.com", client.getEmail()),
                () -> assertEquals(externalAccountId, client.getExternalAccountId())
        );
    }
}
