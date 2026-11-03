package com.shop.backend.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shop.backend.common.exception.BadRequestException;
import com.shop.backend.common.exception.ConflictException;
import com.shop.backend.product.dto.ProductDetailResponse;
import com.shop.backend.product.dto.ProductUpsertRequest;
import com.shop.backend.product.dto.VariantUpsertRequest;
import com.shop.backend.user.Role;
import com.shop.backend.user.RoleRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProductAdminServiceIntegrationTest {

    @Autowired
    private ProductAdminService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository variantRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private EntityManager entityManager;

    private Category testCategory;

    @BeforeEach
    void setUp() {
        testCategory = categoryRepository.save(Category.builder()
                .name("Áo Nam Thể Thao")
                .slug("ao-nam-the-thao-" + System.currentTimeMillis())
                .isActive(true)
                .build());
    }

    @Test
    void shouldPreventDeletingProductWhenVariantIsUsedInOrders() {
        ProductUpsertRequest createRequest = ProductUpsertRequest.builder()
                .name("Áo Chạy Bộ Thoáng Khí")
                .categoryId(testCategory.getId())
                .description("Mô tả sản phẩm chạy bộ")
                .basePrice(199000L)
                .isActive(true)
                .variants(List.of(
                        VariantUpsertRequest.builder()
                                .sku("ACT-BLK-M")
                                .size("M")
                                .color("Đen")
                                .stock(50)
                                .price(199000L)
                                .build()
                ))
                .build();

        ProductDetailResponse created = productService.createProduct(createRequest);
        Long productId = created.getId();
        Long variantId = created.getVariants().get(0).getId();

        Role role = roleRepository.save(Role.builder()
                .name("ROLE_CUSTOMER_" + System.currentTimeMillis())
                .build());

        entityManager.createNativeQuery(
                "INSERT INTO users (role_id, name, email, password_hash, status, data_source) " +
                "VALUES (:roleId, 'Khach Hang Test', :email, 'hash123', 'active', 'website')")
                .setParameter("roleId", role.getId())
                .setParameter("email", "buyer_" + System.currentTimeMillis() + "@test.com")
                .executeUpdate();

        Number userIdNum = (Number) entityManager.createNativeQuery(
                "SELECT LAST_INSERT_ID()").getSingleResult();
        Long userId = userIdNum.longValue();

        entityManager.createNativeQuery(
                "INSERT INTO orders (user_id, status, subtotal, total_amount, receiver_name, receiver_phone, province, data_source, created_at) " +
                "VALUES (:userId, 'completed', 199000.00, 199000.00, 'Nguoi Nhan', '0901234567', 'TP HCM', 'website', NOW())")
                .setParameter("userId", userId)
                .executeUpdate();

        Number orderIdNum = (Number) entityManager.createNativeQuery(
                "SELECT LAST_INSERT_ID()").getSingleResult();
        Long orderId = orderIdNum.longValue();

        entityManager.createNativeQuery(
                "INSERT INTO order_details (order_id, variant_id, product_name, variant_size, variant_color, quantity, unit_price) " +
                "VALUES (:orderId, :variantId, 'Áo Chạy Bộ Thoáng Khí', 'M', 'Đen', 1, 199000.00)")
                .setParameter("orderId", orderId)
                .setParameter("variantId", variantId)
                .executeUpdate();

        entityManager.flush();
        entityManager.clear();

        assertThatThrownBy(() -> productService.deleteProduct(productId))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("vì đã có biến thể phát sinh trong đơn hàng")
                .hasMessageContaining("Ngừng bán");

        assertThat(productRepository.existsById(productId)).isTrue();
    }

    @Test
    void shouldCorrectlyUpsertVariantsWithAddUpdateAndDeleteInSingleRequest() {
        ProductUpsertRequest initialRequest = ProductUpsertRequest.builder()
                .name("Quần Short Nỉ")
                .categoryId(testCategory.getId())
                .description("Quần short thể thao")
                .basePrice(150000L)
                .isActive(true)
                .variants(List.of(
                        VariantUpsertRequest.builder()
                                .sku("QS-GRY-M")
                                .size("M")
                                .color("Xám")
                                .stock(20)
                                .price(150000L)
                                .build(),
                        VariantUpsertRequest.builder()
                                .sku("QS-GRY-L")
                                .size("L")
                                .color("Xám")
                                .stock(0)
                                .price(150000L)
                                .build()
                ))
                .build();

        ProductDetailResponse created = productService.createProduct(initialRequest);
        Long productId = created.getId();
        Long variantMId = created.getVariants().stream()
                .filter(v -> "QS-GRY-M".equals(v.getSku()))
                .findFirst().orElseThrow().getId();
        Long variantLId = created.getVariants().stream()
                .filter(v -> "QS-GRY-L".equals(v.getSku()))
                .findFirst().orElseThrow().getId();

        ProductUpsertRequest updateRequest = ProductUpsertRequest.builder()
                .name("Quần Short Nỉ Cao Cấp")
                .categoryId(testCategory.getId())
                .description("Cập nhật chất liệu nỉ")
                .basePrice(160000L)
                .isActive(true)
                .variants(List.of(
                        VariantUpsertRequest.builder()
                                .id(variantMId)
                                .sku("QS-GRY-M-UPDATED")
                                .size("M")
                                .color("Xám Đậm")
                                .stock(45)
                                .price(165000L)
                                .build(),
                        VariantUpsertRequest.builder()
                                .id(null)
                                .sku("QS-BLK-XL")
                                .size("XL")
                                .color("Đen")
                                .stock(30)
                                .price(170000L)
                                .build()
                ))
                .build();

        ProductDetailResponse updated = productService.updateProduct(productId, updateRequest);

        entityManager.flush();
        entityManager.clear();

        assertThat(updated.getName()).isEqualTo("Quần Short Nỉ Cao Cấp");
        assertThat(updated.getVariants()).hasSize(2);

        assertThat(variantRepository.findById(variantLId)).isEmpty();

        ProductVariant updatedVariantM = variantRepository.findById(variantMId).orElseThrow();
        assertThat(updatedVariantM.getSku()).isEqualTo("QS-GRY-M-UPDATED");
        assertThat(updatedVariantM.getColor()).isEqualTo("Xám Đậm");
        assertThat(updatedVariantM.getStock()).isEqualTo(20);
        assertThat(updatedVariantM.getPrice()).isEqualByComparingTo(new BigDecimal("165000.00"));

        List<ProductVariant> currentVariants = variantRepository.findByProductId(productId);
        ProductVariant addedVariantXL = currentVariants.stream()
                .filter(v -> "QS-BLK-XL".equals(v.getSku()))
                .findFirst()
                .orElseThrow();
        assertThat(addedVariantXL.getSize()).isEqualTo("XL");
        assertThat(addedVariantXL.getColor()).isEqualTo("Đen");
        assertThat(addedVariantXL.getStock()).isEqualTo(30);
    }

    @Test
    void shouldRejectDuplicateSizeAndColorInRequestBeforeTouchingDatabase() {
        ProductUpsertRequest duplicateRequest = ProductUpsertRequest.builder()
                .name("Áo Polo Classic")
                .categoryId(testCategory.getId())
                .basePrice(250000L)
                .isActive(true)
                .variants(List.of(
                        VariantUpsertRequest.builder()
                                .sku("POLO-WHT-L-1")
                                .size("L")
                                .color("Trắng")
                                .stock(10)
                                .build(),
                        VariantUpsertRequest.builder()
                                .sku("POLO-WHT-L-2")
                                .size("L")
                                .color("Trắng")
                                .stock(20)
                                .build()
                ))
                .build();

        assertThatThrownBy(() -> productService.createProduct(duplicateRequest))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("bị trùng lặp");
    }
}
