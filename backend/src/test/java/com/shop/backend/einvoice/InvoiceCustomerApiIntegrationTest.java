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
import com.shop.backend.einvoice.model.TaxTreatment;
import com.shop.backend.einvoice.repository.BuyerInvoiceProfileRepository;
import com.shop.backend.einvoice.repository.InvoiceItemRepository;
import com.shop.backend.einvoice.repository.InvoiceRepository;
import com.shop.backend.einvoice.repository.SellerInvoiceProfileRepository;
import com.shop.backend.order.Order;
import com.shop.backend.order.OrderRepository;
import com.shop.backend.order.OrderStatus;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InvoiceCustomerApiIntegrationTest {

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
    private InvoiceRepository invoiceRepository;

    @Autowired
    private InvoiceItemRepository invoiceItemRepository;

    @Autowired
    private BuyerInvoiceProfileRepository buyerProfileRepository;

    @Autowired
    private SellerInvoiceProfileRepository sellerProfileRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;

    private User customerA;
    private User customerB;
    private BuyerInvoiceProfile buyerProfileA;
    private BuyerInvoiceProfile buyerProfileB;
    private SellerInvoiceProfile sellerProfile;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        cleanTestData();

        Role customerRole = roleRepository.findByName("customer")
                .orElseGet(() -> roleRepository.save(new Role(null, "customer")));

        long nano = System.nanoTime();
        customerA = userRepository.save(User.builder()
                .role(customerRole)
                .name("Customer A")
                .email("cust.a." + nano + "@shop.vn")
                .phone("091" + String.format("%07d", Math.abs(nano % 10000000L)))
                .passwordHash("hashed")
                .status(UserStatus.ACTIVE)
                .dataSource(DataSourceType.WEBSITE)
                .build());

        customerB = userRepository.save(User.builder()
                .role(customerRole)
                .name("Customer B")
                .email("cust.b." + nano + "@shop.vn")
                .phone("092" + String.format("%07d", Math.abs(nano % 10000000L)))
                .passwordHash("hashed")
                .status(UserStatus.ACTIVE)
                .dataSource(DataSourceType.WEBSITE)
                .build());

        buyerProfileA = buyerProfileRepository.save(BuyerInvoiceProfile.builder()
                .customerType(CustomerType.INDIVIDUAL)
                .buyerName("Customer A")
                .email(customerA.getEmail())
                .address("123 Street A, Hanoi")
                .phoneNumber(customerA.getPhone())
                .build());

        buyerProfileB = buyerProfileRepository.save(BuyerInvoiceProfile.builder()
                .customerType(CustomerType.INDIVIDUAL)
                .buyerName("Customer B")
                .email(customerB.getEmail())
                .address("456 Street B, Hanoi")
                .phoneNumber(customerB.getPhone())
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
            entityManager.createQuery("DELETE FROM User u WHERE u.email LIKE '%.test.%@shop.vn' OR u.email LIKE 'cust.%.@shop.vn'").executeUpdate();
            return null;
        });
    }

    private UserPrincipal toPrincipal(User user) {
        User u = userRepository.findWithRoleById(user.getId()).orElseThrow();
        return new UserPrincipal(u);
    }

    private Order createOrder(User user) {
        return orderRepository.save(Order.builder()
                .user(user)
                .status(OrderStatus.COMPLETED)
                .subtotal(new BigDecimal("150000.00"))
                .totalAmount(new BigDecimal("180000.00"))
                .shippingFee(new BigDecimal("30000.00"))
                .receiverName(user.getName())
                .receiverPhone(user.getPhone())
                .addressLine("Test address")
                .ward("Ward 1")
                .district("District 1")
                .province("Hanoi")
                .idempotencyKey("ORD_KEY_" + UUID.randomUUID())
                .build());
    }

    private Invoice createInvoice(Order order, BuyerInvoiceProfile buyer, InvoiceType type, boolean isReplaced) {
        Invoice invoice = invoiceRepository.save(Invoice.builder()
                .order(order)
                .buyerProfile(buyer)
                .sellerProfile(sellerProfile)
                .invoiceType(type)
                .documentStatus(DocumentStatus.ISSUED)
                .isReplaced(isReplaced)
                .invoiceSeries("DEMO1C26TAA")
                .invoiceNumber("DEMO-" + String.format("%07d", (int)(Math.random() * 1000000)))
                .taxAuthorityCode("MTC_TEST_" + UUID.randomUUID().toString().substring(0, 8))
                .taxPointDate(LocalDateTime.now())
                .officiallyIssuedAt(LocalDateTime.now())
                .pricingBasis(PricingBasis.INCLUSIVE)
                .totalMerchandiseBeforeTax(new BigDecimal("138889.00"))
                .totalMerchandiseDiscount(BigDecimal.ZERO)
                .totalShippingBeforeTax(new BigDecimal("27778.00"))
                .totalShippingDiscount(BigDecimal.ZERO)
                .totalTaxableAmount(new BigDecimal("166667.00"))
                .totalTaxAmount(new BigDecimal("13333.00"))
                .totalAfterTax(new BigDecimal("180000.00"))
                .storageProvider("MOCK")
                .providerTransactionId("DEMO-TX-" + UUID.randomUUID().toString().substring(0, 8))
                .idempotencyKey("INV_KEY_" + UUID.randomUUID())
                .build());

        invoiceItemRepository.save(InvoiceItem.builder()
                .invoice(invoice)
                .itemType(InvoiceItemType.MERCHANDISE)
                .itemName("Ao phong co tron")
                .unit("Cai")
                .quantity(1)
                .originalQuantity(1)
                .unitPriceGross(new BigDecimal("150000.00"))
                .unitPriceNet(new BigDecimal("138888.8889"))
                .discountAmount(BigDecimal.ZERO)
                .taxableAmount(new BigDecimal("138889.00"))
                .taxCategory("HANG_HOA")
                .taxTreatment(TaxTreatment.TAXABLE)
                .taxRate(new BigDecimal("0.0800"))
                .taxCode("VAT8")
                .taxAmount(new BigDecimal("11111.00"))
                .lineTotalGross(new BigDecimal("150000.00"))
                .build());

        return invoice;
    }

    @Test
    @DisplayName("TC-12: Customer xem danh sách hóa đơn cá nhân: chỉ nhận hóa đơn thuộc đơn hàng của mình")
    void customerGetMyInvoices_list() throws Exception {
        Order orderA = createOrder(customerA);
        createInvoice(orderA, buyerProfileA, InvoiceType.ORIGINAL, false);

        Order orderB = createOrder(customerB);
        createInvoice(orderB, buyerProfileB, InvoiceType.ORIGINAL, false);

        mockMvc.perform(get("/api/invoices")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].orderId").value(orderA.getId()))
                .andExpect(jsonPath("$.content[0].buyerName").value("Customer A"));
    }

    @Test
    @DisplayName("TC-13: Customer xem chi tiết hóa đơn của mình thành công")
    void customerGetInvoiceById_success() throws Exception {
        Order orderA = createOrder(customerA);
        Invoice invoiceA = createInvoice(orderA, buyerProfileA, InvoiceType.ORIGINAL, false);

        mockMvc.perform(get("/api/invoices/{id}", invoiceA.getId())
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(invoiceA.getId()))
                .andExpect(jsonPath("$.orderId").value(orderA.getId()))
                .andExpect(jsonPath("$.buyer.buyerName").value("Customer A"))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].itemName").value("Ao phong co tron"));
    }

    @Test
    @DisplayName("TC-14: Customer xem hóa đơn của chính mình qua /api/orders/{orderId}/invoice thành công")
    void customerGetInvoiceByOrderId_success() throws Exception {
        Order orderA = createOrder(customerA);
        Invoice invoiceA = createInvoice(orderA, buyerProfileA, InvoiceType.ORIGINAL, false);

        mockMvc.perform(get("/api/orders/{orderId}/invoice", orderA.getId())
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(invoiceA.getId()))
                .andExpect(jsonPath("$.orderId").value(orderA.getId()))
                .andExpect(jsonPath("$.isDemo").value(true))
                .andExpect(jsonPath("$.demoNotice").isNotEmpty())
                .andExpect(jsonPath("$.buyer.buyerName").value("Customer A"))
                .andExpect(jsonPath("$.totalAfterTax").value(180000))
                .andExpect(jsonPath("$.items[0].itemName").value("Ao phong co tron"));
    }

    @Test
    @DisplayName("TC-15: Replacement handling: Khi Original bị thay thế, Customer nhận hóa đơn REPLACEMENT hiện hành")
    void replacementHandling_customerReceivesActiveReplacement() throws Exception {
        Order orderA = createOrder(customerA);

        // 1. Original invoice (marked as replaced)
        Invoice original = createInvoice(orderA, buyerProfileA, InvoiceType.ORIGINAL, true);

        // 2. Replacement invoice (active)
        Invoice replacement = invoiceRepository.save(Invoice.builder()
                .order(orderA)
                .buyerProfile(buyerProfileA)
                .sellerProfile(sellerProfile)
                .invoiceType(InvoiceType.REPLACEMENT)
                .documentStatus(DocumentStatus.ISSUED)
                .isReplaced(false)
                .replacingInvoice(original)
                .invoiceSeries("DEMO1C26TAA")
                .invoiceNumber("DEMO-0099999")
                .taxAuthorityCode("MTC_REPLACEMENT_123")
                .taxPointDate(LocalDateTime.now())
                .officiallyIssuedAt(LocalDateTime.now())
                .pricingBasis(PricingBasis.INCLUSIVE)
                .totalMerchandiseBeforeTax(new BigDecimal("138889.00"))
                .totalMerchandiseDiscount(BigDecimal.ZERO)
                .totalShippingBeforeTax(new BigDecimal("27778.00"))
                .totalShippingDiscount(BigDecimal.ZERO)
                .totalTaxableAmount(new BigDecimal("166667.00"))
                .totalTaxAmount(new BigDecimal("13333.00"))
                .totalAfterTax(new BigDecimal("180000.00"))
                .storageProvider("MOCK")
                .providerTransactionId("DEMO-REP-123")
                .idempotencyKey("REP_KEY_" + UUID.randomUUID())
                .build());

        // Link original to replacement
        original.setReplacedByInvoice(replacement);
        invoiceRepository.save(original);

        // Customer queries order invoice -> MUST receive replacement invoice
        mockMvc.perform(get("/api/orders/{orderId}/invoice", orderA.getId())
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(replacement.getId()))
                .andExpect(jsonPath("$.invoiceType").value("REPLACEMENT"))
                .andExpect(jsonPath("$.replacingInvoiceId").value(original.getId()))
                .andExpect(jsonPath("$.isReplaced").value(false));
    }

    @Test
    @DisplayName("TC-16: Anti-IDOR qua orderId: Customer B xem hóa đơn đơn hàng của Customer A nhận 404")
    void customerGetInvoiceByOrderId_idor_returns404() throws Exception {
        Order orderA = createOrder(customerA);
        createInvoice(orderA, buyerProfileA, InvoiceType.ORIGINAL, false);

        mockMvc.perform(get("/api/orders/{orderId}/invoice", orderA.getId())
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerB))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TC-16 (b): Anti-IDOR qua invoiceId: Customer B xem chi tiết invoiceId của Customer A nhận 404")
    void customerGetInvoiceById_idor_returns404() throws Exception {
        Order orderA = createOrder(customerA);
        Invoice invoiceA = createInvoice(orderA, buyerProfileA, InvoiceType.ORIGINAL, false);

        mockMvc.perform(get("/api/invoices/{id}", invoiceA.getId())
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerB))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TC-17: Unauthenticated request vào API Customer nhận 401")
    void unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/invoices"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/orders/1/invoice"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("EINV-RESET-3.1-04: Thay thế nhiều cấp (A -> B -> C), Customer nhận Replacement cuối cùng C")
    void customerGetInvoiceByOrderId_multiLevelReplacement_returnsLatest() throws Exception {
        Order orderA = createOrder(customerA);

        Invoice invA = createInvoice(orderA, buyerProfileA, InvoiceType.ORIGINAL, true);

        Invoice invB = invoiceRepository.save(Invoice.builder()
                .order(orderA)
                .buyerProfile(buyerProfileA)
                .sellerProfile(sellerProfile)
                .invoiceType(InvoiceType.REPLACEMENT)
                .documentStatus(DocumentStatus.ISSUED)
                .pricingBasis(PricingBasis.INCLUSIVE)
                .taxPointDate(LocalDateTime.now())
                .isReplaced(true)
                .replacingInvoice(invA)
                .invoiceSeries("DEMO1C26TAA")
                .invoiceNumber("DEMO-0000002")
                .idempotencyKey("REP_B_" + UUID.randomUUID())
                .build());

        Invoice invC = invoiceRepository.save(Invoice.builder()
                .order(orderA)
                .buyerProfile(buyerProfileA)
                .sellerProfile(sellerProfile)
                .invoiceType(InvoiceType.REPLACEMENT)
                .documentStatus(DocumentStatus.ISSUED)
                .pricingBasis(PricingBasis.INCLUSIVE)
                .taxPointDate(LocalDateTime.now())
                .isReplaced(false)
                .replacingInvoice(invB)
                .invoiceSeries("DEMO1C26TAA")
                .invoiceNumber("DEMO-0000003")
                .idempotencyKey("REP_C_" + UUID.randomUUID())
                .build());

        invA.setReplacedByInvoice(invB);
        invoiceRepository.save(invA);

        invB.setReplacedByInvoice(invC);
        invoiceRepository.save(invB);

        // Customer queries -> MUST resolve to C
        mockMvc.perform(get("/api/orders/{orderId}/invoice", orderA.getId())
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(invC.getId()))
                .andExpect(jsonPath("$.invoiceType").value("REPLACEMENT"))
                .andExpect(jsonPath("$.invoiceNumber").value("DEMO-0000003"))
                .andExpect(jsonPath("$.isReplaced").value(false));
    }

    @Test
    @DisplayName("EINV-RESET-3.1-05: Chu kỳ thay thế (A -> B -> A) không gây infinite loop, an toàn hệ thống")
    void customerGetInvoiceByOrderId_cycleLoopSafe() throws Exception {
        Order orderA = createOrder(customerA);

        Invoice invA = createInvoice(orderA, buyerProfileA, InvoiceType.ORIGINAL, true);

        Invoice invB = invoiceRepository.save(Invoice.builder()
                .order(orderA)
                .buyerProfile(buyerProfileA)
                .sellerProfile(sellerProfile)
                .invoiceType(InvoiceType.REPLACEMENT)
                .documentStatus(DocumentStatus.ISSUED)
                .pricingBasis(PricingBasis.INCLUSIVE)
                .taxPointDate(LocalDateTime.now())
                .isReplaced(true)
                .replacingInvoice(invA)
                .invoiceSeries("DEMO1C26TAA")
                .invoiceNumber("DEMO-0000002")
                .idempotencyKey("REP_CYCLE_B_" + UUID.randomUUID())
                .build());

        // Create cycle: A -> B, and B -> A
        invA.setReplacedByInvoice(invB);
        invoiceRepository.save(invA);

        invB.setReplacedByInvoice(invA);
        invoiceRepository.save(invB);

        // System must detect cycle and return without StackOverflow/hang
        mockMvc.perform(get("/api/orders/{orderId}/invoice", orderA.getId())
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("EINV-RESET-3.3-01: Original ISSUED, không Replacement -> trả Original, không có cảnh báo")
    void customerGetInvoiceByOrderId_originalIssued_noReplacement() throws Exception {
        Order orderA = createOrder(customerA);
        Invoice original = createInvoice(orderA, buyerProfileA, InvoiceType.ORIGINAL, false);

        mockMvc.perform(get("/api/orders/{orderId}/invoice", orderA.getId())
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(original.getId()))
                .andExpect(jsonPath("$.invoiceType").value("ORIGINAL"))
                .andExpect(jsonPath("$.documentStatus").value("ISSUED"))
                .andExpect(jsonPath("$.replacementNotice").doesNotExist());
    }

    @Test
    @DisplayName("EINV-RESET-3.3-02: Original ISSUED, Replacement ISSUED liên kết hợp lệ -> trả Replacement")
    void customerGetInvoiceByOrderId_originalIssued_replacementIssued_returnsReplacement() throws Exception {
        Order orderA = createOrder(customerA);
        Invoice original = createInvoice(orderA, buyerProfileA, InvoiceType.ORIGINAL, true);

        Invoice rep = invoiceRepository.save(Invoice.builder()
                .order(orderA)
                .buyerProfile(buyerProfileA)
                .sellerProfile(sellerProfile)
                .invoiceType(InvoiceType.REPLACEMENT)
                .documentStatus(DocumentStatus.ISSUED)
                .pricingBasis(PricingBasis.INCLUSIVE)
                .taxPointDate(LocalDateTime.now())
                .isReplaced(false)
                .replacingInvoice(original)
                .invoiceSeries("DEMO1C26TAA")
                .invoiceNumber("DEMO-0000088")
                .idempotencyKey("REP_ISSUED_" + UUID.randomUUID())
                .build());

        original.setReplacedByInvoice(rep);
        invoiceRepository.save(original);

        mockMvc.perform(get("/api/orders/{orderId}/invoice", orderA.getId())
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(rep.getId()))
                .andExpect(jsonPath("$.invoiceType").value("REPLACEMENT"))
                .andExpect(jsonPath("$.documentStatus").value("ISSUED"))
                .andExpect(jsonPath("$.replacingInvoiceId").value(original.getId()));
    }

    @Test
    @DisplayName("EINV-RESET-3.3-03: Original ISSUED, Replacement DRAFT -> trả Original và cảnh báo đang xử lý")
    void customerGetInvoiceByOrderId_originalIssued_replacementDraft_returnsOriginalWithWarning() throws Exception {
        Order orderA = createOrder(customerA);
        Invoice original = createInvoice(orderA, buyerProfileA, InvoiceType.ORIGINAL, true);

        Invoice repDraft = invoiceRepository.save(Invoice.builder()
                .order(orderA)
                .buyerProfile(buyerProfileA)
                .sellerProfile(sellerProfile)
                .invoiceType(InvoiceType.REPLACEMENT)
                .documentStatus(DocumentStatus.DRAFT)
                .pricingBasis(PricingBasis.INCLUSIVE)
                .taxPointDate(LocalDateTime.now())
                .isReplaced(false)
                .replacingInvoice(original)
                .idempotencyKey("REP_DRAFT_" + UUID.randomUUID())
                .build());

        original.setReplacedByInvoice(repDraft);
        invoiceRepository.save(original);

        mockMvc.perform(get("/api/orders/{orderId}/invoice", orderA.getId())
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(original.getId()))
                .andExpect(jsonPath("$.invoiceType").value("ORIGINAL"))
                .andExpect(jsonPath("$.documentStatus").value("ISSUED"))
                .andExpect(jsonPath("$.replacementStatus").value("DRAFT"))
                .andExpect(jsonPath("$.replacementNotice").isNotEmpty());
    }

    @Test
    @DisplayName("EINV-RESET-3.3-04: Original ISSUED, Replacement ISSUE_FAILED -> trả Original và cảnh báo thất bại")
    void customerGetInvoiceByOrderId_originalIssued_replacementFailed_returnsOriginalWithWarning() throws Exception {
        Order orderA = createOrder(customerA);
        Invoice original = createInvoice(orderA, buyerProfileA, InvoiceType.ORIGINAL, true);

        Invoice repFailed = invoiceRepository.save(Invoice.builder()
                .order(orderA)
                .buyerProfile(buyerProfileA)
                .sellerProfile(sellerProfile)
                .invoiceType(InvoiceType.REPLACEMENT)
                .documentStatus(DocumentStatus.ISSUE_FAILED)
                .pricingBasis(PricingBasis.INCLUSIVE)
                .taxPointDate(LocalDateTime.now())
                .isReplaced(false)
                .replacingInvoice(original)
                .idempotencyKey("REP_FAILED_" + UUID.randomUUID())
                .build());

        original.setReplacedByInvoice(repFailed);
        invoiceRepository.save(original);

        mockMvc.perform(get("/api/orders/{orderId}/invoice", orderA.getId())
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(original.getId()))
                .andExpect(jsonPath("$.invoiceType").value("ORIGINAL"))
                .andExpect(jsonPath("$.documentStatus").value("ISSUED"))
                .andExpect(jsonPath("$.replacementStatus").value("ISSUE_FAILED"))
                .andExpect(jsonPath("$.replacementNotice").isNotEmpty());
    }

    @Test
    @DisplayName("EINV-RESET-3.3-05: Replacement mồ côi có ID lớn hơn không được chọn thay cho Original")
    void customerGetInvoiceByOrderId_orphanedReplacementWithHigherId_ignored() throws Exception {
        Order orderA = createOrder(customerA);
        Invoice original = createInvoice(orderA, buyerProfileA, InvoiceType.ORIGINAL, false);

        Invoice repOrphan = invoiceRepository.save(Invoice.builder()
                .order(orderA)
                .buyerProfile(buyerProfileA)
                .sellerProfile(sellerProfile)
                .invoiceType(InvoiceType.REPLACEMENT)
                .documentStatus(DocumentStatus.ISSUED)
                .pricingBasis(PricingBasis.INCLUSIVE)
                .taxPointDate(LocalDateTime.now())
                .isReplaced(false)
                .invoiceSeries("DEMO1C26TAA")
                .invoiceNumber("DEMO-0099999")
                .idempotencyKey("REP_ORPHAN_" + UUID.randomUUID())
                .build());

        mockMvc.perform(get("/api/orders/{orderId}/invoice", orderA.getId())
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(original.getId()))
                .andExpect(jsonPath("$.invoiceType").value("ORIGINAL"))
                .andExpect(jsonPath("$.documentStatus").value("ISSUED"));
    }

    @Test
    @DisplayName("EINV-RESET-3.3-08: DRAFT trong Mock configuration -> isDemo=false, demoMode=true, demoNotice != null")
    void customerGetInvoice_draftInMockConfig_isDemoFalseDemoModeTrue() throws Exception {
        Order orderA = createOrder(customerA);
        Invoice draftInvoice = invoiceRepository.save(Invoice.builder()
                .order(orderA)
                .buyerProfile(buyerProfileA)
                .sellerProfile(sellerProfile)
                .invoiceType(InvoiceType.ORIGINAL)
                .documentStatus(DocumentStatus.DRAFT)
                .pricingBasis(PricingBasis.INCLUSIVE)
                .taxPointDate(LocalDateTime.now())
                .isReplaced(false)
                .idempotencyKey("DRAFT_KEY_" + UUID.randomUUID())
                .build());

        mockMvc.perform(get("/api/invoices/{id}", draftInvoice.getId())
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(draftInvoice.getId()))
                .andExpect(jsonPath("$.documentStatus").value("DRAFT"))
                .andExpect(jsonPath("$.isDemo").value(false))
                .andExpect(jsonPath("$.demoMode").value(true))
                .andExpect(jsonPath("$.demoNotice").isNotEmpty());
    }

    @Test
    @DisplayName("EINV-RESET-3.3-09: MISA invoice không bị đánh dấu Demo -> isDemo=false, demoMode=false, demoNotice=null")
    void customerGetInvoice_misaInvoice_notMarkedAsDemo() throws Exception {
        Order orderA = createOrder(customerA);
        Invoice misaInvoice = invoiceRepository.save(Invoice.builder()
                .order(orderA)
                .buyerProfile(buyerProfileA)
                .sellerProfile(sellerProfile)
                .invoiceType(InvoiceType.ORIGINAL)
                .documentStatus(DocumentStatus.ISSUED)
                .pricingBasis(PricingBasis.INCLUSIVE)
                .taxPointDate(LocalDateTime.now())
                .officiallyIssuedAt(LocalDateTime.now())
                .isReplaced(false)
                .storageProvider("LOCAL")
                .providerTransactionId(UUID.randomUUID().toString())
                .invoiceSeries("1C26TAA")
                .invoiceNumber("00000001")
                .taxAuthorityCode("MTC_MISA_001")
                .idempotencyKey("MISA_KEY_" + UUID.randomUUID())
                .build());

        mockMvc.perform(get("/api/invoices/{id}", misaInvoice.getId())
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(misaInvoice.getId()))
                .andExpect(jsonPath("$.documentStatus").value("ISSUED"))
                .andExpect(jsonPath("$.isDemo").value(false))
                .andExpect(jsonPath("$.demoMode").value(false))
                .andExpect(jsonPath("$.demoNotice").doesNotExist());
    }

    @Test
    @DisplayName("EINV-RESET-3.3-10: Mock-issued invoice -> isDemo=true, demoMode=true, demoNotice != null")
    void customerGetInvoice_mockIssued_isDemoTrue() throws Exception {
        Order orderA = createOrder(customerA);
        Invoice mockInvoice = createInvoice(orderA, buyerProfileA, InvoiceType.ORIGINAL, false);

        mockMvc.perform(get("/api/invoices/{id}", mockInvoice.getId())
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(mockInvoice.getId()))
                .andExpect(jsonPath("$.documentStatus").value("ISSUED"))
                .andExpect(jsonPath("$.isDemo").value(true))
                .andExpect(jsonPath("$.demoMode").value(true))
                .andExpect(jsonPath("$.demoNotice").isNotEmpty());
    }
}
