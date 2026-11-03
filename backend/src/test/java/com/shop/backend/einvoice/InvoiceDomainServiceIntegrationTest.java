package com.shop.backend.einvoice;

import com.shop.backend.common.model.DataSourceType;
import com.shop.backend.einvoice.dto.CreateFulfillmentEventCommand;
import com.shop.backend.einvoice.dto.CreateFulfillmentItemCommand;
import com.shop.backend.einvoice.dto.CreateReplacementInvoiceCommand;
import com.shop.backend.einvoice.dto.InvoiceCalculationCommand;
import com.shop.backend.einvoice.dto.InvoiceLineCalculationCommand;
import com.shop.backend.einvoice.exception.InvoiceIdempotencyException;
import com.shop.backend.einvoice.model.BuyerInvoiceProfile;
import com.shop.backend.einvoice.model.CustomerType;
import com.shop.backend.einvoice.model.DocumentStatus;
import com.shop.backend.einvoice.model.FulfillmentEvent;
import com.shop.backend.einvoice.model.Invoice;
import com.shop.backend.einvoice.model.InvoiceBalanceAdjustment;
import com.shop.backend.einvoice.model.InvoiceItem;
import com.shop.backend.einvoice.model.InvoiceOutbox;
import com.shop.backend.einvoice.model.InvoiceType;
import com.shop.backend.einvoice.model.OutboxStatus;
import com.shop.backend.einvoice.model.PricingBasis;
import com.shop.backend.einvoice.model.ProofSource;
import com.shop.backend.einvoice.model.SellerInvoiceProfile;
import com.shop.backend.einvoice.model.TaxConfiguration;
import com.shop.backend.einvoice.model.TaxTreatment;
import com.shop.backend.einvoice.repository.FulfillmentEventItemRepository;
import com.shop.backend.einvoice.repository.FulfillmentEventRepository;
import com.shop.backend.einvoice.repository.InvoiceBalanceAdjustmentRepository;
import com.shop.backend.einvoice.repository.InvoiceItemRepository;
import com.shop.backend.einvoice.repository.InvoiceOutboxRepository;
import com.shop.backend.einvoice.repository.InvoiceRepository;
import com.shop.backend.einvoice.repository.TaxConfigurationRepository;
import com.shop.backend.einvoice.service.FulfillmentService;
import com.shop.backend.einvoice.service.InvoiceDomainService;
import com.shop.backend.order.Order;
import com.shop.backend.order.OrderDetail;
import com.shop.backend.order.OrderDetailRepository;
import com.shop.backend.order.OrderRepository;
import com.shop.backend.order.OrderStatus;
import com.shop.backend.payment.PaymentMethod;
import com.shop.backend.product.Category;
import com.shop.backend.product.CategoryRepository;
import com.shop.backend.product.Product;
import com.shop.backend.product.ProductRepository;
import com.shop.backend.product.ProductVariant;
import com.shop.backend.product.ProductVariantRepository;
import com.shop.backend.user.Role;
import com.shop.backend.user.RoleRepository;
import com.shop.backend.user.User;
import com.shop.backend.user.UserRepository;
import com.shop.backend.user.UserStatus;
import org.springframework.dao.DataIntegrityViolationException;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
public class InvoiceDomainServiceIntegrationTest {

    private static final String TEST_PREFIX = "einv-dom-";

    @Autowired
    private InvoiceDomainService invoiceDomainService;

    @Autowired
    private FulfillmentService fulfillmentService;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private InvoiceItemRepository invoiceItemRepository;

    @Autowired
    private InvoiceBalanceAdjustmentRepository invoiceBalanceAdjustmentRepository;

    @Autowired
    private InvoiceOutboxRepository invoiceOutboxRepository;

    @Autowired
    private TaxConfigurationRepository taxConfigurationRepository;

    @Autowired
    private FulfillmentEventRepository fulfillmentEventRepository;

    @Autowired
    private FulfillmentEventItemRepository fulfillmentEventItemRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderDetailRepository orderDetailRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

    private TransactionTemplate transactionTemplate;

    private Long testOrderId;
    private Long testDetailId1;
    private Long testDetailId2;
    private Long testFulfillmentEventId;
    private Long testVariantId;
    private Long testProductId;
    private Long testCategoryId;
    private Long testUserId;
    private Long testTaxConfigClothingId;
    private Long testTaxConfigShippingId;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);

        cleanTestData();

        transactionTemplate.execute(status -> {
            // Seed Tax Configurations
            TaxConfiguration taxClothing = TaxConfiguration.builder()
                    .taxCategory("clothing")
                    .pricingBasis(PricingBasis.INCLUSIVE)
                    .taxTreatment(TaxTreatment.TAXABLE)
                    .taxRate(new BigDecimal("0.0800"))
                    .taxCode("VAT8")
                    .effectiveFrom(LocalDateTime.now().minusDays(10))
                    .effectiveTo(LocalDateTime.now().plusYears(1))
                    .legalReference("Decree 254/2026")
                    .build();
            testTaxConfigClothingId = taxConfigurationRepository.save(taxClothing).getId();

            TaxConfiguration taxShipping = TaxConfiguration.builder()
                    .taxCategory("shipping")
                    .pricingBasis(PricingBasis.INCLUSIVE)
                    .taxTreatment(TaxTreatment.TAXABLE)
                    .taxRate(new BigDecimal("0.0800"))
                    .taxCode("VAT8")
                    .effectiveFrom(LocalDateTime.now().minusDays(10))
                    .effectiveTo(LocalDateTime.now().plusYears(1))
                    .legalReference("Decree 254/2026")
                    .build();
            testTaxConfigShippingId = taxConfigurationRepository.save(taxShipping).getId();

            // Seed User and Catalog
            Role customerRole = roleRepository.findByName("customer")
                    .orElseGet(() -> roleRepository.save(new Role(null, "customer")));

            User user = userRepository.save(User.builder()
                    .email(TEST_PREFIX + "user_" + System.nanoTime() + "@example.com")
                    .passwordHash("secret123")
                    .name(TEST_PREFIX + "Invoice User")
                    .phone(String.format("09%08d", Math.abs((System.nanoTime() + 2222) % 100000000L)))
                    .role(customerRole)
                    .status(UserStatus.ACTIVE)
                    .dataSource(DataSourceType.WEBSITE)
                    .build());
            testUserId = user.getId();

            Category cat = categoryRepository.save(Category.builder()
                    .name(TEST_PREFIX + "Category")
                    .slug(TEST_PREFIX + "cat-" + System.nanoTime())
                    .isActive(true)
                    .build());
            testCategoryId = cat.getId();

            Product product = productRepository.save(Product.builder()
                    .category(cat)
                    .name(TEST_PREFIX + "Jacket")
                    .slug(TEST_PREFIX + "prod-" + System.nanoTime())
                    .basePrice(new BigDecimal("180000.00"))
                    .isActive(true)
                    .dataSource(DataSourceType.WEBSITE)
                    .build());
            testProductId = product.getId();

            ProductVariant variant = productVariantRepository.save(ProductVariant.builder()
                    .product(product)
                    .sku(TEST_PREFIX + "SKU-" + System.nanoTime())
                    .size("L")
                    .color("Black")
                    .stock(100)
                    .price(new BigDecimal("180000.00"))
                    .build());
            testVariantId = variant.getId();

            // Seed Order with 2 line items and shipping fee
            Order order = Order.builder()
                    .user(user)
                    .receiverName(TEST_PREFIX + "Buyer Name")
                    .receiverPhone("0988776655")
                    .province("Hà Nội")
                    .paymentMethod(PaymentMethod.COD)
                    .subtotal(new BigDecimal("560000.00"))
                    .discountAmount(new BigDecimal("70000.00"))
                    .shippingFee(new BigDecimal("30000.00"))
                    .totalAmount(new BigDecimal("520000.00"))
                    .status(OrderStatus.SHIPPING)
                    .dataSource(DataSourceType.WEBSITE)
                    .createdAt(LocalDateTime.now().minusDays(1))
                    .build();

            OrderDetail d1 = OrderDetail.builder()
                    .variant(variant)
                    .productName(TEST_PREFIX + "Jacket L")
                    .variantSize("L")
                    .variantColor("Black")
                    .quantity(2)
                    .unitPrice(new BigDecimal("180000.00"))
                    .discountAmount(BigDecimal.ZERO)
                    .build();
            order.addOrderDetail(d1);

            OrderDetail d2 = OrderDetail.builder()
                    .variant(variant)
                    .productName(TEST_PREFIX + "Jacket XL")
                    .variantSize("XL")
                    .variantColor("Black")
                    .quantity(1)
                    .unitPrice(new BigDecimal("200000.00"))
                    .discountAmount(BigDecimal.ZERO)
                    .build();
            order.addOrderDetail(d2);

            Order savedOrder = orderRepository.save(order);
            testOrderId = savedOrder.getId();
            testDetailId1 = savedOrder.getOrderDetails().get(0).getId();
            testDetailId2 = savedOrder.getOrderDetails().get(1).getId();

            return null;
        });

        // Record Fulfillment Event for this order
        CreateFulfillmentEventCommand fCmd = CreateFulfillmentEventCommand.builder()
                .orderId(testOrderId)
                .fulfillmentCode(TEST_PREFIX + "FUL_" + System.nanoTime())
                .carrierName("GHN")
                .trackingNumber(TEST_PREFIX + "TRK_" + System.nanoTime())
                .deliveredAt(LocalDateTime.now())
                .proofSource(ProofSource.CARRIER_WEBHOOK)
                .proofReference("PROOF_REF_001")
                .items(List.of(
                        CreateFulfillmentItemCommand.builder()
                                .orderDetailId(testDetailId1)
                                .deliveredQuantity(2)
                                .unitPriceSnapshot(new BigDecimal("180000.00"))
                                .discountAllocated(BigDecimal.ZERO)
                                .build(),
                        CreateFulfillmentItemCommand.builder()
                                .orderDetailId(testDetailId2)
                                .deliveredQuantity(1)
                                .unitPriceSnapshot(new BigDecimal("200000.00"))
                                .discountAllocated(BigDecimal.ZERO)
                                .build()
                ))
                .build();

        FulfillmentEvent fe = fulfillmentService.recordFulfillment(fCmd);
        testFulfillmentEventId = fe.getId();
    }

    @AfterEach
    void tearDown() {
        cleanTestData();
    }

    private void cleanTestData() {
        transactionTemplate.execute(status -> {
            entityManager.createQuery("DELETE FROM InvoiceOutbox io WHERE io.eventKey LIKE :prefix")
                    .setParameter("prefix", TEST_PREFIX + "%")
                    .executeUpdate();
            entityManager.createQuery("DELETE FROM InvoiceBalanceAdjustment iba WHERE iba.originalInvoice.idempotencyKey LIKE :prefix")
                    .setParameter("prefix", TEST_PREFIX + "%")
                    .executeUpdate();
            entityManager.createQuery("DELETE FROM InvoiceItem ii WHERE ii.originalInvoiceItem IS NOT NULL AND ii.itemName LIKE :prefix")
                    .setParameter("prefix", TEST_PREFIX + "%")
                    .executeUpdate();
            entityManager.createQuery("DELETE FROM InvoiceItem ii WHERE ii.itemName LIKE :prefix")
                    .setParameter("prefix", TEST_PREFIX + "%")
                    .executeUpdate();

            // Nullify self-referencing replacement and adjustment FKs before deleting invoices
            entityManager.createNativeQuery("UPDATE invoice_items SET original_invoice_item_id = NULL")
                    .executeUpdate();
            entityManager.createNativeQuery("UPDATE invoices SET replacing_invoice_id = NULL, replaced_by_invoice_id = NULL, original_invoice_id = NULL")
                    .executeUpdate();

            entityManager.createQuery("DELETE FROM Invoice i WHERE i.originalInvoice IS NOT NULL AND i.idempotencyKey LIKE :prefix")
                    .setParameter("prefix", TEST_PREFIX + "%")
                    .executeUpdate();
            entityManager.createQuery("DELETE FROM Invoice i WHERE i.idempotencyKey LIKE :prefix")
                    .setParameter("prefix", TEST_PREFIX + "%")
                    .executeUpdate();
            entityManager.createQuery("DELETE FROM FulfillmentEventItem fei WHERE fei.fulfillmentEvent.fulfillmentCode LIKE :prefix")
                    .setParameter("prefix", TEST_PREFIX + "%")
                    .executeUpdate();
            entityManager.createQuery("DELETE FROM FulfillmentEvent fe WHERE fe.fulfillmentCode LIKE :prefix")
                    .setParameter("prefix", TEST_PREFIX + "%")
                    .executeUpdate();
            entityManager.createQuery("DELETE FROM OrderDetail od WHERE od.productName LIKE :prefix")
                    .setParameter("prefix", TEST_PREFIX + "%")
                    .executeUpdate();
            entityManager.createQuery("DELETE FROM Order o WHERE o.receiverName LIKE :prefix")
                    .setParameter("prefix", TEST_PREFIX + "%")
                    .executeUpdate();
            entityManager.createQuery("DELETE FROM ProductVariant pv WHERE pv.sku LIKE :prefix")
                    .setParameter("prefix", TEST_PREFIX + "%")
                    .executeUpdate();
            entityManager.createQuery("DELETE FROM Product p WHERE p.name LIKE :prefix")
                    .setParameter("prefix", TEST_PREFIX + "%")
                    .executeUpdate();
            entityManager.createQuery("DELETE FROM Category c WHERE c.name LIKE :prefix")
                    .setParameter("prefix", TEST_PREFIX + "%")
                    .executeUpdate();
            entityManager.createQuery("DELETE FROM User u WHERE u.name LIKE :prefix")
                    .setParameter("prefix", TEST_PREFIX + "%")
                    .executeUpdate();

            entityManager.createQuery("DELETE FROM TaxConfiguration tc WHERE tc.legalReference = 'Decree 254/2026'")
                    .executeUpdate();

            return null;
        });
    }

    @Test
    @DisplayName("createOriginalInvoice - creates draft invoice, line items, balance, and outbox atomically")
    void createOriginalInvoice_success() {
        BuyerInvoiceProfile buyer = BuyerInvoiceProfile.builder()
                .customerType(CustomerType.INDIVIDUAL)
                .buyerName("Nguyen Van A")
                .address("123 Le Loi, TP.HCM")
                .email("buyer@example.com")
                .phoneNumber("0988776655")
                .isImmutable(false)
                .build();

        SellerInvoiceProfile seller = SellerInvoiceProfile.builder()
                .sellerLegalName("CLOSET SHOP VIETNAM CO., LTD")
                .sellerBrandName("Closet-Shop")
                .sellerTaxCode("0101234567")
                .sellerAddress("789 Nguyen Hue, Quan 1, TP.HCM")
                .sellerPhone("02838222999")
                .sellerEmail("einvoice@closetshop.vn")
                .isImmutable(false)
                .build();

        InvoiceCalculationCommand calcCmd = InvoiceCalculationCommand.builder()
                .orderId(testOrderId)
                .fulfillmentEventId(testFulfillmentEventId)
                .taxPoint(LocalDateTime.now())
                .pricingBasis(PricingBasis.INCLUSIVE)
                .merchandiseVoucherAmount(new BigDecimal("70000.00"))
                .shippingFee(new BigDecimal("30000.00"))
                .shippingPromotionAmount(BigDecimal.ZERO)
                .shippingTaxCategory("shipping")
                .items(List.of(
                        InvoiceLineCalculationCommand.builder()
                                .orderDetailId(testDetailId1)
                                .itemName(TEST_PREFIX + "Jacket L")
                                .unit("cai")
                                .quantity(2)
                                .unitPrice(new BigDecimal("180000.00"))
                                .taxCategory("clothing")
                                .isDiscountable(true)
                                .build(),
                        InvoiceLineCalculationCommand.builder()
                                .orderDetailId(testDetailId2)
                                .itemName(TEST_PREFIX + "Jacket XL")
                                .unit("cai")
                                .quantity(1)
                                .unitPrice(new BigDecimal("200000.00"))
                                .taxCategory("clothing")
                                .isDiscountable(true)
                                .build()
                ))
                .build();

        String idempotencyKey = TEST_PREFIX + "ORIG_IDEM_" + System.nanoTime();

        Invoice invoice = invoiceDomainService.createOriginalInvoice(
                testFulfillmentEventId,
                idempotencyKey,
                buyer,
                seller,
                calcCmd
        );

        assertThat(invoice).isNotNull();
        assertThat(invoice.getId()).isNotNull();
        assertThat(invoice.getInvoiceType()).isEqualTo(InvoiceType.ORIGINAL);
        assertThat(invoice.getDocumentStatus()).isEqualTo(DocumentStatus.DRAFT);
        assertThat(invoice.getOriginalInvoice()).isNull();
        assertThat(invoice.getIdempotencyKey()).isEqualTo(idempotencyKey);

        // Verification of monetary totals
        // Merchandise: 360k + 200k - 70k voucher = 490k
        // Shipping: 30k
        // Total Gross: 520k
        assertThat(invoice.getTotalAfterTax()).isEqualByComparingTo(new BigDecimal("520000.00"));
        assertThat(invoice.getTotalMerchandiseDiscount()).isEqualByComparingTo(new BigDecimal("70000.00"));
        assertThat(invoice.getTotalShippingBeforeTax()).isEqualByComparingTo(new BigDecimal("30000.00"));

        // Taxable + Tax Amount must exactly equal Total Gross
        BigDecimal sumTaxableAndTax = invoice.getTotalTaxableAmount().add(invoice.getTotalTaxAmount());
        assertThat(sumTaxableAndTax).isEqualByComparingTo(invoice.getTotalAfterTax());

        // Verify Line Items
        List<InvoiceItem> items = invoiceDomainService.getInvoiceItems(invoice.getId());
        assertThat(items).hasSize(3); // 2 merchandise items + 1 shipping fee

        // Verify Initial Balance Adjustments (only merchandise items)
        List<InvoiceBalanceAdjustment> balances = invoiceBalanceAdjustmentRepository.findByOriginalInvoiceId(invoice.getId());
        assertThat(balances).hasSize(2);
        for (InvoiceBalanceAdjustment b : balances) {
            assertThat(b.getRemainingQuantity()).isGreaterThanOrEqualTo(1);
            assertThat(b.getRemainingTaxableAmount()).isPositive();
            assertThat(b.getRemainingTaxAmount()).isPositive();
        }

        // Verify Outbox Event created
        List<InvoiceOutbox> outboxEvents = invoiceOutboxRepository.findByInvoiceId(invoice.getId());
        assertThat(outboxEvents).hasSize(1);
        InvoiceOutbox outbox = outboxEvents.get(0);
        assertThat(outbox.getEventType()).isEqualTo("ISSUE_INVOICE");
        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.PENDING);
    }

    @Test
    @DisplayName("createOriginalInvoice - throws InvoiceIdempotencyException on duplicate fulfillment event")
    void createOriginalInvoice_duplicateFulfillmentEvent_throwsException() {
        BuyerInvoiceProfile buyer = BuyerInvoiceProfile.builder()
                .customerType(CustomerType.INDIVIDUAL)
                .buyerName("Nguyen Van A")
                .address("123 Le Loi, TP.HCM")
                .email("buyer@example.com")
                .phoneNumber("0988776655")
                .build();

        SellerInvoiceProfile seller = SellerInvoiceProfile.builder()
                .sellerLegalName("CLOSET SHOP VIETNAM")
                .sellerBrandName("Closet-Shop")
                .sellerTaxCode("0101234567")
                .sellerAddress("789 Nguyen Hue, Q1")
                .sellerPhone("02838222999")
                .build();

        InvoiceCalculationCommand calcCmd = InvoiceCalculationCommand.builder()
                .orderId(testOrderId)
                .fulfillmentEventId(testFulfillmentEventId)
                .taxPoint(LocalDateTime.now())
                .pricingBasis(PricingBasis.INCLUSIVE)
                .merchandiseVoucherAmount(BigDecimal.ZERO)
                .shippingFee(BigDecimal.ZERO)
                .shippingPromotionAmount(BigDecimal.ZERO)
                .shippingTaxCategory("shipping")
                .items(List.of(
                        InvoiceLineCalculationCommand.builder()
                                .orderDetailId(testDetailId1)
                                .itemName(TEST_PREFIX + "Jacket")
                                .unit("cai")
                                .quantity(1)
                                .unitPrice(new BigDecimal("180000.00"))
                                .taxCategory("clothing")
                                .build()
                ))
                .build();

        // First creation succeeds
        invoiceDomainService.createOriginalInvoice(
                testFulfillmentEventId,
                TEST_PREFIX + "KEY_1_" + System.nanoTime(),
                buyer,
                seller,
                calcCmd
        );

        // Second creation on SAME fulfillment event must fail
        assertThatThrownBy(() -> invoiceDomainService.createOriginalInvoice(
                testFulfillmentEventId,
                TEST_PREFIX + "KEY_2_" + System.nanoTime(),
                buyer,
                seller,
                calcCmd
        ))
                .isInstanceOf(InvoiceIdempotencyException.class)
                .hasMessageContaining("An ORIGINAL invoice already exists for fulfillment event");
    }

    @Test
    @DisplayName("createOriginalInvoice - throws InvoiceIdempotencyException on duplicate idempotency key")
    void createOriginalInvoice_duplicateIdempotencyKey_throwsException() {
        BuyerInvoiceProfile buyer = BuyerInvoiceProfile.builder()
                .customerType(CustomerType.INDIVIDUAL)
                .buyerName("Nguyen Van A")
                .address("123 Le Loi, TP.HCM")
                .email("buyer@example.com")
                .phoneNumber("0988776655")
                .build();

        SellerInvoiceProfile seller = SellerInvoiceProfile.builder()
                .sellerLegalName("CLOSET SHOP VIETNAM")
                .sellerBrandName("Closet-Shop")
                .sellerTaxCode("0101234567")
                .sellerAddress("789 Nguyen Hue, Q1")
                .sellerPhone("02838222999")
                .build();

        InvoiceCalculationCommand calcCmd = InvoiceCalculationCommand.builder()
                .orderId(testOrderId)
                .fulfillmentEventId(testFulfillmentEventId)
                .taxPoint(LocalDateTime.now())
                .pricingBasis(PricingBasis.INCLUSIVE)
                .merchandiseVoucherAmount(BigDecimal.ZERO)
                .shippingFee(BigDecimal.ZERO)
                .shippingPromotionAmount(BigDecimal.ZERO)
                .shippingTaxCategory("shipping")
                .items(List.of(
                        InvoiceLineCalculationCommand.builder()
                                .orderDetailId(testDetailId1)
                                .itemName(TEST_PREFIX + "Jacket")
                                .unit("cai")
                                .quantity(1)
                                .unitPrice(new BigDecimal("180000.00"))
                                .taxCategory("clothing")
                                .build()
                ))
                .build();

        String duplicateKey = TEST_PREFIX + "SAME_KEY_" + System.nanoTime();

        // First creation succeeds
        invoiceDomainService.createOriginalInvoice(
                testFulfillmentEventId,
                duplicateKey,
                buyer,
                seller,
                calcCmd
        );

        // Second creation with SAME idempotency key must fail immediately
        assertThatThrownBy(() -> invoiceDomainService.createOriginalInvoice(
                testFulfillmentEventId + 999L,
                duplicateKey,
                buyer,
                seller,
                calcCmd
        ))
                .isInstanceOf(InvoiceIdempotencyException.class)
                .hasMessageContaining("Idempotency key already exists");
    }

    @Test
    @DisplayName("createOriginalInvoice - ensures buyer and seller profiles are marked immutable")
    void createOriginalInvoice_buyerAndSellerProfilesAreImmutable() {
        BuyerInvoiceProfile buyer = BuyerInvoiceProfile.builder()
                .customerType(CustomerType.ENTERPRISE)
                .buyerName("Nguyen Van A")
                .companyName("Cong Ty TNHH Thoi Trang ABC")
                .taxCode("0312345678")
                .address("456 Hai Ba Trung, Quan 3, TP.HCM")
                .email("accounting@abc.vn")
                .phoneNumber("0988776655")
                .isImmutable(false)
                .build();

        SellerInvoiceProfile seller = SellerInvoiceProfile.builder()
                .sellerLegalName("CLOSET SHOP VIETNAM CO., LTD")
                .sellerBrandName("Closet-Shop")
                .sellerTaxCode("0101234567")
                .sellerAddress("789 Nguyen Hue, Quan 1, TP.HCM")
                .sellerPhone("02838222999")
                .sellerEmail("einvoice@closetshop.vn")
                .isImmutable(false)
                .build();

        InvoiceCalculationCommand calcCmd = InvoiceCalculationCommand.builder()
                .orderId(testOrderId)
                .fulfillmentEventId(testFulfillmentEventId)
                .taxPoint(LocalDateTime.now())
                .pricingBasis(PricingBasis.INCLUSIVE)
                .merchandiseVoucherAmount(BigDecimal.ZERO)
                .shippingFee(BigDecimal.ZERO)
                .shippingPromotionAmount(BigDecimal.ZERO)
                .shippingTaxCategory("shipping")
                .items(List.of(
                        InvoiceLineCalculationCommand.builder()
                                .orderDetailId(testDetailId1)
                                .itemName(TEST_PREFIX + "Jacket")
                                .unit("cai")
                                .quantity(1)
                                .unitPrice(new BigDecimal("180000.00"))
                                .taxCategory("clothing")
                                .build()
                ))
                .build();

        Invoice invoice = invoiceDomainService.createOriginalInvoice(
                testFulfillmentEventId,
                TEST_PREFIX + "IMMUTABLE_KEY_" + System.nanoTime(),
                buyer,
                seller,
                calcCmd
        );

        assertThat(invoice.getBuyerProfile().getIsImmutable()).isTrue();
        assertThat(invoice.getSellerProfile().getIsImmutable()).isTrue();
    }

    @Test
    @DisplayName("createReplacementInvoice - marks target invoice as replaced while target remains ISSUED, replacement has independent lifecycle")
    void createReplacementInvoice_createsIndependentLifecycleAndMarksOriginalReplaced() {
        BuyerInvoiceProfile buyer = BuyerInvoiceProfile.builder()
                .customerType(CustomerType.INDIVIDUAL)
                .buyerName("Nguyen Van A")
                .address("123 Le Loi, TP.HCM")
                .email("buyer@example.com")
                .build();

        SellerInvoiceProfile seller = SellerInvoiceProfile.builder()
                .sellerLegalName("CLOSET SHOP VIETNAM")
                .sellerBrandName("Closet-Shop")
                .sellerTaxCode("0101234567")
                .sellerAddress("789 Nguyen Hue, Q1")
                .sellerPhone("02838222999")
                .build();

        InvoiceCalculationCommand calcCmd = InvoiceCalculationCommand.builder()
                .orderId(testOrderId)
                .fulfillmentEventId(testFulfillmentEventId)
                .taxPoint(LocalDateTime.now())
                .pricingBasis(PricingBasis.INCLUSIVE)
                .merchandiseVoucherAmount(BigDecimal.ZERO)
                .shippingFee(BigDecimal.ZERO)
                .shippingPromotionAmount(BigDecimal.ZERO)
                .shippingTaxCategory("shipping")
                .items(List.of(
                        InvoiceLineCalculationCommand.builder()
                                .orderDetailId(testDetailId1)
                                .itemName(TEST_PREFIX + "Jacket L")
                                .unit("cai")
                                .quantity(2)
                                .unitPrice(new BigDecimal("180000.00"))
                                .taxCategory("clothing")
                                .build()
                ))
                .build();

        // 1. Create original invoice
        Invoice original = invoiceDomainService.createOriginalInvoice(
                testFulfillmentEventId,
                TEST_PREFIX + "REPLACE_ORIG_" + System.nanoTime(),
                buyer,
                seller,
                calcCmd
        );

        // Transition original to ISSUED (mimicking successful external signing)
        transactionTemplate.execute(status -> {
            Invoice inv = invoiceRepository.findById(original.getId()).orElseThrow();
            inv.setDocumentStatus(DocumentStatus.ISSUED);
            return invoiceRepository.save(inv);
        });

        // 2. Create Replacement Invoice
        CreateReplacementInvoiceCommand repCmd = CreateReplacementInvoiceCommand.builder()
                .targetInvoiceId(original.getId())
                .idempotencyKey(TEST_PREFIX + "REPLACE_KEY_" + System.nanoTime())
                .replacementReason("Sai thong tin dia chi nguoi mua")
                .calculationCommand(calcCmd)
                .build();

        Invoice replacement = invoiceDomainService.createReplacementInvoice(repCmd);

        assertThat(replacement).isNotNull();
        assertThat(replacement.getInvoiceType()).isEqualTo(InvoiceType.REPLACEMENT);
        assertThat(replacement.getDocumentStatus()).isEqualTo(DocumentStatus.DRAFT);
        assertThat(replacement.getReplacingInvoice().getId()).isEqualTo(original.getId());

        // 3. Verify target invoice state: isReplaced = true, replacedByInvoice set, but documentStatus stays ISSUED!
        Invoice reloadedOriginal = invoiceDomainService.getInvoice(original.getId());
        assertThat(reloadedOriginal.getIsReplaced()).isTrue();
        assertThat(reloadedOriginal.getReplacedByInvoice().getId()).isEqualTo(replacement.getId());
        assertThat(reloadedOriginal.getDocumentStatus()).isEqualTo(DocumentStatus.ISSUED);

        // 4. Verify replacement has independent lifecycle
        Invoice pendingIssue = invoiceDomainService.transitionStatus(replacement.getId(), DocumentStatus.PENDING_ISSUE);
        assertThat(pendingIssue.getDocumentStatus()).isEqualTo(DocumentStatus.PENDING_ISSUE);

        Invoice issuing = invoiceDomainService.transitionStatus(replacement.getId(), DocumentStatus.ISSUING);
        assertThat(issuing.getDocumentStatus()).isEqualTo(DocumentStatus.ISSUING);

        Invoice issued = invoiceDomainService.transitionStatus(replacement.getId(), DocumentStatus.ISSUED);
        assertThat(issued.getDocumentStatus()).isEqualTo(DocumentStatus.ISSUED);
    }

    @Test
    @DisplayName("transitionStatus - enforces finite state machine and rejects invalid transitions")
    void transitionStatus_enforcesStateMachine() {
        BuyerInvoiceProfile buyer = BuyerInvoiceProfile.builder()
                .customerType(CustomerType.INDIVIDUAL)
                .buyerName("Nguyen Van A")
                .address("123 Le Loi, TP.HCM")
                .email("buyer@example.com")
                .build();

        SellerInvoiceProfile seller = SellerInvoiceProfile.builder()
                .sellerLegalName("CLOSET SHOP VIETNAM")
                .sellerBrandName("Closet-Shop")
                .sellerTaxCode("0101234567")
                .sellerAddress("789 Nguyen Hue, Q1")
                .sellerPhone("02838222999")
                .build();

        InvoiceCalculationCommand calcCmd = InvoiceCalculationCommand.builder()
                .orderId(testOrderId)
                .fulfillmentEventId(testFulfillmentEventId)
                .taxPoint(LocalDateTime.now())
                .pricingBasis(PricingBasis.INCLUSIVE)
                .merchandiseVoucherAmount(BigDecimal.ZERO)
                .shippingFee(BigDecimal.ZERO)
                .shippingPromotionAmount(BigDecimal.ZERO)
                .shippingTaxCategory("shipping")
                .items(List.of(
                        InvoiceLineCalculationCommand.builder()
                                .orderDetailId(testDetailId1)
                                .itemName(TEST_PREFIX + "Jacket")
                                .unit("cai")
                                .quantity(1)
                                .unitPrice(new BigDecimal("180000.00"))
                                .taxCategory("clothing")
                                .build()
                ))
                .build();

        Invoice invoice = invoiceDomainService.createOriginalInvoice(
                testFulfillmentEventId,
                TEST_PREFIX + "TRANS_KEY_" + System.nanoTime(),
                buyer,
                seller,
                calcCmd
        );

        // 1. Invalid jump: DRAFT directly to ISSUED must fail
        assertThatThrownBy(() -> invoiceDomainService.transitionStatus(invoice.getId(), DocumentStatus.ISSUED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid status transition from DRAFT to ISSUED");

        // 2. Valid path: DRAFT -> PENDING_ISSUE -> ISSUING -> ISSUANCE_UNKNOWN -> RECONCILING -> ISSUED
        invoiceDomainService.transitionStatus(invoice.getId(), DocumentStatus.PENDING_ISSUE);
        invoiceDomainService.transitionStatus(invoice.getId(), DocumentStatus.ISSUING);
        invoiceDomainService.transitionStatus(invoice.getId(), DocumentStatus.ISSUANCE_UNKNOWN);
        invoiceDomainService.transitionStatus(invoice.getId(), DocumentStatus.RECONCILING);
        Invoice issuedInv = invoiceDomainService.transitionStatus(invoice.getId(), DocumentStatus.ISSUED);
        assertThat(issuedInv.getDocumentStatus()).isEqualTo(DocumentStatus.ISSUED);

        // 3. Terminal status: Cannot transition from ISSUED to anything
        assertThatThrownBy(() -> invoiceDomainService.transitionStatus(invoice.getId(), DocumentStatus.DRAFT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid status transition from ISSUED to DRAFT");
    }

    @Test
    @DisplayName("documentStatus - database check constraint chk_invoice_doc_status rejects unapproved statuses")
    void documentStatus_databaseCheckConstraint_rejectsInvalidStatus() {
        BuyerInvoiceProfile buyer = BuyerInvoiceProfile.builder()
                .customerType(CustomerType.INDIVIDUAL)
                .buyerName("Nguyen Van A")
                .address("123 Le Loi, TP.HCM")
                .email("buyer@example.com")
                .build();

        SellerInvoiceProfile seller = SellerInvoiceProfile.builder()
                .sellerLegalName("CLOSET SHOP VIETNAM")
                .sellerBrandName("Closet-Shop")
                .sellerTaxCode("0101234567")
                .sellerAddress("789 Nguyen Hue, Q1")
                .sellerPhone("02838222999")
                .build();

        InvoiceCalculationCommand calcCmd = InvoiceCalculationCommand.builder()
                .orderId(testOrderId)
                .fulfillmentEventId(testFulfillmentEventId)
                .taxPoint(LocalDateTime.now())
                .pricingBasis(PricingBasis.INCLUSIVE)
                .merchandiseVoucherAmount(BigDecimal.ZERO)
                .shippingFee(BigDecimal.ZERO)
                .shippingPromotionAmount(BigDecimal.ZERO)
                .shippingTaxCategory("shipping")
                .items(List.of(
                        InvoiceLineCalculationCommand.builder()
                                .orderDetailId(testDetailId1)
                                .itemName(TEST_PREFIX + "Jacket")
                                .unit("cai")
                                .quantity(1)
                                .unitPrice(new BigDecimal("180000.00"))
                                .taxCategory("clothing")
                                .build()
                ))
                .build();

        Invoice invoice = invoiceDomainService.createOriginalInvoice(
                testFulfillmentEventId,
                TEST_PREFIX + "CHK_KEY_" + System.nanoTime(),
                buyer,
                seller,
                calcCmd
        );

        // Attempt to update document_status to 'cancelled' (which was removed from approved lifecycle statuses)
        assertThatThrownBy(() -> {
            transactionTemplate.execute(status -> {
                entityManager.createNativeQuery("UPDATE invoices SET document_status = 'cancelled' WHERE id = :id")
                        .setParameter("id", invoice.getId())
                        .executeUpdate();
                return null;
            });
        }).hasMessageContaining("chk_invoice_doc_status");
    }

    @Test
    @DisplayName("buyerProfile - data minimization: individual buyer does not require ID card/CCCD or tax code")
    void buyerProfile_dataMinimization_noIdCardRequired() {
        BuyerInvoiceProfile minimalBuyer = BuyerInvoiceProfile.builder()
                .customerType(CustomerType.INDIVIDUAL)
                .buyerName("Tran Thi B")
                .address("Ho Chi Minh City")
                .email("buyer.b@example.com")
                .taxCode(null) // No tax code required for retail individual
                .companyName(null)
                .phoneNumber(null)
                .build();

        SellerInvoiceProfile seller = SellerInvoiceProfile.builder()
                .sellerLegalName("CLOSET SHOP VIETNAM")
                .sellerBrandName("Closet-Shop")
                .sellerTaxCode("0101234567")
                .sellerAddress("789 Nguyen Hue, Q1")
                .sellerPhone("02838222999")
                .build();

        InvoiceCalculationCommand calcCmd = InvoiceCalculationCommand.builder()
                .orderId(testOrderId)
                .fulfillmentEventId(testFulfillmentEventId)
                .taxPoint(LocalDateTime.now())
                .pricingBasis(PricingBasis.INCLUSIVE)
                .merchandiseVoucherAmount(BigDecimal.ZERO)
                .shippingFee(BigDecimal.ZERO)
                .shippingPromotionAmount(BigDecimal.ZERO)
                .shippingTaxCategory("shipping")
                .items(List.of(
                        InvoiceLineCalculationCommand.builder()
                                .orderDetailId(testDetailId1)
                                .itemName(TEST_PREFIX + "Jacket")
                                .unit("cai")
                                .quantity(1)
                                .unitPrice(new BigDecimal("180000.00"))
                                .taxCategory("clothing")
                                .build()
                ))
                .build();

        Invoice invoice = invoiceDomainService.createOriginalInvoice(
                testFulfillmentEventId,
                TEST_PREFIX + "MINIMAL_KEY_" + System.nanoTime(),
                minimalBuyer,
                seller,
                calcCmd
        );

        assertThat(invoice.getBuyerProfile()).isNotNull();
        assertThat(invoice.getBuyerProfile().getCustomerType()).isEqualTo(CustomerType.INDIVIDUAL);
        assertThat(invoice.getBuyerProfile().getTaxCode()).isNull();
        assertThat(invoice.getBuyerProfile().getCompanyName()).isNull();
    }
}
