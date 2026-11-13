package com.fintechfive.ftrade.service;

import com.fintechfive.ftrade.entity.Client;
import com.fintechfive.ftrade.repository.ClientMapper;
import com.fintechfive.ftrade.dto.AccountDTO;
import java.util.UUID;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClientServiceTest {

    @Mock
    private ClientMapper clientMapper;

    @InjectMocks
    private ClientService clientService;

    private final UUID externalClientId = UUID.randomUUID();

    @Test
    void getInternalClientIdReturnsIdFromMapper() {
        when(clientMapper.findInternalIdByExternalClientId(externalClientId)).thenReturn(7L);
        assertEquals(7L, clientService.getInternalClientId(externalClientId));
    }

    @Test
    void getInternalClientIdReturnsNullWhenNotFound() {
        when(clientMapper.findInternalIdByExternalClientId(externalClientId)).thenReturn(null);
        assertNull(clientService.getInternalClientId(externalClientId));
    }

    @Test
    void getClientByExternalIdReturnsClientFromMapper() {
        Client client = new Client("Ada", "Lovelace", "ada@example.com", null);
        when(clientMapper.findInternalIdByExternalClientId(externalClientId)).thenReturn(7L);
        when(clientMapper.findById(7L)).thenReturn(client);
        assertSame(client, clientService.getClientByExternalId(externalClientId));
    }

    @Test
    void getClientByExternalIdReturnsNullWithoutLookupWhenIdNotFound() {
        when(clientMapper.findInternalIdByExternalClientId(externalClientId)).thenReturn(null);
        assertNull(clientService.getClientByExternalId(externalClientId));
        verify(clientMapper, never()).findById(any());
    }

    @Test
    void getTradingAccountsReturnsPublicAccountIds() {
        UUID accountId = UUID.randomUUID();
        when(clientMapper.findInternalIdByExternalClientId(externalClientId)).thenReturn(7L);
        when(clientMapper.findExternalAccountIdsByClientId(7L)).thenReturn(List.of(accountId));

        assertEquals(List.of(new AccountDTO(accountId)), clientService.getTradingAccounts(externalClientId));
    }

    @Test
    void getTradingAccountsReturnsMultiplePublicAccountIds() {
        UUID firstAccountId = UUID.randomUUID();
        UUID secondAccountId = UUID.randomUUID();
        when(clientMapper.findInternalIdByExternalClientId(externalClientId)).thenReturn(7L);
        when(clientMapper.findExternalAccountIdsByClientId(7L))
                .thenReturn(List.of(firstAccountId, secondAccountId));

        assertEquals(
                List.of(new AccountDTO(firstAccountId), new AccountDTO(secondAccountId)),
                clientService.getTradingAccounts(externalClientId));
    }

    @Test
    void getExternalClientIdForUserReturnsTheLinkedClientsId() {
        UUID externalClientId = UUID.randomUUID();
        when(clientMapper.findExternalClientIdByUserId(7L)).thenReturn(externalClientId);

        assertEquals(externalClientId, clientService.getExternalClientIdForUser(7L));
    }

    @Test
    void getExternalClientIdForUserIsNullWhenTheUserHasNoClient() {
        when(clientMapper.findExternalClientIdByUserId(7L)).thenReturn(null);

        assertNull(clientService.getExternalClientIdForUser(7L));
    }
}
