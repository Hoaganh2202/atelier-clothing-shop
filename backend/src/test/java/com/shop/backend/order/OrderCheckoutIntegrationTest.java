package com.shop.backend.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.backend.address.UserAddress;
import com.shop.backend.address.UserAddressRepository;
import com.shop.backend.common.model.DataSourceType;
import com.shop.backend.common.model.DiscountType;
import com.shop.backend.inventory.InventoryMovementType;
import com.shop.backend.inventory.InventoryStockService;
import com.shop.backend.inventory.InventoryTransaction;
import com.shop.backend.inventory.InventoryTransactionRepository;
import com.shop.backend.order.dto.CheckoutItemRequest;
import com.shop.backend.order.dto.CheckoutRequest;
import com.shop.backend.payment.Payment;
import com.shop.backend.payment.PaymentMethod;
import com.shop.backend.payment.PaymentRepository;
import com.shop.backend.product.Category;
import com.shop.backend.product.CategoryRepository;
import com.shop.backend.product.Product;
import com.shop.backend.product.ProductRepository;
import com.shop.backend.product.ProductVariant;
import com.shop.backend.product.ProductVariantRepository;
import com.shop.backend.security.UserPrincipal;
import com.shop.backend.user.Role;
import com.shop.backend.user.RoleRepository;
import com.shop.backend.user.User;
import com.shop.backend.user.UserRepository;
import com.shop.backend.user.UserStatus;
import com.shop.backend.voucher.Voucher;
import com.shop.backend.voucher.VoucherRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class OrderCheckoutIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderDetailRepository orderDetailRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private ProductVariantRepository variantRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserAddressRepository addressRepository;

    @Autowired
    private VoucherRepository voucherRepository;

    @Autowired
    private InventoryTransactionRepository inventoryTransactionRepository;

    @SpyBean
    private InventoryStockService inventoryStockService;

    private User customerA;
    private User customerB;
    private User adminUser;
    private UserAddress addressA;
    private UserAddress addressB;
    private Product activeProduct;
    private Product inactiveProduct;
    private ProductVariant variant1;
    private ProductVariant variant2;
    private ProductVariant singleStockVariant;

    @BeforeEach
    void setUp() {
        Role customerRole = roleRepository.findByName("customer")
                .orElseGet(() -> roleRepository.save(new Role(null, "customer")));
        Role adminRole = roleRepository.findByName("admin")
                .orElseGet(() -> roleRepository.save(new Role(null, "admin")));

        customerA = userRepository.save(User.builder()
                .role(customerRole)
                .name("Khách Hàng A")
                .email("customer.a." + System.nanoTime() + "@example.com")
                .phone("091" + String.format("%07d", Math.abs(System.nanoTime() % 10000000L)))
                .passwordHash("hashed")
                .status(UserStatus.ACTIVE)
                .dataSource(DataSourceType.WEBSITE)
                .build());

        customerB = userRepository.save(User.builder()
                .role(customerRole)
                .name("Khách Hàng B")
                .email("customer.b." + System.nanoTime() + "@example.com")
                .phone("092" + String.format("%07d", Math.abs(System.nanoTime() % 10000000L)))
                .passwordHash("hashed")
                .status(UserStatus.ACTIVE)
                .dataSource(DataSourceType.WEBSITE)
                .build());

        adminUser = userRepository.save(User.builder()
                .role(adminRole)
                .name("Admin Quản Trị")
                .email("admin.checkout." + System.nanoTime() + "@example.com")
                .phone("093" + String.format("%07d", Math.abs(System.nanoTime() % 10000000L)))
                .passwordHash("hashed")
                .status(UserStatus.ACTIVE)
                .dataSource(DataSourceType.WEBSITE)
                .build());

        addressA = addressRepository.save(UserAddress.builder()
                .user(customerA)
                .receiverName("Nguyễn Văn A")
                .phone("0912345678")
                .province("Hồ Chí Minh")
                .district("Quận 1")
                .ward("Bến Nghé")
                .address("123 Lê Lợi")
                .isDefault(true)
                .build());

        addressB = addressRepository.save(UserAddress.builder()
                .user(customerB)
                .receiverName("Trần Văn B")
                .phone("0923456789")
                .province("Hà Nội")
                .district("Hoàn Kiếm")
                .ward("Hàng Bạc")
                .address("456 Hàng Bông")
                .isDefault(true)
                .build());

        Category category = categoryRepository.save(Category.builder()
                .name("Thời Trang Nam Test " + System.nanoTime())
                .slug("thoi-trang-nam-test-" + System.nanoTime())
                .isActive(true)
                .build());

        activeProduct = productRepository.save(Product.builder()
                .category(category)
                .name("Áo Thun Cotton Atelier")
                .slug("ao-thun-cotton-atelier-" + System.nanoTime())
                .basePrice(new BigDecimal("200000.00"))
                .isActive(true)
                .dataSource(DataSourceType.WEBSITE)
                .build());

        inactiveProduct = productRepository.save(Product.builder()
                .category(category)
                .name("Áo Khoác Cũ Inactive")
                .slug("ao-khoac-cu-inactive-" + System.nanoTime())
                .basePrice(new BigDecimal("500000.00"))
                .isActive(false)
                .dataSource(DataSourceType.WEBSITE)
                .build());

        variant1 = variantRepository.save(ProductVariant.builder()
                .product(activeProduct)
                .sku("TSHIRT-BLACK-M-" + System.nanoTime())
                .size("M")
                .color("Đen")
                .stock(20)
                .price(new BigDecimal("220000.00"))
                .build());

        variant2 = variantRepository.save(ProductVariant.builder()
                .product(activeProduct)
                .sku("TSHIRT-WHITE-L-" + System.nanoTime())
                .size("L")
                .color("Trắng")
                .stock(10)
                .price(new BigDecimal("250000.00"))
                .build());

        singleStockVariant = variantRepository.save(ProductVariant.builder()
                .product(activeProduct)
                .sku("TSHIRT-LIMITED-" + System.nanoTime())
                .size("XL")
                .color("Đỏ")
                .stock(1)
                .price(new BigDecimal("300000.00"))
                .build());
    }

    @AfterEach
    void tearDown() {
        inventoryTransactionRepository.deleteAll();
        paymentRepository.deleteAll();
        orderDetailRepository.deleteAll();
        orderRepository.deleteAll();
        addressRepository.deleteAll();
        variantRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();
    }

    private UserPrincipal toPrincipal(User user) {
        User userWithRole = userRepository.findWithRoleById(user.getId()).orElseThrow();
        return new UserPrincipal(userWithRole);
    }

    @Test
    @DisplayName("1. Checkout hợp lệ: tạo đúng Order, OrderDetail, Payment và trừ kho nguyên tử")
    void testValidCheckout_Success() throws Exception {
        CheckoutRequest request = CheckoutRequest.builder()
                .addressId(addressA.getId())
                .paymentMethod(PaymentMethod.COD)
                .note("Giao giờ hành chính")
                .items(List.of(
                        CheckoutItemRequest.builder().variantId(variant1.getId()).quantity(2).build()
                ))
                .build();

        String body = objectMapper.writeValueAsString(request);

        mockMvc.perform(post("/api/orders/checkout")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").isNumber())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.subtotal").value(440000L)) // 2 * 220.000 = 440.000
                .andExpect(jsonPath("$.shippingFee").value(30000L)) // < 500k -> 30k
                .andExpect(jsonPath("$.discountAmount").value(0L))
                .andExpect(jsonPath("$.totalAmount").value(470000L)) // 440k + 30k = 470k
                .andExpect(jsonPath("$.paymentMethod").value("COD"))
                .andExpect(jsonPath("$.paymentStatus").value("PENDING"))
                .andExpect(jsonPath("$.receiverName").value("Nguyễn Văn A"))
                .andExpect(jsonPath("$.receiverPhone").value("0912345678"))
                .andExpect(jsonPath("$.addressLine").value("123 Lê Lợi"))
                .andExpect(jsonPath("$.items", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.items[0].productName").value("Áo Thun Cotton Atelier"))
                .andExpect(jsonPath("$.items[0].variantSize").value("M"))
                .andExpect(jsonPath("$.items[0].variantColor").value("Đen"))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].unitPrice").value(220000L));

        ProductVariant updatedVariant = variantRepository.findById(variant1.getId()).orElseThrow();
        assertThat(updatedVariant.getStock()).isEqualTo(18); // 20 - 2 = 18

        List<Payment> payments = paymentRepository.findAll();
        assertThat(payments).isNotEmpty();
    }

    @Test
    @DisplayName("2. Client gửi giá giả hoặc tổng tiền giả: Server bỏ qua và tính đúng giá")
    void testFakePriceInjected_IgnoredByServer() throws Exception {
        String fakePayload = "{"
                + "\"addressId\":" + addressA.getId() + ","
                + "\"paymentMethod\":\"COD\","
                + "\"subtotal\":1000,"
                + "\"totalAmount\":500,"
                + "\"shippingFee\":0,"
                + "\"items\":[{\"variantId\":" + variant1.getId() + ",\"quantity\":1,\"unitPrice\":100}]"
                + "}";

        mockMvc.perform(post("/api/orders/checkout")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fakePayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subtotal").value(220000L)) // Không phải 1000
                .andExpect(jsonPath("$.shippingFee").value(30000L)) // Không phải 0
                .andExpect(jsonPath("$.totalAmount").value(250000L)); // Không phải 500
    }

    @Test
    @DisplayName("3. Validation lỗi: Sản phẩm inactive, variant không tồn tại, quantity <= 0, danh sách rỗng")
    void testValidationErrors() throws Exception {
        ProductVariant inactiveVariant = variantRepository.save(ProductVariant.builder()
                .product(inactiveProduct)
                .sku("INACTIVE-VAR-" + System.nanoTime())
                .size("M")
                .color("Đen")
                .stock(10)
                .build());

        // 3.1 Sản phẩm inactive
        CheckoutRequest reqInactive = CheckoutRequest.builder()
                .addressId(addressA.getId())
                .paymentMethod(PaymentMethod.COD)
                .items(List.of(CheckoutItemRequest.builder().variantId(inactiveVariant.getId()).quantity(1).build()))
                .build();
        mockMvc.perform(post("/api/orders/checkout")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqInactive)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("ngừng kinh doanh")));

        // 3.2 Variant không tồn tại
        CheckoutRequest reqNonExistent = CheckoutRequest.builder()
                .addressId(addressA.getId())
                .paymentMethod(PaymentMethod.COD)
                .items(List.of(CheckoutItemRequest.builder().variantId(9999999L).quantity(1).build()))
                .build();
        mockMvc.perform(post("/api/orders/checkout")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqNonExistent)))
                .andExpect(status().isNotFound());

        // 3.3 Quantity = 0
        CheckoutRequest reqZeroQty = CheckoutRequest.builder()
                .addressId(addressA.getId())
                .paymentMethod(PaymentMethod.COD)
                .items(List.of(CheckoutItemRequest.builder().variantId(variant1.getId()).quantity(0).build()))
                .build();
        mockMvc.perform(post("/api/orders/checkout")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqZeroQty)))
                .andExpect(status().isBadRequest());

        // 3.4 Items rỗng
        CheckoutRequest reqEmpty = CheckoutRequest.builder()
                .addressId(addressA.getId())
                .paymentMethod(PaymentMethod.COD)
                .items(List.of())
                .build();
        mockMvc.perform(post("/api/orders/checkout")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqEmpty)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("4. Không đủ tồn kho: Ném OutOfStockException và rollback hoàn toàn")
    void testInsufficientStock_Rollback() throws Exception {
        int initialStock = singleStockVariant.getStock(); // 1
        long initialOrderCount = orderRepository.count();

        CheckoutRequest request = CheckoutRequest.builder()
                .addressId(addressA.getId())
                .paymentMethod(PaymentMethod.COD)
                .items(List.of(
                        CheckoutItemRequest.builder().variantId(singleStockVariant.getId()).quantity(2).build()
                ))
                .build();

        mockMvc.perform(post("/api/orders/checkout")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("không đủ số lượng trong kho")));

        ProductVariant variantAfter = variantRepository.findById(singleStockVariant.getId()).orElseThrow();
        assertThat(variantAfter.getStock()).isEqualTo(initialStock);
        assertThat(orderRepository.count()).isEqualTo(initialOrderCount);
    }

    @Test
    @DisplayName("5. Nhiều mặt hàng, mặt hàng sau bị hết kho: Rollback toàn bộ, không mất kho mặt hàng trước")
    void testMultipleItems_LastOutOfStock_RollbackFirstItemStock() throws Exception {
        int initialStock1 = variant1.getStock(); // 20
        int initialStockSingle = singleStockVariant.getStock(); // 1

        CheckoutRequest request = CheckoutRequest.builder()
                .addressId(addressA.getId())
                .paymentMethod(PaymentMethod.COD)
                .items(List.of(
                        CheckoutItemRequest.builder().variantId(variant1.getId()).quantity(5).build(),
                        CheckoutItemRequest.builder().variantId(singleStockVariant.getId()).quantity(10).build() // Hết hàng
                ))
                .build();

        mockMvc.perform(post("/api/orders/checkout")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("không đủ số lượng trong kho")));

        ProductVariant v1After = variantRepository.findById(variant1.getId()).orElseThrow();
        ProductVariant singleAfter = variantRepository.findById(singleStockVariant.getId()).orElseThrow();

        assertThat(v1After.getStock()).isEqualTo(initialStock1); // Không bị trừ 5, vẫn là 20
        assertThat(singleAfter.getStock()).isEqualTo(initialStockSingle); // Vẫn nguyên 1
        assertThat(inventoryTransactionRepository.count()).isZero();
    }

    @Test
    @DisplayName("6. Concurrency: Hai request cạnh tranh mua biến thể chỉ còn 1 sản phẩm - đúng 1 request thành công, không oversell")
    void testConcurrentCheckout_NoOversell() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger outOfStockCount = new AtomicInteger(0);

        Runnable buyTask = () -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                CheckoutRequest req = CheckoutRequest.builder()
                        .addressId(addressA.getId())
                        .paymentMethod(PaymentMethod.COD)
                        .items(List.of(CheckoutItemRequest.builder().variantId(singleStockVariant.getId()).quantity(1).build()))
                        .build();

                MvcResult res = mockMvc.perform(post("/api/orders/checkout")
                                .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA)))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                        .andReturn();

                int code = res.getResponse().getStatus();
                if (code == 201) {
                    successCount.incrementAndGet();
                } else if (code == 400) {
                    outOfStockCount.incrementAndGet();
                }
            } catch (Exception e) {
                outOfStockCount.incrementAndGet();
            }
        };

        executor.submit(buyTask);
        executor.submit(buyTask);

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(outOfStockCount.get()).isEqualTo(1);

        ProductVariant finalVariant = variantRepository.findById(singleStockVariant.getId()).orElseThrow();
        assertThat(finalVariant.getStock()).isEqualTo(0); // Không bị âm (-1)
    }

    @Test
    @DisplayName("7. Gộp các item trùng variantId trong cùng request và sắp xếp giảm nguy cơ Deadlock")
    void testConsolidateDuplicateVariantsInRequest() throws Exception {
        int initialStock = variant1.getStock(); // 20

        CheckoutRequest request = CheckoutRequest.builder()
                .addressId(addressA.getId())
                .paymentMethod(PaymentMethod.COD)
                .items(List.of(
                        CheckoutItemRequest.builder().variantId(variant1.getId()).quantity(2).build(),
                        CheckoutItemRequest.builder().variantId(variant1.getId()).quantity(3).build()
                ))
                .build();

        mockMvc.perform(post("/api/orders/checkout")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.items[0].quantity").value(5))
                .andExpect(jsonPath("$.subtotal").value(1100000L)); // 5 * 220.000 = 1.100.000

        ProductVariant v1After = variantRepository.findById(variant1.getId()).orElseThrow();
        assertThat(v1After.getStock()).isEqualTo(initialStock - 5);
    }

    @Test
    @DisplayName("8. Địa chỉ thuộc user khác: Bị từ chối 400 Bad Request")
    void testAddressBelongsToAnotherUser_Rejected() throws Exception {
        CheckoutRequest request = CheckoutRequest.builder()
                .addressId(addressB.getId()) // Customer A dùng address của Customer B
                .paymentMethod(PaymentMethod.COD)
                .items(List.of(CheckoutItemRequest.builder().variantId(variant1.getId()).quantity(1).build()))
                .build();

        mockMvc.perform(post("/api/orders/checkout")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("không thuộc tài khoản")));
    }

    @Test
    @DisplayName("9. Ngưỡng miễn phí vận chuyển: Dưới 500k tính 30k, từ 500k trở lên tính 0đ")
    void testShippingThresholds() throws Exception {
        // 9.1 Dưới 500k (2 * 220.000 = 440.000đ) -> Phí ship 30.000đ
        CheckoutRequest reqUnder = CheckoutRequest.builder()
                .addressId(addressA.getId())
                .paymentMethod(PaymentMethod.COD)
                .items(List.of(CheckoutItemRequest.builder().variantId(variant1.getId()).quantity(2).build()))
                .build();

        mockMvc.perform(post("/api/orders/checkout")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqUnder)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subtotal").value(440000L))
                .andExpect(jsonPath("$.shippingFee").value(30000L))
                .andExpect(jsonPath("$.totalAmount").value(470000L));

        // 9.2 Đúng 500k (2 * 250.000 = 500.000đ) -> Phí ship 0đ
        CheckoutRequest reqExact = CheckoutRequest.builder()
                .addressId(addressA.getId())
                .paymentMethod(PaymentMethod.COD)
                .items(List.of(CheckoutItemRequest.builder().variantId(variant2.getId()).quantity(2).build()))
                .build();

        mockMvc.perform(post("/api/orders/checkout")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqExact)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subtotal").value(500000L))
                .andExpect(jsonPath("$.shippingFee").value(0L))
                .andExpect(jsonPath("$.totalAmount").value(500000L));

        // 9.3 Trên 500k (3 * 250.000 = 750.000đ) -> Phí ship 0đ
        CheckoutRequest reqOver = CheckoutRequest.builder()
                .addressId(addressA.getId())
                .paymentMethod(PaymentMethod.COD)
                .items(List.of(CheckoutItemRequest.builder().variantId(variant2.getId()).quantity(3).build()))
                .build();

        mockMvc.perform(post("/api/orders/checkout")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqOver)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subtotal").value(750000L))
                .andExpect(jsonPath("$.shippingFee").value(0L))
                .andExpect(jsonPath("$.totalAmount").value(750000L));
    }

    @Test
    @DisplayName("10. Voucher: Áp dụng % và số tiền cố định, kiểm tra minOrder, hết hạn, usageLimit, perUserLimit")
    void testVoucherApplicationAndValidation() throws Exception {
        Voucher voucherPercent = voucherRepository.save(Voucher.builder()
                .code("SALE10-" + System.nanoTime())
                .discountType(DiscountType.PERCENT)
                .discountValue(new BigDecimal("10.00"))
                .minOrder(new BigDecimal("300000.00"))
                .maxDiscount(new BigDecimal("50000.00"))
                .usageLimit(10)
                .perUserLimit(1)
                .startAt(LocalDateTime.now().minusDays(1))
                .endAt(LocalDateTime.now().plusDays(2))
                .isActive(true)
                .build());

        // Subtotal = 2 * 250k = 500k -> 10% = 50k (đạt maxDiscount 50k), ship = 0 -> total = 450k
        CheckoutRequest reqValidVoucher = CheckoutRequest.builder()
                .addressId(addressA.getId())
                .paymentMethod(PaymentMethod.COD)
                .voucherCode(voucherPercent.getCode())
                .items(List.of(CheckoutItemRequest.builder().variantId(variant2.getId()).quantity(2).build()))
                .build();

        mockMvc.perform(post("/api/orders/checkout")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqValidVoucher)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subtotal").value(500000L))
                .andExpect(jsonPath("$.discountAmount").value(50000L))
                .andExpect(jsonPath("$.totalAmount").value(450000L));

        // Dùng lại voucher này cho customerA -> Bị chặn perUserLimit = 1
        mockMvc.perform(post("/api/orders/checkout")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqValidVoucher)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("hết lượt")));

        // Voucher hết hạn
        Voucher expiredVoucher = voucherRepository.save(Voucher.builder()
                .code("EXPIRED-" + System.nanoTime())
                .discountType(DiscountType.AMOUNT)
                .discountValue(new BigDecimal("20000.00"))
                .startAt(LocalDateTime.now().minusDays(10))
                .endAt(LocalDateTime.now().minusDays(1))
                .isActive(true)
                .build());

        CheckoutRequest reqExpired = CheckoutRequest.builder()
                .addressId(addressB.getId())
                .paymentMethod(PaymentMethod.COD)
                .voucherCode(expiredVoucher.getCode())
                .items(List.of(CheckoutItemRequest.builder().variantId(variant1.getId()).quantity(1).build()))
                .build();

        mockMvc.perform(post("/api/orders/checkout")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerB)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqExpired)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("hết hạn")));

        // Voucher không đạt minOrder
        Voucher highMinVoucher = voucherRepository.save(Voucher.builder()
                .code("MIN1M-" + System.nanoTime())
                .discountType(DiscountType.AMOUNT)
                .discountValue(new BigDecimal("100000.00"))
                .minOrder(new BigDecimal("1000000.00"))
                .startAt(LocalDateTime.now().minusDays(1))
                .endAt(LocalDateTime.now().plusDays(2))
                .isActive(true)
                .build());

        CheckoutRequest reqLowSubtotal = CheckoutRequest.builder()
                .addressId(addressB.getId())
                .paymentMethod(PaymentMethod.COD)
                .voucherCode(highMinVoucher.getCode())
                .items(List.of(CheckoutItemRequest.builder().variantId(variant1.getId()).quantity(1).build())) // subtotal 220k
                .build();

        mockMvc.perform(post("/api/orders/checkout")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerB)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqLowSubtotal)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("tối thiểu")));
    }

    @Test
    @DisplayName("11. Phương thức thanh toán không được hỗ trợ bị từ chối")
    void testUnsupportedPaymentMethod_Rejected() throws Exception {
        CheckoutRequest reqBank = CheckoutRequest.builder()
                .addressId(addressA.getId())
                .paymentMethod(PaymentMethod.BANK)
                .items(List.of(CheckoutItemRequest.builder().variantId(variant1.getId()).quantity(1).build()))
                .build();

        mockMvc.perform(post("/api/orders/checkout")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqBank)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("chỉ hỗ trợ COD")));

        CheckoutRequest reqVnpay = CheckoutRequest.builder()
                .addressId(addressA.getId())
                .paymentMethod(PaymentMethod.VNPAY)
                .items(List.of(CheckoutItemRequest.builder().variantId(variant1.getId()).quantity(1).build()))
                .build();

        mockMvc.perform(post("/api/orders/checkout")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqVnpay)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("chỉ hỗ trợ COD")));
    }

    @Test
    @DisplayName("12. Idempotency: Gửi lại cùng key trả kết quả cũ mà không trừ kho lần 2; key mới tạo đơn độc lập")
    void testIdempotencyKey_PreventDuplicateOrders() throws Exception {
        String testKey = "idemp-" + System.nanoTime();
        int initialStock = variant1.getStock(); // 20

        CheckoutRequest req1 = CheckoutRequest.builder()
                .addressId(addressA.getId())
                .paymentMethod(PaymentMethod.COD)
                .idempotencyKey(testKey)
                .items(List.of(CheckoutItemRequest.builder().variantId(variant1.getId()).quantity(2).build()))
                .build();

        MvcResult firstResult = mockMvc.perform(post("/api/orders/checkout")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isCreated())
                .andReturn();

        long firstOrderId = objectMapper.readTree(firstResult.getResponse().getContentAsString()).get("orderId").asLong();

        ProductVariant afterFirst = variantRepository.findById(variant1.getId()).orElseThrow();
        assertThat(afterFirst.getStock()).isEqualTo(initialStock - 2); // 18

        // Gửi lại cùng request và cùng idempotencyKey
        MvcResult secondResult = mockMvc.perform(post("/api/orders/checkout")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isCreated())
                .andReturn();

        long secondOrderId = objectMapper.readTree(secondResult.getResponse().getContentAsString()).get("orderId").asLong();
        assertThat(secondOrderId).isEqualTo(firstOrderId); // Trả lại đúng order cũ

        ProductVariant afterSecond = variantRepository.findById(variant1.getId()).orElseThrow();
        assertThat(afterSecond.getStock()).isEqualTo(initialStock - 2); // Vẫn là 18, KHÔNG bị trừ tiếp!

        // Gửi với key mới -> tạo order mới
        CheckoutRequest reqNewKey = CheckoutRequest.builder()
                .addressId(addressA.getId())
                .paymentMethod(PaymentMethod.COD)
                .idempotencyKey("new-key-" + System.nanoTime())
                .items(List.of(CheckoutItemRequest.builder().variantId(variant1.getId()).quantity(1).build()))
                .build();

        MvcResult thirdResult = mockMvc.perform(post("/api/orders/checkout")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqNewKey)))
                .andExpect(status().isCreated())
                .andReturn();

        long thirdOrderId = objectMapper.readTree(thirdResult.getResponse().getContentAsString()).get("orderId").asLong();
        assertThat(thirdOrderId).isNotEqualTo(firstOrderId);
    }

    @Test
    @DisplayName("13. Role Guard: Chỉ ROLE_CUSTOMER được phép checkout, ADMIN hoặc Unauthenticated bị từ chối")
    void testRoleAccessRestrictions() throws Exception {
        CheckoutRequest validRequest = CheckoutRequest.builder()
                .addressId(addressA.getId())
                .paymentMethod(PaymentMethod.COD)
                .items(List.of(CheckoutItemRequest.builder().variantId(variant1.getId()).quantity(1).build()))
                .build();

        // 13.1 Chưa đăng nhập -> 401 Unauthorized
        mockMvc.perform(post("/api/orders/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isUnauthorized());

        // 13.2 Admin đăng nhập nhưng không có role customer -> 403 Forbidden
        mockMvc.perform(post("/api/orders/checkout")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(adminUser)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("14. Concurrency Idempotency: Hai request cùng user và cùng key chạy song song - chỉ tạo 1 Order, cả 2 nhận cùng orderId, không lỗi 500/409")
    void testConcurrentIdempotentRequests_SameUserAndKey() throws Exception {
        String raceKey = "race-key-" + System.nanoTime();
        int initialStock = variant1.getStock(); // 20
        long initialOrderCount = orderRepository.count();

        CheckoutRequest request = CheckoutRequest.builder()
                .addressId(addressA.getId())
                .paymentMethod(PaymentMethod.COD)
                .idempotencyKey(raceKey)
                .items(List.of(CheckoutItemRequest.builder().variantId(variant1.getId()).quantity(2).build()))
                .build();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicLong orderId1 = new AtomicLong(0);
        AtomicLong orderId2 = new AtomicLong(0);

        Runnable task1 = () -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                MvcResult result = mockMvc.perform(post("/api/orders/checkout")
                                .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA)))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andReturn();
                if (result.getResponse().getStatus() == 201) {
                    successCount.incrementAndGet();
                    long id = objectMapper.readTree(result.getResponse().getContentAsString()).get("orderId").asLong();
                    orderId1.set(id);
                }
            } catch (Exception ignored) {
            }
        };

        Runnable task2 = () -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                MvcResult result = mockMvc.perform(post("/api/orders/checkout")
                                .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA)))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andReturn();
                if (result.getResponse().getStatus() == 201) {
                    successCount.incrementAndGet();
                    long id = objectMapper.readTree(result.getResponse().getContentAsString()).get("orderId").asLong();
                    orderId2.set(id);
                }
            } catch (Exception ignored) {
            }
        };

        executor.submit(task1);
        executor.submit(task2);

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        assertThat(successCount.get()).isEqualTo(2); // Cả hai thread đều nhận HTTP 201 Created
        assertThat(orderId1.get()).isPositive();
        assertThat(orderId2.get()).isPositive();
        assertThat(orderId1.get()).isEqualTo(orderId2.get()); // Cả 2 thread đều nhận cùng 1 orderId

        assertThat(orderRepository.count()).isEqualTo(initialOrderCount + 1); // Chỉ có ĐÚNG 1 order được tạo
        ProductVariant variantAfter = variantRepository.findById(variant1.getId()).orElseThrow();
        assertThat(variantAfter.getStock()).isEqualTo(initialStock - 2); // Chỉ bị trừ kho ĐÚNG 1 lần (20 - 2 = 18)
    }

    @Test
    @DisplayName("15. Idempotency: Cùng user và cùng key nhưng payload khác nhau - trả về đơn hàng cũ, không tạo đơn thứ hai")
    void testSameKeyDifferentPayload_ReturnsOriginalOrder_NoNewOrderCreated() throws Exception {
        String testKey = "diff-payload-" + System.nanoTime();
        int initialStock = variant1.getStock(); // 20

        CheckoutRequest req1 = CheckoutRequest.builder()
                .addressId(addressA.getId())
                .paymentMethod(PaymentMethod.COD)
                .idempotencyKey(testKey)
                .items(List.of(CheckoutItemRequest.builder().variantId(variant1.getId()).quantity(1).build()))
                .build();

        MvcResult res1 = mockMvc.perform(post("/api/orders/checkout")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isCreated())
                .andReturn();

        long firstId = objectMapper.readTree(res1.getResponse().getContentAsString()).get("orderId").asLong();

        // Gửi lại cùng key nhưng payload khác (mua 5 sản phẩm, note khác)
        CheckoutRequest req2 = CheckoutRequest.builder()
                .addressId(addressA.getId())
                .paymentMethod(PaymentMethod.COD)
                .idempotencyKey(testKey)
                .note("Ghi chú khác hoàn toàn")
                .items(List.of(CheckoutItemRequest.builder().variantId(variant1.getId()).quantity(5).build()))
                .build();

        MvcResult res2 = mockMvc.perform(post("/api/orders/checkout")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isCreated())
                .andReturn();

        long secondId = objectMapper.readTree(res2.getResponse().getContentAsString()).get("orderId").asLong();
        assertThat(secondId).isEqualTo(firstId); // Vẫn trả về order cũ

        ProductVariant after = variantRepository.findById(variant1.getId()).orElseThrow();
        assertThat(after.getStock()).isEqualTo(initialStock - 1); // Chỉ trừ 1 sản phẩm của req1, không trừ 5 của req2
    }

    @Test
    @DisplayName("16. Concurrency Voucher: Hai khách hàng đồng thời checkout bằng cùng Voucher chỉ còn 1 lượt sử dụng - đúng 1 người thành công, không vượt hạn mức")
    void testConcurrentVoucherCheckout_OnlyOneAllowedWhenLimitIsOne() throws Exception {
        Voucher limitedVoucher = voucherRepository.save(Voucher.builder()
                .code("RACEVOUCHER-" + System.nanoTime())
                .discountType(DiscountType.AMOUNT)
                .discountValue(new BigDecimal("30000.00"))
                .minOrder(new BigDecimal("100000.00"))
                .usageLimit(1) // Chỉ đúng 1 lượt sử dụng cho toàn hệ thống
                .perUserLimit(1)
                .startAt(LocalDateTime.now().minusDays(1))
                .endAt(LocalDateTime.now().plusDays(2))
                .isActive(true)
                .build());

        int initialStock1 = variant1.getStock(); // 20

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failedCount = new AtomicInteger(0);

        Runnable userATask = () -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                CheckoutRequest req = CheckoutRequest.builder()
                        .addressId(addressA.getId())
                        .paymentMethod(PaymentMethod.COD)
                        .voucherCode(limitedVoucher.getCode())
                        .items(List.of(CheckoutItemRequest.builder().variantId(variant1.getId()).quantity(1).build()))
                        .build();

                MvcResult res = mockMvc.perform(post("/api/orders/checkout")
                                .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA)))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                        .andReturn();

                if (res.getResponse().getStatus() == 201) {
                    successCount.incrementAndGet();
                } else if (res.getResponse().getStatus() == 400) {
                    failedCount.incrementAndGet();
                }
            } catch (Exception e) {
                failedCount.incrementAndGet();
            }
        };

        Runnable userBTask = () -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                CheckoutRequest req = CheckoutRequest.builder()
                        .addressId(addressB.getId())
                        .paymentMethod(PaymentMethod.COD)
                        .voucherCode(limitedVoucher.getCode())
                        .items(List.of(CheckoutItemRequest.builder().variantId(variant1.getId()).quantity(1).build()))
                        .build();

                MvcResult res = mockMvc.perform(post("/api/orders/checkout")
                                .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerB)))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                        .andReturn();

                if (res.getResponse().getStatus() == 201) {
                    successCount.incrementAndGet();
                } else if (res.getResponse().getStatus() == 400) {
                    failedCount.incrementAndGet();
                }
            } catch (Exception e) {
                failedCount.incrementAndGet();
            }
        };

        executor.submit(userATask);
        executor.submit(userBTask);

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        assertThat(successCount.get()).isEqualTo(1); // Đúng 1 người áp dụng thành công
        assertThat(failedCount.get()).isEqualTo(1); // Người kia bị từ chối 400
        assertThat(orderRepository.countActiveOrdersByVoucherId(limitedVoucher.getId())).isEqualTo(1); // Không vượt hạn mức 1

        ProductVariant after = variantRepository.findById(variant1.getId()).orElseThrow();
        assertThat(after.getStock()).isEqualTo(initialStock1 - 1); // Chỉ trừ 1 của người thắng, người thua rollback sạch
    }

    @Test
    @DisplayName("17. Deadlock Prevention: Hai request mua chéo tập biến thể cùng lúc không bị deadlock nhờ sắp xếp variantId tăng dần")
    void testDeadlockPrevention_CrossOrderVariants() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);

        // Task 1: Gửi order [variant1, variant2]
        Runnable task1 = () -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                CheckoutRequest req = CheckoutRequest.builder()
                        .addressId(addressA.getId())
                        .paymentMethod(PaymentMethod.COD)
                        .items(List.of(
                                CheckoutItemRequest.builder().variantId(variant1.getId()).quantity(1).build(),
                                CheckoutItemRequest.builder().variantId(variant2.getId()).quantity(1).build()
                        ))
                        .build();

                MvcResult res = mockMvc.perform(post("/api/orders/checkout")
                                .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA)))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                        .andReturn();
                if (res.getResponse().getStatus() == 201) {
                    successCount.incrementAndGet();
                }
            } catch (Exception ignored) {
            }
        };

        // Task 2: Gửi order ngược thứ tự [variant2, variant1]
        Runnable task2 = () -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                CheckoutRequest req = CheckoutRequest.builder()
                        .addressId(addressB.getId())
                        .paymentMethod(PaymentMethod.COD)
                        .items(List.of(
                                CheckoutItemRequest.builder().variantId(variant2.getId()).quantity(1).build(),
                                CheckoutItemRequest.builder().variantId(variant1.getId()).quantity(1).build()
                        ))
                        .build();

                MvcResult res = mockMvc.perform(post("/api/orders/checkout")
                                .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerB)))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                        .andReturn();
                if (res.getResponse().getStatus() == 201) {
                    successCount.incrementAndGet();
                }
            } catch (Exception ignored) {
            }
        };

        executor.submit(task1);
        executor.submit(task2);

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        assertThat(successCount.get()).isEqualTo(2); // Cả hai hoàn thành trơn tru, không có deadlock
    }

    @Test
    @DisplayName("B1-1. checkoutShouldWriteDeductionLogForEachOrderDetail: Checkout đơn 2 biến thể -> ghi đúng 2 bản ghi checkout_deduct khớp order_detail_id, stock_after, stock_before")
    void checkoutShouldWriteDeductionLogForEachOrderDetail() throws Exception {
        int initialStock1 = variant1.getStock();
        int initialStock2 = variant2.getStock();

        CheckoutRequest request = CheckoutRequest.builder()
                .addressId(addressA.getId())
                .paymentMethod(PaymentMethod.COD)
                .items(List.of(
                        CheckoutItemRequest.builder().variantId(variant1.getId()).quantity(2).build(),
                        CheckoutItemRequest.builder().variantId(variant2.getId()).quantity(3).build()
                ))
                .build();

        MvcResult result = mockMvc.perform(post("/api/orders/checkout")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
        Long orderId = jsonNode.get("orderId").asLong();

        List<InventoryTransaction> logs = inventoryTransactionRepository.findByOrderIdAndMovementType(
                orderId, InventoryMovementType.CHECKOUT_DEDUCT
        );
        assertThat(logs).hasSize(2);

        List<OrderDetail> orderDetails = orderDetailRepository.findByOrderId(orderId);
        assertThat(orderDetails).hasSize(2);

        for (OrderDetail detail : orderDetails) {
            InventoryTransaction log = logs.stream()
                    .filter(tx -> tx.getOrderDetail() != null && tx.getOrderDetail().getId().equals(detail.getId()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Missing log for detail: " + detail.getId()));

            assertThat(log.getMovementType()).isEqualTo(InventoryMovementType.CHECKOUT_DEDUCT);
            assertThat(log.getQuantity()).isEqualTo(detail.getQuantity());
            assertThat(log.getVariant().getId()).isEqualTo(detail.getVariant().getId());
            assertThat(log.getOrder().getId()).isEqualTo(orderId);
            assertThat(log.getActorUser().getId()).isEqualTo(customerA.getId());
            assertThat(log.getNote()).isEqualTo("Trừ tồn kho khi đặt hàng");
            assertThat(log.getIdempotencyKey()).isEqualTo("ORDER_CHECKOUT:" + detail.getId());

            int expectedStockAfter = detail.getVariant().getId().equals(variant1.getId())
                    ? initialStock1 - 2
                    : initialStock2 - 3;
            assertThat(log.getStockAfter()).isEqualTo(expectedStockAfter);
            assertThat(log.getStockBefore()).isEqualTo(expectedStockAfter + detail.getQuantity());
        }
    }

    @Test
    @DisplayName("B1-2. checkoutShouldRollbackOrderAndStockWhenRecordingDeductionFails: Lỗi ghi log -> rollback Order, OrderDetail, Payment, stock không đổi, không có log")
    void checkoutShouldRollbackOrderAndStockWhenRecordingDeductionFails() throws Exception {
        int initialStock = variant1.getStock();

        doThrow(new RuntimeException("Simulated stock deduction logging failure"))
                .when(inventoryStockService).recordCheckoutDeduction(any(), any(), any());

        CheckoutRequest request = CheckoutRequest.builder()
                .addressId(addressA.getId())
                .paymentMethod(PaymentMethod.COD)
                .items(List.of(
                        CheckoutItemRequest.builder().variantId(variant1.getId()).quantity(2).build()
                ))
                .build();

        mockMvc.perform(post("/api/orders/checkout")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is5xxServerError());

        // Independent queries outside the failed transaction to verify total rollback:
        long orderCount = orderRepository.count();
        long orderDetailCount = orderDetailRepository.count();
        long paymentCount = paymentRepository.count();
        long txCount = inventoryTransactionRepository.count();

        assertThat(orderCount).isZero();
        assertThat(orderDetailCount).isZero();
        assertThat(paymentCount).isZero();
        assertThat(txCount).isZero();

        ProductVariant reloadedVariant = variantRepository.findById(variant1.getId()).orElseThrow();
        assertThat(reloadedVariant.getStock()).isEqualTo(initialStock);
    }

    @Test
    @DisplayName("B1-3. checkoutReplayWithSameIdempotencyKeyShouldNotWriteLogsAgain: Replay cùng idempotency_key -> trả đơn cũ, số log và tồn kho không đổi")
    void checkoutReplayWithSameIdempotencyKeyShouldNotWriteLogsAgain() throws Exception {
        String replayKey = "REPLAY-KEY-" + System.nanoTime();
        int initialStock = variant1.getStock();

        CheckoutRequest request = CheckoutRequest.builder()
                .addressId(addressA.getId())
                .paymentMethod(PaymentMethod.COD)
                .idempotencyKey(replayKey)
                .items(List.of(
                        CheckoutItemRequest.builder().variantId(variant1.getId()).quantity(2).build()
                ))
                .build();

        // 1st request -> Created
        MvcResult res1 = mockMvc.perform(post("/api/orders/checkout")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json1 = objectMapper.readTree(res1.getResponse().getContentAsString());
        Long orderId1 = json1.get("orderId").asLong();

        List<InventoryTransaction> logsAfterFirst = inventoryTransactionRepository.findByOrderIdAndMovementType(
                orderId1, InventoryMovementType.CHECKOUT_DEDUCT
        );
        assertThat(logsAfterFirst).hasSize(1);
        assertThat(variantRepository.findById(variant1.getId()).orElseThrow().getStock()).isEqualTo(initialStock - 2);

        // 2nd request with same idempotency key -> Replay
        MvcResult res2 = mockMvc.perform(post("/api/orders/checkout")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json2 = objectMapper.readTree(res2.getResponse().getContentAsString());
        Long orderId2 = json2.get("orderId").asLong();
        assertThat(orderId2).isEqualTo(orderId1);

        // Assert logs count and stock remained identical
        List<InventoryTransaction> logsAfterSecond = inventoryTransactionRepository.findByOrderIdAndMovementType(
                orderId1, InventoryMovementType.CHECKOUT_DEDUCT
        );
        assertThat(logsAfterSecond).hasSize(1);
        assertThat(variantRepository.findById(variant1.getId()).orElseThrow().getStock()).isEqualTo(initialStock - 2);
    }

    @Test
    @DisplayName("B1-4. checkoutOutOfStockShouldLeaveNoLogs: Hết hàng -> ném OutOfStockException, không tạo order và không có log nào")
    void checkoutOutOfStockShouldLeaveNoLogs() throws Exception {
        CheckoutRequest request = CheckoutRequest.builder()
                .addressId(addressA.getId())
                .paymentMethod(PaymentMethod.COD)
                .items(List.of(
                        CheckoutItemRequest.builder().variantId(singleStockVariant.getId()).quantity(5).build()
                ))
                .build();

        mockMvc.perform(post("/api/orders/checkout")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("không đủ số lượng")));

        assertThat(orderRepository.count()).isZero();
        assertThat(inventoryTransactionRepository.count()).isZero();
        assertThat(variantRepository.findById(singleStockVariant.getId()).orElseThrow().getStock()).isEqualTo(1);
    }

    @Test
    @DisplayName("B1-5. checkoutConcurrentShouldCreateContinuousLogChain: 2 checkout đồng thời cùng variant -> chuỗi log liền mạch và stock cuối chính xác")
    void checkoutConcurrentShouldCreateContinuousLogChain() throws Exception {
        int initialStock = variant1.getStock();
        int numThreads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch readyLatch = new CountDownLatch(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);

        Runnable task1 = () -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                CheckoutRequest req = CheckoutRequest.builder()
                        .addressId(addressA.getId())
                        .paymentMethod(PaymentMethod.COD)
                        .items(List.of(CheckoutItemRequest.builder().variantId(variant1.getId()).quantity(2).build()))
                        .build();
                MvcResult res = mockMvc.perform(post("/api/orders/checkout")
                                .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA)))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                        .andReturn();
                if (res.getResponse().getStatus() == 201) {
                    successCount.incrementAndGet();
                }
            } catch (Exception ignored) {
            }
        };

        Runnable task2 = () -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                CheckoutRequest req = CheckoutRequest.builder()
                        .addressId(addressB.getId())
                        .paymentMethod(PaymentMethod.COD)
                        .items(List.of(CheckoutItemRequest.builder().variantId(variant1.getId()).quantity(3).build()))
                        .build();
                MvcResult res = mockMvc.perform(post("/api/orders/checkout")
                                .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerB)))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                        .andReturn();
                if (res.getResponse().getStatus() == 201) {
                    successCount.incrementAndGet();
                }
            } catch (Exception ignored) {
            }
        };

        executor.submit(task1);
        executor.submit(task2);

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        assertThat(successCount.get()).isEqualTo(2);

        int finalStock = variantRepository.findById(variant1.getId()).orElseThrow().getStock();
        assertThat(finalStock).isEqualTo(initialStock - 5);

        List<InventoryTransaction> logs = inventoryTransactionRepository.findAll().stream()
                .filter(tx -> tx.getVariant().getId().equals(variant1.getId()))
                .toList();
        assertThat(logs).hasSize(2);

        InventoryTransaction txA = logs.get(0);
        InventoryTransaction txB = logs.get(1);

        assertThat(txA.getStockBefore() - txA.getQuantity()).isEqualTo(txA.getStockAfter());
        assertThat(txB.getStockBefore() - txB.getQuantity()).isEqualTo(txB.getStockAfter());

        InventoryTransaction firstTx = txA.getStockBefore() > txB.getStockBefore() ? txA : txB;
        InventoryTransaction secondTx = txA.getStockBefore() > txB.getStockBefore() ? txB : txA;

        assertThat(firstTx.getStockBefore()).isEqualTo(initialStock);
        assertThat(firstTx.getStockAfter()).isEqualTo(secondTx.getStockBefore());
        assertThat(secondTx.getStockAfter()).isEqualTo(finalStock);
    }

    @Test
    @DisplayName("B1-6. checkoutConsolidatedSameVariantShouldCreateSingleMergedDetailAndLog: Đơn có 2 item cùng variant -> gộp 1 OrderDetail và 1 bản ghi log")
    void checkoutConsolidatedSameVariantShouldCreateSingleMergedDetailAndLog() throws Exception {
        int initialStock = variant1.getStock();

        CheckoutRequest request = CheckoutRequest.builder()
                .addressId(addressA.getId())
                .paymentMethod(PaymentMethod.COD)
                .items(List.of(
                        CheckoutItemRequest.builder().variantId(variant1.getId()).quantity(2).build(),
                        CheckoutItemRequest.builder().variantId(variant1.getId()).quantity(3).build()
                ))
                .build();

        MvcResult result = mockMvc.perform(post("/api/orders/checkout")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
        Long orderId = jsonNode.get("orderId").asLong();

        List<OrderDetail> orderDetails = orderDetailRepository.findByOrderId(orderId);
        assertThat(orderDetails).hasSize(1);
        OrderDetail mergedDetail = orderDetails.get(0);
        assertThat(mergedDetail.getQuantity()).isEqualTo(5);

        List<InventoryTransaction> logs = inventoryTransactionRepository.findByOrderIdAndMovementType(
                orderId, InventoryMovementType.CHECKOUT_DEDUCT
        );
        assertThat(logs).hasSize(1);
        InventoryTransaction log = logs.get(0);
        assertThat(log.getQuantity()).isEqualTo(5);
        assertThat(log.getStockBefore()).isEqualTo(initialStock);
        assertThat(log.getStockAfter()).isEqualTo(initialStock - 5);
        assertThat(log.getIdempotencyKey()).isEqualTo("ORDER_CHECKOUT:" + mergedDetail.getId());
    }
}
