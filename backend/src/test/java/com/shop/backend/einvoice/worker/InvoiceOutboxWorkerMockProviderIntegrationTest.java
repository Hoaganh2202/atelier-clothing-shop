package com.shop.backend.einvoice.worker;

import static org.assertj.core.api.Assertions.assertThat;

import com.shop.backend.common.model.DataSourceType;
import com.shop.backend.einvoice.dto.CreateFulfillmentEventCommand;
import com.shop.backend.einvoice.dto.CreateFulfillmentItemCommand;
import com.shop.backend.einvoice.dto.InvoiceCalculationCommand;
import com.shop.backend.einvoice.dto.InvoiceLineCalculationCommand;
import com.shop.backend.einvoice.model.BuyerInvoiceProfile;
import com.shop.backend.einvoice.model.CustomerType;
import com.shop.backend.einvoice.model.DocumentStatus;
import com.shop.backend.einvoice.model.FulfillmentEvent;
import com.shop.backend.einvoice.model.Invoice;
import com.shop.backend.einvoice.model.InvoiceAttempt;
import com.shop.backend.einvoice.model.InvoiceOutbox;
import com.shop.backend.einvoice.model.OutboxStatus;
import com.shop.backend.einvoice.model.PricingBasis;
import com.shop.backend.einvoice.model.ProofSource;
import com.shop.backend.einvoice.model.SellerInvoiceProfile;
import com.shop.backend.einvoice.model.TaxConfiguration;
import com.shop.backend.einvoice.model.TaxTreatment;
import com.shop.backend.einvoice.repository.InvoiceAttemptRepository;
import com.shop.backend.einvoice.repository.InvoiceOutboxRepository;
import com.shop.backend.einvoice.repository.InvoiceRepository;
import com.shop.backend.einvoice.repository.TaxConfigurationRepository;
import com.shop.backend.einvoice.service.FulfillmentService;
import com.shop.backend.einvoice.service.InvoiceDomainService;
import com.shop.backend.order.Order;
import com.shop.backend.order.OrderDetail;
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
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class InvoiceOutboxWorkerMockProviderIntegrationTest {

    private static final String TEST_PREFIX = "mock-wrk-";

    @Autowired
    private InvoiceOutboxWorker invoiceOutboxWorker;

    @Autowired
    private InvoiceDomainService invoiceDomainService;

    @Autowired
    private FulfillmentService fulfillmentService;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private InvoiceOutboxRepository invoiceOutboxRepository;

    @Autowired
    private InvoiceAttemptRepository invoiceAttemptRepository;

    @Autowired
    private TaxConfigurationRepository taxConfigurationRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private EntityManager entityManager;

    private Long testUserId;
    private Long testVariantId;
    private Long testOrderId;
    private Long testOrderDetailId;

    @BeforeEach
    void setUp() {
        cleanTestData();

        transactionTemplate.execute(status -> {
            taxConfigurationRepository.save(TaxConfiguration.builder()
                    .taxCategory("clothing")
                    .taxTreatment(TaxTreatment.TAXABLE)
                    .taxRate(new BigDecimal("0.0800"))
                    .taxCode("VAT8")
                    .legalReference("Nghị định 123/2020/NĐ-CP")
                    .pricingBasis(PricingBasis.INCLUSIVE)
                    .effectiveFrom(LocalDateTime.now().minusDays(10))
                    .effectiveTo(LocalDateTime.now().plusDays(365))
                    .isActive(true)
                    .build());

            Role customerRole = roleRepository.findByName("customer")
                    .orElseGet(() -> roleRepository.save(new Role(null, "customer")));

            String uniquePhone = "09" + String.format("%08d", Math.abs((int)(System.nanoTime() % 100000000L)));

            User user = userRepository.save(User.builder()
                    .email(TEST_PREFIX + "user_" + System.nanoTime() + "@example.com")
                    .passwordHash("secret123")
                    .name(TEST_PREFIX + "Mock User")
                    .phone(uniquePhone)
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

            Product product = productRepository.save(Product.builder()
                    .category(cat)
                    .name(TEST_PREFIX + "Jeans")
                    .slug(TEST_PREFIX + "prod-" + System.nanoTime())
                    .basePrice(new BigDecimal("216000.00"))
                    .isActive(true)
                    .dataSource(DataSourceType.WEBSITE)
                    .build());

            ProductVariant variant = productVariantRepository.save(ProductVariant.builder()
                    .product(product)
                    .sku(TEST_PREFIX + "SKU-" + System.nanoTime())
                    .size("L")
                    .color("Blue")
                    .stock(50)
                    .price(new BigDecimal("216000.00"))
                    .build());
            testVariantId = variant.getId();

            Order order = Order.builder()
                    .user(user)
                    .receiverName(TEST_PREFIX + "Receiver")
                    .receiverPhone(uniquePhone)
                    .province("TP.HCM")
                    .paymentMethod(PaymentMethod.COD)
                    .subtotal(new BigDecimal("216000.00"))
                    .discountAmount(BigDecimal.ZERO)
                    .shippingFee(BigDecimal.ZERO)
                    .totalAmount(new BigDecimal("216000.00"))
                    .status(OrderStatus.SHIPPING)
                    .dataSource(DataSourceType.WEBSITE)
                    .createdAt(LocalDateTime.now().minusDays(1))
                    .build();

            OrderDetail detail = OrderDetail.builder()
                    .variant(variant)
                    .productName(TEST_PREFIX + "Jeans L")
                    .variantSize("L")
                    .variantColor("Blue")
                    .quantity(1)
                    .unitPrice(new BigDecimal("216000.00"))
                    .discountAmount(BigDecimal.ZERO)
                    .build();
            order.addOrderDetail(detail);

            Order savedOrder = orderRepository.save(order);
            testOrderId = savedOrder.getId();
            testOrderDetailId = savedOrder.getOrderDetails().get(0).getId();

            return null;
        });
    }

    @AfterEach
    void tearDown() {
        invoiceOutboxWorker.resetShutdownState();
        cleanTestData();
    }

    private void cleanTestData() {
        transactionTemplate.execute(status -> {
            entityManager.createQuery("DELETE FROM InvoiceAttempt ia WHERE ia.invoice.idempotencyKey LIKE :prefix")
                    .setParameter("prefix", TEST_PREFIX + "%")
                    .executeUpdate();
            entityManager.createQuery("DELETE FROM InvoiceOutbox io").executeUpdate();
            entityManager.createQuery("DELETE FROM InvoiceBalanceAdjustment iba WHERE iba.originalInvoice.idempotencyKey LIKE :prefix")
                    .setParameter("prefix", TEST_PREFIX + "%")
                    .executeUpdate();
            entityManager.createQuery("DELETE FROM InvoiceItem ii WHERE ii.itemName LIKE :prefix")
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
            entityManager.createQuery("DELETE FROM TaxConfiguration tc WHERE tc.legalReference = 'Nghị định 123/2020/NĐ-CP'")
                    .executeUpdate();
            return null;
        });
    }

    @Test
    @DisplayName("Should process outbox event through MockInvoiceProvider and persist clear demo indicators")
    void shouldProcessOutboxThroughMockProviderEndToEnd() {
        String suffix = String.valueOf(System.nanoTime());

        CreateFulfillmentEventCommand fCmd = CreateFulfillmentEventCommand.builder()
                .orderId(testOrderId)
                .fulfillmentCode(TEST_PREFIX + "FUL_" + suffix)
                .carrierName("GHN")
                .trackingNumber(TEST_PREFIX + "TRK_" + suffix)
                .deliveredAt(LocalDateTime.now())
                .proofSource(ProofSource.CARRIER_WEBHOOK)
                .proofReference("PROOF_" + suffix)
                .items(List.of(
                        CreateFulfillmentItemCommand.builder()
                                .orderDetailId(testOrderDetailId)
                                .deliveredQuantity(1)
                                .unitPriceSnapshot(new BigDecimal("216000.00"))
                                .discountAllocated(BigDecimal.ZERO)
                                .build()
                ))
                .build();
        FulfillmentEvent fe = fulfillmentService.recordFulfillment(fCmd);

        BuyerInvoiceProfile buyer = BuyerInvoiceProfile.builder()
                .customerType(CustomerType.INDIVIDUAL)
                .buyerName("Tran Van B")
                .address("456 Nguyen Trai, Q5")
                .email("buyer-demo@example.com")
                .phoneNumber("0912345678")
                .build();

        SellerInvoiceProfile seller = SellerInvoiceProfile.builder()
                .sellerLegalName("CLOSET SHOP VIETNAM")
                .sellerBrandName("Closet-Shop")
                .sellerTaxCode("0101234567")
                .sellerAddress("789 Nguyen Hue, Q1")
                .sellerPhone("02838222999")
                .build();

        InvoiceCalculationCommand calcCmd = InvoiceCalculationCommand.builder()
                .pricingBasis(PricingBasis.INCLUSIVE)
                .merchandiseVoucherAmount(BigDecimal.ZERO)
                .shippingFee(BigDecimal.ZERO)
                .shippingPromotionAmount(BigDecimal.ZERO)
                .shippingTaxCategory("shipping")
                .items(List.of(
                        InvoiceLineCalculationCommand.builder()
                                .orderDetailId(testOrderDetailId)
                                .itemName(TEST_PREFIX + "Jeans L")
                                .unit("cai")
                                .quantity(1)
                                .unitPrice(new BigDecimal("216000.00"))
                                .taxCategory("clothing")
                                .isDiscountable(true)
                                .build()
                ))
                .build();

        Invoice draft = invoiceDomainService.createOriginalInvoice(
                fe.getId(),
                TEST_PREFIX + "IDEM_" + suffix,
                buyer,
                seller,
                calcCmd);

        assertThat(draft.getDocumentStatus()).isEqualTo(DocumentStatus.DRAFT);

        int processedCount = invoiceOutboxWorker.processNextBatch();
        assertThat(processedCount).isGreaterThanOrEqualTo(1);

        Invoice reloaded = invoiceRepository.findById(draft.getId()).orElseThrow();
        assertThat(reloaded.getDocumentStatus()).isEqualTo(DocumentStatus.ISSUED);
        assertThat(reloaded.getInvoiceNumber()).startsWith("DEMO-");
        assertThat(reloaded.getInvoiceSeries()).isEqualTo("DEMO26");
        assertThat(reloaded.getProviderTransactionId()).startsWith("DEMO-MOCK-TX-");
        assertThat(reloaded.getStorageProvider()).isEqualTo("MOCK");
        assertThat(reloaded.getTaxAuthorityCode()).isNull();
        assertThat(reloaded.isDemoIssued()).isTrue();

        List<InvoiceOutbox> outboxEvents = invoiceOutboxRepository.findByInvoiceId(draft.getId());
        assertThat(outboxEvents).hasSize(1);
        assertThat(outboxEvents.get(0).getStatus()).isEqualTo(OutboxStatus.PROCESSED);

        List<InvoiceAttempt> attempts = invoiceAttemptRepository.findByInvoiceIdOrderByAttemptedAtDesc(draft.getId());
        assertThat(attempts).isNotEmpty();
        InvoiceAttempt latestAttempt = attempts.get(0);
        assertThat(latestAttempt.getProviderName()).isEqualTo("MOCK");
        assertThat(latestAttempt.getResponsePayload()).contains("\"mode\":\"DEMO_MOCK\"");
    }
}
