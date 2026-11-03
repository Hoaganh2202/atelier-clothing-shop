package com.shop.backend.einvoice;

import com.shop.backend.common.model.DataSourceType;
import com.shop.backend.einvoice.dto.AdjustItemCommand;
import com.shop.backend.einvoice.dto.CreateAdjustmentInvoiceCommand;
import com.shop.backend.einvoice.dto.CreateFulfillmentEventCommand;
import com.shop.backend.einvoice.dto.CreateFulfillmentItemCommand;
import com.shop.backend.einvoice.dto.InvoiceCalculationCommand;
import com.shop.backend.einvoice.dto.InvoiceLineCalculationCommand;
import com.shop.backend.einvoice.exception.InvoiceBalanceExceededException;
import com.shop.backend.einvoice.model.AdjustmentType;
import com.shop.backend.einvoice.model.BuyerInvoiceProfile;
import com.shop.backend.einvoice.model.CustomerType;
import com.shop.backend.einvoice.model.DocumentStatus;
import com.shop.backend.einvoice.model.FulfillmentEvent;
import com.shop.backend.einvoice.model.Invoice;
import com.shop.backend.einvoice.model.InvoiceBalanceAdjustment;
import com.shop.backend.einvoice.model.InvoiceItem;
import com.shop.backend.einvoice.model.InvoiceItemType;
import com.shop.backend.einvoice.model.InvoiceOutbox;
import com.shop.backend.einvoice.model.InvoiceType;
import com.shop.backend.einvoice.model.OutboxStatus;
import com.shop.backend.einvoice.model.PricingBasis;
import com.shop.backend.einvoice.model.ProofSource;
import com.shop.backend.einvoice.model.SellerInvoiceProfile;
import com.shop.backend.einvoice.model.TaxConfiguration;
import com.shop.backend.einvoice.model.TaxTreatment;
import com.shop.backend.einvoice.provider.dto.ProviderIssueRequest;
import com.shop.backend.einvoice.provider.misa.MisaInvoiceProvider;
import com.shop.backend.einvoice.provider.misa.MisaPayloadMapper;
import com.shop.backend.einvoice.provider.misa.MisaProperties;
import com.shop.backend.einvoice.repository.FulfillmentEventItemRepository;
import com.shop.backend.einvoice.repository.FulfillmentEventRepository;
import com.shop.backend.einvoice.repository.InvoiceBalanceAdjustmentRepository;
import com.shop.backend.einvoice.repository.InvoiceItemRepository;
import com.shop.backend.einvoice.repository.InvoiceOutboxRepository;
import com.shop.backend.einvoice.repository.InvoiceRepository;
import com.shop.backend.einvoice.repository.TaxConfigurationRepository;
import com.shop.backend.einvoice.service.FulfillmentService;
import com.shop.backend.einvoice.service.InvoiceBalanceValidationService;
import com.shop.backend.einvoice.service.InvoiceDomainService;
import com.shop.backend.einvoice.service.InvoiceOutboxService;
import com.shop.backend.einvoice.worker.InvoiceOutboxWorker;
import com.shop.backend.inventory.OrderReturnReceipt;
import com.shop.backend.inventory.OrderReturnReceiptRepository;
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
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
public class InvoiceAdjustmentBalanceIntegrationTest {

    private static final String TEST_PREFIX = "einv-adj-";

    @Autowired
    private InvoiceDomainService invoiceDomainService;

    @Autowired
    private FulfillmentService fulfillmentService;

    @Autowired
    private InvoiceBalanceValidationService invoiceBalanceValidationService;

    @Autowired
    private InvoiceOutboxService invoiceOutboxService;

    @Autowired
    private InvoiceOutboxWorker invoiceOutboxWorker;

    @Autowired
    private MisaPayloadMapper misaPayloadMapper;

    @Autowired
    private MisaInvoiceProvider misaInvoiceProvider;

    @Autowired
    private MisaProperties misaProperties;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private InvoiceItemRepository invoiceItemRepository;

    @Autowired
    private InvoiceBalanceAdjustmentRepository invoiceBalanceAdjustmentRepository;

    @Autowired
    private InvoiceOutboxRepository invoiceOutboxRepository;

    @Autowired
    private OrderReturnReceiptRepository orderReturnReceiptRepository;

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
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

    private TransactionTemplate transactionTemplate;

    private Long testOrderId;
    private Long testDetailId1;
    private Long testFulfillmentEventId;
    private Long testOriginalInvoiceId;
    private Long testOriginalItemId1;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        cleanTestData();

        transactionTemplate.execute(status -> {
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
            taxConfigurationRepository.save(taxClothing);

            Role customerRole = roleRepository.findByName("customer")
                    .orElseGet(() -> roleRepository.save(new Role(null, "customer")));

            User user = userRepository.save(User.builder()
                    .email(TEST_PREFIX + "user_" + System.nanoTime() + "@example.com")
                    .passwordHash("secret123")
                    .name(TEST_PREFIX + "Adjustment User")
                    .phone(String.format("09%08d", Math.abs((System.nanoTime() + 1111) % 100000000L)))
                    .role(customerRole)
                    .status(UserStatus.ACTIVE)
                    .dataSource(DataSourceType.WEBSITE)
                    .build());

            Category cat = categoryRepository.save(Category.builder()
                    .name(TEST_PREFIX + "Category")
                    .slug(TEST_PREFIX + "cat-" + System.nanoTime())
                    .isActive(true)
                    .build());

            Product product = productRepository.save(Product.builder()
                    .category(cat)
                    .name(TEST_PREFIX + "Jacket")
                    .slug(TEST_PREFIX + "prod-" + System.nanoTime())
                    .basePrice(new BigDecimal("100000.00"))
                    .isActive(true)
                    .dataSource(DataSourceType.WEBSITE)
                    .build());

            ProductVariant variant = productVariantRepository.save(ProductVariant.builder()
                    .product(product)
                    .sku(TEST_PREFIX + "SKU-" + System.nanoTime())
                    .size("L")
                    .color("Black")
                    .stock(100)
                    .price(new BigDecimal("100000.00"))
                    .build());

            Order order = Order.builder()
                    .user(user)
                    .receiverName(TEST_PREFIX + "Buyer Name")
                    .receiverPhone("0988776655")
                    .province("Hà Nội")
                    .paymentMethod(PaymentMethod.COD)
                    .subtotal(new BigDecimal("300000.00"))
                    .discountAmount(BigDecimal.ZERO)
                    .shippingFee(BigDecimal.ZERO)
                    .totalAmount(new BigDecimal("300000.00"))
                    .status(OrderStatus.SHIPPING)
                    .dataSource(DataSourceType.WEBSITE)
                    .createdAt(LocalDateTime.now().minusDays(1))
                    .build();

            OrderDetail d1 = OrderDetail.builder()
                    .variant(variant)
                    .productName(TEST_PREFIX + "Jacket L")
                    .variantSize("L")
                    .variantColor("Black")
                    .quantity(3)
                    .unitPrice(new BigDecimal("100000.00"))
                    .discountAmount(BigDecimal.ZERO)
                    .build();
            order.addOrderDetail(d1);

            Order savedOrder = orderRepository.save(order);
            testOrderId = savedOrder.getId();
            testDetailId1 = savedOrder.getOrderDetails().get(0).getId();

            return null;
        });

        CreateFulfillmentEventCommand fCmd = CreateFulfillmentEventCommand.builder()
                .orderId(testOrderId)
                .fulfillmentCode(TEST_PREFIX + "FUL_" + System.nanoTime())
                .carrierName("GHN")
                .trackingNumber(TEST_PREFIX + "TRK_" + System.nanoTime())
                .deliveredAt(LocalDateTime.now())
                .proofSource(ProofSource.CARRIER_WEBHOOK)
                .proofReference("PROOF_001")
                .items(List.of(
                        CreateFulfillmentItemCommand.builder()
                                .orderDetailId(testDetailId1)
                                .deliveredQuantity(3)
                                .unitPriceSnapshot(new BigDecimal("100000.00"))
                                .discountAllocated(BigDecimal.ZERO)
                                .build()
                ))
                .build();
        FulfillmentEvent fe = fulfillmentService.recordFulfillment(fCmd);
        testFulfillmentEventId = fe.getId();

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
                                .itemName(TEST_PREFIX + "Jacket L")
                                .unit("cai")
                                .quantity(3)
                                .unitPrice(new BigDecimal("100000.00"))
                                .taxCategory("clothing")
                                .isDiscountable(true)
                                .build()
                ))
                .build();

        Invoice orig = invoiceDomainService.createOriginalInvoice(
                testFulfillmentEventId,
                TEST_PREFIX + "ORIG_IDEM_" + System.nanoTime(),
                buyer,
                seller,
                calcCmd
        );
        testOriginalInvoiceId = orig.getId();

        transactionTemplate.execute(status -> {
            Invoice origEntity = invoiceRepository.findById(testOriginalInvoiceId).orElseThrow();
            origEntity.setDocumentStatus(DocumentStatus.ISSUED);
            invoiceRepository.save(origEntity);

            List<InvoiceOutbox> setupEvents = invoiceOutboxRepository.findByInvoiceId(testOriginalInvoiceId);
            for (InvoiceOutbox evt : setupEvents) {
                evt.setStatus(OutboxStatus.PROCESSED);
                evt.setProcessedAt(LocalDateTime.now());
                invoiceOutboxRepository.save(evt);
            }
            return null;
        });

        List<InvoiceItem> items = invoiceDomainService.getInvoiceItems(orig.getId());
        testOriginalItemId1 = items.get(0).getId();
    }

    @AfterEach
    void tearDown() {
        cleanTestData();
    }

    private void cleanTestData() {
        transactionTemplate.execute(status -> {
            entityManager.createQuery("DELETE FROM InvoiceOutbox io WHERE io.eventKey LIKE :prefix OR io.eventKey LIKE 'INV-%'")
                    .setParameter("prefix", TEST_PREFIX + "%")
                    .executeUpdate();
            entityManager.createQuery("DELETE FROM InvoiceBalanceAdjustment iba WHERE iba.originalInvoice.idempotencyKey LIKE :prefix OR iba.originalInvoice.idempotencyKey LIKE 'INV_ADJ_RET_%'")
                    .setParameter("prefix", TEST_PREFIX + "%")
                    .executeUpdate();
            entityManager.createQuery("DELETE FROM InvoiceItem ii WHERE ii.originalInvoiceItem IS NOT NULL AND (ii.itemName LIKE :prefix OR ii.invoice.idempotencyKey LIKE :prefix OR ii.invoice.idempotencyKey LIKE 'INV_ADJ_RET_%')")
                    .setParameter("prefix", TEST_PREFIX + "%")
                    .executeUpdate();
            entityManager.createQuery("DELETE FROM InvoiceItem ii WHERE ii.itemName LIKE :prefix OR ii.invoice.idempotencyKey LIKE :prefix")
                    .setParameter("prefix", TEST_PREFIX + "%")
                    .executeUpdate();
            entityManager.createNativeQuery("UPDATE invoice_items SET original_invoice_item_id = NULL")
                    .executeUpdate();
            entityManager.createNativeQuery("UPDATE invoices SET return_receipt_id = NULL, replacing_invoice_id = NULL, replaced_by_invoice_id = NULL, original_invoice_id = NULL")
                    .executeUpdate();
            entityManager.createQuery("DELETE FROM Invoice i WHERE i.originalInvoice IS NOT NULL AND (i.idempotencyKey LIKE :prefix OR i.idempotencyKey LIKE 'INV_ADJ_RET_%')")
                    .setParameter("prefix", TEST_PREFIX + "%")
                    .executeUpdate();
            entityManager.createQuery("DELETE FROM Invoice i WHERE i.idempotencyKey LIKE :prefix OR i.idempotencyKey LIKE 'INV_ADJ_RET_%' OR i.idempotencyKey LIKE 'ORIG_ORD_%'")
                    .setParameter("prefix", TEST_PREFIX + "%")
                    .executeUpdate();
            entityManager.createQuery("DELETE FROM OrderReturnReceipt orr WHERE orr.receiptNumber LIKE :prefix")
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

    private OrderReturnReceipt createTestReturnReceipt(int totalReceivedQty) {
        Order order = orderRepository.findById(testOrderId).orElseThrow();
        User user = userRepository.findById(order.getUser().getId()).orElseThrow();
        OrderReturnReceipt receipt = OrderReturnReceipt.builder()
                .receiptNumber(TEST_PREFIX + "RC_" + System.nanoTime())
                .order(order)
                .actorUser(user)
                .payloadHash("payload_hash_" + System.nanoTime())
                .totalReceivedQuantity(totalReceivedQty)
                .restockedQuantity(totalReceivedQty)
                .discardedQuantity(0)
                .build();
        return orderReturnReceiptRepository.save(receipt);
    }

    @Test
    @DisplayName("Test 1: Completed return receipt creates exactly one Adjustment DRAFT")
    void test01_returnReceiptCompleted_createsExactOneAdjustmentDraft() {
        OrderReturnReceipt receipt = createTestReturnReceipt(1);

        Invoice adj = invoiceDomainService.createAdjustmentDraftFromReturnReceipt(receipt.getId());

        assertThat(adj).isNotNull();
        assertThat(adj.getInvoiceType()).isEqualTo(InvoiceType.ADJUSTMENT);
        assertThat(adj.getDocumentStatus()).isEqualTo(DocumentStatus.DRAFT);
        assertThat(adj.getOriginalInvoice().getId()).isEqualTo(testOriginalInvoiceId);
        assertThat(adj.getReturnReceipt().getId()).isEqualTo(receipt.getId());
        assertThat(adj.getIdempotencyKey()).isEqualTo("INV_ADJ_RET_" + testOriginalInvoiceId + "_" + receipt.getId());

        assertThat(invoiceOutboxRepository.findByInvoiceId(adj.getId())).isEmpty();

        InvoiceBalanceAdjustment balance = invoiceBalanceAdjustmentRepository
                .findByOriginalInvoiceIdAndInvoiceItemId(testOriginalInvoiceId, testOriginalItemId1)
                .orElseThrow();
        assertThat(balance.getOriginalQuantity()).isEqualTo(3);
        assertThat(balance.getTotalAdjustedQuantity()).isEqualTo(1);
        assertThat(balance.getRemainingQuantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("Test 2: Duplicate return receipt event does not create second adjustment")
    void test02_duplicateReceiptEvent_doesNotCreateSecondAdjustment() {
        OrderReturnReceipt receipt = createTestReturnReceipt(1);

        Invoice firstAdj = invoiceDomainService.createAdjustmentDraftFromReturnReceipt(receipt.getId());
        Invoice secondAdj = invoiceDomainService.createAdjustmentDraftFromReturnReceipt(receipt.getId());

        assertThat(secondAdj.getId()).isEqualTo(firstAdj.getId());
        assertThat(invoiceRepository.findByReturnReceiptId(receipt.getId())).isPresent();

        InvoiceBalanceAdjustment balance = invoiceBalanceAdjustmentRepository
                .findByOriginalInvoiceIdAndInvoiceItemId(testOriginalInvoiceId, testOriginalItemId1)
                .orElseThrow();
        assertThat(balance.getTotalAdjustedQuantity()).isEqualTo(1);
        assertThat(balance.getRemainingQuantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("Test 3: Reserve quota calculates exact quantity and proportional amounts")
    void test03_reserveQuota_calculatesExactQuantityAndAmounts() {
        CreateAdjustmentInvoiceCommand cmd = CreateAdjustmentInvoiceCommand.builder()
                .originalInvoiceId(testOriginalInvoiceId)
                .adjustmentType(AdjustmentType.DECREASE)
                .idempotencyKey(TEST_PREFIX + "ADJ_EXACT_AMOUNT_" + System.nanoTime())
                .itemsToAdjust(List.of(
                        AdjustItemCommand.builder()
                                .originalInvoiceItemId(testOriginalItemId1)
                                .quantityToAdjust(2)
                                .build()
                ))
                .build();

        Invoice adj = invoiceDomainService.createAdjustmentInvoice(cmd);

        assertThat(adj).isNotNull();
        assertThat(adj.getDocumentStatus()).isEqualTo(DocumentStatus.DRAFT);
        assertThat(adj.getTotalAfterTax()).isEqualByComparingTo(new BigDecimal("-200000.00"));

        List<InvoiceItem> adjItems = invoiceDomainService.getInvoiceItems(adj.getId());
        assertThat(adjItems).hasSize(1);
        InvoiceItem item = adjItems.get(0);
        assertThat(item.getQuantity()).isEqualTo(-2);
        assertThat(item.getUnitPriceGross()).isEqualByComparingTo(new BigDecimal("100000.00"));
        assertThat(item.getLineTotalGross()).isEqualByComparingTo(new BigDecimal("-200000.00"));
        assertThat(item.getTaxableAmount()).isNegative();
        assertThat(item.getTaxAmount()).isNegative();
        assertThat(item.getTaxableAmount().add(item.getTaxAmount())).isEqualByComparingTo(new BigDecimal("-200000.00"));

        InvoiceBalanceAdjustment balance = invoiceBalanceAdjustmentRepository
                .findByOriginalInvoiceIdAndInvoiceItemId(testOriginalInvoiceId, testOriginalItemId1)
                .orElseThrow();
        assertThat(balance.getOriginalQuantity()).isEqualTo(3);
        assertThat(balance.getTotalAdjustedQuantity()).isEqualTo(2);
        assertThat(balance.getRemainingQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("Test 4: Competing receipts cannot exceed quota under concurrent transactions")
    void test04_competingReceipts_cannotExceedQuota_concurrencyLocked() throws InterruptedException {
        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    CreateAdjustmentInvoiceCommand cmd = CreateAdjustmentInvoiceCommand.builder()
                            .originalInvoiceId(testOriginalInvoiceId)
                            .adjustmentType(AdjustmentType.DECREASE)
                            .idempotencyKey(TEST_PREFIX + "CONC_RACE_" + index + "_" + System.nanoTime())
                            .itemsToAdjust(List.of(
                                    AdjustItemCommand.builder()
                                            .originalInvoiceItemId(testOriginalItemId1)
                                            .quantityToAdjust(2)
                                            .build()
                            ))
                            .build();

                    invoiceDomainService.createAdjustmentInvoice(cmd);
                    successCount.incrementAndGet();
                } catch (Throwable t) {
                    failureCount.incrementAndGet();
                    exceptions.add(t);
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = endLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failureCount.get()).isEqualTo(1);

        Throwable rootFailure = exceptions.get(0);
        while (rootFailure.getCause() != null && !(rootFailure instanceof InvoiceBalanceExceededException)) {
            rootFailure = rootFailure.getCause();
        }
        assertThat(rootFailure).isInstanceOf(InvoiceBalanceExceededException.class);

        InvoiceBalanceAdjustment finalBalance = invoiceBalanceAdjustmentRepository
                .findByOriginalInvoiceIdAndInvoiceItemId(testOriginalInvoiceId, testOriginalItemId1)
                .orElseThrow();
        assertThat(finalBalance.getRemainingQuantity()).isEqualTo(1);
        assertThat(finalBalance.getTotalAdjustedQuantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("Test 5: Receipt or item belonging to different fulfillment cannot debit wrong original invoice")
    void test05_receiptBelongingToDifferentFulfillment_cannotDebitWrongOriginalInvoice() {
        Long secondOrderId = transactionTemplate.execute(status -> {
            Role customerRole = roleRepository.findByName("customer").orElseThrow();
            User user2 = userRepository.save(User.builder()
                    .email(TEST_PREFIX + "user2_" + System.nanoTime() + "@example.com")
                    .passwordHash("secret123")
                    .name(TEST_PREFIX + "Second User")
                    .phone("0988776656")
                    .role(customerRole)
                    .status(UserStatus.ACTIVE)
                    .dataSource(DataSourceType.WEBSITE)
                    .build());

            Category cat = categoryRepository.findAll().get(0);
            Product product = productRepository.findAll().get(0);
            ProductVariant variant = productVariantRepository.findAll().get(0);

            Order order2 = Order.builder()
                    .user(user2)
                    .receiverName(TEST_PREFIX + "Buyer 2")
                    .receiverPhone("0988776656")
                    .province("TP.HCM")
                    .paymentMethod(PaymentMethod.COD)
                    .subtotal(new BigDecimal("100000.00"))
                    .discountAmount(BigDecimal.ZERO)
                    .shippingFee(BigDecimal.ZERO)
                    .totalAmount(new BigDecimal("100000.00"))
                    .status(OrderStatus.SHIPPING)
                    .dataSource(DataSourceType.WEBSITE)
                    .createdAt(LocalDateTime.now().minusDays(1))
                    .build();

            OrderDetail d2 = OrderDetail.builder()
                    .variant(variant)
                    .productName(TEST_PREFIX + "Jacket L (Order 2)")
                    .variantSize("L")
                    .variantColor("Black")
                    .quantity(1)
                    .unitPrice(new BigDecimal("100000.00"))
                    .discountAmount(BigDecimal.ZERO)
                    .build();
            order2.addOrderDetail(d2);
            return orderRepository.save(order2).getId();
        });

        OrderDetail detail2 = orderDetailRepository.findByOrderId(secondOrderId).get(0);

        CreateFulfillmentEventCommand fCmd2 = CreateFulfillmentEventCommand.builder()
                .orderId(secondOrderId)
                .fulfillmentCode(TEST_PREFIX + "FUL2_" + System.nanoTime())
                .carrierName("GHN")
                .trackingNumber(TEST_PREFIX + "TRK2_" + System.nanoTime())
                .deliveredAt(LocalDateTime.now())
                .proofSource(ProofSource.CARRIER_WEBHOOK)
                .proofReference("PROOF_002")
                .items(List.of(
                        CreateFulfillmentItemCommand.builder()
                                .orderDetailId(detail2.getId())
                                .deliveredQuantity(1)
                                .unitPriceSnapshot(new BigDecimal("100000.00"))
                                .discountAllocated(BigDecimal.ZERO)
                                .build()
                ))
                .build();
        FulfillmentEvent fe2 = fulfillmentService.recordFulfillment(fCmd2);

        BuyerInvoiceProfile buyer2 = BuyerInvoiceProfile.builder()
                .customerType(CustomerType.INDIVIDUAL)
                .buyerName("Nguyen Van B")
                .address("456 Nguyen Hue, TP.HCM")
                .email("buyer2@example.com")
                .phoneNumber("0988776656")
                .build();

        SellerInvoiceProfile seller = SellerInvoiceProfile.builder()
                .sellerLegalName("CLOSET SHOP VIETNAM")
                .sellerBrandName("Closet-Shop")
                .sellerTaxCode("0101234567")
                .sellerAddress("789 Nguyen Hue, Q1")
                .sellerPhone("02838222999")
                .build();

        InvoiceCalculationCommand calcCmd2 = InvoiceCalculationCommand.builder()
                .orderId(secondOrderId)
                .fulfillmentEventId(fe2.getId())
                .taxPoint(LocalDateTime.now())
                .pricingBasis(PricingBasis.INCLUSIVE)
                .merchandiseVoucherAmount(BigDecimal.ZERO)
                .shippingFee(BigDecimal.ZERO)
                .shippingPromotionAmount(BigDecimal.ZERO)
                .shippingTaxCategory("shipping")
                .items(List.of(
                        InvoiceLineCalculationCommand.builder()
                                .orderDetailId(detail2.getId())
                                .itemName(TEST_PREFIX + "Jacket L (Order 2)")
                                .unit("cai")
                                .quantity(1)
                                .unitPrice(new BigDecimal("100000.00"))
                                .taxCategory("clothing")
                                .isDiscountable(true)
                                .build()
                ))
                .build();

        Invoice orig2 = invoiceDomainService.createOriginalInvoice(
                fe2.getId(),
                TEST_PREFIX + "ORIG_IDEM2_" + System.nanoTime(),
                buyer2,
                seller,
                calcCmd2
        );

        transactionTemplate.execute(status -> {
            Invoice e = invoiceRepository.findById(orig2.getId()).orElseThrow();
            e.setDocumentStatus(DocumentStatus.ISSUED);
            return invoiceRepository.save(e);
        });

        InvoiceItem item2 = invoiceDomainService.getInvoiceItems(orig2.getId()).get(0);

        CreateAdjustmentInvoiceCommand invalidItemCmd = CreateAdjustmentInvoiceCommand.builder()
                .originalInvoiceId(testOriginalInvoiceId)
                .adjustmentType(AdjustmentType.DECREASE)
                .idempotencyKey(TEST_PREFIX + "CROSS_ITEM_" + System.nanoTime())
                .itemsToAdjust(List.of(
                        AdjustItemCommand.builder()
                                .originalInvoiceItemId(item2.getId())
                                .quantityToAdjust(1)
                                .build()
                ))
                .build();

        assertThatThrownBy(() -> invoiceDomainService.createAdjustmentInvoice(invalidItemCmd))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("belongs to invoice");

        Order order2Entity = orderRepository.findById(secondOrderId).orElseThrow();
        OrderReturnReceipt returnReceipt2 = orderReturnReceiptRepository.save(OrderReturnReceipt.builder()
                .receiptNumber(TEST_PREFIX + "RC_ORDER2_" + System.nanoTime())
                .order(order2Entity)
                .payloadHash("hash_order2_" + System.nanoTime())
                .totalReceivedQuantity(1)
                .restockedQuantity(1)
                .discardedQuantity(0)
                .build());

        CreateAdjustmentInvoiceCommand invalidReceiptCmd = CreateAdjustmentInvoiceCommand.builder()
                .originalInvoiceId(testOriginalInvoiceId)
                .returnReceiptId(returnReceipt2.getId())
                .adjustmentType(AdjustmentType.DECREASE)
                .idempotencyKey(TEST_PREFIX + "CROSS_RECEIPT_" + System.nanoTime())
                .itemsToAdjust(List.of(
                        AdjustItemCommand.builder()
                                .originalInvoiceItemId(testOriginalItemId1)
                                .quantityToAdjust(1)
                                .build()
                ))
                .build();

        assertThatThrownBy(() -> invoiceDomainService.createAdjustmentInvoice(invalidReceiptCmd))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Return receipt order");

        InvoiceBalanceAdjustment b1 = invoiceBalanceAdjustmentRepository
                .findByOriginalInvoiceIdAndInvoiceItemId(testOriginalInvoiceId, testOriginalItemId1)
                .orElseThrow();
        assertThat(b1.getRemainingQuantity()).isEqualTo(3);
        assertThat(b1.getTotalAdjustedQuantity()).isEqualTo(0);

        InvoiceBalanceAdjustment b2 = invoiceBalanceAdjustmentRepository
                .findByOriginalInvoiceIdAndInvoiceItemId(orig2.getId(), item2.getId())
                .orElseThrow();
        assertThat(b2.getRemainingQuantity()).isEqualTo(1);
        assertThat(b2.getTotalAdjustedQuantity()).isEqualTo(0);
    }

    @Test
    @DisplayName("Test 6: Accountant approve transitions DRAFT -> PENDING_ISSUE without premature outbox dispatch")
    void test06_accountantApprove_transitionsDraftToPendingIssue_doesNotEnqueueOutboxPrematurely() {
        CreateAdjustmentInvoiceCommand cmd = CreateAdjustmentInvoiceCommand.builder()
                .originalInvoiceId(testOriginalInvoiceId)
                .adjustmentType(AdjustmentType.DECREASE)
                .idempotencyKey(TEST_PREFIX + "ADJ_APPROVE_" + System.nanoTime())
                .itemsToAdjust(List.of(
                        AdjustItemCommand.builder()
                                .originalInvoiceItemId(testOriginalItemId1)
                                .quantityToAdjust(1)
                                .build()
                ))
                .build();

        Invoice adj = invoiceDomainService.createAdjustmentInvoice(cmd);
        assertThat(adj.getDocumentStatus()).isEqualTo(DocumentStatus.DRAFT);

        Invoice approved = invoiceDomainService.approveAdjustmentInvoice(adj.getId());
        assertThat(approved.getDocumentStatus()).isEqualTo(DocumentStatus.PENDING_ISSUE);

        // Verification: In EINV-2.3.1, outbox event is NOT enqueued prematurely
        assertThat(invoiceOutboxRepository.findByInvoiceId(adj.getId())).isEmpty();

        // Idempotent re-approve
        Invoice reApproved = invoiceDomainService.approveAdjustmentInvoice(adj.getId());
        assertThat(reApproved.getDocumentStatus()).isEqualTo(DocumentStatus.PENDING_ISSUE);
        assertThat(invoiceOutboxRepository.findByInvoiceId(adj.getId())).isEmpty();
    }

    @Test
    @DisplayName("Test 7: Accountant reject DRAFT transitions DRAFT -> ISSUE_FAILED and releases quota")
    void test07_accountantReject_transitionsDraftToIssueFailed_releasesQuota() {
        CreateAdjustmentInvoiceCommand cmd = CreateAdjustmentInvoiceCommand.builder()
                .originalInvoiceId(testOriginalInvoiceId)
                .adjustmentType(AdjustmentType.DECREASE)
                .idempotencyKey(TEST_PREFIX + "ADJ_REJECT_" + System.nanoTime())
                .itemsToAdjust(List.of(
                        AdjustItemCommand.builder()
                                .originalInvoiceItemId(testOriginalItemId1)
                                .quantityToAdjust(1)
                                .build()
                ))
                .build();

        Invoice adj = invoiceDomainService.createAdjustmentInvoice(cmd);
        assertThat(adj.getDocumentStatus()).isEqualTo(DocumentStatus.DRAFT);

        InvoiceBalanceAdjustment balanceReserved = invoiceBalanceAdjustmentRepository
                .findByOriginalInvoiceIdAndInvoiceItemId(testOriginalInvoiceId, testOriginalItemId1)
                .orElseThrow();
        assertThat(balanceReserved.getRemainingQuantity()).isEqualTo(2);
        assertThat(balanceReserved.getTotalAdjustedQuantity()).isEqualTo(1);

        Invoice rejected = invoiceDomainService.rejectAdjustmentInvoice(adj.getId(), "Rejected by accountant");
        assertThat(rejected.getDocumentStatus()).isEqualTo(DocumentStatus.ISSUE_FAILED);

        InvoiceBalanceAdjustment balanceReleased = invoiceBalanceAdjustmentRepository
                .findByOriginalInvoiceIdAndInvoiceItemId(testOriginalInvoiceId, testOriginalItemId1)
                .orElseThrow();
        assertThat(balanceReleased.getOriginalQuantity()).isEqualTo(3);
        assertThat(balanceReleased.getRemainingQuantity()).isEqualTo(3);
        assertThat(balanceReleased.getTotalAdjustedQuantity()).isEqualTo(0);
    }

    @Test
    @DisplayName("Test 8: Per-adjustment release idempotency with multi-adjustments A and B")
    void test08_perAdjustmentReleaseIdempotency_multiAdjustments() {
        Long fiveUnitOrderId = transactionTemplate.execute(status -> {
            Role customerRole = roleRepository.findByName("customer").orElseThrow();
            User user = userRepository.save(User.builder()
                    .email(TEST_PREFIX + "user_5_" + System.nanoTime() + "@example.com")
                    .passwordHash("secret123")
                    .name(TEST_PREFIX + "Five Unit User")
                    .phone("0988776659")
                    .role(customerRole)
                    .status(UserStatus.ACTIVE)
                    .dataSource(DataSourceType.WEBSITE)
                    .build());

            Category cat = categoryRepository.findAll().get(0);
            Product product = productRepository.findAll().get(0);
            ProductVariant variant = productVariantRepository.findAll().get(0);

            Order order = Order.builder()
                    .user(user)
                    .receiverName(TEST_PREFIX + "Buyer Five")
                    .receiverPhone("0988776659")
                    .province("Hà Nội")
                    .paymentMethod(PaymentMethod.COD)
                    .subtotal(new BigDecimal("500000.00"))
                    .discountAmount(BigDecimal.ZERO)
                    .shippingFee(BigDecimal.ZERO)
                    .totalAmount(new BigDecimal("500000.00"))
                    .status(OrderStatus.SHIPPING)
                    .dataSource(DataSourceType.WEBSITE)
                    .createdAt(LocalDateTime.now().minusDays(1))
                    .build();

            OrderDetail d = OrderDetail.builder()
                    .variant(variant)
                    .productName(TEST_PREFIX + "Jacket L (5 units)")
                    .variantSize("L")
                    .variantColor("Black")
                    .quantity(5)
                    .unitPrice(new BigDecimal("100000.00"))
                    .discountAmount(BigDecimal.ZERO)
                    .build();
            order.addOrderDetail(d);
            return orderRepository.save(order).getId();
        });

        OrderDetail detail5 = orderDetailRepository.findByOrderId(fiveUnitOrderId).get(0);

        CreateFulfillmentEventCommand fCmd = CreateFulfillmentEventCommand.builder()
                .orderId(fiveUnitOrderId)
                .fulfillmentCode(TEST_PREFIX + "FUL5_" + System.nanoTime())
                .carrierName("GHN")
                .trackingNumber(TEST_PREFIX + "TRK5_" + System.nanoTime())
                .deliveredAt(LocalDateTime.now())
                .proofSource(ProofSource.CARRIER_WEBHOOK)
                .proofReference("PROOF_005")
                .items(List.of(
                        CreateFulfillmentItemCommand.builder()
                                .orderDetailId(detail5.getId())
                                .deliveredQuantity(5)
                                .unitPriceSnapshot(new BigDecimal("100000.00"))
                                .discountAllocated(BigDecimal.ZERO)
                                .build()
                ))
                .build();
        FulfillmentEvent fe5 = fulfillmentService.recordFulfillment(fCmd);

        BuyerInvoiceProfile buyer = BuyerInvoiceProfile.builder()
                .customerType(CustomerType.INDIVIDUAL)
                .buyerName("Nguyen Van Five")
                .address("123 Le Loi, TP.HCM")
                .email("buyer5@example.com")
                .phoneNumber("0988776659")
                .build();

        SellerInvoiceProfile seller = SellerInvoiceProfile.builder()
                .sellerLegalName("CLOSET SHOP VIETNAM")
                .sellerBrandName("Closet-Shop")
                .sellerTaxCode("0101234567")
                .sellerAddress("789 Nguyen Hue, Q1")
                .sellerPhone("02838222999")
                .build();

        InvoiceCalculationCommand calcCmd = InvoiceCalculationCommand.builder()
                .orderId(fiveUnitOrderId)
                .fulfillmentEventId(fe5.getId())
                .taxPoint(LocalDateTime.now())
                .pricingBasis(PricingBasis.INCLUSIVE)
                .merchandiseVoucherAmount(BigDecimal.ZERO)
                .shippingFee(BigDecimal.ZERO)
                .shippingPromotionAmount(BigDecimal.ZERO)
                .shippingTaxCategory("shipping")
                .items(List.of(
                        InvoiceLineCalculationCommand.builder()
                                .orderDetailId(detail5.getId())
                                .itemName(TEST_PREFIX + "Jacket L (5 units)")
                                .unit("cai")
                                .quantity(5)
                                .unitPrice(new BigDecimal("100000.00"))
                                .taxCategory("clothing")
                                .isDiscountable(true)
                                .build()
                ))
                .build();

        Invoice orig5 = invoiceDomainService.createOriginalInvoice(
                fe5.getId(),
                TEST_PREFIX + "ORIG_IDEM5_" + System.nanoTime(),
                buyer,
                seller,
                calcCmd
        );
        Long orig5Id = orig5.getId();

        transactionTemplate.execute(status -> {
            Invoice e = invoiceRepository.findById(orig5Id).orElseThrow();
            e.setDocumentStatus(DocumentStatus.ISSUED);
            return invoiceRepository.save(e);
        });

        InvoiceItem item5 = invoiceDomainService.getInvoiceItems(orig5Id).get(0);
        Long item5Id = item5.getId();

        InvoiceBalanceAdjustment balInitial = invoiceBalanceAdjustmentRepository
                .findByOriginalInvoiceIdAndInvoiceItemId(orig5Id, item5Id).orElseThrow();
        assertThat(balInitial.getOriginalQuantity()).isEqualTo(5);
        assertThat(balInitial.getRemainingQuantity()).isEqualTo(5);
        assertThat(balInitial.getTotalAdjustedQuantity()).isEqualTo(0);

        BigDecimal origTaxable = balInitial.getOriginalTaxableAmount();
        BigDecimal origTax = balInitial.getOriginalTaxAmount();

        Invoice adjA = invoiceDomainService.createAdjustmentInvoice(CreateAdjustmentInvoiceCommand.builder()
                .originalInvoiceId(orig5Id)
                .adjustmentType(AdjustmentType.DECREASE)
                .idempotencyKey(TEST_PREFIX + "ADJ_A_" + System.nanoTime())
                .itemsToAdjust(List.of(AdjustItemCommand.builder().originalInvoiceItemId(item5Id).quantityToAdjust(2).build()))
                .build());

        InvoiceBalanceAdjustment balAfterA = invoiceBalanceAdjustmentRepository
                .findByOriginalInvoiceIdAndInvoiceItemId(orig5Id, item5Id).orElseThrow();
        assertThat(balAfterA.getRemainingQuantity()).isEqualTo(3);
        assertThat(balAfterA.getTotalAdjustedQuantity()).isEqualTo(2);

        Invoice adjB = invoiceDomainService.createAdjustmentInvoice(CreateAdjustmentInvoiceCommand.builder()
                .originalInvoiceId(orig5Id)
                .adjustmentType(AdjustmentType.DECREASE)
                .idempotencyKey(TEST_PREFIX + "ADJ_B_" + System.nanoTime())
                .itemsToAdjust(List.of(AdjustItemCommand.builder().originalInvoiceItemId(item5Id).quantityToAdjust(3).build()))
                .build());

        InvoiceBalanceAdjustment balAfterB = invoiceBalanceAdjustmentRepository
                .findByOriginalInvoiceIdAndInvoiceItemId(orig5Id, item5Id).orElseThrow();
        assertThat(balAfterB.getRemainingQuantity()).isEqualTo(0);
        assertThat(balAfterB.getTotalAdjustedQuantity()).isEqualTo(5);

        invoiceDomainService.rejectAdjustmentInvoice(adjA.getId(), "Reject A");
        InvoiceBalanceAdjustment balAfterReleaseA = invoiceBalanceAdjustmentRepository
                .findByOriginalInvoiceIdAndInvoiceItemId(orig5Id, item5Id).orElseThrow();
        assertThat(balAfterReleaseA.getRemainingQuantity()).isEqualTo(2);
        assertThat(balAfterReleaseA.getTotalAdjustedQuantity()).isEqualTo(3);

        invoiceDomainService.rejectAdjustmentInvoice(adjA.getId(), "Reject A second time");
        invoiceBalanceValidationService.releaseAdjustmentBalance(adjA.getId());
        InvoiceBalanceAdjustment balAfterRepeatA = invoiceBalanceAdjustmentRepository
                .findByOriginalInvoiceIdAndInvoiceItemId(orig5Id, item5Id).orElseThrow();
        assertThat(balAfterRepeatA.getRemainingQuantity()).isEqualTo(2);
        assertThat(balAfterRepeatA.getTotalAdjustedQuantity()).isEqualTo(3);

        Invoice adjBCurrent = invoiceDomainService.getInvoice(adjB.getId());
        assertThat(adjBCurrent.getDocumentStatus()).isEqualTo(DocumentStatus.DRAFT);

        invoiceDomainService.rejectAdjustmentInvoice(adjB.getId(), "Reject B");
        InvoiceBalanceAdjustment balAfterReleaseB = invoiceBalanceAdjustmentRepository
                .findByOriginalInvoiceIdAndInvoiceItemId(orig5Id, item5Id).orElseThrow();
        assertThat(balAfterReleaseB.getRemainingQuantity()).isEqualTo(5);
        assertThat(balAfterReleaseB.getTotalAdjustedQuantity()).isEqualTo(0);
        assertThat(balAfterReleaseB.getRemainingTaxableAmount()).isEqualByComparingTo(origTaxable);
        assertThat(balAfterReleaseB.getRemainingTaxAmount()).isEqualByComparingTo(origTax);
    }

    @Test
    @DisplayName("Test 9: TIMEOUT_UNKNOWN holds quota reservation without release")
    void test09_timeoutUnknown_holdsReservation() {
        CreateAdjustmentInvoiceCommand cmd = CreateAdjustmentInvoiceCommand.builder()
                .originalInvoiceId(testOriginalInvoiceId)
                .adjustmentType(AdjustmentType.DECREASE)
                .idempotencyKey(TEST_PREFIX + "ADJ_TIMEOUT_" + System.nanoTime())
                .itemsToAdjust(List.of(
                        AdjustItemCommand.builder()
                                .originalInvoiceItemId(testOriginalItemId1)
                                .quantityToAdjust(1)
                                .build()
                ))
                .build();

        Invoice adj = invoiceDomainService.createAdjustmentInvoice(cmd);
        invoiceDomainService.approveAdjustmentInvoice(adj.getId());
        invoiceDomainService.transitionStatus(adj.getId(), DocumentStatus.ISSUING);

        Invoice transitioned = invoiceDomainService.transitionStatus(adj.getId(), DocumentStatus.ISSUANCE_UNKNOWN);
        assertThat(transitioned.getDocumentStatus()).isEqualTo(DocumentStatus.ISSUANCE_UNKNOWN);

        InvoiceBalanceAdjustment balance = invoiceBalanceAdjustmentRepository
                .findByOriginalInvoiceIdAndInvoiceItemId(testOriginalInvoiceId, testOriginalItemId1)
                .orElseThrow();
        assertThat(balance.getRemainingQuantity()).isEqualTo(2);
        assertThat(balance.getTotalAdjustedQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("Test 10: Inquiry NOT_FOUND keeps invoice in ISSUANCE_UNKNOWN without releasing quota")
    void test10_inquiryNotFound_keepsReservationUnknown() {
        CreateAdjustmentInvoiceCommand cmd = CreateAdjustmentInvoiceCommand.builder()
                .originalInvoiceId(testOriginalInvoiceId)
                .adjustmentType(AdjustmentType.DECREASE)
                .idempotencyKey(TEST_PREFIX + "ADJ_INQ_NOT_FOUND_" + System.nanoTime())
                .itemsToAdjust(List.of(
                        AdjustItemCommand.builder()
                                .originalInvoiceItemId(testOriginalItemId1)
                                .quantityToAdjust(1)
                                .build()
                ))
                .build();

        Invoice adj = invoiceDomainService.createAdjustmentInvoice(cmd);
        invoiceDomainService.approveAdjustmentInvoice(adj.getId());
        invoiceDomainService.transitionStatus(adj.getId(), DocumentStatus.ISSUING);
        invoiceDomainService.transitionStatus(adj.getId(), DocumentStatus.ISSUANCE_UNKNOWN);

        invoiceDomainService.transitionStatus(adj.getId(), DocumentStatus.RECONCILING);
        Invoice reUnknown = invoiceDomainService.transitionStatus(adj.getId(), DocumentStatus.ISSUANCE_UNKNOWN);
        assertThat(reUnknown.getDocumentStatus()).isEqualTo(DocumentStatus.ISSUANCE_UNKNOWN);

        InvoiceBalanceAdjustment balance = invoiceBalanceAdjustmentRepository
                .findByOriginalInvoiceIdAndInvoiceItemId(testOriginalInvoiceId, testOriginalItemId1)
                .orElseThrow();
        assertThat(balance.getRemainingQuantity()).isEqualTo(2);
        assertThat(balance.getTotalAdjustedQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("Test 11: DEAD_LETTER after retry exhaustion does not release quota")
    void test11_deadLetterAfterRetryExhaustion_doesNotReleaseQuota() {
        CreateAdjustmentInvoiceCommand cmd = CreateAdjustmentInvoiceCommand.builder()
                .originalInvoiceId(testOriginalInvoiceId)
                .adjustmentType(AdjustmentType.DECREASE)
                .idempotencyKey(TEST_PREFIX + "ADJ_DEAD_LETTER_" + System.nanoTime())
                .itemsToAdjust(List.of(
                        AdjustItemCommand.builder()
                                .originalInvoiceItemId(testOriginalItemId1)
                                .quantityToAdjust(1)
                                .build()
                ))
                .build();

        Invoice adj = invoiceDomainService.createAdjustmentInvoice(cmd);
        invoiceDomainService.approveAdjustmentInvoice(adj.getId());

        InvoiceOutbox manualEvent = invoiceOutboxService.createEvent(
                "ISSUE_INVOICE",
                "INV-" + adj.getId(),
                adj.getId(),
                String.format("{\"invoiceId\":%d,\"invoiceType\":\"ADJUSTMENT\"}", adj.getId())
        );

        transactionTemplate.execute(status -> {
            InvoiceOutbox event = invoiceOutboxRepository.findById(manualEvent.getId()).orElseThrow();
            event.setStatus(OutboxStatus.DEAD_LETTER);
            event.setAttemptCount(5);
            return invoiceOutboxRepository.save(event);
        });

        InvoiceOutbox deadLetterEvent = invoiceOutboxRepository.findById(manualEvent.getId()).orElseThrow();
        assertThat(deadLetterEvent.getStatus()).isEqualTo(OutboxStatus.DEAD_LETTER);

        InvoiceBalanceAdjustment balance = invoiceBalanceAdjustmentRepository
                .findByOriginalInvoiceIdAndInvoiceItemId(testOriginalInvoiceId, testOriginalItemId1)
                .orElseThrow();
        assertThat(balance.getRemainingQuantity()).isEqualTo(2);
        assertThat(balance.getTotalAdjustedQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("Test 12: Stale worker lease rejection does not alter reservation")
    void test12_staleWorkerLeaseRejection_doesNotAlterReservation() {
        CreateAdjustmentInvoiceCommand cmd = CreateAdjustmentInvoiceCommand.builder()
                .originalInvoiceId(testOriginalInvoiceId)
                .adjustmentType(AdjustmentType.DECREASE)
                .idempotencyKey(TEST_PREFIX + "ADJ_STALE_WORKER_" + System.nanoTime())
                .itemsToAdjust(List.of(
                        AdjustItemCommand.builder()
                                .originalInvoiceItemId(testOriginalItemId1)
                                .quantityToAdjust(1)
                                .build()
                ))
                .build();

        Invoice adj = invoiceDomainService.createAdjustmentInvoice(cmd);
        invoiceDomainService.approveAdjustmentInvoice(adj.getId());

        InvoiceOutbox manualEvent = invoiceOutboxService.createEvent(
                "ISSUE_INVOICE",
                "INV-" + adj.getId(),
                adj.getId(),
                String.format("{\"invoiceId\":%d,\"invoiceType\":\"ADJUSTMENT\"}", adj.getId())
        );

        transactionTemplate.execute(status -> {
            InvoiceOutbox event = invoiceOutboxRepository.findById(manualEvent.getId()).orElseThrow();
            event.setStatus(OutboxStatus.PROCESSING);
            event.setLockedBy("worker-node-stale");
            event.setLockedUntil(LocalDateTime.now().minusMinutes(10));
            return invoiceOutboxRepository.save(event);
        });

        InvoiceBalanceAdjustment balance = invoiceBalanceAdjustmentRepository
                .findByOriginalInvoiceIdAndInvoiceItemId(testOriginalInvoiceId, testOriginalItemId1)
                .orElseThrow();
        assertThat(balance.getRemainingQuantity()).isEqualTo(2);
        assertThat(balance.getTotalAdjustedQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("Test 13: Golden Vector assertion for deterministic RefID matches expected UUIDv3 string")
    void test13_goldenVector_refIdMatchesExpectedUuidV3() {
        String inputKey = "INV_ADJ_RET_101_88";
        String expectedRefId = "e7ecf309-3138-31da-a60a-e48b15ccdbc6";

        String refId1 = MisaPayloadMapper.toDeterministicRefId(inputKey);
        String refId2 = MisaPayloadMapper.toDeterministicRefId(inputKey);

        assertThat(refId1).isEqualTo(expectedRefId);
        assertThat(refId2).isEqualTo(expectedRefId);

        String paddedKey = "  \t  INV_ADJ_RET_101_88  \r\n ";
        assertThat(MisaPayloadMapper.toDeterministicRefId(paddedKey)).isEqualTo(expectedRefId);

        assertThat(MisaPayloadMapper.toDeterministicRefId("ORDER_FULFILLMENT_RECEIPT_20261031_001"))
                .isEqualTo("4a88f414-4e67-3f1c-ba76-485c18530140");
    }

    @Test
    @DisplayName("Test 14: Transaction rollback leaves zero orphan reservation")
    void test14_transactionRollback_leavesZeroOrphanReservation() {
        InvoiceBalanceAdjustment initialBalance = invoiceBalanceAdjustmentRepository
                .findByOriginalInvoiceIdAndInvoiceItemId(testOriginalInvoiceId, testOriginalItemId1)
                .orElseThrow();
        assertThat(initialBalance.getRemainingQuantity()).isEqualTo(3);
        assertThat(initialBalance.getTotalAdjustedQuantity()).isEqualTo(0);

        String failIdempotencyKey = TEST_PREFIX + "ROLLBACK_" + System.nanoTime();

        assertThatThrownBy(() -> {
            transactionTemplate.execute(status -> {
                CreateAdjustmentInvoiceCommand cmd = CreateAdjustmentInvoiceCommand.builder()
                        .originalInvoiceId(testOriginalInvoiceId)
                        .adjustmentType(AdjustmentType.DECREASE)
                        .idempotencyKey(failIdempotencyKey)
                        .itemsToAdjust(List.of(
                                AdjustItemCommand.builder()
                                        .originalInvoiceItemId(testOriginalItemId1)
                                        .quantityToAdjust(1)
                                        .build()
                        ))
                        .build();

                invoiceDomainService.createAdjustmentInvoice(cmd);

                throw new RuntimeException("Simulated crash to test transaction rollback");
            });
        }).isInstanceOf(RuntimeException.class).hasMessageContaining("Simulated crash");

        assertThat(invoiceRepository.findByIdempotencyKey(failIdempotencyKey)).isEmpty();

        InvoiceBalanceAdjustment balanceAfterRollback = invoiceBalanceAdjustmentRepository
                .findByOriginalInvoiceIdAndInvoiceItemId(testOriginalInvoiceId, testOriginalItemId1)
                .orElseThrow();
        assertThat(balanceAfterRollback.getOriginalQuantity()).isEqualTo(3);
        assertThat(balanceAfterRollback.getRemainingQuantity()).isEqualTo(3);
        assertThat(balanceAfterRollback.getTotalAdjustedQuantity()).isEqualTo(0);
    }

    @Test
    @DisplayName("Test 15: Original invoice strictly retains ISSUED status after multiple adjustment child invoices are created")
    void test15_originalInvoice_remainsIssued_afterMultipleAdjustments() {
        CreateAdjustmentInvoiceCommand adj1Cmd = CreateAdjustmentInvoiceCommand.builder()
                .originalInvoiceId(testOriginalInvoiceId)
                .adjustmentType(AdjustmentType.DECREASE)
                .idempotencyKey(TEST_PREFIX + "ADJ_MULTI_1_" + System.nanoTime())
                .itemsToAdjust(List.of(
                        AdjustItemCommand.builder()
                                .originalInvoiceItemId(testOriginalItemId1)
                                .quantityToAdjust(1)
                                .build()
                ))
                .build();
        Invoice adj1 = invoiceDomainService.createAdjustmentInvoice(adj1Cmd);
        assertThat(adj1.getInvoiceType()).isEqualTo(InvoiceType.ADJUSTMENT);

        Invoice origAfterAdj1 = invoiceDomainService.getInvoice(testOriginalInvoiceId);
        assertThat(origAfterAdj1.getDocumentStatus()).isEqualTo(DocumentStatus.ISSUED);

        CreateAdjustmentInvoiceCommand adj2Cmd = CreateAdjustmentInvoiceCommand.builder()
                .originalInvoiceId(testOriginalInvoiceId)
                .adjustmentType(AdjustmentType.DECREASE)
                .idempotencyKey(TEST_PREFIX + "ADJ_MULTI_2_" + System.nanoTime())
                .itemsToAdjust(List.of(
                        AdjustItemCommand.builder()
                                .originalInvoiceItemId(testOriginalItemId1)
                                .quantityToAdjust(1)
                                .build()
                ))
                .build();
        Invoice adj2 = invoiceDomainService.createAdjustmentInvoice(adj2Cmd);
        assertThat(adj2.getInvoiceType()).isEqualTo(InvoiceType.ADJUSTMENT);

        Invoice origAfterAdj2 = invoiceDomainService.getInvoice(testOriginalInvoiceId);
        assertThat(origAfterAdj2.getDocumentStatus()).isEqualTo(DocumentStatus.ISSUED);
    }

    @Test
    @DisplayName("Test 16: Approved Adjustment does not call Provider (dispatch postponed to EINV-2.3.2)")
    void test16_approvedAdjustment_unsupportedDispatch_doesNotCallProvider() {
        CreateAdjustmentInvoiceCommand cmd = CreateAdjustmentInvoiceCommand.builder()
                .originalInvoiceId(testOriginalInvoiceId)
                .adjustmentType(AdjustmentType.DECREASE)
                .idempotencyKey(TEST_PREFIX + "ADJ_SAFE_DISPATCH_" + System.nanoTime())
                .itemsToAdjust(List.of(
                        AdjustItemCommand.builder()
                                .originalInvoiceItemId(testOriginalItemId1)
                                .quantityToAdjust(1)
                                .build()
                ))
                .build();

        Invoice adj = invoiceDomainService.createAdjustmentInvoice(cmd);
        invoiceDomainService.approveAdjustmentInvoice(adj.getId());

        assertThat(invoiceOutboxRepository.findByInvoiceId(adj.getId())).isEmpty();

        int processed = invoiceOutboxWorker.processNextBatch("test-worker-safe");
        assertThat(processed).isEqualTo(0);

        Invoice currentAdj = invoiceDomainService.getInvoice(adj.getId());
        assertThat(currentAdj.getDocumentStatus()).isEqualTo(DocumentStatus.PENDING_ISSUE);
    }

    @Test
    @DisplayName("Test 17: Worker refuses to dispatch Adjustment via Original payload mapper and throws on mapper")
    void test17_worker_doesNotSendAdjustmentViaOriginalPayloadMapper() {
        CreateAdjustmentInvoiceCommand cmd = CreateAdjustmentInvoiceCommand.builder()
                .originalInvoiceId(testOriginalInvoiceId)
                .adjustmentType(AdjustmentType.DECREASE)
                .idempotencyKey(TEST_PREFIX + "ADJ_BLOCKED_DISPATCH_" + System.nanoTime())
                .itemsToAdjust(List.of(
                        AdjustItemCommand.builder()
                                .originalInvoiceItemId(testOriginalItemId1)
                                .quantityToAdjust(1)
                                .build()
                ))
                .build();

        Invoice adj = invoiceDomainService.createAdjustmentInvoice(cmd);
        invoiceDomainService.approveAdjustmentInvoice(adj.getId());

        InvoiceOutbox manualEvent = invoiceOutboxService.createEvent(
                "ISSUE_INVOICE",
                "INV-" + adj.getId(),
                adj.getId(),
                String.format("{\"invoiceId\":%d,\"invoiceType\":\"ADJUSTMENT\"}", adj.getId())
        );

        List<InvoiceOutbox> claimed = invoiceOutboxService.claimEventsByType("ISSUE_INVOICE", "worker-adj", Duration.ofMinutes(5), 10);
        InvoiceOutbox claimedAdjEvent = claimed.stream().filter(e -> e.getId().equals(manualEvent.getId())).findFirst().orElseThrow();

        ProviderIssueRequest request = invoiceOutboxService.prepareIssueDispatch(claimedAdjEvent.getId(), claimedAdjEvent.getLeaseToken());
        assertThat(request).isNull();

        InvoiceOutbox reloadedEvent = invoiceOutboxRepository.findById(manualEvent.getId()).orElseThrow();
        assertThat(reloadedEvent.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(reloadedEvent.getLastError()).contains("Unsupported invoice type for provider dispatch: ADJUSTMENT");

        Invoice adjAfter = invoiceDomainService.getInvoice(adj.getId());
        assertThat(adjAfter.getDocumentStatus()).isEqualTo(DocumentStatus.ISSUE_FAILED);

        InvoiceBalanceAdjustment balance = invoiceBalanceAdjustmentRepository
                .findByOriginalInvoiceIdAndInvoiceItemId(testOriginalInvoiceId, testOriginalItemId1)
                .orElseThrow();
        assertThat(balance.getRemainingQuantity()).isEqualTo(3);
        assertThat(balance.getTotalAdjustedQuantity()).isEqualTo(0);

        invoiceBalanceValidationService.releaseAdjustmentBalance(adj.getId());
        InvoiceBalanceAdjustment balanceAfterSecondCall = invoiceBalanceAdjustmentRepository
                .findByOriginalInvoiceIdAndInvoiceItemId(testOriginalInvoiceId, testOriginalItemId1)
                .orElseThrow();
        assertThat(balanceAfterSecondCall.getRemainingQuantity()).isEqualTo(3);
        assertThat(balanceAfterSecondCall.getTotalAdjustedQuantity()).isEqualTo(0);

        Invoice origInvoice = invoiceDomainService.getInvoice(testOriginalInvoiceId);
        assertThat(origInvoice.getDocumentStatus()).isEqualTo(DocumentStatus.ISSUED);

        ProviderIssueRequest dummyAdjRequest = ProviderIssueRequest.builder()
                .invoiceId(adj.getId())
                .refId("dummy-ref")
                .invoiceType(InvoiceType.ADJUSTMENT)
                .build();

        assertThatThrownBy(() -> misaInvoiceProvider.issueInvoice(dummyAdjRequest))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Invoice issuance for type ADJUSTMENT is not supported");
    }

    @Test
    @DisplayName("Test 18: Original invoice dispatches normally without disruption")
    void test18_originalInvoice_dispatchesNormally() {
        Invoice orig = invoiceDomainService.getInvoice(testOriginalInvoiceId);
        List<InvoiceItem> items = invoiceDomainService.getInvoiceItems(testOriginalInvoiceId);

        InvoiceOutbox origEvent = invoiceOutboxRepository.findByInvoiceId(testOriginalInvoiceId).get(0);
        transactionTemplate.execute(status -> {
            Invoice origEntity = invoiceRepository.findById(testOriginalInvoiceId).orElseThrow();
            origEntity.setDocumentStatus(DocumentStatus.PENDING_ISSUE);
            invoiceRepository.save(origEntity);

            InvoiceOutbox e = invoiceOutboxRepository.findById(origEvent.getId()).orElseThrow();
            e.setStatus(OutboxStatus.PENDING);
            e.setLockedUntil(null);
            e.setLockedBy(null);
            e.setLeaseToken(null);
            return invoiceOutboxRepository.save(e);
        });

        List<InvoiceOutbox> claimed = invoiceOutboxService.claimEventsByType("ISSUE_INVOICE", "worker-orig", Duration.ofMinutes(5), 10);
        InvoiceOutbox claimedOrig = claimed.stream().filter(e -> e.getId().equals(origEvent.getId())).findFirst().orElseThrow();

        ProviderIssueRequest req = invoiceOutboxService.prepareIssueDispatch(claimedOrig.getId(), claimedOrig.getLeaseToken());
        assertThat(req).isNotNull();
        assertThat(req.getInvoiceType()).isEqualTo(InvoiceType.ORIGINAL);
        assertThat(req.getInvoiceId()).isEqualTo(testOriginalInvoiceId);
    }

    @Test
    @DisplayName("Test 19: Replacement invoice unsupported dispatch is blocked and throws on mapper")
    void test19_replacement_unsupportedDispatch_doesNotSendViaOriginalPayloadMapper() {
        ProviderIssueRequest dummyRepRequest = ProviderIssueRequest.builder()
                .invoiceId(999L)
                .refId("dummy-rep-ref")
                .invoiceType(InvoiceType.REPLACEMENT)
                .build();

        assertThatThrownBy(() -> misaInvoiceProvider.issueInvoice(dummyRepRequest))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Invoice issuance for type REPLACEMENT is not supported");
    }

    @Test
    @DisplayName("Test 20: releaseAdjustmentBalance rejects forbidden states (ISSUED, ISSUING, ISSUANCE_UNKNOWN, RECONCILING)")
    void test20_releaseAdjustmentBalance_rejectsForbiddenStates() {
        CreateAdjustmentInvoiceCommand cmd = CreateAdjustmentInvoiceCommand.builder()
                .originalInvoiceId(testOriginalInvoiceId)
                .adjustmentType(AdjustmentType.DECREASE)
                .idempotencyKey(TEST_PREFIX + "ADJ_FORBIDDEN_" + System.nanoTime())
                .itemsToAdjust(List.of(
                        AdjustItemCommand.builder()
                                .originalInvoiceItemId(testOriginalItemId1)
                                .quantityToAdjust(1)
                                .build()
                ))
                .build();

        Invoice adj = invoiceDomainService.createAdjustmentInvoice(cmd);

        List<DocumentStatus> forbiddenStatuses = List.of(
                DocumentStatus.ISSUED,
                DocumentStatus.ISSUING,
                DocumentStatus.ISSUANCE_UNKNOWN,
                DocumentStatus.RECONCILING
        );

        for (DocumentStatus st : forbiddenStatuses) {
            transactionTemplate.execute(status -> {
                Invoice e = invoiceRepository.findById(adj.getId()).orElseThrow();
                e.setDocumentStatus(st);
                return invoiceRepository.save(e);
            });

            assertThatThrownBy(() -> invoiceBalanceValidationService.releaseAdjustmentBalance(adj.getId()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot release balance for adjustment invoice")
                    .hasMessageContaining("in status " + st);
        }
    }

    @Test
    @DisplayName("Test 21: releaseAdjustmentBalance fails fast and rolls back when adjustment item has missing originalInvoiceItem")
    void test21_releaseAdjustmentBalance_failsFastOnMissingOriginalItem() {
        CreateAdjustmentInvoiceCommand cmd = CreateAdjustmentInvoiceCommand.builder()
                .originalInvoiceId(testOriginalInvoiceId)
                .adjustmentType(AdjustmentType.DECREASE)
                .idempotencyKey(TEST_PREFIX + "ADJ_MISSING_ORIG_ITEM_" + System.nanoTime())
                .itemsToAdjust(List.of(
                        AdjustItemCommand.builder()
                                .originalInvoiceItemId(testOriginalItemId1)
                                .quantityToAdjust(1)
                                .build()
                ))
                .build();

        Invoice adj = invoiceDomainService.createAdjustmentInvoice(cmd);

        // Intentionally corrupt one item to simulate missing originalInvoiceItem
        transactionTemplate.execute(status -> {
            InvoiceItem item = invoiceItemRepository.findByInvoiceId(adj.getId()).get(0);
            item.setOriginalInvoiceItem(null);
            return invoiceItemRepository.save(item);
        });

        assertThatThrownBy(() -> invoiceBalanceValidationService.releaseAdjustmentBalance(adj.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("has no linked originalInvoiceItem; cannot safely release balance");

        // Verify transaction rolled back: document status is NOT ISSUE_FAILED
        Invoice reloadedAdj = invoiceDomainService.getInvoice(adj.getId());
        assertThat(reloadedAdj.getDocumentStatus()).isEqualTo(DocumentStatus.DRAFT);

        // Verify balance was NOT partially credited: quota remains reserved
        InvoiceBalanceAdjustment balance = invoiceBalanceAdjustmentRepository
                .findByOriginalInvoiceIdAndInvoiceItemId(testOriginalInvoiceId, testOriginalItemId1)
                .orElseThrow();
        assertThat(balance.getRemainingQuantity()).isEqualTo(2);
        assertThat(balance.getTotalAdjustedQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("Test 22: releaseAdjustmentBalance fails fast and rolls back when balance record is missing")
    void test22_releaseAdjustmentBalance_failsFastOnMissingBalanceRecord() {
        CreateAdjustmentInvoiceCommand cmd = CreateAdjustmentInvoiceCommand.builder()
                .originalInvoiceId(testOriginalInvoiceId)
                .adjustmentType(AdjustmentType.DECREASE)
                .idempotencyKey(TEST_PREFIX + "ADJ_MISSING_BAL_" + System.nanoTime())
                .itemsToAdjust(List.of(
                        AdjustItemCommand.builder()
                                .originalInvoiceItemId(testOriginalItemId1)
                                .quantityToAdjust(1)
                                .build()
                ))
                .build();

        Invoice adj = invoiceDomainService.createAdjustmentInvoice(cmd);

        // Delete balance record to simulate inconsistent data
        transactionTemplate.execute(status -> {
            InvoiceBalanceAdjustment bal = invoiceBalanceAdjustmentRepository
                    .findByOriginalInvoiceIdAndInvoiceItemId(testOriginalInvoiceId, testOriginalItemId1)
                    .orElseThrow();
            invoiceBalanceAdjustmentRepository.delete(bal);
            return null;
        });

        assertThatThrownBy(() -> invoiceBalanceValidationService.releaseAdjustmentBalance(adj.getId()))
                .isInstanceOf(InvoiceBalanceExceededException.class)
                .hasMessageContaining("No balance records found for original invoice ID");

        // Verify transaction rolled back: document status is NOT ISSUE_FAILED
        Invoice reloadedAdj = invoiceDomainService.getInvoice(adj.getId());
        assertThat(reloadedAdj.getDocumentStatus()).isEqualTo(DocumentStatus.DRAFT);
    }

    @Test
    @DisplayName("Test 23: transitionStatus to ISSUE_FAILED invokes releaseAdjustmentBalance atomically")
    void test23_transitionStatusToIssueFailed_releasesBalanceAtomically() {
        CreateAdjustmentInvoiceCommand cmd = CreateAdjustmentInvoiceCommand.builder()
                .originalInvoiceId(testOriginalInvoiceId)
                .adjustmentType(AdjustmentType.DECREASE)
                .idempotencyKey(TEST_PREFIX + "ADJ_TRANSITION_FAIL_" + System.nanoTime())
                .itemsToAdjust(List.of(
                        AdjustItemCommand.builder()
                                .originalInvoiceItemId(testOriginalItemId1)
                                .quantityToAdjust(1)
                                .build()
                ))
                .build();

        Invoice adj = invoiceDomainService.createAdjustmentInvoice(cmd);
        assertThat(adj.getDocumentStatus()).isEqualTo(DocumentStatus.DRAFT);

        Invoice failed = invoiceDomainService.transitionStatus(adj.getId(), DocumentStatus.ISSUE_FAILED);
        assertThat(failed.getDocumentStatus()).isEqualTo(DocumentStatus.ISSUE_FAILED);

        // Verify balance was released
        InvoiceBalanceAdjustment balance = invoiceBalanceAdjustmentRepository
                .findByOriginalInvoiceIdAndInvoiceItemId(testOriginalInvoiceId, testOriginalItemId1)
                .orElseThrow();
        assertThat(balance.getRemainingQuantity()).isEqualTo(3);
        assertThat(balance.getTotalAdjustedQuantity()).isEqualTo(0);
    }

    @Test
    @DisplayName("Test 24: releaseAdjustmentBalance lock ordering and entity resolution when originalInvoiceId < adjustmentInvoiceId")
    void test24_releaseAdjustmentBalance_lockOrder_origLessThanAdj() {
        Long origId = testOriginalInvoiceId;
        Invoice orig = invoiceDomainService.getInvoice(origId);
        assertThat(orig.getDocumentStatus()).isEqualTo(DocumentStatus.ISSUED);

        InvoiceBalanceAdjustment initialBal = invoiceBalanceAdjustmentRepository
                .findByOriginalInvoiceIdAndInvoiceItemId(origId, testOriginalItemId1).orElseThrow();
        int initialRemaining = initialBal.getRemainingQuantity();
        BigDecimal initialTaxable = initialBal.getRemainingTaxableAmount();
        BigDecimal initialTax = initialBal.getRemainingTaxAmount();

        Invoice adjA = invoiceDomainService.createAdjustmentInvoice(CreateAdjustmentInvoiceCommand.builder()
                .originalInvoiceId(origId)
                .adjustmentType(AdjustmentType.DECREASE)
                .idempotencyKey(TEST_PREFIX + "LOCK_A_" + System.nanoTime())
                .itemsToAdjust(List.of(AdjustItemCommand.builder().originalInvoiceItemId(testOriginalItemId1).quantityToAdjust(1).build()))
                .build());

        Invoice adjB = invoiceDomainService.createAdjustmentInvoice(CreateAdjustmentInvoiceCommand.builder()
                .originalInvoiceId(origId)
                .adjustmentType(AdjustmentType.DECREASE)
                .idempotencyKey(TEST_PREFIX + "LOCK_B_" + System.nanoTime())
                .itemsToAdjust(List.of(AdjustItemCommand.builder().originalInvoiceItemId(testOriginalItemId1).quantityToAdjust(1).build()))
                .build());

        // Verify natural ordering origId < adjA < adjB
        assertThat(origId).isLessThan(adjA.getId());
        assertThat(adjA.getId()).isLessThan(adjB.getId());

        // Release Adj A
        invoiceBalanceValidationService.releaseAdjustmentBalance(adjA.getId());

        // 1. Verify correct Adjustment Invoice is moved to ISSUE_FAILED
        Invoice adjAReloaded = invoiceDomainService.getInvoice(adjA.getId());
        assertThat(adjAReloaded.getDocumentStatus()).isEqualTo(DocumentStatus.ISSUE_FAILED);

        // 2. Verify Original Invoice remains untouched in ISSUED
        Invoice origReloaded = invoiceDomainService.getInvoice(origId);
        assertThat(origReloaded.getDocumentStatus()).isEqualTo(DocumentStatus.ISSUED);

        // 3. Verify Adj B remains untouched in DRAFT
        Invoice adjBReloaded = invoiceDomainService.getInvoice(adjB.getId());
        assertThat(adjBReloaded.getDocumentStatus()).isEqualTo(DocumentStatus.DRAFT);

        // 4. Verify only Adj A's quota was released; Adj B's quota is still held
        InvoiceBalanceAdjustment balAfterA = invoiceBalanceAdjustmentRepository
                .findByOriginalInvoiceIdAndInvoiceItemId(origId, testOriginalItemId1).orElseThrow();
        assertThat(balAfterA.getRemainingQuantity()).isEqualTo(initialRemaining - 1);
        assertThat(balAfterA.getTotalAdjustedQuantity()).isEqualTo(1);

        // 5. Repeat release on Adj A is idempotent
        invoiceBalanceValidationService.releaseAdjustmentBalance(adjA.getId());
        InvoiceBalanceAdjustment balAfterRepeatA = invoiceBalanceAdjustmentRepository
                .findByOriginalInvoiceIdAndInvoiceItemId(origId, testOriginalItemId1).orElseThrow();
        assertThat(balAfterRepeatA.getRemainingQuantity()).isEqualTo(initialRemaining - 1);
        assertThat(balAfterRepeatA.getTotalAdjustedQuantity()).isEqualTo(1);

        // Clean up Adj B
        invoiceBalanceValidationService.releaseAdjustmentBalance(adjB.getId());
        InvoiceBalanceAdjustment balFinal = invoiceBalanceAdjustmentRepository
                .findByOriginalInvoiceIdAndInvoiceItemId(origId, testOriginalItemId1).orElseThrow();
        assertThat(balFinal.getRemainingQuantity()).isEqualTo(initialRemaining);
        assertThat(balFinal.getTotalAdjustedQuantity()).isEqualTo(0);
    }

    @Test
    @DisplayName("Test 25: releaseAdjustmentBalance lock ordering and entity resolution when adjustmentInvoiceId < originalInvoiceId")
    void test25_releaseAdjustmentBalance_lockOrder_adjLessThanOrig() {
        Invoice templateOrig = invoiceDomainService.getInvoice(testOriginalInvoiceId);

        // Step 1: Create the early Adjustment Invoice first so its auto-increment ID is SMALLER
        Invoice earlyAdj = transactionTemplate.execute(status -> {
            Invoice adj = Invoice.builder()
                    .order(orderRepository.findById(testOrderId).orElseThrow())
                    .buyerProfile(templateOrig.getBuyerProfile())
                    .sellerProfile(templateOrig.getSellerProfile())
                    .invoiceType(InvoiceType.ADJUSTMENT)
                    .adjustmentType(AdjustmentType.DECREASE)
                    .documentStatus(DocumentStatus.DRAFT)
                    .taxPointDate(LocalDateTime.now())
                    .pricingBasis(PricingBasis.INCLUSIVE)
                    .idempotencyKey(TEST_PREFIX + "EARLY_ADJ_" + System.nanoTime())
                    .build();
            return invoiceRepository.save(adj);
        });

        // Step 2: Create the late Original Invoice second so its auto-increment ID is LARGER
        Invoice lateOrig = transactionTemplate.execute(status -> {
            Invoice orig = Invoice.builder()
                    .order(orderRepository.findById(testOrderId).orElseThrow())
                    .buyerProfile(templateOrig.getBuyerProfile())
                    .sellerProfile(templateOrig.getSellerProfile())
                    .invoiceType(InvoiceType.ORIGINAL)
                    .documentStatus(DocumentStatus.ISSUED)
                    .taxPointDate(LocalDateTime.now())
                    .pricingBasis(PricingBasis.INCLUSIVE)
                    .idempotencyKey(TEST_PREFIX + "LATE_ORIG_" + System.nanoTime())
                    .build();
            return invoiceRepository.save(orig);
        });

        // Strict verification: adjustmentInvoiceId MUST be strictly less than originalInvoiceId
        assertThat(earlyAdj.getId()).isLessThan(lateOrig.getId());

        // Step 3: Create items for lateOrig and earlyAdj, plus balance row
        Long lateOrigItemId = transactionTemplate.execute(status -> {
            InvoiceItem origItem = InvoiceItem.builder()
                    .invoice(lateOrig)
                    .itemType(InvoiceItemType.MERCHANDISE)
                    .itemName(TEST_PREFIX + "Test Item Inverted")
                    .unit("cai")
                    .originalQuantity(5)
                    .quantity(5)
                    .unitPriceGross(new BigDecimal("100000.00"))
                    .unitPriceNet(new BigDecimal("92592.5926"))
                    .taxableAmount(new BigDecimal("462962.96"))
                    .taxCategory("clothing")
                    .taxTreatment(TaxTreatment.TAXABLE)
                    .taxRate(new BigDecimal("0.08"))
                    .taxCode("VAT8")
                    .taxAmount(new BigDecimal("37037.04"))
                    .discountAmount(BigDecimal.ZERO)
                    .lineTotalGross(new BigDecimal("500000.00"))
                    .build();
            InvoiceItem savedOrigItem = invoiceItemRepository.save(origItem);

            // Create balance row for lateOrig holding 2 reserved by earlyAdj, 3 remaining
            InvoiceBalanceAdjustment bal = InvoiceBalanceAdjustment.builder()
                    .originalInvoice(lateOrig)
                    .invoiceItem(savedOrigItem)
                    .originalQuantity(5)
                    .totalAdjustedQuantity(2)
                    .remainingQuantity(3)
                    .originalTaxableAmount(new BigDecimal("462962.96"))
                    .totalAdjustedTaxableAmount(new BigDecimal("185185.18"))
                    .remainingTaxableAmount(new BigDecimal("277777.78"))
                    .originalTaxAmount(new BigDecimal("37037.04"))
                    .totalAdjustedTaxAmount(new BigDecimal("14814.82"))
                    .remainingTaxAmount(new BigDecimal("22222.22"))
                    .version(0L)
                    .build();
            invoiceBalanceAdjustmentRepository.save(bal);

            // Link earlyAdj to lateOrig
            earlyAdj.setOriginalInvoice(lateOrig);
            invoiceRepository.save(earlyAdj);

            // Create item on earlyAdj referencing lateOrigItem
            InvoiceItem adjItem = InvoiceItem.builder()
                    .invoice(earlyAdj)
                    .originalInvoiceItem(savedOrigItem)
                    .itemType(InvoiceItemType.MERCHANDISE)
                    .itemName(TEST_PREFIX + "Test Item Inverted Adj")
                    .unit("cai")
                    .originalQuantity(5)
                    .quantity(-2)
                    .unitPriceGross(new BigDecimal("100000.00"))
                    .unitPriceNet(new BigDecimal("92592.5926"))
                    .taxableAmount(new BigDecimal("-185185.18"))
                    .taxCategory("clothing")
                    .taxTreatment(TaxTreatment.TAXABLE)
                    .taxRate(new BigDecimal("0.08"))
                    .taxCode("VAT8")
                    .taxAmount(new BigDecimal("-14814.82"))
                    .discountAmount(BigDecimal.ZERO)
                    .lineTotalGross(new BigDecimal("-200000.00"))
                    .build();
            invoiceItemRepository.save(adjItem);

            return savedOrigItem.getId();
        });

        // Step 4: Execute release on earlyAdj (where adjustmentInvoiceId < originalInvoiceId)
        invoiceBalanceValidationService.releaseAdjustmentBalance(earlyAdj.getId());

        // 1. Verify correct Adjustment Invoice (first entity locked) is moved to ISSUE_FAILED
        Invoice reloadedAdj = invoiceDomainService.getInvoice(earlyAdj.getId());
        assertThat(reloadedAdj.getDocumentStatus()).isEqualTo(DocumentStatus.ISSUE_FAILED);

        // 2. CRITICAL: Verify Original Invoice (second entity locked) was NOT mutated to ISSUE_FAILED
        Invoice reloadedOrig = invoiceDomainService.getInvoice(lateOrig.getId());
        assertThat(reloadedOrig.getDocumentStatus()).isEqualTo(DocumentStatus.ISSUED);

        // 3. Verify lateOrig balance was correctly restored from 3 to 5
        InvoiceBalanceAdjustment balAfterRelease = invoiceBalanceAdjustmentRepository
                .findByOriginalInvoiceIdAndInvoiceItemId(lateOrig.getId(), lateOrigItemId).orElseThrow();
        assertThat(balAfterRelease.getRemainingQuantity()).isEqualTo(5);
        assertThat(balAfterRelease.getTotalAdjustedQuantity()).isEqualTo(0);
        assertThat(balAfterRelease.getRemainingTaxableAmount()).isEqualByComparingTo(new BigDecimal("462962.96"));
        assertThat(balAfterRelease.getRemainingTaxAmount()).isEqualByComparingTo(new BigDecimal("37037.04"));

        // 4. Verify repeated release is idempotent and does not corrupt state
        invoiceBalanceValidationService.releaseAdjustmentBalance(earlyAdj.getId());
        InvoiceBalanceAdjustment balAfterRepeat = invoiceBalanceAdjustmentRepository
                .findByOriginalInvoiceIdAndInvoiceItemId(lateOrig.getId(), lateOrigItemId).orElseThrow();
        assertThat(balAfterRepeat.getRemainingQuantity()).isEqualTo(5);
        assertThat(balAfterRepeat.getTotalAdjustedQuantity()).isEqualTo(0);
    }

    @Test
    @DisplayName("Test 26 (Case A): Concurrent Create vs Release on same Original Invoice - zero deadlock and accurate quota")
    void test26_concurrentCreateVsRelease_noDeadlock() throws Exception {
        Long origId = testOriginalInvoiceId;
        InvoiceBalanceAdjustment balInitial = invoiceBalanceAdjustmentRepository
                .findByOriginalInvoiceIdAndInvoiceItemId(origId, testOriginalItemId1).orElseThrow();
        int initialRemaining = balInitial.getRemainingQuantity();

        // Create an existing adjustment invoice in DRAFT to be released by Thread 2
        Invoice existingAdj = invoiceDomainService.createAdjustmentInvoice(CreateAdjustmentInvoiceCommand.builder()
                .originalInvoiceId(origId)
                .adjustmentType(AdjustmentType.DECREASE)
                .idempotencyKey(TEST_PREFIX + "CONC_EXISTING_" + System.nanoTime())
                .itemsToAdjust(List.of(AdjustItemCommand.builder().originalInvoiceItemId(testOriginalItemId1).quantityToAdjust(1).build()))
                .build());

        InvoiceBalanceAdjustment balAfterExisting = invoiceBalanceAdjustmentRepository
                .findByOriginalInvoiceIdAndInvoiceItemId(origId, testOriginalItemId1).orElseThrow();
        assertThat(balAfterExisting.getRemainingQuantity()).isEqualTo(initialRemaining - 1);

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

        // Thread 1: Create a new Adjustment
        executor.submit(() -> {
            try {
                startLatch.await();
                invoiceDomainService.createAdjustmentInvoice(CreateAdjustmentInvoiceCommand.builder()
                        .originalInvoiceId(origId)
                        .adjustmentType(AdjustmentType.DECREASE)
                        .idempotencyKey(TEST_PREFIX + "CONC_NEW_" + System.nanoTime())
                        .itemsToAdjust(List.of(AdjustItemCommand.builder().originalInvoiceItemId(testOriginalItemId1).quantityToAdjust(1).build()))
                        .build());
            } catch (Throwable t) {
                errors.add(t);
            } finally {
                doneLatch.countDown();
            }
        });

        // Thread 2: Release the existing Adjustment
        executor.submit(() -> {
            try {
                startLatch.await();
                invoiceBalanceValidationService.releaseAdjustmentBalance(existingAdj.getId());
            } catch (Throwable t) {
                errors.add(t);
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        boolean completed = doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        assertThat(completed).isTrue();
        assertThat(errors).isEmpty();

        // Net effect: existing released (+1), new created (-1) -> remaining should equal initialRemaining - 1
        InvoiceBalanceAdjustment balFinal = invoiceBalanceAdjustmentRepository
                .findByOriginalInvoiceIdAndInvoiceItemId(origId, testOriginalItemId1).orElseThrow();
        assertThat(balFinal.getRemainingQuantity()).isEqualTo(initialRemaining - 1);

        // Verify existing adjustment is in ISSUE_FAILED
        Invoice releasedAdj = invoiceDomainService.getInvoice(existingAdj.getId());
        assertThat(releasedAdj.getDocumentStatus()).isEqualTo(DocumentStatus.ISSUE_FAILED);
    }

    @Test
    @DisplayName("Test 27 (Case C): Concurrent Release vs Release on the same Adjustment Invoice - exactly once release")
    void test27_concurrentReleaseVsRelease_exactlyOnce() throws Exception {
        Long origId = testOriginalInvoiceId;
        InvoiceBalanceAdjustment balInitial = invoiceBalanceAdjustmentRepository
                .findByOriginalInvoiceIdAndInvoiceItemId(origId, testOriginalItemId1).orElseThrow();
        int initialRemaining = balInitial.getRemainingQuantity();

        Invoice adj = invoiceDomainService.createAdjustmentInvoice(CreateAdjustmentInvoiceCommand.builder()
                .originalInvoiceId(origId)
                .adjustmentType(AdjustmentType.DECREASE)
                .idempotencyKey(TEST_PREFIX + "CONC_REL_REL_" + System.nanoTime())
                .itemsToAdjust(List.of(AdjustItemCommand.builder().originalInvoiceItemId(testOriginalItemId1).quantityToAdjust(1).build()))
                .build());

        InvoiceBalanceAdjustment balAfterCreate = invoiceBalanceAdjustmentRepository
                .findByOriginalInvoiceIdAndInvoiceItemId(origId, testOriginalItemId1).orElseThrow();
        assertThat(balAfterCreate.getRemainingQuantity()).isEqualTo(initialRemaining - 1);

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    invoiceBalanceValidationService.releaseAdjustmentBalance(adj.getId());
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        assertThat(completed).isTrue();
        assertThat(errors).isEmpty();

        // Quota MUST be restored exactly once (+1, not +2)
        InvoiceBalanceAdjustment balFinal = invoiceBalanceAdjustmentRepository
                .findByOriginalInvoiceIdAndInvoiceItemId(origId, testOriginalItemId1).orElseThrow();
        assertThat(balFinal.getRemainingQuantity()).isEqualTo(initialRemaining);
        assertThat(balFinal.getTotalAdjustedQuantity()).isEqualTo(balInitial.getTotalAdjustedQuantity());

        Invoice adjFinal = invoiceDomainService.getInvoice(adj.getId());
        assertThat(adjFinal.getDocumentStatus()).isEqualTo(DocumentStatus.ISSUE_FAILED);
    }

    @Test
    @DisplayName("Test 28 (Case D): Concurrent mixed ID ordering release operations - no lock inversion deadlock")
    void test28_concurrentMixedIdOrdering_noDeadlock() throws Exception {
        Invoice templateOrig = invoiceDomainService.getInvoice(testOriginalInvoiceId);

        // Setup Pair 1: earlyAdj1 (ID X) < lateOrig1 (ID Y)
        Invoice earlyAdj1 = transactionTemplate.execute(status -> {
            Invoice adj = Invoice.builder()
                    .order(orderRepository.findById(testOrderId).orElseThrow())
                    .buyerProfile(templateOrig.getBuyerProfile())
                    .sellerProfile(templateOrig.getSellerProfile())
                    .invoiceType(InvoiceType.ADJUSTMENT)
                    .adjustmentType(AdjustmentType.DECREASE)
                    .documentStatus(DocumentStatus.DRAFT)
                    .taxPointDate(LocalDateTime.now())
                    .pricingBasis(PricingBasis.INCLUSIVE)
                    .idempotencyKey(TEST_PREFIX + "MIX_EARLY1_" + System.nanoTime())
                    .build();
            return invoiceRepository.save(adj);
        });

        Invoice lateOrig1 = transactionTemplate.execute(status -> {
            Invoice orig = Invoice.builder()
                    .order(orderRepository.findById(testOrderId).orElseThrow())
                    .buyerProfile(templateOrig.getBuyerProfile())
                    .sellerProfile(templateOrig.getSellerProfile())
                    .invoiceType(InvoiceType.ORIGINAL)
                    .documentStatus(DocumentStatus.ISSUED)
                    .taxPointDate(LocalDateTime.now())
                    .pricingBasis(PricingBasis.INCLUSIVE)
                    .idempotencyKey(TEST_PREFIX + "MIX_LATE1_" + System.nanoTime())
                    .build();
            return invoiceRepository.save(orig);
        });

        assertThat(earlyAdj1.getId()).isLessThan(lateOrig1.getId());

        transactionTemplate.execute(status -> {
            InvoiceItem origItem = InvoiceItem.builder()
                    .invoice(lateOrig1)
                    .itemType(InvoiceItemType.MERCHANDISE)
                    .itemName(TEST_PREFIX + "Mix Item 1")
                    .unit("cai")
                    .originalQuantity(5)
                    .quantity(5)
                    .unitPriceGross(new BigDecimal("100000.00"))
                    .unitPriceNet(new BigDecimal("92592.5926"))
                    .taxableAmount(new BigDecimal("462962.96"))
                    .taxCategory("clothing")
                    .taxTreatment(TaxTreatment.TAXABLE)
                    .taxRate(new BigDecimal("0.08"))
                    .taxCode("VAT8")
                    .taxAmount(new BigDecimal("37037.04"))
                    .discountAmount(BigDecimal.ZERO)
                    .lineTotalGross(new BigDecimal("500000.00"))
                    .build();
            InvoiceItem savedOrigItem = invoiceItemRepository.save(origItem);

            InvoiceBalanceAdjustment bal = InvoiceBalanceAdjustment.builder()
                    .originalInvoice(lateOrig1)
                    .invoiceItem(savedOrigItem)
                    .originalQuantity(5)
                    .totalAdjustedQuantity(1)
                    .remainingQuantity(4)
                    .originalTaxableAmount(new BigDecimal("462962.96"))
                    .totalAdjustedTaxableAmount(new BigDecimal("92592.59"))
                    .remainingTaxableAmount(new BigDecimal("370370.37"))
                    .originalTaxAmount(new BigDecimal("37037.04"))
                    .totalAdjustedTaxAmount(new BigDecimal("7407.41"))
                    .remainingTaxAmount(new BigDecimal("29629.63"))
                    .version(0L)
                    .build();
            invoiceBalanceAdjustmentRepository.save(bal);

            earlyAdj1.setOriginalInvoice(lateOrig1);
            invoiceRepository.save(earlyAdj1);

            InvoiceItem adjItem = InvoiceItem.builder()
                    .invoice(earlyAdj1)
                    .originalInvoiceItem(savedOrigItem)
                    .itemType(InvoiceItemType.MERCHANDISE)
                    .itemName(TEST_PREFIX + "Mix Item 1 Adj")
                    .unit("cai")
                    .originalQuantity(5)
                    .quantity(-1)
                    .unitPriceGross(new BigDecimal("100000.00"))
                    .unitPriceNet(new BigDecimal("92592.5926"))
                    .taxableAmount(new BigDecimal("-92592.59"))
                    .taxCategory("clothing")
                    .taxTreatment(TaxTreatment.TAXABLE)
                    .taxRate(new BigDecimal("0.08"))
                    .taxCode("VAT8")
                    .taxAmount(new BigDecimal("-7407.41"))
                    .discountAmount(BigDecimal.ZERO)
                    .lineTotalGross(new BigDecimal("-100000.00"))
                    .build();
            invoiceItemRepository.save(adjItem);
            return null;
        });

        // Setup Pair 2: standard origId < adjId
        Invoice adjStandard = invoiceDomainService.createAdjustmentInvoice(CreateAdjustmentInvoiceCommand.builder()
                .originalInvoiceId(testOriginalInvoiceId)
                .adjustmentType(AdjustmentType.DECREASE)
                .idempotencyKey(TEST_PREFIX + "MIX_STD_" + System.nanoTime())
                .itemsToAdjust(List.of(AdjustItemCommand.builder().originalInvoiceItemId(testOriginalItemId1).quantityToAdjust(1).build()))
                .build());
        assertThat(testOriginalInvoiceId).isLessThan(adjStandard.getId());

        // Concurrent execution of both releases
        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

        executor.submit(() -> {
            try {
                startLatch.await();
                invoiceBalanceValidationService.releaseAdjustmentBalance(earlyAdj1.getId());
            } catch (Throwable t) {
                errors.add(t);
            } finally {
                doneLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                invoiceBalanceValidationService.releaseAdjustmentBalance(adjStandard.getId());
            } catch (Throwable t) {
                errors.add(t);
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        boolean completed = doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        assertThat(completed).isTrue();
        assertThat(errors).isEmpty();

        Invoice reloadedEarlyAdj = invoiceDomainService.getInvoice(earlyAdj1.getId());
        assertThat(reloadedEarlyAdj.getDocumentStatus()).isEqualTo(DocumentStatus.ISSUE_FAILED);

        Invoice reloadedLateOrig = invoiceDomainService.getInvoice(lateOrig1.getId());
        assertThat(reloadedLateOrig.getDocumentStatus()).isEqualTo(DocumentStatus.ISSUED);

        Invoice reloadedStdAdj = invoiceDomainService.getInvoice(adjStandard.getId());
        assertThat(reloadedStdAdj.getDocumentStatus()).isEqualTo(DocumentStatus.ISSUE_FAILED);
    }

    @Test
    @DisplayName("Test 29: Quota Invariant lifecycle (Orig=5, AdjA=2, AdjB=3, ReleaseA, RepeatReleaseA, ReleaseB)")
    void test29_quotaInvariant_exactSequenceVerification() {
        Long fiveOrderId = transactionTemplate.execute(status -> {
            Role customerRole = roleRepository.findByName("customer").orElseThrow();
            String uniquePhone = "09" + String.format("%08d", Math.abs(System.nanoTime() % 100000000L));
            User user = userRepository.save(User.builder()
                    .email(TEST_PREFIX + "user_inv_" + System.nanoTime() + "@example.com")
                    .passwordHash("secret123")
                    .name(TEST_PREFIX + "Invariant User")
                    .phone(uniquePhone)
                    .role(customerRole)
                    .status(UserStatus.ACTIVE)
                    .dataSource(DataSourceType.WEBSITE)
                    .build());

            ProductVariant variant = productVariantRepository.findAll().get(0);

            Order order = Order.builder()
                    .user(user)
                    .receiverName(TEST_PREFIX + "Buyer Invariant")
                    .receiverPhone(uniquePhone)
                    .province("Hà Nội")
                    .paymentMethod(PaymentMethod.COD)
                    .subtotal(new BigDecimal("500000.00"))
                    .discountAmount(BigDecimal.ZERO)
                    .shippingFee(BigDecimal.ZERO)
                    .totalAmount(new BigDecimal("500000.00"))
                    .status(OrderStatus.SHIPPING)
                    .dataSource(DataSourceType.WEBSITE)
                    .createdAt(LocalDateTime.now().minusDays(1))
                    .build();

            OrderDetail d = OrderDetail.builder()
                    .variant(variant)
                    .productName(TEST_PREFIX + "Jacket Invariant (5 units)")
                    .variantSize("L")
                    .variantColor("Black")
                    .quantity(5)
                    .unitPrice(new BigDecimal("100000.00"))
                    .discountAmount(BigDecimal.ZERO)
                    .build();
            order.addOrderDetail(d);
            return orderRepository.save(order).getId();
        });

        OrderDetail detail = orderDetailRepository.findByOrderId(fiveOrderId).get(0);

        CreateFulfillmentEventCommand fCmd = CreateFulfillmentEventCommand.builder()
                .orderId(fiveOrderId)
                .fulfillmentCode(TEST_PREFIX + "FUL_INV_" + System.nanoTime())
                .carrierName("GHN")
                .trackingNumber(TEST_PREFIX + "TRK_INV_" + System.nanoTime())
                .deliveredAt(LocalDateTime.now())
                .proofSource(ProofSource.CARRIER_WEBHOOK)
                .proofReference("PROOF_INV")
                .items(List.of(
                        CreateFulfillmentItemCommand.builder()
                                .orderDetailId(detail.getId())
                                .deliveredQuantity(5)
                                .unitPriceSnapshot(new BigDecimal("100000.00"))
                                .discountAllocated(BigDecimal.ZERO)
                                .build()
                ))
                .build();
        FulfillmentEvent fe = fulfillmentService.recordFulfillment(fCmd);

        BuyerInvoiceProfile buyer = BuyerInvoiceProfile.builder()
                .customerType(CustomerType.INDIVIDUAL)
                .buyerName("Nguyen Invariant")
                .address("123 Le Loi, TP.HCM")
                .email("inv@example.com")
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
                .orderId(fiveOrderId)
                .fulfillmentEventId(fe.getId())
                .taxPoint(LocalDateTime.now())
                .pricingBasis(PricingBasis.INCLUSIVE)
                .merchandiseVoucherAmount(BigDecimal.ZERO)
                .shippingFee(BigDecimal.ZERO)
                .shippingPromotionAmount(BigDecimal.ZERO)
                .shippingTaxCategory("shipping")
                .items(List.of(
                        InvoiceLineCalculationCommand.builder()
                                .orderDetailId(detail.getId())
                                .itemName(TEST_PREFIX + "Jacket Invariant (5 units)")
                                .unit("cai")
                                .quantity(5)
                                .unitPrice(new BigDecimal("100000.00"))
                                .taxCategory("clothing")
                                .isDiscountable(true)
                                .build()
                ))
                .build();

        Invoice orig = invoiceDomainService.createOriginalInvoice(
                fe.getId(),
                TEST_PREFIX + "ORIG_INV_" + System.nanoTime(),
                buyer,
                seller,
                calcCmd
        );
        Long origId = orig.getId();

        transactionTemplate.execute(status -> {
            Invoice e = invoiceRepository.findById(origId).orElseThrow();
            e.setDocumentStatus(DocumentStatus.ISSUED);
            return invoiceRepository.save(e);
        });

        InvoiceItem item = invoiceDomainService.getInvoiceItems(origId).get(0);
        Long itemId = item.getId();

        // 1. Initial State: Original quantity = 5
        InvoiceBalanceAdjustment bal0 = invoiceBalanceAdjustmentRepository
                .findByOriginalInvoiceIdAndInvoiceItemId(origId, itemId).orElseThrow();
        BigDecimal origTaxable = bal0.getOriginalTaxableAmount();
        BigDecimal origTax = bal0.getOriginalTaxAmount();
        assertBalanceInvariants(bal0, 5, 0, origTaxable, BigDecimal.ZERO, origTax, BigDecimal.ZERO);

        // 2. Adjustment A reserves 2 units
        Invoice adjA = invoiceDomainService.createAdjustmentInvoice(CreateAdjustmentInvoiceCommand.builder()
                .originalInvoiceId(origId)
                .adjustmentType(AdjustmentType.DECREASE)
                .idempotencyKey(TEST_PREFIX + "INV_A_" + System.nanoTime())
                .itemsToAdjust(List.of(AdjustItemCommand.builder().originalInvoiceItemId(itemId).quantityToAdjust(2).build()))
                .build());

        InvoiceBalanceAdjustment balA = invoiceBalanceAdjustmentRepository
                .findByOriginalInvoiceIdAndInvoiceItemId(origId, itemId).orElseThrow();
        BigDecimal expectedTaxableA = origTaxable.multiply(BigDecimal.valueOf(2)).divide(BigDecimal.valueOf(5), 2, RoundingMode.HALF_UP);
        BigDecimal expectedTaxA = origTax.multiply(BigDecimal.valueOf(2)).divide(BigDecimal.valueOf(5), 2, RoundingMode.HALF_UP);
        assertBalanceInvariants(balA, 3, 2, origTaxable.subtract(expectedTaxableA), expectedTaxableA, origTax.subtract(expectedTaxA), expectedTaxA);

        // 3. Adjustment B reserves remaining 3 units
        Invoice adjB = invoiceDomainService.createAdjustmentInvoice(CreateAdjustmentInvoiceCommand.builder()
                .originalInvoiceId(origId)
                .adjustmentType(AdjustmentType.DECREASE)
                .idempotencyKey(TEST_PREFIX + "INV_B_" + System.nanoTime())
                .itemsToAdjust(List.of(AdjustItemCommand.builder().originalInvoiceItemId(itemId).quantityToAdjust(3).build()))
                .build());

        InvoiceBalanceAdjustment balB = invoiceBalanceAdjustmentRepository
                .findByOriginalInvoiceIdAndInvoiceItemId(origId, itemId).orElseThrow();
        assertBalanceInvariants(balB, 0, 5, BigDecimal.ZERO, origTaxable, BigDecimal.ZERO, origTax);

        // 4. Release A once
        invoiceDomainService.rejectAdjustmentInvoice(adjA.getId(), "Reject A");
        InvoiceBalanceAdjustment balRelA = invoiceBalanceAdjustmentRepository
                .findByOriginalInvoiceIdAndInvoiceItemId(origId, itemId).orElseThrow();
        BigDecimal remainingAfterRelA = origTaxable.subtract(origTaxable.subtract(expectedTaxableA));
        BigDecimal remTaxAfterRelA = origTax.subtract(origTax.subtract(expectedTaxA));
        assertBalanceInvariants(balRelA, 2, 3, expectedTaxableA, origTaxable.subtract(expectedTaxableA), expectedTaxA, origTax.subtract(expectedTaxA));

        // 5. Release A a second time (idempotent, no double release)
        invoiceBalanceValidationService.releaseAdjustmentBalance(adjA.getId());
        InvoiceBalanceAdjustment balRepeatA = invoiceBalanceAdjustmentRepository
                .findByOriginalInvoiceIdAndInvoiceItemId(origId, itemId).orElseThrow();
        assertBalanceInvariants(balRepeatA, 2, 3, expectedTaxableA, origTaxable.subtract(expectedTaxableA), expectedTaxA, origTax.subtract(expectedTaxA));

        // 6. Confirm B's quota and status are unaffected
        Invoice adjBCurrent = invoiceDomainService.getInvoice(adjB.getId());
        assertThat(adjBCurrent.getDocumentStatus()).isEqualTo(DocumentStatus.DRAFT);

        // 7. Release B
        invoiceDomainService.rejectAdjustmentInvoice(adjB.getId(), "Reject B");
        InvoiceBalanceAdjustment balRelB = invoiceBalanceAdjustmentRepository
                .findByOriginalInvoiceIdAndInvoiceItemId(origId, itemId).orElseThrow();

        // 8. Confirm final quota strictly restored to initial state
        assertBalanceInvariants(balRelB, 5, 0, origTaxable, BigDecimal.ZERO, origTax, BigDecimal.ZERO);
    }

    private void assertBalanceInvariants(InvoiceBalanceAdjustment bal, int expectedRemQty, int expectedAdjQty,
                                         BigDecimal expectedRemTaxable, BigDecimal expectedAdjTaxable,
                                         BigDecimal expectedRemTax, BigDecimal expectedAdjTax) {
        assertThat(bal.getRemainingQuantity() + bal.getTotalAdjustedQuantity()).isEqualTo(bal.getOriginalQuantity());
        assertThat(bal.getRemainingTaxableAmount().add(bal.getTotalAdjustedTaxableAmount()))
                .isEqualByComparingTo(bal.getOriginalTaxableAmount());
        assertThat(bal.getRemainingTaxAmount().add(bal.getTotalAdjustedTaxAmount()))
                .isEqualByComparingTo(bal.getOriginalTaxAmount());

        assertThat(bal.getRemainingQuantity()).isEqualTo(expectedRemQty);
        assertThat(bal.getTotalAdjustedQuantity()).isEqualTo(expectedAdjQty);
        assertThat(bal.getRemainingTaxableAmount()).isEqualByComparingTo(expectedRemTaxable);
        assertThat(bal.getTotalAdjustedTaxableAmount()).isEqualByComparingTo(expectedAdjTaxable);
        assertThat(bal.getRemainingTaxAmount()).isEqualByComparingTo(expectedRemTax);
        assertThat(bal.getTotalAdjustedTaxAmount()).isEqualByComparingTo(expectedAdjTax);
    }

    @Test
    @DisplayName("Release exceeding quantity throws InvoiceBalanceExceededException and rolls back transaction")
    void test30_releaseOverQuantity_mustRollbackAndPreserveAggregates() {
        Invoice adj = invoiceDomainService.createAdjustmentInvoice(CreateAdjustmentInvoiceCommand.builder()
                .originalInvoiceId(testOriginalInvoiceId)
                .adjustmentType(AdjustmentType.DECREASE)
                .idempotencyKey(TEST_PREFIX + "INV_OVER_QTY_" + System.nanoTime())
                .itemsToAdjust(List.of(AdjustItemCommand.builder()
                        .originalInvoiceItemId(testOriginalItemId1)
                        .quantityToAdjust(1)
                        .build()))
                .build());

        InvoiceBalanceAdjustment balBefore = invoiceBalanceAdjustmentRepository
                .findByOriginalInvoiceIdAndInvoiceItemId(testOriginalInvoiceId, testOriginalItemId1).orElseThrow();
        int expectedRemQty = balBefore.getRemainingQuantity();
        int expectedAdjQty = balBefore.getTotalAdjustedQuantity();
        BigDecimal expectedRemTaxable = balBefore.getRemainingTaxableAmount();
        BigDecimal expectedAdjTaxable = balBefore.getTotalAdjustedTaxableAmount();
        BigDecimal expectedRemTax = balBefore.getRemainingTaxAmount();
        BigDecimal expectedAdjTax = balBefore.getTotalAdjustedTaxAmount();

        transactionTemplate.execute(status -> {
            InvoiceItem item = invoiceItemRepository.findByInvoiceId(adj.getId()).get(0);
            item.setQuantity(-999);
            invoiceItemRepository.save(item);
            return null;
        });

        assertThatThrownBy(() -> invoiceBalanceValidationService.releaseAdjustmentBalance(adj.getId()))
                .isInstanceOf(InvoiceBalanceExceededException.class)
                .hasMessageContaining("exceeds total adjusted quantity");

        Invoice adjAfter = invoiceDomainService.getInvoice(adj.getId());
        assertThat(adjAfter.getDocumentStatus()).isEqualTo(DocumentStatus.DRAFT);

        InvoiceBalanceAdjustment balAfter = invoiceBalanceAdjustmentRepository
                .findByOriginalInvoiceIdAndInvoiceItemId(testOriginalInvoiceId, testOriginalItemId1).orElseThrow();
        assertThat(balAfter.getRemainingQuantity()).isEqualTo(expectedRemQty);
        assertThat(balAfter.getTotalAdjustedQuantity()).isEqualTo(expectedAdjQty);
        assertThat(balAfter.getRemainingTaxableAmount()).isEqualByComparingTo(expectedRemTaxable);
        assertThat(balAfter.getTotalAdjustedTaxableAmount()).isEqualByComparingTo(expectedAdjTaxable);
        assertThat(balAfter.getRemainingTaxAmount()).isEqualByComparingTo(expectedRemTax);
        assertThat(balAfter.getTotalAdjustedTaxAmount()).isEqualByComparingTo(expectedAdjTax);
    }

    @Test
    @DisplayName("Release exceeding taxable amount throws InvoiceBalanceExceededException and rolls back transaction")
    void test31_releaseOverTaxableAmount_mustRollbackAndPreserveAggregates() {
        Invoice adj = invoiceDomainService.createAdjustmentInvoice(CreateAdjustmentInvoiceCommand.builder()
                .originalInvoiceId(testOriginalInvoiceId)
                .adjustmentType(AdjustmentType.DECREASE)
                .idempotencyKey(TEST_PREFIX + "INV_OVER_TAXABLE_" + System.nanoTime())
                .itemsToAdjust(List.of(AdjustItemCommand.builder()
                        .originalInvoiceItemId(testOriginalItemId1)
                        .quantityToAdjust(1)
                        .build()))
                .build());

        InvoiceBalanceAdjustment balBefore = invoiceBalanceAdjustmentRepository
                .findByOriginalInvoiceIdAndInvoiceItemId(testOriginalInvoiceId, testOriginalItemId1).orElseThrow();
        int expectedRemQty = balBefore.getRemainingQuantity();
        int expectedAdjQty = balBefore.getTotalAdjustedQuantity();
        BigDecimal expectedRemTaxable = balBefore.getRemainingTaxableAmount();
        BigDecimal expectedAdjTaxable = balBefore.getTotalAdjustedTaxableAmount();
        BigDecimal expectedRemTax = balBefore.getRemainingTaxAmount();
        BigDecimal expectedAdjTax = balBefore.getTotalAdjustedTaxAmount();

        transactionTemplate.execute(status -> {
            InvoiceItem item = invoiceItemRepository.findByInvoiceId(adj.getId()).get(0);
            item.setTaxableAmount(new BigDecimal("-99999999.00"));
            invoiceItemRepository.save(item);
            return null;
        });

        assertThatThrownBy(() -> invoiceBalanceValidationService.releaseAdjustmentBalance(adj.getId()))
                .isInstanceOf(InvoiceBalanceExceededException.class)
                .hasMessageContaining("exceeds total adjusted taxable amount");

        Invoice adjAfter = invoiceDomainService.getInvoice(adj.getId());
        assertThat(adjAfter.getDocumentStatus()).isEqualTo(DocumentStatus.DRAFT);

        InvoiceBalanceAdjustment balAfter = invoiceBalanceAdjustmentRepository
                .findByOriginalInvoiceIdAndInvoiceItemId(testOriginalInvoiceId, testOriginalItemId1).orElseThrow();
        assertThat(balAfter.getRemainingQuantity()).isEqualTo(expectedRemQty);
        assertThat(balAfter.getTotalAdjustedQuantity()).isEqualTo(expectedAdjQty);
        assertThat(balAfter.getRemainingTaxableAmount()).isEqualByComparingTo(expectedRemTaxable);
        assertThat(balAfter.getTotalAdjustedTaxableAmount()).isEqualByComparingTo(expectedAdjTaxable);
        assertThat(balAfter.getRemainingTaxAmount()).isEqualByComparingTo(expectedRemTax);
        assertThat(balAfter.getTotalAdjustedTaxAmount()).isEqualByComparingTo(expectedAdjTax);
    }

    @Test
    @DisplayName("Release exceeding tax amount throws InvoiceBalanceExceededException and rolls back transaction")
    void test32_releaseOverTaxAmount_mustRollbackAndPreserveAggregates() {
        Invoice adj = invoiceDomainService.createAdjustmentInvoice(CreateAdjustmentInvoiceCommand.builder()
                .originalInvoiceId(testOriginalInvoiceId)
                .adjustmentType(AdjustmentType.DECREASE)
                .idempotencyKey(TEST_PREFIX + "INV_OVER_TAX_" + System.nanoTime())
                .itemsToAdjust(List.of(AdjustItemCommand.builder()
                        .originalInvoiceItemId(testOriginalItemId1)
                        .quantityToAdjust(1)
                        .build()))
                .build());

        InvoiceBalanceAdjustment balBefore = invoiceBalanceAdjustmentRepository
                .findByOriginalInvoiceIdAndInvoiceItemId(testOriginalInvoiceId, testOriginalItemId1).orElseThrow();
        int expectedRemQty = balBefore.getRemainingQuantity();
        int expectedAdjQty = balBefore.getTotalAdjustedQuantity();
        BigDecimal expectedRemTaxable = balBefore.getRemainingTaxableAmount();
        BigDecimal expectedAdjTaxable = balBefore.getTotalAdjustedTaxableAmount();
        BigDecimal expectedRemTax = balBefore.getRemainingTaxAmount();
        BigDecimal expectedAdjTax = balBefore.getTotalAdjustedTaxAmount();

        transactionTemplate.execute(status -> {
            InvoiceItem item = invoiceItemRepository.findByInvoiceId(adj.getId()).get(0);
            item.setTaxAmount(new BigDecimal("-99999999.00"));
            invoiceItemRepository.save(item);
            return null;
        });

        assertThatThrownBy(() -> invoiceBalanceValidationService.releaseAdjustmentBalance(adj.getId()))
                .isInstanceOf(InvoiceBalanceExceededException.class)
                .hasMessageContaining("exceeds total adjusted tax amount");

        Invoice adjAfter = invoiceDomainService.getInvoice(adj.getId());
        assertThat(adjAfter.getDocumentStatus()).isEqualTo(DocumentStatus.DRAFT);

        InvoiceBalanceAdjustment balAfter = invoiceBalanceAdjustmentRepository
                .findByOriginalInvoiceIdAndInvoiceItemId(testOriginalInvoiceId, testOriginalItemId1).orElseThrow();
        assertThat(balAfter.getRemainingQuantity()).isEqualTo(expectedRemQty);
        assertThat(balAfter.getTotalAdjustedQuantity()).isEqualTo(expectedAdjQty);
        assertThat(balAfter.getRemainingTaxableAmount()).isEqualByComparingTo(expectedRemTaxable);
        assertThat(balAfter.getTotalAdjustedTaxableAmount()).isEqualByComparingTo(expectedAdjTaxable);
        assertThat(balAfter.getRemainingTaxAmount()).isEqualByComparingTo(expectedRemTax);
        assertThat(balAfter.getTotalAdjustedTaxAmount()).isEqualByComparingTo(expectedAdjTax);
    }

    @Test
    @DisplayName("Concurrent createAdjustmentDraftFromReturnReceipt vs rejectAdjustmentInvoice executes without deadlock")
    void test33_concurrentCreateFromReturnReceiptVsRelease_noDeadlock() throws Exception {
        Invoice adj1 = invoiceDomainService.createAdjustmentInvoice(CreateAdjustmentInvoiceCommand.builder()
                .originalInvoiceId(testOriginalInvoiceId)
                .adjustmentType(AdjustmentType.DECREASE)
                .idempotencyKey(TEST_PREFIX + "INV_CONC_REL_" + System.nanoTime())
                .itemsToAdjust(List.of(AdjustItemCommand.builder()
                        .originalInvoiceItemId(testOriginalItemId1)
                        .quantityToAdjust(1)
                        .build()))
                .build());

        OrderReturnReceipt rr = createTestReturnReceipt(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

        executor.submit(() -> {
            try {
                startLatch.await();
                invoiceDomainService.createAdjustmentDraftFromReturnReceipt(rr.getId());
            } catch (Throwable t) {
                errors.add(t);
            } finally {
                doneLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                invoiceDomainService.rejectAdjustmentInvoice(adj1.getId(), "Reject reason");
            } catch (Throwable t) {
                errors.add(t);
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        assertThat(completed).isTrue();
        assertThat(errors).isEmpty();

        Invoice adjFromReceipt = invoiceRepository.findByReturnReceiptId(rr.getId()).orElseThrow();
        assertThat(adjFromReceipt.getDocumentStatus()).isEqualTo(DocumentStatus.DRAFT);

        Invoice adj1After = invoiceDomainService.getInvoice(adj1.getId());
        assertThat(adj1After.getDocumentStatus()).isEqualTo(DocumentStatus.ISSUE_FAILED);
    }
}
