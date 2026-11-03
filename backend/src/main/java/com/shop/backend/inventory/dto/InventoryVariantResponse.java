package com.shop.backend.inventory.dto;

import com.shop.backend.inventory.InventoryStockStatus;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Thông tin chi tiết tồn kho của một biến thể sản phẩm")
public class InventoryVariantResponse {

    @Schema(description = "ID biến thể", example = "1")
    private Long id;

    @Schema(description = "ID sản phẩm cha", example = "10")
    private Long productId;

    @Schema(description = "Tên sản phẩm", example = "Áo Thun Cotton Basic")
    private String productName;

    @Schema(description = "Trạng thái hiển thị kinh doanh của sản phẩm", example = "true")
    private Boolean productIsActive;

    @Schema(description = "Đường dẫn ảnh đại diện chính của sản phẩm", example = "/uploads/products/ao-thun.jpg")
    private String primaryImageUrl;

    @Schema(description = "Tên danh mục hàng hóa", example = "Áo Nam")
    private String categoryName;

    @Schema(description = "Mã SKU duy nhất của biến thể", example = "AT-COT-BLK-M")
    private String sku;

    @Schema(description = "Kích cỡ biến thể", example = "M")
    private String size;

    @Schema(description = "Màu sắc biến thể", example = "Đen")
    private String color;

    @Schema(description = "Số lượng tồn kho hiện tại", example = "45")
    private Integer stock;

    @Schema(description = "Giá bán áp dụng dạng số nguyên VND", example = "250000")
    private Long price;

    @Schema(description = "Trạng thái tồn kho (OUT_OF_STOCK, LOW_STOCK, IN_STOCK)", example = "IN_STOCK")
    private InventoryStockStatus stockStatus;
}
