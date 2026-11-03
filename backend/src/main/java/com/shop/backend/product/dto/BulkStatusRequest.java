package com.shop.backend.product.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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
public class BulkStatusRequest {

    @NotEmpty(message = "Danh sách ID sản phẩm không được rỗng")
    private List<Long> ids;

    @NotNull(message = "Trạng thái không được để trống")
    private Boolean isActive;
}
