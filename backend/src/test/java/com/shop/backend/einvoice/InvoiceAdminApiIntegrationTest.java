package com.shop.backend.einvoice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.backend.common.model.DataSourceType;
import com.shop.backend.einvoice.model.BuyerInvoiceProfile;
import com.shop.backend.einvoice.model.CustomerType;
import com.shop.backend.einvoice.model.DocumentStatus;
import com.shop.backend.einvoice.model.Invoice;
import com.shop.backend.einvoice.model.InvoiceItem;
import com.shop.backend.einvoice.model.InvoiceItemType;
import com.shop.backend.einvoice.model.InvoiceType;
import com.shop.backend.einvoice.model.PricingBasis;
import com.shop.backend.einvoice.model.SellerInvoiceProfile;
import com.shop.backend.einvoice.model.TaxConfiguration;
import com.shop.backend.einvoice.model.TaxTreatment;
import com.shop.backend.einvoice.repository.BuyerInvoiceProfileRepository;
import com.shop.backend.einvoice.repository.InvoiceItemRepository;
import com.shop.backend.einvoice.repository.InvoiceOutboxRepository;
import com.shop.backend.einvoice.repository.InvoiceRepository;
import com.shop.backend.einvoice.repository.SellerInvoiceProfileRepository;
import com.shop.backend.einvoice.repository.TaxConfigurationRepository;
import com.shop.backend.order.Order;
import com.shop.backend.order.OrderDetail;
import com.shop.backend.order.OrderDetailRepository;
import com.shop.backend.order.OrderRepository;
import com.shop.backend.order.OrderStatus;
import com.shop.backend.payment.Payment;
import com.shop.backend.payment.PaymentMethod;
import com.shop.backend.payment.PaymentRepository;
import com.shop.backend.payment.PaymentStatus;
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
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InvoiceAdminApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private InvoiceOutboxRepository invoiceOutboxRepository;

    @Autowired
    private InvoiceItemRepository invoiceItemRepository;

    @Autowired
    private BuyerInvoiceProfileRepository buyerProfileRepository;

    @Autowired
    private SellerInvoiceProfileRepository sellerProfileRepository;

    @Autowired
    private OrderDetailRepository orderDetailRepository;

    @Autowired
    private TaxConfigurationRepository taxConfigurationRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;

    private User adminUser;
    private User staffUser;
    private User customerUser;
    private BuyerInvoiceProfile buyerProfile;
    private SellerInvoiceProfile sellerProfile;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        cleanTestData();

        if (taxConfigurationRepository.findEffectiveConfiguration("clothing", PricingBasis.INCLUSIVE, LocalDateTime.now()).isEmpty()) {
            taxConfigurationRepository.save(TaxConfiguration.builder()
                    .taxCategory("clothing")
                    .pricingBasis(PricingBasis.INCLUSIVE)
                    .taxTreatment(TaxTreatment.TAXABLE)
                    .taxRate(new BigDecimal("0.0800"))
                    .taxCode("VAT8")
                    .effectiveFrom(LocalDateTime.now().minusDays(10))
                    .effectiveTo(LocalDateTime.now().plusYears(1))
                    .legalReference("Decree 254/2026")
                    .build());
        }
        if (taxConfigurationRepository.findEffectiveConfiguration("shipping", PricingBasis.INCLUSIVE, LocalDateTime.now()).isEmpty()) {
            taxConfigurationRepository.save(TaxConfiguration.builder()
                    .taxCategory("shipping")
                    .pricingBasis(PricingBasis.INCLUSIVE)
                    .taxTreatment(TaxTreatment.TAXABLE)
                    .taxRate(new BigDecimal("0.0800"))
                    .taxCode("VAT8")
                    .effectiveFrom(LocalDateTime.now().minusDays(10))
                    .effectiveTo(LocalDateTime.now().plusYears(1))
                    .legalReference("Decree 254/2026")
                    .build());
        }

        Role adminRole = roleRepository.findByName("admin")
                .orElseGet(() -> roleRepository.save(new Role(null, "admin")));
        Role staffRole = roleRepository.findByName("staff")
                .orElseGet(() -> roleRepository.save(new Role(null, "staff")));
        Role customerRole = roleRepository.findByName("customer")
                .orElseGet(() -> roleRepository.save(new Role(null, "customer")));

        long nano = System.nanoTime();
        adminUser = userRepository.save(User.builder()
                .role(adminRole)
                .name("Admin Tester")
                .email("admin.test." + nano + "@shop.vn")
                .phone("091" + String.format("%07d", Math.abs(nano % 10000000L)))
                .passwordHash("hashed")
                .status(UserStatus.ACTIVE)
                .dataSource(DataSourceType.WEBSITE)
                .build());

        staffUser = userRepository.save(User.builder()
                .role(staffRole)
                .name("Staff Tester")
                .email("staff.test." + nano + "@shop.vn")
                .phone("092" + String.format("%07d", Math.abs(nano % 10000000L)))
                .passwordHash("hashed")
                .status(UserStatus.ACTIVE)
                .dataSource(DataSourceType.WEBSITE)
                .build());

        customerUser = userRepository.save(User.builder()
                .role(customerRole)
                .name("Customer Tester")
                .email("cust.test." + nano + "@shop.vn")
                .phone("093" + String.format("%07d", Math.abs(nano % 10000000L)))
                .passwordHash("hashed")
                .status(UserStatus.ACTIVE)
                .dataSource(DataSourceType.WEBSITE)
                .build());

        buyerProfile = buyerProfileRepository.save(BuyerInvoiceProfile.builder()
                .customerType(CustomerType.INDIVIDUAL)
                .buyerName("Nguyen Van A")
                .email("buyer.a@shop.vn")
                .address("123 Duong Cau Giay, Ha Noi")
                .phoneNumber("0901234567")
                .build());

        sellerProfile = sellerProfileRepository.save(SellerInvoiceProfile.builder()
                .sellerTaxCode("0101234567")
                .sellerLegalName("CONG TY TNHH CLOSET SHOP")
                .sellerBrandName("ATELIER")
                .sellerAddress("124 Nguyen Trai, Q1, TP HCM")
                .sellerPhone("19006868")
                .sellerEmail("contact@closetshop.com")
                .build());
    }

    @AfterEach
    void tearDown() {
        cleanTestData();
    }

    private void cleanTestData() {
        transactionTemplate.execute(status -> {
            entityManager.createQuery("DELETE FROM InvoiceOutbox").executeUpdate();
            entityManager.createQuery("DELETE FROM InvoiceBalanceAdjustment").executeUpdate();
            entityManager.createQuery("UPDATE InvoiceItem ii SET ii.originalInvoiceItem = null").executeUpdate();
            entityManager.createQuery("DELETE FROM InvoiceItem").executeUpdate();
            entityManager.createQuery("UPDATE Invoice i SET i.replacedByInvoice = null, i.replacingInvoice = null, i.originalInvoice = null").executeUpdate();
            entityManager.createQuery("DELETE FROM Invoice").executeUpdate();
            entityManager.createQuery("DELETE FROM BuyerInvoiceProfile").executeUpdate();
            entityManager.createQuery("DELETE FROM SellerInvoiceProfile").executeUpdate();
            entityManager.createQuery("DELETE FROM Payment").executeUpdate();
            entityManager.createQuery("DELETE FROM OrderDetail").executeUpdate();
            entityManager.createQuery("DELETE FROM Order").executeUpdate();
            entityManager.createQuery("DELETE FROM ProductVariant").executeUpdate();
            entityManager.createQuery("DELETE FROM Product").executeUpdate();
            entityManager.createQuery("DELETE FROM Category").executeUpdate();
            entityManager.createQuery("DELETE FROM User u WHERE u.email LIKE '%.test.%@shop.vn'").executeUpdate();
            return null;
        });
    }

    private UserPrincipal toPrincipal(User user) {
        User u = userRepository.findWithRoleById(user.getId()).orElseThrow();
        return new UserPrincipal(u);
    }

    private Order createOrder(User user, OrderStatus status) {
        return orderRepository.save(Order.builder()
                .user(user)
                .status(status)
                .subtotal(new BigDecimal("200000.00"))
                .totalAmount(new BigDecimal("230000.00"))
                .shippingFee(new BigDecimal("30000.00"))
                .receiverName(user.getName())
                .receiverPhone(user.getPhone())
                .addressLine("123 Test Street")
                .ward("Dich Vong")
                .district("Cau Giay")
                .province("Ha Noi")
                .idempotencyKey("ORD_KEY_" + UUID.randomUUID())
                .build());
    }

    private Order createOrderWithDetails(User user, OrderStatus status) {
        long nano = System.nanoTime();
        Category cat = categoryRepository.save(Category.builder()
                .name("Cat-" + nano)
                .slug("cat-" + nano)
                .isActive(true)
                .build());
        Product product = productRepository.save(Product.builder()
                .category(cat)
                .name("Test Product " + nano)
                .slug("test-product-" + nano)
                .basePrice(new BigDecimal("200000.00"))
                .isActive(true)
                .dataSource(DataSourceType.WEBSITE)
                .build());
        ProductVariant variant = productVariantRepository.save(ProductVariant.builder()
                .product(product)
                .sku("SKU-" + System.nanoTime())
                .size("M")
                .color("White")
                .stock(100)
                .price(new BigDecimal("200000.00"))
                .build());

        Order order = Order.builder()
                .user(user)
                .status(status)
                .subtotal(new BigDecimal("200000.00"))
                .totalAmount(new BigDecimal("230000.00"))
                .shippingFee(new BigDecimal("30000.00"))
                .receiverName(user.getName())
                .receiverPhone(user.getPhone())
                .addressLine("123 Test Street")
                .ward("Dich Vong")
                .district("Cau Giay")
                .province("Ha Noi")
                .idempotencyKey("ORD_KEY_" + UUID.randomUUID())
                .dataSource(DataSourceType.WEBSITE)
                .build();

        OrderDetail detail = OrderDetail.builder()
                .variant(variant)
                .productName("Ao so mi trang")
                .variantSize("M")
                .variantColor("White")
                .quantity(1)
                .unitPrice(new BigDecimal("200000.00"))
                .discountAmount(BigDecimal.ZERO)
                .build();
        order.addOrderDetail(detail);

        return orderRepository.save(order);
    }

    private Invoice createTestInvoice(Order order, InvoiceType type, DocumentStatus docStatus, boolean isDemo) {
        Invoice invoice = invoiceRepository.save(Invoice.builder()
                .order(order)
                .buyerProfile(buyerProfile)
                .sellerProfile(sellerProfile)
                .invoiceType(type)
                .documentStatus(docStatus)
                .invoiceSeries(isDemo ? "DEMO1C26TAA" : "1C26TAA")
                .invoiceNumber(isDemo ? "DEMO-0000001" : "0000001")
                .taxAuthorityCode("MTC_TEST_123456")
                .taxPointDate(LocalDateTime.now())
                .officiallyIssuedAt(LocalDateTime.now())
                .pricingBasis(PricingBasis.INCLUSIVE)
                .totalMerchandiseBeforeTax(new BigDecimal("185185.00"))
                .totalMerchandiseDiscount(BigDecimal.ZERO)
                .totalShippingBeforeTax(new BigDecimal("27778.00"))
                .totalShippingDiscount(BigDecimal.ZERO)
                .totalTaxableAmount(new BigDecimal("212963.00"))
                .totalTaxAmount(new BigDecimal("17037.00"))
                .totalAfterTax(new BigDecimal("230000.00"))
                .storageProvider(isDemo ? "MOCK" : "MISA")
                .providerTransactionId(isDemo ? "DEMO-TX-12345" : "MISA-TX-12345")
                .idempotencyKey("INV_KEY_" + UUID.randomUUID())
                .build());

        invoiceItemRepository.save(InvoiceItem.builder()
                .invoice(invoice)
                .itemType(InvoiceItemType.MERCHANDISE)
                .itemName("Ao so mi trang")
                .unit("Cai")
                .quantity(1)
                .originalQuantity(1)
                .unitPriceGross(new BigDecimal("200000.00"))
                .unitPriceNet(new BigDecimal("185185.1852"))
                .discountAmount(BigDecimal.ZERO)
                .taxableAmount(new BigDecimal("185185.00"))
                .taxCategory("HANG_HOA")
                .taxTreatment(TaxTreatment.TAXABLE)
                .taxRate(new BigDecimal("0.0800"))
                .taxCode("VAT8")
                .taxAmount(new BigDecimal("14815.00"))
                .lineTotalGross(new BigDecimal("200000.00"))
                .build());

        return invoice;
    }

    @Test
    @DisplayName("TC-01: Admin lấy danh sách hóa đơn thành công")
    void adminGetInvoices_success() throws Exception {
        Order order = createOrder(customerUser, OrderStatus.COMPLETED);
        createTestInvoice(order, InvoiceType.ORIGINAL, DocumentStatus.ISSUED, true);

        mockMvc.perform(get("/api/admin/invoices")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(adminUser))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].invoiceType").value("ORIGINAL"))
                .andExpect(jsonPath("$.content[0].documentStatus").value("ISSUED"))
                .andExpect(jsonPath("$.content[0].isDemo").value(true))
                .andExpect(jsonPath("$.content[0].totalAfterTax").value(230000));
    }

    @Test
    @DisplayName("TC-02: Admin lọc theo documentStatus và invoiceType")
    void adminFilterInvoices_statusAndType() throws Exception {
        Order order1 = createOrder(customerUser, OrderStatus.COMPLETED);
        createTestInvoice(order1, InvoiceType.ORIGINAL, DocumentStatus.ISSUED, true);

        Order order2 = createOrder(customerUser, OrderStatus.CONFIRMED);
        createTestInvoice(order2, InvoiceType.ORIGINAL, DocumentStatus.DRAFT, false);

        // Filter status ISSUED
        mockMvc.perform(get("/api/admin/invoices")
                        .param("documentStatus", "ISSUED")
                        .param("invoiceType", "ORIGINAL")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(adminUser))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].documentStatus").value("ISSUED"))
                .andExpect(jsonPath("$.content[0].invoiceType").value("ORIGINAL"));
    }

    @Test
    @DisplayName("TC-03: Admin lọc theo isDemo (true/false)")
    void adminFilterInvoices_isDemo() throws Exception {
        Order order1 = createOrder(customerUser, OrderStatus.COMPLETED);
        createTestInvoice(order1, InvoiceType.ORIGINAL, DocumentStatus.ISSUED, true);

        Order order2 = createOrder(customerUser, OrderStatus.CONFIRMED);
        createTestInvoice(order2, InvoiceType.ORIGINAL, DocumentStatus.DRAFT, false);

        // Filter isDemo=true
        mockMvc.perform(get("/api/admin/invoices")
                        .param("isDemo", "true")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(adminUser))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].isDemo").value(true));

        // Filter isDemo=false
        mockMvc.perform(get("/api/admin/invoices")
                        .param("isDemo", "false")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(adminUser))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].isDemo").value(false));
    }

    @Test
    @DisplayName("TC-04: Admin xem chi tiết hóa đơn đầy đủ snapshot và items")
    void adminGetInvoiceDetail_success() throws Exception {
        Order order = createOrder(customerUser, OrderStatus.COMPLETED);
        Invoice invoice = createTestInvoice(order, InvoiceType.ORIGINAL, DocumentStatus.ISSUED, true);

        mockMvc.perform(get("/api/admin/invoices/{id}", invoice.getId())
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(adminUser))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(invoice.getId()))
                .andExpect(jsonPath("$.orderId").value(order.getId()))
                .andExpect(jsonPath("$.isDemo").value(true))
                .andExpect(jsonPath("$.demoNotice").isNotEmpty())
                .andExpect(jsonPath("$.buyer.buyerName").value("Nguyen Van A"))
                .andExpect(jsonPath("$.seller.sellerLegalName").value("CONG TY TNHH CLOSET SHOP"))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].itemName").value("Ao so mi trang"))
                .andExpect(jsonPath("$.totalAfterTax").value(230000));
    }

    @Test
    @DisplayName("TC-05: Admin retry đơn Payment SUCCESS chưa có invoice -> thành công")
    void adminRetryInvoice_success() throws Exception {
        Order order = createOrderWithDetails(customerUser, OrderStatus.CONFIRMED);
        paymentRepository.save(Payment.builder()
                .order(order)
                .method(PaymentMethod.VNPAY)
                .status(PaymentStatus.SUCCESS)
                .amount(new BigDecimal("230000.00"))
                .paidAt(LocalDateTime.now())
                .build());

        mockMvc.perform(post("/api/admin/invoices/orders/{orderId}/retry", order.getId())
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(adminUser))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoiceCreated").value(true))
                .andExpect(jsonPath("$.message").value("Đã tạo hóa đơn và đưa vào hàng đợi xử lý."))
                .andExpect(jsonPath("$.invoice.orderId").value(order.getId()))
                .andExpect(jsonPath("$.invoice.documentStatus").value("DRAFT"));

        assertThat(invoiceRepository.findByOrderId(order.getId())).hasSize(1);
    }

    @Test
    @DisplayName("TC-06: Không retry đơn Payment FAILED")
    void adminRetryInvoice_rejected_paymentFailed() throws Exception {
        Order order = createOrder(customerUser, OrderStatus.PENDING);
        paymentRepository.save(Payment.builder()
                .order(order)
                .method(PaymentMethod.VNPAY)
                .status(PaymentStatus.FAILED)
                .amount(new BigDecimal("230000.00"))
                .build());

        mockMvc.perform(post("/api/admin/invoices/orders/{orderId}/retry", order.getId())
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(adminUser))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TC-07: Không retry đơn CANCELLED")
    void adminRetryInvoice_rejected_orderCancelled() throws Exception {
        Order order = createOrder(customerUser, OrderStatus.CANCELLED);
        paymentRepository.save(Payment.builder()
                .order(order)
                .method(PaymentMethod.VNPAY)
                .status(PaymentStatus.SUCCESS)
                .amount(new BigDecimal("230000.00"))
                .paidAt(LocalDateTime.now())
                .build());

        mockMvc.perform(post("/api/admin/invoices/orders/{orderId}/retry", order.getId())
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(adminUser))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TC-08: Retry nhiều lần là idempotent, không tạo invoice trùng")
    void adminRetryInvoice_idempotent() throws Exception {
        Order order = createOrderWithDetails(customerUser, OrderStatus.CONFIRMED);
        paymentRepository.save(Payment.builder()
                .order(order)
                .method(PaymentMethod.VNPAY)
                .status(PaymentStatus.SUCCESS)
                .amount(new BigDecimal("230000.00"))
                .paidAt(LocalDateTime.now())
                .build());

        // Call 1 -> creates
        mockMvc.perform(post("/api/admin/invoices/orders/{orderId}/retry", order.getId())
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(adminUser))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoiceCreated").value(true))
                .andExpect(jsonPath("$.message").value("Đã tạo hóa đơn và đưa vào hàng đợi xử lý."));

        // Call 2 -> idempotent, returns existing
        mockMvc.perform(post("/api/admin/invoices/orders/{orderId}/retry", order.getId())
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(adminUser))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoiceCreated").value(false))
                .andExpect(jsonPath("$.message").value("Đơn hàng đã có hóa đơn, không cần tạo lại."));

        assertThat(invoiceRepository.findByOrderId(order.getId())).hasSize(1);
    }

    @Test
    @DisplayName("TC-09: STAFF được xem danh sách & chi tiết, nhưng bị cấm Retry (403)")
    void staffPermission_canView_cannotRetry() throws Exception {
        Order order = createOrder(customerUser, OrderStatus.COMPLETED);
        Invoice invoice = createTestInvoice(order, InvoiceType.ORIGINAL, DocumentStatus.ISSUED, true);

        // Staff can list
        mockMvc.perform(get("/api/admin/invoices")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(staffUser))))
                .andExpect(status().isOk());

        // Staff can view detail
        mockMvc.perform(get("/api/admin/invoices/{id}", invoice.getId())
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(staffUser))))
                .andExpect(status().isOk());

        // Staff CANNOT retry
        mockMvc.perform(post("/api/admin/invoices/orders/{orderId}/retry", order.getId())
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(staffUser))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TC-10: CUSTOMER bị cấm truy cập /api/admin/invoices (403)")
    void adminEndpoints_customerForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/invoices")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerUser))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TC-11: Unauthenticated bị từ chối 401 trên admin endpoints")
    void adminEndpoints_unauthenticatedUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/invoices"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("EINV-RESET-3.1-01: MISA invoice có trường nullable (providerTransactionId, invoiceSeries null) vẫn được lọc đúng khi isDemo=false")
    void adminFilterInvoices_isDemoNullSafe_misaWithNullableFields() throws Exception {
        Order order = createOrder(customerUser, OrderStatus.COMPLETED);

        // MISA invoice with null providerTransactionId & null invoiceSeries
        invoiceRepository.save(Invoice.builder()
                .order(order)
                .buyerProfile(buyerProfile)
                .sellerProfile(sellerProfile)
                .invoiceType(InvoiceType.ORIGINAL)
                .documentStatus(DocumentStatus.ISSUED)
                .invoiceSeries(null)
                .invoiceNumber("0000001")
                .taxPointDate(LocalDateTime.now())
                .pricingBasis(PricingBasis.INCLUSIVE)
                .totalMerchandiseBeforeTax(new BigDecimal("185185.00"))
                .totalMerchandiseDiscount(BigDecimal.ZERO)
                .totalShippingBeforeTax(new BigDecimal("27778.00"))
                .totalShippingDiscount(BigDecimal.ZERO)
                .totalTaxableAmount(new BigDecimal("212963.00"))
                .totalTaxAmount(new BigDecimal("17037.00"))
                .totalAfterTax(new BigDecimal("230000.00"))
                .storageProvider("MISA")
                .providerTransactionId(null)
                .idempotencyKey("INV_MISA_NULL_" + UUID.randomUUID())
                .build());

        // Must appear when isDemo=false
        mockMvc.perform(get("/api/admin/invoices")
                        .param("isDemo", "false")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(adminUser))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].isDemo").value(false))
                .andExpect(jsonPath("$.content[0].invoiceNumber").value("0000001"));

        // Must NOT appear when isDemo=true
        mockMvc.perform(get("/api/admin/invoices")
                        .param("isDemo", "true")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(adminUser))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("EINV-RESET-3.1-02: Kết quả isDemo=true và isDemo=false không chồng lấn và không bỏ sót")
    void adminFilterInvoices_isDemo_noOverlapAndNoOmission() throws Exception {
        Order order1 = createOrder(customerUser, OrderStatus.COMPLETED);
        // 1. Mock invoice -> isDemo = true
        createTestInvoice(order1, InvoiceType.ORIGINAL, DocumentStatus.ISSUED, true);

        Order order2 = createOrder(customerUser, OrderStatus.COMPLETED);
        // 2. Full MISA invoice -> isDemo = false
        createTestInvoice(order2, InvoiceType.ORIGINAL, DocumentStatus.ISSUED, false);

        Order order3 = createOrder(customerUser, OrderStatus.CONFIRMED);
        // 3. MISA invoice with nullable fields -> isDemo = false
        invoiceRepository.save(Invoice.builder()
                .order(order3)
                .buyerProfile(buyerProfile)
                .sellerProfile(sellerProfile)
                .invoiceType(InvoiceType.ORIGINAL)
                .documentStatus(DocumentStatus.DRAFT)
                .invoiceSeries(null)
                .invoiceNumber(null)
                .taxPointDate(LocalDateTime.now())
                .pricingBasis(PricingBasis.INCLUSIVE)
                .totalMerchandiseBeforeTax(new BigDecimal("185185.00"))
                .totalMerchandiseDiscount(BigDecimal.ZERO)
                .totalShippingBeforeTax(new BigDecimal("27778.00"))
                .totalShippingDiscount(BigDecimal.ZERO)
                .totalTaxableAmount(new BigDecimal("212963.00"))
                .totalTaxAmount(new BigDecimal("17037.00"))
                .totalAfterTax(new BigDecimal("230000.00"))
                .storageProvider("MISA")
                .providerTransactionId(null)
                .idempotencyKey("INV_MISA_NULL2_" + UUID.randomUUID())
                .build());

        // Total without isDemo filter -> 3
        mockMvc.perform(get("/api/admin/invoices")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(adminUser))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));

        // isDemo=true -> exactly 1
        mockMvc.perform(get("/api/admin/invoices")
                        .param("isDemo", "true")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(adminUser))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].isDemo").value(true));

        // isDemo=false -> exactly 2
        mockMvc.perform(get("/api/admin/invoices")
                        .param("isDemo", "false")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(adminUser))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].isDemo").value(false))
                .andExpect(jsonPath("$.content[1].isDemo").value(false));
    }

    @Test
    @DisplayName("EINV-RESET-3.1-03: Nhận diện isDemo của DRAFT vs Mock Issued vs MISA")
    void adminInvoiceDemoStatus_draftVsIssuedVsMisa() throws Exception {
        Order order = createOrder(customerUser, OrderStatus.COMPLETED);

        // 1. DRAFT invoice (all provider fields null) -> isDemo = false
        Invoice draftInvoice = invoiceRepository.save(Invoice.builder()
                .order(order)
                .buyerProfile(buyerProfile)
                .sellerProfile(sellerProfile)
                .invoiceType(InvoiceType.ORIGINAL)
                .documentStatus(DocumentStatus.DRAFT)
                .taxPointDate(LocalDateTime.now())
                .pricingBasis(PricingBasis.INCLUSIVE)
                .totalMerchandiseBeforeTax(new BigDecimal("185185.00"))
                .totalMerchandiseDiscount(BigDecimal.ZERO)
                .totalShippingBeforeTax(new BigDecimal("27778.00"))
                .totalShippingDiscount(BigDecimal.ZERO)
                .totalTaxableAmount(new BigDecimal("212963.00"))
                .totalTaxAmount(new BigDecimal("17037.00"))
                .totalAfterTax(new BigDecimal("230000.00"))
                .idempotencyKey("INV_DRAFT_" + UUID.randomUUID())
                .build());

        mockMvc.perform(get("/api/admin/invoices/{id}", draftInvoice.getId())
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(adminUser))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDemo").value(false))
                .andExpect(jsonPath("$.demoMode").value(true))
                .andExpect(jsonPath("$.demoNotice").isNotEmpty());

        // 2. Mock Issued invoice -> isDemo = true
        Invoice mockIssued = createTestInvoice(order, InvoiceType.ORIGINAL, DocumentStatus.ISSUED, true);
        mockMvc.perform(get("/api/admin/invoices/{id}", mockIssued.getId())
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(adminUser))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDemo").value(true))
                .andExpect(jsonPath("$.demoMode").value(true))
                .andExpect(jsonPath("$.demoNotice").isNotEmpty());

        // 3. MISA invoice -> isDemo = false
        Invoice misaIssued = createTestInvoice(order, InvoiceType.ORIGINAL, DocumentStatus.ISSUED, false);
        mockMvc.perform(get("/api/admin/invoices/{id}", misaIssued.getId())
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(adminUser))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDemo").value(false))
                .andExpect(jsonPath("$.demoMode").value(false))
                .andExpect(jsonPath("$.demoNotice").doesNotExist());
    }
}
