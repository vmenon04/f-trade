package com.fintechfive.ftrade.service;

import com.fintechfive.ftrade.repository.ClientAccountMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientAccountServiceTest {

    @Mock
    private ClientAccountMapper clientAccountMapper;

    @InjectMocks
    private ClientAccountService clientAccountService;

    @Test
    void getClientIdByAccountIdDelegatesToMapper() {
        when(clientAccountMapper.getClientIdByAccountId(42L)).thenReturn(List.of(7L, 8L));

        List<Long> result = clientAccountService.getClientIdByAccountId(42L);

        assertEquals(List.of(7L, 8L), result);
        verify(clientAccountMapper).getClientIdByAccountId(42L);
        verifyNoMoreInteractions(clientAccountMapper);
    }

    @Test
    void getClientIdByAccountIdPassesNullThroughToMapper() {
        when(clientAccountMapper.getClientIdByAccountId(null)).thenReturn(null);

        List<Long> result = clientAccountService.getClientIdByAccountId(null);

        assertNull(result);
        verify(clientAccountMapper).getClientIdByAccountId(null);
        verifyNoMoreInteractions(clientAccountMapper);
    }
}
