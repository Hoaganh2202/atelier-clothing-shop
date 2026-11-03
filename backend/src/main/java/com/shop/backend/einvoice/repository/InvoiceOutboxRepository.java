package com.shop.backend.einvoice.repository;

import com.shop.backend.einvoice.model.InvoiceOutbox;
import com.shop.backend.einvoice.model.OutboxStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InvoiceOutboxRepository extends JpaRepository<InvoiceOutbox, Long> {

    @Query(value = "SELECT CURRENT_TIMESTAMP", nativeQuery = true)
    LocalDateTime getCurrentDatabaseTime();

    Optional<InvoiceOutbox> findByEventKey(String eventKey);

    @Query(value = "SELECT * FROM invoice_outbox WHERE event_key = :eventKey FOR UPDATE", nativeQuery = true)
    Optional<InvoiceOutbox> findByEventKeyForUpdate(@Param("eventKey") String eventKey);

    List<InvoiceOutbox> findByInvoiceId(Long invoiceId);

    List<InvoiceOutbox> findByInvoiceIdAndEventType(Long invoiceId, String eventType);

    @Query(value = "SELECT * FROM invoice_outbox " +
                   "WHERE status = 'pending' " +
                   "  AND available_at <= :now " +
                   "  AND (locked_until IS NULL OR locked_until < :now) " +
                   "ORDER BY id ASC " +
                   "LIMIT :batchSize " +
                   "FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<InvoiceOutbox> claimAvailableEvents(@Param("now") LocalDateTime now, @Param("batchSize") int batchSize);

    @Query(value = "SELECT * FROM invoice_outbox " +
                   "WHERE status = 'pending' " +
                   "  AND event_type = :eventType " +
                   "  AND available_at <= :now " +
                   "  AND (locked_until IS NULL OR locked_until < :now) " +
                   "ORDER BY id ASC " +
                   "LIMIT :batchSize " +
                   "FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<InvoiceOutbox> claimAvailableEventsByType(
            @Param("eventType") String eventType,
            @Param("now") LocalDateTime now,
            @Param("batchSize") int batchSize);

    @Query(value = "SELECT * FROM invoice_outbox " +
                   "WHERE status = 'processing' " +
                   "  AND locked_until < :now " +
                   "ORDER BY id ASC " +
                   "FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<InvoiceOutbox> findExpiredLeasesForUpdate(@Param("now") LocalDateTime now);

    @Query("SELECT o FROM InvoiceOutbox o " +
           "WHERE o.status = :status " +
           "  AND o.lockedUntil < :now")
    List<InvoiceOutbox> findExpiredLeases(@Param("status") OutboxStatus status, @Param("now") LocalDateTime now);
}
