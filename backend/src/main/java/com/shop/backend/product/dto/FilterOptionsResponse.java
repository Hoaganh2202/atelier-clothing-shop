package com.shop.backend.product.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FilterOptionsResponse {
    private List<String> sizes;
    private List<String> colors;
    private Long minPrice;
    private Long maxPrice;
}
