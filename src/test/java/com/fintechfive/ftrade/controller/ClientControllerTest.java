package com.fintechfive.ftrade.controller;

import com.fintechfive.ftrade.entity.Client;
import com.fintechfive.ftrade.dto.AccountDTO;
import com.fintechfive.ftrade.dto.ClientDetailsDTO;
import com.fintechfive.ftrade.dto.OpenAccountRequestDTO;
import com.fintechfive.ftrade.exception.AccountConflictException;
import com.fintechfive.ftrade.exception.ClientDetailsRequiredException;
import com.fintechfive.ftrade.exception.GlobalExceptionHandler;
import com.fintechfive.ftrade.service.AccountOpeningService;
import com.fintechfive.ftrade.service.ClientService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.UUID;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ClientControllerTest {

    @Mock
    private ClientService clientService;

    @Mock
    private AccountOpeningService accountOpeningService;

    @InjectMocks
    private ClientController clientController;

    private MockMvc mockMvc;

    private static final Long USER_ID = 7L;

    @BeforeEach
    void setUp() {
        // the advice turns the service's exceptions into error responses, as it does in the running app
        mockMvc = MockMvcBuilders.standaloneSetup(clientController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        // @AuthenticationPrincipal reads the logged-in user from the security context; who may see which
        // client (@PreAuthorize) is tested in OwnershipAuthorizationTest
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", USER_ID.toString())
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getLoggedInClient_userWithClient_returnsClientAndAccounts() throws Exception {
        UUID clientId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        when(clientService.getExternalClientIdForUser(USER_ID)).thenReturn(clientId);
        when(clientService.getClientByExternalId(clientId))
                .thenReturn(new Client("Ada", "Lovelace", "ada@example.com", null));
        when(clientService.getTradingAccounts(clientId))
                .thenReturn(List.of(new AccountDTO(accountId)));

        mockMvc.perform(get("/api/v1/clients/me").contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").value(clientId.toString()))
                .andExpect(jsonPath("$.clientName").value("Ada Lovelace"))
                .andExpect(jsonPath("$.tradingAccounts[0].accountId").value(accountId.toString()));
    }

    @Test
    void openAccount_newUser_returns201WithTheNewAccount() throws Exception {
        UUID clientId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        when(clientService.getExternalClientIdForUser(USER_ID)).thenReturn(clientId);
        when(clientService.getClientByExternalId(clientId))
                .thenReturn(new Client("Ada", "Lovelace", "ada@example.com", null));
        when(clientService.getTradingAccounts(clientId)).thenReturn(List.of(new AccountDTO(accountId)));

        postOpenAccount(validAccountJson())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clientName").value("Ada Lovelace"))
                .andExpect(jsonPath("$.tradingAccounts[0].accountId").value(accountId.toString()));
        verify(accountOpeningService).openAccount(USER_ID, new OpenAccountRequestDTO(new ClientDetailsDTO(
                "Ada", "Lovelace", "ada@example.com", LocalDate.of(1990, 1, 1))));
    }

    @Test
    void openAccount_existingClientWithoutBody_returns201WithAllAccounts() throws Exception {
        UUID clientId = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(clientService.getExternalClientIdForUser(USER_ID)).thenReturn(clientId);
        when(clientService.getClientByExternalId(clientId))
                .thenReturn(new Client("Ada", "Lovelace", "ada@example.com", null));
        when(clientService.getTradingAccounts(clientId)).thenReturn(List.of(new AccountDTO(first), new AccountDTO(second)));

        mockMvc.perform(post("/api/v1/clients/me/accounts").contextPath("/api"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tradingAccounts", hasSize(2)));
        verify(accountOpeningService).openAccount(USER_ID, null);
    }

    @Test
    void openAccount_conflict_returns409WithTheMessage() throws Exception {
        doThrow(new AccountConflictException("That email is already used by another client"))
                .when(accountOpeningService).openAccount(eq(USER_ID), any());

        postOpenAccount(validAccountJson())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("That email is already used by another client"));
    }

    @Test
    void openAccount_newUserWithoutDetails_returns400WithTheMessage() throws Exception {
        doThrow(new ClientDetailsRequiredException("Enter your details to open your first trading account"))
                .when(accountOpeningService).openAccount(eq(USER_ID), any());

        postOpenAccount("{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Enter your details to open your first trading account"));
    }

    @Test
    void openAccount_invalidDetails_returns400AndOpensNothing() throws Exception {
        postOpenAccount("""
                {"clientDetails": {"firstName": "", "lastName": "Lovelace", "email": "not-an-email", "birthDate": "1990-01-01"}}
                """)
                .andExpect(status().isBadRequest());
        verify(accountOpeningService, never()).openAccount(any(), any());
    }

    private ResultActions postOpenAccount(String json) throws Exception {
        return mockMvc.perform(post("/api/v1/clients/me/accounts").contextPath("/api")
                .contentType(APPLICATION_JSON)
                .content(json));
    }

    private static String validAccountJson() {
        return """
                {"clientDetails": {"firstName": "Ada", "lastName": "Lovelace", "email": "ada@example.com", "birthDate": "1990-01-01"}}
                """;
    }

    @Test
    void getLoggedInClient_userWithoutClient_returns404() throws Exception {
        when(clientService.getExternalClientIdForUser(USER_ID)).thenReturn(null);

        mockMvc.perform(get("/api/v1/clients/me").contextPath("/api"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getClient_existingClient_returns200WithFullName() throws Exception {
        UUID clientId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        when(clientService.getClientByExternalId(clientId))
                .thenReturn(new Client("Ada", "Lovelace", "ada@example.com", null));
        when(clientService.getTradingAccounts(clientId))
            .thenReturn(List.of(new AccountDTO(accountId)));

        mockMvc.perform(get("/api/v1/clients/{clientId}", clientId).contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").value(clientId.toString()))
                .andExpect(jsonPath("$.clientName").value("Ada Lovelace"))
                .andExpect(jsonPath("$.tradingAccounts[0].accountId").value(accountId.toString()));
    }

    @Test
    void getClient_unknownClient_returns404() throws Exception {
        UUID clientId = UUID.randomUUID();
        when(clientService.getClientByExternalId(clientId)).thenReturn(null);

        mockMvc.perform(get("/api/v1/clients/{clientId}", clientId).contextPath("/api"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getClient_malformedId_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/clients/{clientId}", "not-a-uuid").contextPath("/api"))
                .andExpect(status().isBadRequest());
    }
}
