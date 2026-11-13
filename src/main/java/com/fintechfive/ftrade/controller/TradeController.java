package com.fintechfive.ftrade.controller;

import com.fintechfive.ftrade.dto.TradeRequestDTO;
import com.fintechfive.ftrade.service.RecipientService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;
import java.util.UUID;


// off when trade.stage.api.enabled=false, so a container can run only some stages; on when unset
@ConditionalOnProperty(name = "trade.stage.api.enabled", havingValue = "true", matchIfMissing = true)
@RestController
@RequestMapping("/v1/accounts/{accountId}/trades")
public class TradeController {

    private final RecipientService recipientService;

    public TradeController(RecipientService recipientService) {
        this.recipientService = recipientService;
    }

    @PreAuthorize("@accessControlService.userOwnsAccount(#dto.accountId(), #jwt.getSubject())")
    @PostMapping
    public ResponseEntity<Void> submitOrder(
        @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody TradeRequestDTO dto) {

        return recipientService.publishOrder(dto, Long.valueOf(Objects.requireNonNull(jwt.getSubject())));
    }

}
