package com.fintechfive.ftrade.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.fintechfive.ftrade.service.AccountService;
import com.fintechfive.ftrade.service.ClientAccountService;
import com.fintechfive.ftrade.service.ClientService;
import com.fintechfive.ftrade.service.UserService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccessControlServiceTest {

    @Mock
    private AccountService accountService;

    @Mock
    private ClientService clientService;

    @Mock
    private ClientAccountService clientAccountService;

    @Mock
    private UserService userService;

    @InjectMocks
    private AccessControlService accessControlService;

    @Test
    void canAccessClientData_whenUserOwnsClient_returnsTrue() {
        UUID externalClientId = UUID.randomUUID();

        when(clientService.getInternalClientId(externalClientId)).thenReturn(10L);
        when(userService.getUserIdByClientId(10L)).thenReturn(42L);

        boolean allowed = accessControlService.canAccessClientData(externalClientId, "42");

        assertTrue(allowed);
    }

    @Test
    void canAccessClientData_whenUserDoesNotOwnClient_returnsFalse() {
        UUID externalClientId = UUID.randomUUID();

        when(clientService.getInternalClientId(externalClientId)).thenReturn(10L);
        when(userService.getUserIdByClientId(10L)).thenReturn(99L);

        boolean allowed = accessControlService.canAccessClientData(externalClientId, "42");

        assertFalse(allowed);
    }

    @Test
    void userOwnsAccount_whenNoLinkedClients_returnsFalse() {
        UUID externalAccountId = UUID.randomUUID();

        when(accountService.getAccountIdByExternalAccountId(externalAccountId)).thenReturn(5L);
        when(clientAccountService.getClientIdByAccountId(5L)).thenReturn(List.of());

        boolean allowed = accessControlService.userOwnsAccount(externalAccountId, "42");

        assertFalse(allowed);
    }

    @Test
    void userOwnsAccount_whenAnyLinkedClientBelongsToUser_returnsTrue() {
        UUID externalAccountId = UUID.randomUUID();

        when(accountService.getAccountIdByExternalAccountId(externalAccountId)).thenReturn(5L);
        when(clientAccountService.getClientIdByAccountId(5L)).thenReturn(List.of(100L, 200L));
        when(userService.getUserIdByClientId(100L)).thenReturn(11L);
        when(userService.getUserIdByClientId(200L)).thenReturn(42L);

        boolean allowed = accessControlService.userOwnsAccount(externalAccountId, "42");

        assertTrue(allowed);
    }

    @Test
    void userOwnsAccount_whenNoLinkedClientBelongsToUser_returnsFalse() {
        UUID externalAccountId = UUID.randomUUID();

        when(accountService.getAccountIdByExternalAccountId(externalAccountId)).thenReturn(5L);
        when(clientAccountService.getClientIdByAccountId(5L)).thenReturn(List.of(100L, 200L));
        when(userService.getUserIdByClientId(100L)).thenReturn(11L);
        when(userService.getUserIdByClientId(200L)).thenReturn(12L);

        boolean allowed = accessControlService.userOwnsAccount(externalAccountId, "42");

        assertFalse(allowed);
    }
}

