package com.shop.backend.einvoice.worker;

import com.shop.backend.einvoice.service.InvoiceOutboxService;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InvoiceLeaseReaper {

    private static final Logger log = LoggerFactory.getLogger(InvoiceLeaseReaper.class);

    private final InvoiceOutboxService invoiceOutboxService;
    private final InvoiceOutboxProperties properties;

    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    @Scheduled(fixedDelayString = "${app.einvoice.outbox.lease-reaper-interval-ms:30000}")
    public void scheduledReap() {
        if (!properties.isWorkerEnabled() || shuttingDown.get()) {
            return;
        }
        reapExpiredLeases();
    }

    public int reapExpiredLeases() {
        if (shuttingDown.get()) {
            log.info("Lease reaper [{}] is shutting down; skipping expired lease scan", properties.getWorkerId());
            return 0;
        }

        int reapedCount = invoiceOutboxService.reapExpiredLeases();
        if (reapedCount > 0) {
            log.warn("Lease reaper [{}] reclaimed {} expired outbox leases", properties.getWorkerId(), reapedCount);
        }
        return reapedCount;
    }

    @EventListener(ContextClosedEvent.class)
    public void onContextClosed() {
        initiateShutdown();
    }

    @PreDestroy
    public void initiateShutdown() {
        if (shuttingDown.compareAndSet(false, true)) {
            log.info("InvoiceLeaseReaper [{}] marked as shutting down", properties.getWorkerId());
        }
    }

    public void resetShutdownState() {
        shuttingDown.set(false);
    }

    public boolean isShuttingDown() {
        return shuttingDown.get();
    }
}
