package com.shop.backend.product;

import com.shop.backend.common.dto.ErrorResponse;
import com.shop.backend.common.dto.PageResponse;
import com.shop.backend.product.dto.BulkStatusRequest;
import com.shop.backend.product.dto.ImageMetadataUpdateRequest;
import com.shop.backend.product.dto.LowStockVariantResponse;
import com.shop.backend.product.dto.ProductAdminListItemResponse;
import com.shop.backend.product.dto.ProductDetailResponse;
import com.shop.backend.product.dto.ProductImageResponse;
import com.shop.backend.product.dto.ProductUpsertRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/products")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
@Tag(name = "Admin Product Management", description = "Quản lý sản phẩm, biến thể và hình ảnh dành cho Admin/Staff")
public class ProductAdminController {

    private final ProductAdminService productService;

    @GetMapping
    @Operation(summary = "Lấy danh sách sản phẩm phân trang và tìm kiếm theo bộ lọc", description = "Hỗ trợ lọc theo tên/slug, danh mục, trạng thái kích hoạt, phân trang tối đa 100 sản phẩm/trang")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lấy danh sách sản phẩm thành công"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực danh tính", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Không có quyền truy cập", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PageResponse<ProductAdminListItemResponse>> getProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Boolean isActive,
            @PageableDefault(page = 0, size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(productService.getProducts(keyword, categoryId, isActive, pageable));
    }

    @GetMapping("/variants/low-stock")
    @Operation(
            summary = "Lấy danh sách biến thể sản phẩm có tồn kho thấp (dưới ngưỡng threshold)",
            description = "Dùng cho Admin Dashboard cảnh báo tồn kho sắp hết. Chỉ trả về các biến thể có stock < threshold của các sản phẩm đang hoạt động (isActive = true), sắp xếp theo stock tăng dần."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lấy danh sách biến thể tồn kho thấp thành công"),
            @ApiResponse(responseCode = "400", description = "Tham số yêu cầu không hợp lệ", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực danh tính", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Không có quyền truy cập", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PageResponse<LowStockVariantResponse>> getLowStockVariants(
            @RequestParam(required = false) Integer threshold,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size
    ) {
        return ResponseEntity.ok(productService.getLowStockVariants(threshold, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lấy chi tiết sản phẩm kèm toàn bộ biến thể và hình ảnh")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lấy thông tin chi tiết sản phẩm thành công"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực danh tính", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Không có quyền truy cập", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy sản phẩm", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ProductDetailResponse> getProductById(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getProductById(id));
    }

    @PostMapping
    @Operation(summary = "Tạo mới sản phẩm kèm danh sách biến thể ban đầu")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Tạo mới sản phẩm thành công"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu yêu cầu không hợp lệ", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực danh tính", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Không có quyền truy cập", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy danh mục", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Trùng slug, SKU hoặc thuộc tính biến thể", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ProductDetailResponse> createProduct(@Valid @RequestBody ProductUpsertRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.createProduct(request));
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Cập nhật sản phẩm và đồng bộ upsert danh sách biến thể",
            description = "Danh sách variants gửi lên phải là TOÀN BỘ variant của sản phẩm. Các biến thể không có trong danh sách sẽ bị xóa (chặn xóa và trả 409 nếu variant đã có đơn hàng hoặc giỏ hàng)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cập nhật sản phẩm thành công"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu yêu cầu không hợp lệ", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực danh tính", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Không có quyền truy cập", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy sản phẩm hoặc danh mục", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Trùng slug, SKU hoặc xóa biến thể đã phát sinh đơn/giỏ hàng", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ProductDetailResponse> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProductUpsertRequest request
    ) {
        return ResponseEntity.ok(productService.updateProduct(id, request));
    }

    @PatchMapping("/bulk-status")
    @Operation(summary = "Cập nhật trạng thái kinh doanh hàng loạt cho danh sách sản phẩm")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Cập nhật trạng thái thành công"),
            @ApiResponse(responseCode = "400", description = "Danh sách ID không hợp lệ", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực danh tính", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Không có quyền truy cập", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> updateBulkStatus(@Valid @RequestBody BulkStatusRequest request) {
        productService.updateBulkStatus(request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa sản phẩm (chặn xóa và trả 409 nếu sản phẩm đã phát sinh đơn hàng, đánh giá hoặc giỏ hàng)")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Xóa sản phẩm thành công"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực danh tính", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Không có quyền truy cập", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy sản phẩm", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Xung đột toàn vẹn dữ liệu đơn hàng, đánh giá hoặc giỏ hàng", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Tải lên hình ảnh sản phẩm và lưu file vào thư mục máy chủ")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Tải lên ảnh thành công"),
            @ApiResponse(responseCode = "400", description = "File không hợp lệ hoặc không phải ảnh", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực danh tính", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Không có quyền truy cập", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy sản phẩm", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ProductImageResponse> uploadProductImage(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "isPrimary", required = false) Boolean isPrimary,
            @RequestParam(value = "color", required = false) String color,
            @RequestParam(value = "alt", required = false) String alt
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(productService.uploadProductImage(id, file, isPrimary, color, alt));
    }

    @PatchMapping("/{id}/images/{imageId}")
    @Operation(summary = "Cập nhật thông tin mô tả, màu sắc, thứ tự hiển thị của ảnh")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cập nhật thông tin ảnh thành công"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu yêu cầu không hợp lệ", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực danh tính", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Không có quyền truy cập", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy sản phẩm hoặc ảnh", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ProductImageResponse> updateImageMetadata(
            @PathVariable Long id,
            @PathVariable Long imageId,
            @Valid @RequestBody ImageMetadataUpdateRequest request
    ) {
        return ResponseEntity.ok(productService.updateImageMetadata(id, imageId, request));
    }

    @DeleteMapping("/{id}/images/{imageId}")
    @Operation(summary = "Xóa hình ảnh sản phẩm khỏi database và máy chủ")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Xóa ảnh thành công"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực danh tính", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Không có quyền truy cập", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy sản phẩm hoặc ảnh", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> deleteProductImage(@PathVariable Long id, @PathVariable Long imageId) {
        productService.deleteProductImage(id, imageId);
        return ResponseEntity.noContent().build();
    }
}
