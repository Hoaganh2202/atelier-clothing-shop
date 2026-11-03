package com.shop.backend.product.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Thông tin danh mục sản phẩm công khai")
public class CategoryPublicResponse {

    @Schema(description = "ID danh mục")
    private Long id;

    @Schema(description = "Tên danh mục")
    private String name;

    @Schema(description = "Slug danh mục")
    private String slug;

    @ArraySchema(schema = @Schema(implementation = CategoryPublicResponse.class, description = "Danh sách danh mục con"))
    private List<CategoryPublicResponse> children;
}
