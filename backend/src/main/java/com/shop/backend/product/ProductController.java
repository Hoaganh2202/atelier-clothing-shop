package com.shop.backend.product;

import com.shop.backend.common.dto.PageResponse;
import com.shop.backend.product.dto.FilterOptionsResponse;
import com.shop.backend.product.dto.ProductCardResponse;
import com.shop.backend.product.dto.ProductPublicDetailResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "Product (Storefront)", description = "APIs xem sản phẩm công khai cho khách hàng")
public class ProductController {

    private final ProductService productService;

    @GetMapping
    @Operation(summary = "Lấy danh sách sản phẩm có phân trang, tìm kiếm và lọc")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lấy danh sách sản phẩm thành công"),
            @ApiResponse(responseCode = "400", description = "Tham số lọc không hợp lệ")
    })
    public ResponseEntity<PageResponse<ProductCardResponse>> getProducts(
            @Parameter(description = "Từ khóa tìm kiếm (tên hoặc slug)")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "ID danh mục (bao gồm cả danh mục con)")
            @RequestParam(required = false) Long categoryId,
            @Parameter(description = "Giá tối thiểu")
            @RequestParam(required = false) Long minPrice,
            @Parameter(description = "Giá tối đa")
            @RequestParam(required = false) Long maxPrice,
            @Parameter(description = "Danh sách kích cỡ")
            @RequestParam(required = false) List<String> sizes,
            @Parameter(description = "Danh sách màu sắc")
            @RequestParam(required = false) List<String> colors,
            @Parameter(description = "Kiểu sắp xếp: newest, price_asc, price_desc")
            @RequestParam(defaultValue = "newest") String sort,
            @Parameter(description = "Số trang (bắt đầu từ 0)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Kích thước trang (1 - 50)")
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(productService.getProducts(keyword, categoryId, minPrice, maxPrice, sizes, colors, sort, page, size));
    }

    @GetMapping("/filter-options")
    @Operation(summary = "Lấy danh sách các tùy chọn bộ lọc (size, color, min/max price) theo danh mục")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lấy tùy chọn bộ lọc thành công"),
            @ApiResponse(responseCode = "404", description = "Danh mục không tồn tại")
    })
    public ResponseEntity<FilterOptionsResponse> getFilterOptions(
            @Parameter(description = "ID danh mục lọc tùy chọn (bỏ trống để lấy toàn bộ)")
            @RequestParam(required = false) Long categoryId
    ) {
        return ResponseEntity.ok(productService.getFilterOptions(categoryId));
    }

    @GetMapping("/{slug}")
    @Operation(summary = "Lấy chi tiết sản phẩm theo slug")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lấy thông tin chi tiết thành công"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy sản phẩm hoặc sản phẩm đã ngừng kinh doanh")
    })
    public ResponseEntity<ProductPublicDetailResponse> getProductBySlug(
            @Parameter(description = "Slug của sản phẩm")
            @PathVariable String slug
    ) {
        return ResponseEntity.ok(productService.getProductBySlug(slug));
    }
}
