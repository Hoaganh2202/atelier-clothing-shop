package com.shop.backend.einvoice.service.impl;

import com.shop.backend.einvoice.dto.AdjustItemCommand;
import com.shop.backend.einvoice.exception.InvoiceBalanceExceededException;
import com.shop.backend.einvoice.exception.InvoiceNotFoundException;
import com.shop.backend.einvoice.model.DocumentStatus;
import com.shop.backend.einvoice.model.Invoice;
import com.shop.backend.einvoice.model.InvoiceBalanceAdjustment;
import com.shop.backend.einvoice.model.InvoiceItem;
import com.shop.backend.einvoice.model.InvoiceItemType;
import com.shop.backend.einvoice.model.InvoiceType;
import com.shop.backend.einvoice.repository.InvoiceBalanceAdjustmentRepository;
import com.shop.backend.einvoice.repository.InvoiceItemRepository;
import com.shop.backend.einvoice.repository.InvoiceRepository;
import com.shop.backend.einvoice.service.InvoiceBalanceValidationService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InvoiceBalanceValidationServiceImpl implements InvoiceBalanceValidationService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceBalanceValidationServiceImpl.class);

    private final InvoiceBalanceAdjustmentRepository invoiceBalanceAdjustmentRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceItemRepository invoiceItemRepository;
    private final EntityManager entityManager;

    @Override
    @Transactional
    public void initializeBalance(Invoice originalInvoice, List<InvoiceItem> items) {
        if (originalInvoice == null) {
            throw new IllegalArgumentException("originalInvoice must not be null");
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("items must not be null or empty");
        }

        List<InvoiceBalanceAdjustment> balances = new ArrayList<>();
        for (InvoiceItem item : items) {
            if (item.getItemType() == InvoiceItemType.MERCHANDISE) {
                InvoiceBalanceAdjustment balance = InvoiceBalanceAdjustment.builder()
                        .originalInvoice(originalInvoice)
                        .invoiceItem(item)
                        .originalQuantity(item.getQuantity())
                        .totalAdjustedQuantity(0)
                        .remainingQuantity(item.getQuantity())
                        .originalTaxableAmount(item.getTaxableAmount())
                        .totalAdjustedTaxableAmount(BigDecimal.ZERO)
                        .remainingTaxableAmount(item.getTaxableAmount())
                        .originalTaxAmount(item.getTaxAmount())
                        .totalAdjustedTaxAmount(BigDecimal.ZERO)
                        .remainingTaxAmount(item.getTaxAmount())
                        .version(0L)
                        .build();
                balances.add(balance);
            }
        }
        invoiceBalanceAdjustmentRepository.saveAll(balances);
    }

    @Override
    @Transactional
    public void reserveBalance(Long originalInvoiceId, List<AdjustItemCommand> itemsToAdjust) {
        validateAndDebitBalance(originalInvoiceId, itemsToAdjust);
    }

    @Override
    @Transactional
    public void releaseAdjustmentBalance(Long adjustmentInvoiceId) {
        if (adjustmentInvoiceId == null) {
            throw new IllegalArgumentException("adjustmentInvoiceId must not be null");
        }

        Invoice unconfirmed = invoiceRepository.findById(adjustmentInvoiceId)
                .orElseThrow(() -> new InvoiceNotFoundException("Adjustment invoice not found: " + adjustmentInvoiceId));

        if (unconfirmed.getInvoiceType() != InvoiceType.ADJUSTMENT) {
            throw new IllegalArgumentException("Invoice " + adjustmentInvoiceId + " is not an ADJUSTMENT invoice");
        }

        // Idempotent terminal state: already released
        if (unconfirmed.getDocumentStatus() == DocumentStatus.ISSUE_FAILED) {
            log.info("Adjustment invoice {} is already in ISSUE_FAILED; skipping balance release (idempotent)", adjustmentInvoiceId);
            return;
        }

        // Forbidden states: cannot release if active in issuance or officially issued
        if (unconfirmed.getDocumentStatus() == DocumentStatus.ISSUED
                || unconfirmed.getDocumentStatus() == DocumentStatus.ISSUING
                || unconfirmed.getDocumentStatus() == DocumentStatus.ISSUANCE_UNKNOWN
                || unconfirmed.getDocumentStatus() == DocumentStatus.RECONCILING) {
            throw new IllegalStateException(
                    "Cannot release balance for adjustment invoice " + adjustmentInvoiceId
                            + " in status " + unconfirmed.getDocumentStatus());
        }

        if (unconfirmed.getDocumentStatus() != DocumentStatus.DRAFT
                && unconfirmed.getDocumentStatus() != DocumentStatus.PENDING_ISSUE) {
            throw new IllegalStateException(
                    "Unexpected document status for balance release on adjustment invoice " + adjustmentInvoiceId
                            + ": " + unconfirmed.getDocumentStatus());
        }

        Invoice original = unconfirmed.getOriginalInvoice();
        if (original == null) {
            throw new IllegalStateException("Adjustment invoice " + adjustmentInvoiceId + " has no original invoice");
        }

        Long originalId = original.getId();
        if (originalId.equals(adjustmentInvoiceId)) {
            throw new IllegalStateException("Adjustment invoice " + adjustmentInvoiceId
                    + " cannot reference itself as original invoice");
        }

        // Detach unconfirmed to ensure findByIdForUpdate reads fresh row under pessimistic lock
        entityManager.detach(unconfirmed);

        // Deterministic lock hierarchy: lock by ascending invoice ID to prevent deadlocks
        Long firstId = Math.min(originalId, adjustmentInvoiceId);
        Long secondId = Math.max(originalId, adjustmentInvoiceId);

        Invoice first = invoiceRepository.findByIdForUpdate(firstId)
                .orElseThrow(() -> new InvoiceNotFoundException("Invoice not found for lock: " + firstId));

        Invoice second = invoiceRepository.findByIdForUpdate(secondId)
                .orElseThrow(() -> new InvoiceNotFoundException("Invoice not found for lock: " + secondId));

        entityManager.refresh(first, LockModeType.PESSIMISTIC_WRITE);
        entityManager.refresh(second, LockModeType.PESSIMISTIC_WRITE);

        Invoice adjInvoice = first.getId().equals(adjustmentInvoiceId) ? first : second;
        Invoice originalInvoice = first.getId().equals(originalId) ? first : second;

        // Verify entities and relations post-lock from authoritative locked state
        if (adjInvoice.getInvoiceType() != InvoiceType.ADJUSTMENT) {
            throw new IllegalArgumentException("Locked invoice " + adjustmentInvoiceId + " is not an ADJUSTMENT invoice");
        }
        if (adjInvoice.getOriginalInvoice() == null || !adjInvoice.getOriginalInvoice().getId().equals(originalInvoice.getId())) {
            throw new IllegalStateException("Locked adjustment invoice " + adjustmentInvoiceId
                    + " does not reference original invoice " + originalId);
        }

        // Re-check status after acquiring pessimistic lock (concurrency safety)
        if (adjInvoice.getDocumentStatus() == DocumentStatus.ISSUE_FAILED) {
            log.info("Adjustment invoice {} became ISSUE_FAILED while waiting for lock; skipping balance release (idempotent)", adjustmentInvoiceId);
            return;
        }

        if (adjInvoice.getDocumentStatus() == DocumentStatus.ISSUED
                || adjInvoice.getDocumentStatus() == DocumentStatus.ISSUING
                || adjInvoice.getDocumentStatus() == DocumentStatus.ISSUANCE_UNKNOWN
                || adjInvoice.getDocumentStatus() == DocumentStatus.RECONCILING) {
            throw new IllegalStateException(
                    "Cannot release balance for adjustment invoice " + adjustmentInvoiceId
                            + " in status " + adjInvoice.getDocumentStatus());
        }

        if (adjInvoice.getDocumentStatus() != DocumentStatus.DRAFT
                && adjInvoice.getDocumentStatus() != DocumentStatus.PENDING_ISSUE) {
            throw new IllegalStateException(
                    "Unexpected document status for balance release on adjustment invoice " + adjustmentInvoiceId
                            + ": " + adjInvoice.getDocumentStatus());
        }

        List<InvoiceBalanceAdjustment> balances = invoiceBalanceAdjustmentRepository
                .findByOriginalInvoiceIdForUpdate(originalInvoice.getId());

        if (balances.isEmpty()) {
            throw new InvoiceBalanceExceededException("No balance records found for original invoice ID: " + originalId);
        }

        Map<Long, InvoiceBalanceAdjustment> balanceMap = balances.stream()
                .collect(Collectors.toMap(b -> b.getInvoiceItem().getId(), b -> b));

        List<InvoiceItem> adjItems = invoiceItemRepository.findByInvoiceId(adjInvoice.getId());
        if (adjItems.isEmpty()) {
            throw new IllegalStateException("Adjustment invoice " + adjustmentInvoiceId + " has no invoice items");
        }

        for (InvoiceItem item : adjItems) {
            if (item.getOriginalInvoiceItem() == null) {
                throw new IllegalStateException(
                        "Adjustment item " + item.getId() + " on adjustment invoice " + adjustmentInvoiceId
                                + " has no linked originalInvoiceItem; cannot safely release balance");
            }

            Long origItemId = item.getOriginalInvoiceItem().getId();
            InvoiceBalanceAdjustment balance = balanceMap.get(origItemId);
            if (balance == null) {
                throw new IllegalStateException(
                        "No InvoiceBalanceAdjustment found for original item ID " + origItemId
                                + " referenced by adjustment item " + item.getId());
            }

            int qtyToRelease = Math.abs(item.getQuantity() != null ? item.getQuantity() : 0);
            if (qtyToRelease <= 0) {
                continue;
            }

            if (qtyToRelease > balance.getTotalAdjustedQuantity()) {
                throw new InvoiceBalanceExceededException(String.format(
                        "Release quantity %d exceeds total adjusted quantity %d for invoice item %d",
                        qtyToRelease, balance.getTotalAdjustedQuantity(), origItemId));
            }

            BigDecimal taxableToRelease = item.getTaxableAmount() != null ? item.getTaxableAmount().abs() : null;
            if (taxableToRelease == null) {
                taxableToRelease = balance.getOriginalTaxableAmount()
                        .multiply(BigDecimal.valueOf(qtyToRelease))
                        .divide(BigDecimal.valueOf(balance.getOriginalQuantity()), 2, RoundingMode.HALF_UP);
            }
            if (taxableToRelease.compareTo(balance.getTotalAdjustedTaxableAmount()) > 0) {
                throw new InvoiceBalanceExceededException(String.format(
                        "Release taxable amount %s exceeds total adjusted taxable amount %s for invoice item %d",
                        taxableToRelease, balance.getTotalAdjustedTaxableAmount(), origItemId));
            }

            BigDecimal taxToRelease = item.getTaxAmount() != null ? item.getTaxAmount().abs() : null;
            if (taxToRelease == null) {
                taxToRelease = balance.getOriginalTaxAmount()
                        .multiply(BigDecimal.valueOf(qtyToRelease))
                        .divide(BigDecimal.valueOf(balance.getOriginalQuantity()), 2, RoundingMode.HALF_UP);
            }
            if (taxToRelease.compareTo(balance.getTotalAdjustedTaxAmount()) > 0) {
                throw new InvoiceBalanceExceededException(String.format(
                        "Release tax amount %s exceeds total adjusted tax amount %s for invoice item %d",
                        taxToRelease, balance.getTotalAdjustedTaxAmount(), origItemId));
            }

            balance.setTotalAdjustedQuantity(balance.getTotalAdjustedQuantity() - qtyToRelease);
            balance.setRemainingQuantity(balance.getRemainingQuantity() + qtyToRelease);

            balance.setTotalAdjustedTaxableAmount(balance.getTotalAdjustedTaxableAmount().subtract(taxableToRelease));
            balance.setRemainingTaxableAmount(balance.getRemainingTaxableAmount().add(taxableToRelease));

            balance.setTotalAdjustedTaxAmount(balance.getTotalAdjustedTaxAmount().subtract(taxToRelease));
            balance.setRemainingTaxAmount(balance.getRemainingTaxAmount().add(taxToRelease));
        }

        adjInvoice.setDocumentStatus(DocumentStatus.ISSUE_FAILED);
        invoiceRepository.save(adjInvoice);
        invoiceBalanceAdjustmentRepository.saveAll(balances);
    }

    @Override
    @Transactional
    public void releaseBalance(Long originalInvoiceId, List<AdjustItemCommand> itemsToRelease) {
        if (originalInvoiceId == null) {
            throw new IllegalArgumentException("originalInvoiceId must not be null");
        }
        if (itemsToRelease == null || itemsToRelease.isEmpty()) {
            return;
        }

        // Enforce unified lock hierarchy: invoices -> invoice_balance_adjustments
        invoiceRepository.findByIdForUpdate(originalInvoiceId)
                .orElseThrow(() -> new InvoiceNotFoundException("Original invoice not found: " + originalInvoiceId));

        List<InvoiceBalanceAdjustment> balances = invoiceBalanceAdjustmentRepository
                .findByOriginalInvoiceIdForUpdate(originalInvoiceId);

        if (balances.isEmpty()) {
            throw new InvoiceBalanceExceededException(
                    "No balance records found for original invoice ID: " + originalInvoiceId);
        }

        Map<Long, InvoiceBalanceAdjustment> balanceMap = balances.stream()
                .collect(Collectors.toMap(b -> b.getInvoiceItem().getId(), b -> b));

        for (AdjustItemCommand cmd : itemsToRelease) {
            Long itemId = cmd.getOriginalInvoiceItemId();
            InvoiceBalanceAdjustment balance = balanceMap.get(itemId);

            if (balance == null) {
                continue;
            }

            int qtyToRelease = cmd.getQuantityToAdjust() != null ? cmd.getQuantityToAdjust() : 0;
            if (qtyToRelease <= 0) {
                continue;
            }

            if (qtyToRelease > balance.getTotalAdjustedQuantity()) {
                throw new InvoiceBalanceExceededException(String.format(
                        "Release quantity %d exceeds total adjusted quantity %d for invoice item %d",
                        qtyToRelease, balance.getTotalAdjustedQuantity(), itemId));
            }

            BigDecimal taxableToRelease = cmd.getTaxableAmountToAdjust();
            if (taxableToRelease == null) {
                taxableToRelease = balance.getOriginalTaxableAmount()
                        .multiply(BigDecimal.valueOf(qtyToRelease))
                        .divide(BigDecimal.valueOf(balance.getOriginalQuantity()), 2, RoundingMode.HALF_UP);
            }
            if (taxableToRelease.compareTo(balance.getTotalAdjustedTaxableAmount()) > 0) {
                throw new InvoiceBalanceExceededException(String.format(
                        "Release taxable amount %s exceeds total adjusted taxable amount %s for invoice item %d",
                        taxableToRelease, balance.getTotalAdjustedTaxableAmount(), itemId));
            }

            BigDecimal taxToRelease = cmd.getTaxAmountToAdjust();
            if (taxToRelease == null) {
                taxToRelease = balance.getOriginalTaxAmount()
                        .multiply(BigDecimal.valueOf(qtyToRelease))
                        .divide(BigDecimal.valueOf(balance.getOriginalQuantity()), 2, RoundingMode.HALF_UP);
            }
            if (taxToRelease.compareTo(balance.getTotalAdjustedTaxAmount()) > 0) {
                throw new InvoiceBalanceExceededException(String.format(
                        "Release tax amount %s exceeds total adjusted tax amount %s for invoice item %d",
                        taxToRelease, balance.getTotalAdjustedTaxAmount(), itemId));
            }

            balance.setTotalAdjustedQuantity(balance.getTotalAdjustedQuantity() - qtyToRelease);
            balance.setRemainingQuantity(balance.getRemainingQuantity() + qtyToRelease);

            balance.setTotalAdjustedTaxableAmount(balance.getTotalAdjustedTaxableAmount().subtract(taxableToRelease));
            balance.setRemainingTaxableAmount(balance.getRemainingTaxableAmount().add(taxableToRelease));

            balance.setTotalAdjustedTaxAmount(balance.getTotalAdjustedTaxAmount().subtract(taxToRelease));
            balance.setRemainingTaxAmount(balance.getRemainingTaxAmount().add(taxToRelease));
        }

        invoiceBalanceAdjustmentRepository.saveAll(balances);
    }

    @Override
    @Transactional
    public void validateAndDebitBalance(Long originalInvoiceId, List<AdjustItemCommand> itemsToAdjust) {
        if (originalInvoiceId == null) {
            throw new IllegalArgumentException("originalInvoiceId must not be null");
        }
        if (itemsToAdjust == null || itemsToAdjust.isEmpty()) {
            throw new IllegalArgumentException("itemsToAdjust must not be null or empty");
        }

        // Enforce unified lock hierarchy: Invoice locks -> Balance row locks
        invoiceRepository.findByIdForUpdate(originalInvoiceId)
                .orElseThrow(() -> new InvoiceNotFoundException("Original invoice not found: " + originalInvoiceId));

        List<InvoiceBalanceAdjustment> balances = invoiceBalanceAdjustmentRepository
                .findByOriginalInvoiceIdForUpdate(originalInvoiceId);

        if (balances.isEmpty()) {
            throw new InvoiceBalanceExceededException(
                    "No balance records found for original invoice ID: " + originalInvoiceId);
        }

        Map<Long, InvoiceBalanceAdjustment> balanceMap = balances.stream()
                .collect(Collectors.toMap(b -> b.getInvoiceItem().getId(), b -> b));

        for (AdjustItemCommand cmd : itemsToAdjust) {
            Long itemId = cmd.getOriginalInvoiceItemId();
            InvoiceBalanceAdjustment balance = balanceMap.get(itemId);

            if (balance == null) {
                throw new InvoiceBalanceExceededException(
                    "No balance record found for invoice item ID: " + itemId);
            }

            int qtyToAdjust = cmd.getQuantityToAdjust();
            if (qtyToAdjust > balance.getRemainingQuantity()) {
                throw new InvoiceBalanceExceededException(String.format(
                        "Adjustment quantity %d exceeds remaining quantity %d for invoice item %d",
                        qtyToAdjust, balance.getRemainingQuantity(), itemId));
            }

            BigDecimal taxableToAdjust = cmd.getTaxableAmountToAdjust();
            if (taxableToAdjust == null) {
                taxableToAdjust = balance.getOriginalTaxableAmount()
                        .multiply(BigDecimal.valueOf(qtyToAdjust))
                        .divide(BigDecimal.valueOf(balance.getOriginalQuantity()), 2, RoundingMode.HALF_UP);
            }
            if (taxableToAdjust.compareTo(balance.getRemainingTaxableAmount()) > 0) {
                throw new InvoiceBalanceExceededException(String.format(
                        "Adjustment taxable amount %s exceeds remaining taxable amount %s for invoice item %d",
                        taxableToAdjust, balance.getRemainingTaxableAmount(), itemId));
            }

            BigDecimal taxToAdjust = cmd.getTaxAmountToAdjust();
            if (taxToAdjust == null) {
                taxToAdjust = balance.getOriginalTaxAmount()
                        .multiply(BigDecimal.valueOf(qtyToAdjust))
                        .divide(BigDecimal.valueOf(balance.getOriginalQuantity()), 2, RoundingMode.HALF_UP);
            }
            if (taxToAdjust.compareTo(balance.getRemainingTaxAmount()) > 0) {
                throw new InvoiceBalanceExceededException(String.format(
                        "Adjustment tax amount %s exceeds remaining tax amount %s for invoice item %d",
                        taxToAdjust, balance.getRemainingTaxAmount(), itemId));
            }

            balance.setTotalAdjustedQuantity(balance.getTotalAdjustedQuantity() + qtyToAdjust);
            balance.setRemainingQuantity(balance.getRemainingQuantity() - qtyToAdjust);

            balance.setTotalAdjustedTaxableAmount(balance.getTotalAdjustedTaxableAmount().add(taxableToAdjust));
            balance.setRemainingTaxableAmount(balance.getRemainingTaxableAmount().subtract(taxableToAdjust));

            balance.setTotalAdjustedTaxAmount(balance.getTotalAdjustedTaxAmount().add(taxToAdjust));
            balance.setRemainingTaxAmount(balance.getRemainingTaxAmount().subtract(taxToAdjust));
        }

        invoiceBalanceAdjustmentRepository.saveAll(balances);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InvoiceBalanceAdjustment> getBalances(Long originalInvoiceId) {
        return invoiceBalanceAdjustmentRepository.findByOriginalInvoiceId(originalInvoiceId);
    }
}
