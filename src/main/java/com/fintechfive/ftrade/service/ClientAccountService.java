package com.fintechfive.ftrade.service;

import com.fintechfive.ftrade.repository.ClientAccountMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ClientAccountService {

    private final ClientAccountMapper clientAccountMapper;

    public ClientAccountService(ClientAccountMapper clientAccountMapper) {
        this.clientAccountMapper = clientAccountMapper;
    }

    public List<Long> getClientIdByAccountId(Long accountId) {
        return clientAccountMapper.getClientIdByAccountId(accountId);
    }

    public boolean existsByClientIdAndAccountId(Long clientId, Long accountId) {
        return clientAccountMapper.existsByClientIdAndAccountId(clientId, accountId);
    }
}
