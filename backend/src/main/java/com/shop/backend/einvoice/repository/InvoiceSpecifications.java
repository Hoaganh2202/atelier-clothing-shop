package com.shop.backend.einvoice.repository;

import com.shop.backend.einvoice.model.DocumentStatus;
import com.shop.backend.einvoice.model.Invoice;
import com.shop.backend.einvoice.model.InvoiceType;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

public final class InvoiceSpecifications {

    private InvoiceSpecifications() {}

    public static Specification<Invoice> withKeyword(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return null;
        }
        String trimmed = keyword.trim();
        String pattern = "%" + trimmed.toLowerCase() + "%";
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.like(cb.lower(root.get("invoiceNumber")), pattern));
            predicates.add(cb.like(cb.lower(root.get("invoiceSeries")), pattern));
            predicates.add(cb.like(cb.lower(root.get("taxAuthorityCode")), pattern));
            predicates.add(cb.like(cb.lower(root.get("buyerProfile").get("buyerName")), pattern));

            // If keyword is numeric, also search by order.id
            try {
                Long orderIdVal = Long.parseLong(trimmed);
                predicates.add(cb.equal(root.get("order").get("id"), orderIdVal));
            } catch (NumberFormatException ignored) {
                // Not a number, ignore
            }

            return cb.or(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<Invoice> withOrderId(Long orderId) {
        if (orderId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("order").get("id"), orderId);
    }

    public static Specification<Invoice> withDocumentStatus(DocumentStatus status) {
        if (status == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("documentStatus"), status);
    }

    public static Specification<Invoice> withInvoiceType(InvoiceType type) {
        if (type == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("invoiceType"), type);
    }

    public static Specification<Invoice> withIsDemo(Boolean isDemo) {
        if (isDemo == null) {
            return null;
        }
        return (root, query, cb) -> {
            Predicate isMockStorage = cb.and(
                    cb.isNotNull(root.get("storageProvider")),
                    cb.equal(cb.upper(root.get("storageProvider")), "MOCK")
            );
            Predicate isDemoTx = cb.and(
                    cb.isNotNull(root.get("providerTransactionId")),
                    cb.like(root.get("providerTransactionId"), "DEMO-%")
            );
            Predicate isDemoSeries = cb.and(
                    cb.isNotNull(root.get("invoiceSeries")),
                    cb.like(root.get("invoiceSeries"), "DEMO%")
            );
            Predicate isDemoNumber = cb.and(
                    cb.isNotNull(root.get("invoiceNumber")),
                    cb.like(root.get("invoiceNumber"), "DEMO-%")
            );

            Predicate demoOr = cb.or(isMockStorage, isDemoTx, isDemoSeries, isDemoNumber);

            if (isDemo) {
                return demoOr;
            } else {
                Predicate notMockStorage = cb.or(
                        cb.isNull(root.get("storageProvider")),
                        cb.notEqual(cb.upper(root.get("storageProvider")), "MOCK")
                );
                Predicate notDemoTx = cb.or(
                        cb.isNull(root.get("providerTransactionId")),
                        cb.notLike(root.get("providerTransactionId"), "DEMO-%")
                );
                Predicate notDemoSeries = cb.or(
                        cb.isNull(root.get("invoiceSeries")),
                        cb.notLike(root.get("invoiceSeries"), "DEMO%")
                );
                Predicate notDemoNumber = cb.or(
                        cb.isNull(root.get("invoiceNumber")),
                        cb.notLike(root.get("invoiceNumber"), "DEMO-%")
                );

                return cb.and(notMockStorage, notDemoTx, notDemoSeries, notDemoNumber);
            }
        };
    }

    public static Specification<Invoice> withDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null && endDate == null) {
            return null;
        }
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startDate.atStartOfDay()));
            }
            if (endDate != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), endDate.plusDays(1).atStartOfDay()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<Invoice> withCustomerUserId(Long customerUserId) {
        if (customerUserId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("order").get("user").get("id"), customerUserId);
    }
}
