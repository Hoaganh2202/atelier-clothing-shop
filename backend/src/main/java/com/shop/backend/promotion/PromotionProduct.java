package com.shop.backend.promotion;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "product_promotions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PromotionProduct {

    @EmbeddedId
    private PromotionProductId id;
}
