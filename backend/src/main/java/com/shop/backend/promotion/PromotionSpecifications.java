package com.shop.backend.promotion;

import com.shop.backend.common.model.DiscountType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

public final class PromotionSpecifications {

    private PromotionSpecifications() {
    }

    public static Specification<Promotion> byFilter(
            String keyword,
            DiscountType discountType,
            Boolean isActive,
            PromotionTimeStatus status,
            Long productId,
            LocalDateTime now) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StringUtils.hasText(keyword)) {
                String pattern = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("name")), pattern));
            }

            if (discountType != null) {
                predicates.add(cb.equal(root.get("discountType"), discountType));
            }

            if (isActive != null) {
                predicates.add(cb.equal(root.get("isActive"), isActive));
            }

            if (status != null && now != null) {
                switch (status) {
                    case SCHEDULED -> predicates.add(cb.greaterThan(root.get("startAt"), now));
                    case RUNNING -> predicates.add(cb.and(
                            cb.lessThanOrEqualTo(root.get("startAt"), now),
                            cb.greaterThanOrEqualTo(root.get("endAt"), now)));
                    case EXPIRED -> predicates.add(cb.lessThan(root.get("endAt"), now));
                }
            }

            if (productId != null && query != null) {
                Subquery<Long> subquery = query.subquery(Long.class);
                Root<PromotionProduct> pp = subquery.from(PromotionProduct.class);
                subquery.select(pp.get("id").get("promotionId"))
                        .where(cb.equal(pp.get("id").get("productId"), productId));
                predicates.add(root.get("id").in(subquery));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
