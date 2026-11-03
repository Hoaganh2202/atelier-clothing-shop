package com.shop.backend.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.backend.address.UserAddress;
import com.shop.backend.address.UserAddressRepository;
import com.shop.backend.product.Category;
import com.shop.backend.product.CategoryRepository;
import com.shop.backend.common.model.DataSourceType;
import com.shop.backend.inventory.InventoryTransactionRepository;
import com.shop.backend.order.Order;
import com.shop.backend.order.OrderDetailRepository;
import com.shop.backend.order.OrderRepository;
import com.shop.backend.order.dto.CheckoutItemRequest;
import com.shop.backend.order.dto.CheckoutRequest;
import com.shop.backend.payment.PaymentMethod;
import com.shop.backend.payment.PaymentRepository;
import com.shop.backend.product.Product;
import com.shop.backend.product.ProductRepository;
import com.shop.backend.product.ProductVariant;
import com.shop.backend.product.ProductVariantRepository;
import com.shop.backend.security.JwtService;
import com.shop.backend.user.Role;
import com.shop.backend.user.RoleRepository;
import com.shop.backend.user.User;
import com.shop.backend.user.UserRepository;
import com.shop.backend.user.UserStatus;
import com.shop.backend.common.model.DiscountType;
import com.shop.backend.voucher.Voucher;
import com.shop.backend.voucher.VoucherRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class StoreSettingsOperationalIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StoreSettingsRepository storeSettingsRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserAddressRepository addressRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderDetailRepository orderDetailRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private InventoryTransactionRepository inventoryTransactionRepository;

    @Autowired
    private VoucherRepository voucherRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private User adminUser;
    private User customerUser;
    private String adminToken;
    private String customerToken;

    private Category testCategory;
    private Product testProduct;
    private ProductVariant variantA;
    private ProductVariant variantB;
    private ProductVariant variantBoundary;
    private UserAddress testAddress;

    @BeforeEach
    void setUp() {
        Role adminRole = roleRepository.findByName("admin")
                .orElseGet(() -> roleRepository.save(new Role(null, "admin")));
        Role customerRole = roleRepository.findByName("customer")
                .orElseGet(() -> roleRepository.save(new Role(null, "customer")));

        String runId = UUID.randomUUID().toString().substring(0, 8);
        adminUser = userRepository.save(User.builder()
                .name("Admin Op Test")
                .email("admin_op_" + runId + "@shop.com")
                .phone("090" + runId.replaceAll("[^0-9]", "4").substring(0, 7))
                .passwordHash(passwordEncoder.encode("Password123!"))
                .role(adminRole)
                .status(UserStatus.ACTIVE)
                .dataSource(DataSourceType.WEBSITE)
                .build());

        customerUser = userRepository.save(User.builder()
                .name("Customer Op Test")
                .email("customer_op_" + runId + "@shop.com")
                .phone("091" + runId.replaceAll("[^0-9]", "5").substring(0, 7))
                .passwordHash(passwordEncoder.encode("Password123!"))
                .role(customerRole)
                .status(UserStatus.ACTIVE)
                .dataSource(DataSourceType.WEBSITE)
                .build());

        adminToken = "Bearer " + jwtService.generateToken(adminUser);
        customerToken = "Bearer " + jwtService.generateToken(customerUser);

        testAddress = addressRepository.save(UserAddress.builder()
                .user(customerUser)
                .receiverName("Người Nhận Test")
                .phone("0918888888")
                .province("Hà Nội")
                .district("Cầu Giấy")
                .ward("Dịch Vọng")
                .address("123 Xuân Thủy")
                .isDefault(true)
                .build());

        testCategory = categoryRepository.save(Category.builder()
                .name("Cat Test " + runId)
                .slug("cat-test-" + runId)
                .isActive(true)
                .build());

        testProduct = productRepository.save(Product.builder()
                .name("Áo Thun Op Test " + runId)
                .slug("ao-thun-op-" + runId)
                .category(testCategory)
                .basePrice(new BigDecimal("200000.00"))
                .isActive(true)
                .build());

        variantA = productVariantRepository.save(ProductVariant.builder()
                .product(testProduct)
                .sku("SKU-OPA-" + runId)
                .color("Đen")
                .size("M")
                .price(new BigDecimal("200000.00"))
                .stock(5) // Tồn 5
                .build());

        variantB = productVariantRepository.save(ProductVariant.builder()
                .product(testProduct)
                .sku("SKU-OPB-" + runId)
                .color("Trắng")
                .size("L")
                .price(new BigDecimal("200000.00"))
                .stock(20) // Tồn 20
                .build());
    }

    @AfterEach
    void tearDown() {
        // Restore default settings to prevent polluting other integration tests
        storeSettingsRepository.findById(1L).ifPresent(s -> {
            s.setLowStockThreshold(10);
            s.setDefaultShippingFee(new BigDecimal("30000.00"));
            s.setFreeShippingThreshold(new BigDecimal("500000.00"));
            s.setUpdatedBy("system");
            storeSettingsRepository.save(s);
        });

        inventoryTransactionRepository.deleteAll();
        paymentRepository.deleteAll();
        orderDetailRepository.deleteAll();
        orderRepository.deleteAll();
        voucherRepository.deleteAll();

        if (testAddress != null && testAddress.getId() != null) {
            addressRepository.deleteById(testAddress.getId());
        }

        if (variantA != null && variantA.getId() != null) productVariantRepository.deleteById(variantA.getId());
        if (variantB != null && variantB.getId() != null) productVariantRepository.deleteById(variantB.getId());
        if (variantBoundary != null && variantBoundary.getId() != null) productVariantRepository.deleteById(variantBoundary.getId());
        if (testProduct != null && testProduct.getId() != null) productRepository.deleteById(testProduct.getId());
        if (testCategory != null && testCategory.getId() != null) categoryRepository.deleteById(testCategory.getId());

        if (customerUser != null && customerUser.getId() != null) userRepository.deleteById(customerUser.getId());
        if (adminUser != null && adminUser.getId() != null) userRepository.deleteById(adminUser.getId());
    }

    @Test
    @DisplayName("B4: GET /api/settings/shipping là public endpoint, chỉ trả đúng 2 trường")
    void getShippingPolicy_publicEndpoint() throws Exception {
        // Gọi không có header Authorization -> 200 OK
        mockMvc.perform(get("/api/settings/shipping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shippingFee").value(30000))
                .andExpect(jsonPath("$.freeShippingThreshold").value(500000))
                .andExpect(jsonPath("$.taxCode").doesNotExist())
                .andExpect(jsonPath("$.storeName").doesNotExist())
                .andExpect(jsonPath("$.lowStockThreshold").doesNotExist());

        // Thay đổi cài đặt và gọi lại
        StoreSettings settings = storeSettingsRepository.findById(1L).orElseThrow();
        settings.setDefaultShippingFee(new BigDecimal("45000.00"));
        settings.setFreeShippingThreshold(new BigDecimal("700000.00"));
        storeSettingsRepository.save(settings);

        mockMvc.perform(get("/api/settings/shipping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shippingFee").value(45000))
                .andExpect(jsonPath("$.freeShippingThreshold").value(700000));
    }

    @Test
    @DisplayName("B2: Nối ngưỡng tồn kho thấp - không truyền param lấy theo settings, có truyền param thì ghi đè")
    void lowStockThreshold_integration() throws Exception {
        // Cài đặt ngưỡng = 3 -> variantA (tồn 5) không bị coi là low stock
        StoreSettings settings = storeSettingsRepository.findById(1L).orElseThrow();
        settings.setLowStockThreshold(3);
        storeSettingsRepository.save(settings);

        // Gọi inventory variants không truyền threshold và lọc LOW_STOCK
        mockMvc.perform(get("/api/admin/inventory/variants")
                        .param("stockStatus", "LOW_STOCK")
                        .param("keyword", variantA.getSku())
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        // Cài đặt ngưỡng = 8 -> variantA (tồn 5) bị coi là low stock
        settings.setLowStockThreshold(8);
        storeSettingsRepository.save(settings);

        mockMvc.perform(get("/api/admin/inventory/variants")
                        .param("stockStatus", "LOW_STOCK")
                        .param("keyword", variantA.getSku())
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        // Có truyền param threshold = 2 (ghi đè cài đặt 8) -> variantA (tồn 5) không còn là low stock
        mockMvc.perform(get("/api/admin/inventory/variants")
                        .param("stockStatus", "LOW_STOCK")
                        .param("threshold", "2")
                        .param("keyword", variantA.getSku())
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("B3: Nối phí ship và ngưỡng miễn phí ship vào Checkout")
    void checkout_shippingFeeAndFreeThreshold() throws Exception {
        // Đặt phí ship mặc định = 40.000, ngưỡng miễn phí = 600.000
        StoreSettings settings = storeSettingsRepository.findById(1L).orElseThrow();
        settings.setDefaultShippingFee(new BigDecimal("40000.00"));
        settings.setFreeShippingThreshold(new BigDecimal("600000.00"));
        storeSettingsRepository.save(settings);

        // 1. Đơn dưới ngưỡng: 1 áo 200.000đ (< 600.000đ) -> phí ship 40.000đ, tổng tiền 240.000đ
        CheckoutRequest reqUnder = CheckoutRequest.builder()
                .addressId(testAddress.getId())
                .paymentMethod(PaymentMethod.COD)
                .items(List.of(new CheckoutItemRequest(variantB.getId(), 1)))
                .build();

        String resUnderStr = mockMvc.perform(post("/api/orders/checkout")
                        .header("Authorization", customerToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqUnder)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subtotal").value(200000))
                .andExpect(jsonPath("$.shippingFee").value(40000))
                .andExpect(jsonPath("$.totalAmount").value(240000))
                .andReturn().getResponse().getContentAsString();

        Long orderId1 = objectMapper.readTree(resUnderStr).get("orderId").asLong();

        // 2. Đơn đúng bằng ngưỡng: 3 áo 200.000đ = 600.000đ (>= 600.000đ) -> phí ship 0đ, tổng tiền 600.000đ
        CheckoutRequest reqEqual = CheckoutRequest.builder()
                .addressId(testAddress.getId())
                .paymentMethod(PaymentMethod.COD)
                .items(List.of(new CheckoutItemRequest(variantB.getId(), 3)))
                .build();

        mockMvc.perform(post("/api/orders/checkout")
                        .header("Authorization", customerToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqEqual)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subtotal").value(600000))
                .andExpect(jsonPath("$.shippingFee").value(0))
                .andExpect(jsonPath("$.totalAmount").value(600000));

        // 3. Snapshot: Đổi cấu hình phí ship thành 85.000đ -> đơn cũ (orderId1) vẫn giữ nguyên shippingFee 40.000đ
        settings.setDefaultShippingFee(new BigDecimal("85000.00"));
        storeSettingsRepository.save(settings);

        Order persistedOrder1 = orderRepository.findById(orderId1).orElseThrow();
        assertThat(persistedOrder1.getShippingFee()).isEqualByComparingTo(new BigDecimal("40000.00"));
        assertThat(persistedOrder1.getTotalAmount()).isEqualByComparingTo(new BigDecimal("240000.00"));
    }

    @Test
    @DisplayName("P6.1: Dashboard Summary phản ánh ngưỡng tồn kho thấp từ cấu hình hệ thống")
    void dashboardSummaryReflectsLowStockThresholdSetting() throws Exception {
        // Biến thể variantA có tồn = 5
        // 1. Cài đặt ngưỡng = 3 -> variantA (tồn 5) không bị tính là low stock
        StoreSettings settings = storeSettingsRepository.findById(1L).orElseThrow();
        settings.setLowStockThreshold(3);
        storeSettingsRepository.save(settings);

        String resThreshold3 = mockMvc.perform(get("/api/admin/dashboard/summary")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long lowStockCountAtThreshold3 = objectMapper.readTree(resThreshold3).get("lowStockCount").asLong();

        // 2. Cài đặt ngưỡng = 8 -> variantA (tồn 5) được tính là low stock, số lượng tăng đúng 1
        settings.setLowStockThreshold(8);
        storeSettingsRepository.save(settings);

        String resThreshold8 = mockMvc.perform(get("/api/admin/dashboard/summary")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long lowStockCountAtThreshold8 = objectMapper.readTree(resThreshold8).get("lowStockCount").asLong();

        assertThat(lowStockCountAtThreshold8).isEqualTo(lowStockCountAtThreshold3 + 1);
    }

    @Test
    @DisplayName("P6.2: Ca biên miễn phí ship: 599.999 có ship, 600.000 miễn phí")
    void checkoutFreeShippingBoundaryScenarios() throws Exception {
        StoreSettings settings = storeSettingsRepository.findById(1L).orElseThrow();
        settings.setDefaultShippingFee(new BigDecimal("40000.00"));
        settings.setFreeShippingThreshold(new BigDecimal("600000.00"));
        storeSettingsRepository.save(settings);

        // Tạo biến thể có giá 599.999đ
        variantBoundary = productVariantRepository.save(ProductVariant.builder()
                .product(testProduct)
                .sku("SKU-BOUND-" + UUID.randomUUID().toString().substring(0, 6))
                .color("Đỏ")
                .size("XL")
                .price(new BigDecimal("599999.00"))
                .stock(50)
                .build());

        // A. Đơn đúng 599.999đ (< 600.000đ) -> Tính phí ship 40.000đ, tổng 639.999đ
        CheckoutRequest reqUnder = CheckoutRequest.builder()
                .addressId(testAddress.getId())
                .paymentMethod(PaymentMethod.COD)
                .items(List.of(new CheckoutItemRequest(variantBoundary.getId(), 1)))
                .build();

        String resUnder = mockMvc.perform(post("/api/orders/checkout")
                        .header("Authorization", customerToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqUnder)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subtotal").value(599999))
                .andExpect(jsonPath("$.shippingFee").value(40000))
                .andExpect(jsonPath("$.totalAmount").value(639999))
                .andReturn().getResponse().getContentAsString();

        Long orderUnderId = objectMapper.readTree(resUnder).get("orderId").asLong();
        Order orderUnderDb = orderRepository.findById(orderUnderId).orElseThrow();
        assertThat(orderUnderDb.getShippingFee()).isEqualByComparingTo(new BigDecimal("40000.00"));
        assertThat(orderUnderDb.getTotalAmount()).isEqualByComparingTo(new BigDecimal("639999.00"));

        // B. Đơn đúng 600.000đ (>= 600.000đ) -> Miễn phí ship (0đ), tổng 600.000đ
        CheckoutRequest reqExact = CheckoutRequest.builder()
                .addressId(testAddress.getId())
                .paymentMethod(PaymentMethod.COD)
                .items(List.of(new CheckoutItemRequest(variantB.getId(), 3))) // 3 * 200.000 = 600.000
                .build();

        String resExact = mockMvc.perform(post("/api/orders/checkout")
                        .header("Authorization", customerToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqExact)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subtotal").value(600000))
                .andExpect(jsonPath("$.shippingFee").value(0))
                .andExpect(jsonPath("$.totalAmount").value(600000))
                .andReturn().getResponse().getContentAsString();

        Long orderExactId = objectMapper.readTree(resExact).get("orderId").asLong();
        Order orderExactDb = orderRepository.findById(orderExactId).orElseThrow();
        assertThat(orderExactDb.getShippingFee()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(orderExactDb.getTotalAmount()).isEqualByComparingTo(new BigDecimal("600000.00"));
    }

    @Test
    @DisplayName("P6.3: Voucher giảm giá: Tạm tính >= ngưỡng freeship, sau giảm < ngưỡng -> Vẫn miễn phí ship")
    void checkoutWithVoucherKeepsFreeShippingWhenSubtotalMeetsThreshold() throws Exception {
        StoreSettings settings = storeSettingsRepository.findById(1L).orElseThrow();
        settings.setDefaultShippingFee(new BigDecimal("40000.00"));
        settings.setFreeShippingThreshold(new BigDecimal("600000.00"));
        storeSettingsRepository.save(settings);

        // Tạo voucher giảm 100.000đ cho đơn từ 500.000đ
        Voucher voucher = voucherRepository.save(Voucher.builder()
                .code("VOUCHER-OP-" + UUID.randomUUID().toString().substring(0, 6))
                .discountType(DiscountType.AMOUNT)
                .discountValue(new BigDecimal("100000.00"))
                .minOrder(new BigDecimal("500000.00"))
                .usageLimit(100)
                .perUserLimit(10)
                .startAt(LocalDateTime.now().minusDays(1))
                .endAt(LocalDateTime.now().plusDays(2))
                .isActive(true)
                .build());

        // 3 áo 200.000 = tạm tính 600.000đ (>= ngưỡng 600k)
        // Áp voucher giảm 100k -> tổng sau giảm = 500.000đ (< ngưỡng 600k)
        // Phí ship vẫn phải = 0đ, tổng tiền = 500.000đ
        CheckoutRequest req = CheckoutRequest.builder()
                .addressId(testAddress.getId())
                .paymentMethod(PaymentMethod.COD)
                .voucherCode(voucher.getCode())
                .items(List.of(new CheckoutItemRequest(variantB.getId(), 3)))
                .build();

        String res = mockMvc.perform(post("/api/orders/checkout")
                        .header("Authorization", customerToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subtotal").value(600000))
                .andExpect(jsonPath("$.discountAmount").value(100000))
                .andExpect(jsonPath("$.shippingFee").value(0))
                .andExpect(jsonPath("$.totalAmount").value(500000))
                .andReturn().getResponse().getContentAsString();

        Long orderId = objectMapper.readTree(res).get("orderId").asLong();
        Order persistedOrder = orderRepository.findById(orderId).orElseThrow();
        assertThat(persistedOrder.getSubtotal()).isEqualByComparingTo(new BigDecimal("600000.00"));
        assertThat(persistedOrder.getDiscountAmount()).isEqualByComparingTo(new BigDecimal("100000.00"));
        assertThat(persistedOrder.getShippingFee()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(persistedOrder.getTotalAmount()).isEqualByComparingTo(new BigDecimal("500000.00"));
    }
}
