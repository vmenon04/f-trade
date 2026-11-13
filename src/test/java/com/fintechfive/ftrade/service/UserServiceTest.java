package com.fintechfive.ftrade.service;

import com.fintechfive.ftrade.repository.UserMapper;
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
class UserServiceTest {

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private UserService userService;

    @Test
    void getUserIdByClientIdDelegatesToMapper() {
        when(userMapper.findUserIdByClientId(42L)).thenReturn(1001L);

        Long result = userService.getUserIdByClientId(42L);

        assertEquals(1001L, result);
        verify(userMapper).findUserIdByClientId(42L);
        verifyNoMoreInteractions(userMapper);
    }

    @Test
    void getUserIdByClientIdPassesNullThroughToMapper() {
        when(userMapper.findUserIdByClientId(null)).thenReturn(null);

        Long result = userService.getUserIdByClientId(null);

        assertNull(result);
        verify(userMapper).findUserIdByClientId(null);
        verifyNoMoreInteractions(userMapper);
    }
}
