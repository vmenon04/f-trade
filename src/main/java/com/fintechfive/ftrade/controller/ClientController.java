package com.fintechfive.ftrade.controller;

import com.fintechfive.ftrade.dto.ClientResponseDTO;
import com.fintechfive.ftrade.dto.OpenAccountRequestDTO;
import com.fintechfive.ftrade.entity.Client;
import com.fintechfive.ftrade.service.AccountOpeningService;
import com.fintechfive.ftrade.service.ClientService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v1/clients")
public class ClientController {

    private final ClientService clientService;
    private final AccountOpeningService accountOpeningService;

    public ClientController(ClientService clientService, AccountOpeningService accountOpeningService) {
        this.clientService = clientService;
        this.accountOpeningService = accountOpeningService;
    }

    // the logged-in user's client and trading accounts, found from the user in the token
    @GetMapping("/me")
    public ResponseEntity<ClientResponseDTO> getLoggedInClient(@AuthenticationPrincipal Jwt jwt) {
        UUID externalClientId = clientService.getExternalClientIdForUser(Long.parseLong(jwt.getSubject()));
        if (externalClientId == null) {
            // a user who only registered a login has no client or accounts yet
            return ResponseEntity.notFound().build();
        }
        // the user's own client, so no ownership check is needed here
        return clientResponse(externalClientId);
    }

    // opens a trading account (with no cash) for the logged-in user; their first one also creates their client,
    // so it needs their details. Errors are turned into responses by GlobalExceptionHandler.
    @PostMapping("/me/accounts")
    public ResponseEntity<ClientResponseDTO> openAccount(@AuthenticationPrincipal Jwt jwt,
                                                         @Valid @RequestBody(required = false) OpenAccountRequestDTO request) {
        accountOpeningService.openAccount(Long.parseLong(jwt.getSubject()), request);
        // the user has a client now, so this always finds one
        return ResponseEntity.status(HttpStatus.CREATED).body(getLoggedInClient(jwt).getBody());
    }

    @PreAuthorize("@accessControlService.canAccessClientData(#externalClientId, #jwt.getSubject())")
    @GetMapping("/{clientId}")
    public ResponseEntity<ClientResponseDTO> getClient(@PathVariable("clientId") UUID externalClientId,
                                                       @AuthenticationPrincipal Jwt jwt) {
        return clientResponse(externalClientId);
    }

    private ResponseEntity<ClientResponseDTO> clientResponse(UUID externalClientId) {
        Client client = clientService.getClientByExternalId(externalClientId);
        if (client == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(new ClientResponseDTO(
            externalClientId,
            client.getFirstName() + " " + client.getLastName(),
            clientService.getTradingAccounts(externalClientId)));
    }
}
