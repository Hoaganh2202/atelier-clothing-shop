package com.shop.backend.einvoice.service;

import com.shop.backend.einvoice.model.DocumentStatus;
import com.shop.backend.einvoice.model.Invoice;
import com.shop.backend.einvoice.model.InvoiceOutbox;
import com.shop.backend.einvoice.model.OutboxStatus;
import com.shop.backend.einvoice.repository.InvoiceAttemptRepository;
import com.shop.backend.einvoice.repository.InvoiceItemRepository;
import com.shop.backend.einvoice.repository.InvoiceOutboxRepository;
import com.shop.backend.einvoice.repository.InvoiceRepository;
import com.shop.backend.einvoice.service.impl.InvoiceOutboxServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvoiceOutboxReactivationRetryTest {

    @Mock
    private InvoiceOutboxRepository invoiceOutboxRepository;

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private InvoiceItemRepository invoiceItemRepository;

    @Mock
    private InvoiceAttemptRepository invoiceAttemptRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private com.shop.backend.einvoice.service.InvoiceBalanceValidationService invoiceBalanceValidationService;

    private InvoiceOutboxServiceImpl service;

    @BeforeEach
    void setUp() {
        lenient().when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());

        service = new InvoiceOutboxServiceImpl(
                invoiceOutboxRepository,
                invoiceRepository,
                invoiceItemRepository,
                invoiceAttemptRepository,
                transactionManager,
                invoiceBalanceValidationService);
    }

    @Test
    @DisplayName("Test 1 - Transient lock/deadlock retries and succeeds on next attempt")
    void transientLock_retriesAndSucceeds() {
        Long invoiceId = 100L;
        Invoice invoice = Invoice.builder()
                .id(invoiceId)
                .idempotencyKey("KEY-100")
                .documentStatus(DocumentStatus.ISSUANCE_UNKNOWN)
                .build();

        AtomicInteger callCount = new AtomicInteger(0);
        when(invoiceRepository.findByIdForUpdate(invoiceId)).thenAnswer(inv -> {
            int count = callCount.incrementAndGet();
            if (count == 1) {
                throw new CannotAcquireLockException("Deadlock", new SQLException("Deadlock", "40001", 1213));
            }
            return Optional.of(invoice);
        });

        when(invoiceOutboxRepository.getCurrentDatabaseTime()).thenReturn(LocalDateTime.now());
        when(invoiceOutboxRepository.findByEventKey("RECONCILE_INVOICE_100")).thenReturn(Optional.empty());
        when(invoiceOutboxRepository.save(any(InvoiceOutbox.class))).thenAnswer(inv -> inv.getArgument(0));

        InvoiceOutbox result = service.reactivateReconciliation(invoiceId);

        assertThat(result).isNotNull();
        assertThat(result.getEventType()).isEqualTo("RECONCILE_STATUS");
        assertThat(callCount.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("Test 2 - Duplicate event key recovers existing event idempotently without retrying")
    void duplicateEventKey_recoversExistingEventIdempotently() {
        Long invoiceId = 101L;
        Invoice invoice = Invoice.builder()
                .id(invoiceId)
                .idempotencyKey("KEY-101")
                .documentStatus(DocumentStatus.ISSUANCE_UNKNOWN)
                .build();

        when(invoiceRepository.findByIdForUpdate(invoiceId)).thenReturn(Optional.of(invoice));
        when(invoiceOutboxRepository.getCurrentDatabaseTime()).thenReturn(LocalDateTime.now());

        // First attempt throws duplicate event key on save
        AtomicInteger saveCount = new AtomicInteger(0);
        when(invoiceOutboxRepository.findByEventKey("RECONCILE_INVOICE_101")).thenAnswer(inv -> {
            if (saveCount.get() == 0) {
                return Optional.empty();
            }
            return Optional.of(InvoiceOutbox.builder()
                    .id(501L)
                    .eventType("RECONCILE_STATUS")
                    .eventKey("RECONCILE_INVOICE_101")
                    .status(OutboxStatus.PENDING)
                    .attemptCount(0)
                    .build());
        });

        when(invoiceOutboxRepository.save(any(InvoiceOutbox.class))).thenAnswer(inv -> {
            saveCount.incrementAndGet();
            throw new DataIntegrityViolationException(
                    "Duplicate entry 'RECONCILE_INVOICE_101' for key 'uk_invoice_outbox_event_key'",
                    new SQLException("Duplicate entry", "23000", 1062));
        });

        InvoiceOutbox result = service.reactivateReconciliation(invoiceId);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(501L);
        assertThat(result.getStatus()).isEqualTo(OutboxStatus.PENDING);
    }

    @Test
    @DisplayName("Test 2b - Duplicate of a different unique constraint (e.g. uq_invoice_idempotency) throws immediately without recovery")
    void duplicateOtherUniqueConstraint_throwsImmediatelyWithoutRecovery() {
        Long invoiceId = 105L;
        Invoice invoice = Invoice.builder()
                .id(invoiceId)
                .idempotencyKey("KEY-105")
                .documentStatus(DocumentStatus.ISSUANCE_UNKNOWN)
                .build();

        when(invoiceRepository.findByIdForUpdate(invoiceId)).thenReturn(Optional.of(invoice));
        when(invoiceOutboxRepository.getCurrentDatabaseTime()).thenReturn(LocalDateTime.now());
        when(invoiceOutboxRepository.findByEventKey("RECONCILE_INVOICE_105")).thenReturn(Optional.empty());

        AtomicInteger saveAttempts = new AtomicInteger(0);
        when(invoiceOutboxRepository.save(any(InvoiceOutbox.class))).thenAnswer(inv -> {
            saveAttempts.incrementAndGet();
            throw new DataIntegrityViolationException(
                    "Duplicate entry 'KEY-105' for key 'uq_invoice_idempotency'",
                    new SQLException("Duplicate entry", "23000", 1062));
        });

        assertThatThrownBy(() -> service.reactivateReconciliation(invoiceId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_invoice_idempotency");

        // Must throw immediately on attempt 1 without retrying or calling recoverExistingReconcileEvent
        assertThat(saveAttempts.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("Test 2c - isDuplicateEventKeyViolation accurately distinguishes uk_invoice_outbox_event_key from other unique constraints")
    void isDuplicateEventKeyViolation_classificationAccuracy() {
        // Exact constraint match
        DataIntegrityViolationException exactMatch = new DataIntegrityViolationException(
                "Duplicate entry 'RECONCILE_1' for key 'uk_invoice_outbox_event_key'",
                new SQLException("Duplicate entry", "23000", 1062));
        assertThat(InvoiceOutboxServiceImpl.isDuplicateEventKeyViolation(exactMatch)).isTrue();

        // Other unique constraint (should NOT match)
        DataIntegrityViolationException otherUnique = new DataIntegrityViolationException(
                "Duplicate entry 'KEY-1' for key 'uq_invoice_idempotency'",
                new SQLException("Duplicate entry", "23000", 1062));
        assertThat(InvoiceOutboxServiceImpl.isDuplicateEventKeyViolation(otherUnique)).isFalse();

        // Generic duplicate message without constraint name (should NOT match)
        DataIntegrityViolationException generic1062 = new DataIntegrityViolationException(
                "Duplicate entry '1' for key 'PRIMARY'",
                new SQLException("Duplicate entry", "23000", 1062));
        assertThat(InvoiceOutboxServiceImpl.isDuplicateEventKeyViolation(generic1062)).isFalse();

        // Non-duplicate error (FK)
        DataIntegrityViolationException fkError = new DataIntegrityViolationException(
                "Cannot add or update a child row: a foreign key constraint fails",
                new SQLException("FK fails", "23000", 1452));
        assertThat(InvoiceOutboxServiceImpl.isDuplicateEventKeyViolation(fkError)).isFalse();
    }

    @Test
    @DisplayName("Test 3 - Real constraint violation (e.g. FK, NOT NULL) throws immediately without retry")
    void realConstraintViolation_throwsImmediatelyWithoutRetry() {
        Long invoiceId = 102L;
        Invoice invoice = Invoice.builder()
                .id(invoiceId)
                .idempotencyKey("KEY-102")
                .documentStatus(DocumentStatus.ISSUANCE_UNKNOWN)
                .build();

        when(invoiceRepository.findByIdForUpdate(invoiceId)).thenReturn(Optional.of(invoice));
        when(invoiceOutboxRepository.getCurrentDatabaseTime()).thenReturn(LocalDateTime.now());
        when(invoiceOutboxRepository.findByEventKey("RECONCILE_INVOICE_102")).thenReturn(Optional.empty());

        AtomicInteger saveAttempts = new AtomicInteger(0);
        when(invoiceOutboxRepository.save(any(InvoiceOutbox.class))).thenAnswer(inv -> {
            saveAttempts.incrementAndGet();
            throw new DataIntegrityViolationException(
                    "Cannot add or update a child row: a foreign key constraint fails (`fk_invoice_outbox_invoice`)",
                    new SQLException("Foreign key constraint fails", "23000", 1452));
        });

        assertThatThrownBy(() -> service.reactivateReconciliation(invoiceId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("foreign key constraint fails");

        // Must throw immediately on attempt 1 without retrying
        assertThat(saveAttempts.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("Test 4 - Retry uses fresh transaction (PROPAGATION_REQUIRES_NEW)")
    void retry_usesFreshTransactionDefinition() {
        Long invoiceId = 103L;
        Invoice invoice = Invoice.builder()
                .id(invoiceId)
                .idempotencyKey("KEY-103")
                .documentStatus(DocumentStatus.ISSUANCE_UNKNOWN)
                .build();

        when(invoiceRepository.findByIdForUpdate(invoiceId)).thenReturn(Optional.of(invoice));
        when(invoiceOutboxRepository.getCurrentDatabaseTime()).thenReturn(LocalDateTime.now());
        when(invoiceOutboxRepository.findByEventKey("RECONCILE_INVOICE_103")).thenReturn(Optional.empty());
        when(invoiceOutboxRepository.save(any(InvoiceOutbox.class))).thenAnswer(inv -> inv.getArgument(0));

        service.reactivateReconciliation(invoiceId);

        // Verify that transaction definition passed to transactionManager specifies PROPAGATION_REQUIRES_NEW
        verify(transactionManager).getTransaction(argThat(def ->
                def.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW));
    }

    @Test
    @DisplayName("Test 5 - Thread interruption restores interrupt flag and stops cleanly")
    void threadInterruption_restoresInterruptFlagAndStops() {
        Long invoiceId = 104L;

        // Force a transient lock failure
        when(invoiceRepository.findByIdForUpdate(invoiceId))
                .thenThrow(new CannotAcquireLockException("Deadlock", new SQLException("Deadlock", "40001", 1213)));

        // Pre-interrupt the current thread so Thread.sleep throws InterruptedException immediately
        Thread.currentThread().interrupt();

        try {
            assertThatThrownBy(() -> service.reactivateReconciliation(invoiceId))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Interrupted while waiting to retry reconciliation reactivation");

            // Verify thread interrupt flag was restored
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            // Clear interrupt flag for remaining test runner threads
            Thread.interrupted();
        }
    }
}
