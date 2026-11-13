package com.fintechfive.ftrade.service;

import com.fintechfive.ftrade.dto.ClientDetailsDTO;
import com.fintechfive.ftrade.dto.OpenAccountRequestDTO;
import com.fintechfive.ftrade.exception.AccountConflictException;
import com.fintechfive.ftrade.exception.ClientDetailsRequiredException;
import com.fintechfive.ftrade.repository.AccountOpeningMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountOpeningServiceTest {

    private static final Long USER_ID = 7L;
    private static final Long CLIENT_ID = 11L;
    private static final Long ACCOUNT_ID = 21L;
    private static final LocalDate BIRTH_DATE = LocalDate.of(1990, 1, 1);

    @Mock
    private AccountOpeningMapper accountOpeningMapper;

    @InjectMocks
    private AccountOpeningService accountOpeningService;

    @Test
    void openAccount_newUser_createsClientAccountAndLinks() {
        when(accountOpeningMapper.findClientIdByUserId(USER_ID)).thenReturn(null);
        when(accountOpeningMapper.insertClient("Ada", "Lovelace", "ada@example.com", BIRTH_DATE)).thenReturn(CLIENT_ID);
        when(accountOpeningMapper.linkUserToClient(USER_ID, CLIENT_ID)).thenReturn(1);
        when(accountOpeningMapper.insertAccount()).thenReturn(ACCOUNT_ID);

        // surrounding spaces from the form are trimmed
        accountOpeningService.openAccount(USER_ID, withDetails(" Ada ", "Lovelace ", " ada@example.com"));

        InOrder order = inOrder(accountOpeningMapper);
        order.verify(accountOpeningMapper).insertClient("Ada", "Lovelace", "ada@example.com", BIRTH_DATE);
        order.verify(accountOpeningMapper).linkUserToClient(USER_ID, CLIENT_ID);
        order.verify(accountOpeningMapper).insertAccount();
        order.verify(accountOpeningMapper).linkClientToAccount(CLIENT_ID, ACCOUNT_ID);
    }

    @Test
    void openAccount_newUserWithoutDetails_throwsAndCreatesNothing() {
        when(accountOpeningMapper.findClientIdByUserId(USER_ID)).thenReturn(null);

        assertThrows(ClientDetailsRequiredException.class,
                () -> accountOpeningService.openAccount(USER_ID, new OpenAccountRequestDTO(null)));
        assertThrows(ClientDetailsRequiredException.class,
                () -> accountOpeningService.openAccount(USER_ID, null));

        verify(accountOpeningMapper, never()).insertClient(any(), any(), any(), any());
        verify(accountOpeningMapper, never()).insertAccount();
    }

    @Test
    void openAccount_emailAlreadyUsed_throwsConflictAndCreatesNoAccount() {
        when(accountOpeningMapper.findClientIdByUserId(USER_ID)).thenReturn(null);
        when(accountOpeningMapper.insertClient("Ada", "Lovelace", "ada@example.com", BIRTH_DATE))
                .thenThrow(new DuplicateKeyException("clients_email_key"));

        assertThrows(AccountConflictException.class,
                () -> accountOpeningService.openAccount(USER_ID, withDetails("Ada", "Lovelace", "ada@example.com")));

        verify(accountOpeningMapper, never()).insertAccount();
    }

    @Test
    void openAccount_clientLinkedByAnotherRequest_throwsConflictAndCreatesNoAccount() {
        when(accountOpeningMapper.findClientIdByUserId(USER_ID)).thenReturn(null);
        when(accountOpeningMapper.insertClient("Ada", "Lovelace", "ada@example.com", BIRTH_DATE)).thenReturn(CLIENT_ID);
        when(accountOpeningMapper.linkUserToClient(USER_ID, CLIENT_ID)).thenReturn(0);

        assertThrows(AccountConflictException.class,
                () -> accountOpeningService.openAccount(USER_ID, withDetails("Ada", "Lovelace", "ada@example.com")));

        verify(accountOpeningMapper, never()).insertAccount();
        verify(accountOpeningMapper, never()).linkClientToAccount(anyLong(), anyLong());
    }

    @Test
    void openAccount_existingClient_linksANewAccountWithoutCreatingAClient() {
        when(accountOpeningMapper.findClientIdByUserId(USER_ID)).thenReturn(CLIENT_ID);
        when(accountOpeningMapper.insertAccount()).thenReturn(ACCOUNT_ID);

        accountOpeningService.openAccount(USER_ID, null);

        verify(accountOpeningMapper, never()).insertClient(any(), any(), any(), any());
        verify(accountOpeningMapper).linkClientToAccount(CLIENT_ID, ACCOUNT_ID);
    }

    private static OpenAccountRequestDTO withDetails(String firstName, String lastName, String email) {
        return new OpenAccountRequestDTO(new ClientDetailsDTO(firstName, lastName, email, BIRTH_DATE));
    }
}
