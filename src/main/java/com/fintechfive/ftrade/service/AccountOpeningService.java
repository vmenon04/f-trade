package com.fintechfive.ftrade.service;

import com.fintechfive.ftrade.dto.ClientDetailsDTO;
import com.fintechfive.ftrade.dto.OpenAccountRequestDTO;
import com.fintechfive.ftrade.exception.AccountConflictException;
import com.fintechfive.ftrade.exception.ClientDetailsRequiredException;
import com.fintechfive.ftrade.repository.AccountOpeningMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opens trading accounts for a user who has registered a login with auth-service.
 */
@Service
public class AccountOpeningService {

    private final AccountOpeningMapper accountOpeningMapper;

    public AccountOpeningService(AccountOpeningMapper accountOpeningMapper) {
        this.accountOpeningMapper = accountOpeningMapper;
    }

    /**
     * Opens an account with no cash and links it to the user's client. A user without a client yet gets one
     * created from their details first. All of it commits together, or none of it does.
     *
     * @param userId logged-in user id from the token
     * @param request the user's client details; only needed (and only used) when the user has no client yet
     * @throws ClientDetailsRequiredException if the user has no client and no details were given
     * @throws AccountConflictException if another client already uses the email, or a client was created for
     *                                  the user by another request at the same time
     */
    @Transactional
    public void openAccount(Long userId, OpenAccountRequestDTO request) {
        Long clientId = accountOpeningMapper.findClientIdByUserId(userId);
        if (clientId == null) {
            clientId = createClient(userId, request == null ? null : request.clientDetails());
        }
        Long accountId = accountOpeningMapper.insertAccount();
        accountOpeningMapper.linkClientToAccount(clientId, accountId);
    }

    private Long createClient(Long userId, ClientDetailsDTO details) {
        if (details == null) {
            throw new ClientDetailsRequiredException("Enter your details to open your first trading account");
        }
        Long clientId;
        try {
            clientId = accountOpeningMapper.insertClient(details.firstName().trim(), details.lastName().trim(),
                    details.email().trim(), details.birthDate());
        } catch (DuplicateKeyException e) {
            throw new AccountConflictException("That email is already used by another client");
        }
        if (accountOpeningMapper.linkUserToClient(userId, clientId) == 0) {
            // another request linked a client first; rolls back the client inserted above
            throw new AccountConflictException("You already have a trading account");
        }
        return clientId;
    }
}
