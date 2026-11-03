package com.shop.backend.voucher;

import com.shop.backend.common.model.DiscountType;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

final class VoucherSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private VoucherSpecifications() {
    }

    static Specification<Voucher> byFilter(
            String keyword,
            DiscountType discountType,
            Boolean isActive,
            VoucherTimeStatus status,
            LocalDateTime now) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (keyword != null) {
                String escaped = keyword
                        .replace("\\", "\\\\")
                        .replace("%", "\\%")
                        .replace("_", "\\_");
                predicates.add(cb.like(root.get("code"), "%" + escaped + "%", LIKE_ESCAPE));
            }
            if (discountType != null) {
                predicates.add(cb.equal(root.get("discountType"), discountType));
            }
            if (isActive != null) {
                predicates.add(cb.equal(root.get("isActive"), isActive));
            }
            if (status != null) {
                switch (status) {
                    case SCHEDULED -> predicates.add(cb.greaterThan(root.get("startAt"), now));
                    case RUNNING -> {
                        predicates.add(cb.lessThanOrEqualTo(root.get("startAt"), now));
                        predicates.add(cb.greaterThanOrEqualTo(root.get("endAt"), now));
                    }
                    case EXPIRED -> predicates.add(cb.lessThan(root.get("endAt"), now));
                }
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
