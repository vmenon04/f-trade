package com.fintechfive.ftrade.service;

import com.fintechfive.ftrade.exception.InsufficientHoldingsException;
import com.fintechfive.ftrade.enums.ActivityStatus;
import com.fintechfive.ftrade.repository.AccountHoldingMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountHoldingServiceTest {

    private static final Long ACCOUNT_ID = 1L;
    private static final Long INSTRUMENT_ID = 10L;

    @Mock
    private AccountHoldingMapper accountHoldingMapper;

    @InjectMocks
    private AccountHoldingService accountHoldingService;

    // Compares by value, ignoring scale (so 150 matches 150.00000000)
    private static BigDecimal amountEqualTo(String expected) {
        return argThat(actual -> actual != null && actual.compareTo(new BigDecimal(expected)) == 0);
    }

    private void givenActiveQuantity(String quantity) {
        when(accountHoldingMapper.findActiveQuantity(ACCOUNT_ID, INSTRUMENT_ID))
                .thenReturn(quantity == null ? null : new BigDecimal(quantity));
    }

    // The previous active holding is deactivated before the new snapshot is inserted
    private void verifyDeactivatedThenInserted(String quantity, ActivityStatus status) {
        InOrder inOrder = inOrder(accountHoldingMapper);
        inOrder.verify(accountHoldingMapper).deactivateHolding(ACCOUNT_ID, INSTRUMENT_ID);
        inOrder.verify(accountHoldingMapper)
                .insertSnapshot(eq(ACCOUNT_ID), eq(INSTRUMENT_ID), amountEqualTo(quantity), eq(status));
    }

    private void verifyNothingChanged() {
        verify(accountHoldingMapper, never()).deactivateHolding(anyLong(), anyLong());
        verify(accountHoldingMapper, never()).insertSnapshot(anyLong(), anyLong(), any(), any());
    }

    @Test
    void getQuantityReturnsActiveQuantity() {
        givenActiveQuantity("100");
        assertEquals(0, new BigDecimal("100").compareTo(accountHoldingService.getQuantity(ACCOUNT_ID, INSTRUMENT_ID)));
    }

    @Test
    void getQuantityReturnsZeroWhenNoActiveHolding() {
        givenActiveQuantity(null);
        assertEquals(0, BigDecimal.ZERO.compareTo(accountHoldingService.getQuantity(ACCOUNT_ID, INSTRUMENT_ID)));
    }

    @Test
    void addQuantityInsertsIncreasedSnapshot() {
        givenActiveQuantity("100");
        accountHoldingService.addQuantity(ACCOUNT_ID, INSTRUMENT_ID, new BigDecimal("50"));
        verifyDeactivatedThenInserted("150", ActivityStatus.ACTIVE);
    }

    @Test
    void addQuantityStartsFromZeroForNewHolding() {
        givenActiveQuantity(null);
        accountHoldingService.addQuantity(ACCOUNT_ID, INSTRUMENT_ID, new BigDecimal("50"));
        verifyDeactivatedThenInserted("50", ActivityStatus.ACTIVE);
    }

    @Test
    void addQuantityRejectsNonPositive() {
        BigDecimal negative = new BigDecimal("-5");
        assertThrows(IllegalArgumentException.class,
                () -> accountHoldingService.addQuantity(ACCOUNT_ID, INSTRUMENT_ID, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> accountHoldingService.addQuantity(ACCOUNT_ID, INSTRUMENT_ID, negative));
        verifyNoInteractions(accountHoldingMapper);
    }

    @Test
    void removeQuantityInsertsDecreasedSnapshot() {
        givenActiveQuantity("100");
        accountHoldingService.removeQuantity(ACCOUNT_ID, INSTRUMENT_ID, new BigDecimal("30"));
        verifyDeactivatedThenInserted("70", ActivityStatus.ACTIVE);
    }

    @Test
    void removeQuantityMarksInactiveOnFullSell() {
        givenActiveQuantity("100");
        accountHoldingService.removeQuantity(ACCOUNT_ID, INSTRUMENT_ID, new BigDecimal("100"));
        verifyDeactivatedThenInserted("0", ActivityStatus.INACTIVE);
    }

    @Test
    void removeQuantityThrowsWhenInsufficient() {
        givenActiveQuantity("100");
        BigDecimal quantity = new BigDecimal("100.5");
        assertThrows(InsufficientHoldingsException.class,
                () -> accountHoldingService.removeQuantity(ACCOUNT_ID, INSTRUMENT_ID, quantity));
        verifyNothingChanged();
    }

    @Test
    void removeQuantityThrowsWhenNoActiveHolding() {
        givenActiveQuantity(null);
        BigDecimal quantity = new BigDecimal("10");
        assertThrows(InsufficientHoldingsException.class,
                () -> accountHoldingService.removeQuantity(ACCOUNT_ID, INSTRUMENT_ID, quantity));
        verifyNothingChanged();
    }

    @Test
    void removeQuantityRejectsNonPositive() {
        BigDecimal negative = new BigDecimal("-5");
        assertThrows(IllegalArgumentException.class,
                () -> accountHoldingService.removeQuantity(ACCOUNT_ID, INSTRUMENT_ID, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> accountHoldingService.removeQuantity(ACCOUNT_ID, INSTRUMENT_ID, negative));
        verifyNoInteractions(accountHoldingMapper);
    }

    @Test
    void rejectsNullQuantity() {
        assertThrows(IllegalArgumentException.class,
                () -> accountHoldingService.addQuantity(ACCOUNT_ID, INSTRUMENT_ID, null));
        assertThrows(IllegalArgumentException.class,
                () -> accountHoldingService.removeQuantity(ACCOUNT_ID, INSTRUMENT_ID, null));
        verifyNoInteractions(accountHoldingMapper);
    }
}
