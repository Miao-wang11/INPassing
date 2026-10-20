package com.ikeu.server.aspect;

import com.ikeu.server.service.CreditService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Regression: no active synchronization after delivery must not break HTTP response. */
class CreditScoreAspectTest {
    @Test void deliveredOutsideTransactionProcessesCreditImmediately() {
        CreditService credit = mock(CreditService.class);
        new CreditScoreAspect(credit).afterConfirmDeliver(11L, 99L, new Object());
        verify(credit).processCreditOnDelivered(99L);
    }

    @Test void inTransactionProcessesOnlyAfterCommit() {
        CreditService credit = mock(CreditService.class);
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            new CreditScoreAspect(credit).afterConfirmDeliver(11L, 99L, new Object());
            verifyNoInteractions(credit);
            List<TransactionSynchronization> callbacks = TransactionSynchronizationManager.getSynchronizations();
            assertEquals(1, callbacks.size());
            callbacks.get(0).afterCommit();
            verify(credit).processCreditOnDelivered(99L);
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test void creditFailureCannotPretendCommittedDeliveryFailed() {
        CreditService credit = mock(CreditService.class);
        doThrow(new IllegalStateException("simulated credit subsystem error"))
                .when(credit).processCreditOnDelivered(99L);
        assertDoesNotThrow(() ->
                new CreditScoreAspect(credit).afterConfirmDeliver(11L, 99L, new Object()));
    }
}
