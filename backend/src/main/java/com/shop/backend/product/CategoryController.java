package com.shop.backend.product;

import com.shop.backend.product.dto.CategoryPublicResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
@Tag(name = "Category (Storefront)", description = "APIs xem cây danh mục sản phẩm cho khách hàng")
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    @Operation(summary = "Lấy cây danh mục sản phẩm đang hoạt động")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lấy cây danh mục thành công")
    })
    public ResponseEntity<List<CategoryPublicResponse>> getCategories() {
        return ResponseEntity.ok(categoryService.getCategoryTree());
    }
}
