package com.shop.backend.promotion;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PromotionProductId implements Serializable {

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "promotion_id", nullable = false)
    private Long promotionId;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PromotionProductId that = (PromotionProductId) o;
        return Objects.equals(productId, that.productId) && Objects.equals(promotionId, that.promotionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productId, promotionId);
    }
}
