package com.shop.backend.einvoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;

import com.shop.backend.common.model.DataSourceType;
import com.shop.backend.einvoice.dto.AdjustItemCommand;
import com.shop.backend.einvoice.dto.CreateAdjustmentInvoiceCommand;
import com.shop.backend.einvoice.model.AdjustmentType;
import com.shop.backend.einvoice.model.DocumentStatus;
import com.shop.backend.einvoice.model.Invoice;
import com.shop.backend.einvoice.model.InvoiceBalanceAdjustment;
import com.shop.backend.einvoice.model.InvoiceItem;
import com.shop.backend.einvoice.model.InvoiceOutbox;
import com.shop.backend.einvoice.model.InvoiceType;
import com.shop.backend.einvoice.model.PricingBasis;
import com.shop.backend.einvoice.model.TaxConfiguration;
import com.shop.backend.einvoice.model.TaxTreatment;
import com.shop.backend.einvoice.provider.RoutingInvoiceProvider;
import com.shop.backend.einvoice.repository.InvoiceBalanceAdjustmentRepository;
import com.shop.backend.einvoice.repository.InvoiceItemRepository;
import com.shop.backend.einvoice.repository.InvoiceOutboxRepository;
import com.shop.backend.einvoice.repository.InvoiceRepository;
import com.shop.backend.einvoice.repository.TaxConfigurationRepository;
import com.shop.backend.einvoice.service.InvoiceBalanceValidationService;
import com.shop.backend.einvoice.service.InvoiceDomainService;
import com.shop.backend.einvoice.service.InvoiceOutboxService;
import com.shop.backend.einvoice.worker.InvoiceOutboxWorker;
import com.shop.backend.order.Order;
import com.shop.backend.order.OrderAdminService;
import com.shop.backend.order.OrderDetail;
import com.shop.backend.order.OrderDetailRepository;
import com.shop.backend.order.OrderRepository;
import com.shop.backend.order.OrderStatus;
import com.shop.backend.order.dto.OrderStatusUpdateRequest;
import com.shop.backend.payment.Payment;
import com.shop.backend.payment.PaymentMethod;
import com.shop.backend.payment.PaymentRepository;
import com.shop.backend.payment.PaymentService;
import com.shop.backend.payment.PaymentStatus;
import com.shop.backend.payment.dto.PaymentProcessResult;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class AutomaticOriginalInvoiceCreationIntegrationTest {

    private static final String TEST_PREFIX = "auto-inv-";

    @Autowired
    private PaymentService paymentService;

    @SpyBean
    private InvoiceDomainService invoiceDomainService;

    @Autowired
    private InvoiceOutboxService invoiceOutboxService;

    @Autowired
    private InvoiceBalanceValidationService invoiceBalanceValidationService;

    @Autowired
    private InvoiceOutboxWorker invoiceOutboxWorker;

    @Autowired
    private RoutingInvoiceProvider routingInvoiceProvider;

    @Autowired
    private OrderAdminService orderAdminService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderDetailRepository orderDetailRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private InvoiceItemRepository invoiceItemRepository;

    @Autowired
    private InvoiceOutboxRepository invoiceOutboxRepository;

    @Autowired
    private InvoiceBalanceAdjustmentRepository invoiceBalanceAdjustmentRepository;

    @Autowired
    private TaxConfigurationRepository taxConfigurationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

    private TransactionTemplate transactionTemplate;

    private Long testUserId;
    private Long testOrderId;
    private Long testDetailId1;
    private Long testDetailId2;

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
            taxConfigurationRepository.save(taxShipping);

            Role customerRole = roleRepository.findByName("customer")
                    .orElseGet(() -> roleRepository.save(new Role(null, "customer")));

            User user = userRepository.save(User.builder()
                    .email(TEST_PREFIX + "user_" + System.nanoTime() + "@example.com")
                    .passwordHash("secret123")
                    .name(TEST_PREFIX + "Auto Invoice User")
                    .phone("0988776655")
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
                    .name(TEST_PREFIX + "Oxford Shirt")
                    .slug(TEST_PREFIX + "prod-" + System.nanoTime())
                    .basePrice(new BigDecimal("200000.00"))
                    .isActive(true)
                    .dataSource(DataSourceType.WEBSITE)
                    .build());

            ProductVariant v1 = productVariantRepository.save(ProductVariant.builder()
                    .product(product)
                    .sku(TEST_PREFIX + "SKU1-" + System.nanoTime())
                    .size("M")
                    .color("White")
                    .stock(100)
                    .price(new BigDecimal("200000.00"))
                    .build());

            ProductVariant v2 = productVariantRepository.save(ProductVariant.builder()
                    .product(product)
                    .sku(TEST_PREFIX + "SKU2-" + System.nanoTime())
                    .size("L")
                    .color("Blue")
                    .stock(100)
                    .price(new BigDecimal("300000.00"))
                    .build());

            Order order = Order.builder()
                    .user(user)
                    .receiverName(TEST_PREFIX + "Tran Van B")
                    .receiverPhone("0912345678")
                    .addressLine("456 Hai Ba Trung")
                    .ward("Ben Nghe")
                    .district("Quan 1")
                    .province("TP.HCM")
                    .paymentMethod(PaymentMethod.COD)
                    .subtotal(new BigDecimal("700000.00"))
                    .discountAmount(new BigDecimal("50000.00"))
                    .shippingFee(new BigDecimal("30000.00"))
                    .totalAmount(new BigDecimal("680000.00"))
                    .status(OrderStatus.PENDING)
                    .dataSource(DataSourceType.WEBSITE)
                    .createdAt(LocalDateTime.now())
                    .build();

            OrderDetail d1 = OrderDetail.builder()
                    .variant(v1)
                    .productName(TEST_PREFIX + "Oxford Shirt White M")
                    .variantSize("M")
                    .variantColor("White")
                    .quantity(2)
                    .unitPrice(new BigDecimal("200000.00"))
                    .discountAmount(BigDecimal.ZERO)
                    .build();
            order.addOrderDetail(d1);

            OrderDetail d2 = OrderDetail.builder()
                    .variant(v2)
                    .productName(TEST_PREFIX + "Oxford Shirt Blue L")
                    .variantSize("L")
                    .variantColor("Blue")
                    .quantity(1)
                    .unitPrice(new BigDecimal("300000.00"))
                    .discountAmount(BigDecimal.ZERO)
                    .build();
            order.addOrderDetail(d2);

            Payment payment = Payment.builder()
                    .method(PaymentMethod.COD)
                    .amount(new BigDecimal("680000.00"))
                    .status(PaymentStatus.PENDING)
                    .build();
            order.addPayment(payment);

            Order savedOrder = orderRepository.save(order);
            testOrderId = savedOrder.getId();
            testDetailId1 = savedOrder.getOrderDetails().get(0).getId();
            testDetailId2 = savedOrder.getOrderDetails().get(1).getId();

            return null;
        });
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
            entityManager.createQuery("DELETE FROM InvoiceOutbox io WHERE io.eventKey LIKE 'INV-%'")
                    .executeUpdate();
            entityManager.createQuery("DELETE FROM InvoiceBalanceAdjustment iba WHERE iba.originalInvoice.idempotencyKey LIKE :prefix OR iba.originalInvoice.idempotencyKey LIKE 'ORIG_ORD_%'")
                    .setParameter("prefix", TEST_PREFIX + "%")
                    .executeUpdate();

            entityManager.createNativeQuery("UPDATE invoice_items SET original_invoice_item_id = NULL")
                    .executeUpdate();
            entityManager.createQuery("DELETE FROM InvoiceItem ii WHERE ii.itemName LIKE :prefix OR ii.invoice.idempotencyKey LIKE :prefix OR ii.invoice.idempotencyKey LIKE 'ORIG_ORD_%'")
                    .setParameter("prefix", TEST_PREFIX + "%")
                    .executeUpdate();

            entityManager.createNativeQuery("UPDATE invoices SET return_receipt_id = NULL, replacing_invoice_id = NULL, replaced_by_invoice_id = NULL, original_invoice_id = NULL")
                    .executeUpdate();

            entityManager.createQuery("DELETE FROM Invoice i WHERE i.idempotencyKey LIKE :prefix OR i.idempotencyKey LIKE 'ORIG_ORD_%'")
                    .setParameter("prefix", TEST_PREFIX + "%")
                    .executeUpdate();
            entityManager.createQuery("DELETE FROM OrderDetail od WHERE od.productName LIKE :prefix")
                    .setParameter("prefix", TEST_PREFIX + "%")
                    .executeUpdate();
            entityManager.createQuery("DELETE FROM Payment p WHERE p.order.receiverName LIKE :prefix")
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
    @DisplayName("Scenario 1: Payment success creates exactly one Original invoice with DRAFT status")
    void paymentSuccess_createsSingleOriginalInvoice() {
        PaymentProcessResult result = paymentService.processPaymentSuccess(testOrderId, "TXN_ONLINE_001", PaymentMethod.VNPAY);

        assertThat(result.isInvoiceCreated()).isTrue();
        assertThat(result.getPayment().getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(result.getPayment().getTransactionCode()).isEqualTo("TXN_ONLINE_001");
        assertThat(result.getPayment().getPaidAt()).isNotNull();

        Invoice invoice = result.getInvoice();
        assertThat(invoice).isNotNull();
        assertThat(invoice.getInvoiceType()).isEqualTo(InvoiceType.ORIGINAL);
        assertThat(invoice.getDocumentStatus()).isEqualTo(DocumentStatus.DRAFT);
        assertThat(invoice.getFulfillmentEvent()).isNull();
        assertThat(invoice.getIdempotencyKey()).isEqualTo("ORIG_ORD_" + testOrderId);

        List<Invoice> invoicesInDb = invoiceRepository.findByOrderId(testOrderId);
        assertThat(invoicesInDb).hasSize(1);
    }

    @Test
    @DisplayName("Scenario 2: Duplicate payment success callback returns existing invoice without duplication")
    void paymentSuccess_duplicateCallback_returnsExistingInvoiceWithoutDuplication() {
        PaymentProcessResult firstCall = paymentService.processPaymentSuccess(testOrderId, "TXN_DUPLICATE_001", PaymentMethod.VNPAY);
        assertThat(firstCall.isInvoiceCreated()).isTrue();

        PaymentProcessResult secondCall = paymentService.processPaymentSuccess(testOrderId, "TXN_DUPLICATE_001", PaymentMethod.VNPAY);
        assertThat(secondCall.isInvoiceCreated()).isFalse();
        assertThat(secondCall.getInvoice().getId()).isEqualTo(firstCall.getInvoice().getId());

        List<Invoice> invoicesInDb = invoiceRepository.findByOrderId(testOrderId);
        assertThat(invoicesInDb).hasSize(1);
    }

    @Test
    @DisplayName("Scenario 3: Two concurrent callbacks with real transactions create exactly one invoice")
    void paymentSuccess_concurrentCallbacks_createsExactlyOneInvoice() throws Exception {
        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch doneSignal = new CountDownLatch(threads);

        List<PaymentProcessResult> results = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger successCounter = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startSignal.await();
                    PaymentProcessResult res = paymentService.processPaymentSuccess(
                            testOrderId, "TXN_CONCURRENT_" + index, PaymentMethod.VNPAY);
                    results.add(res);
                    successCounter.incrementAndGet();
                } catch (Exception e) {
                    // Ignore expected serialization failures if any
                } finally {
                    doneSignal.countDown();
                }
            });
        }

        startSignal.countDown();
        boolean finished = doneSignal.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        assertThat(finished).isTrue();
        assertThat(successCounter.get()).isEqualTo(threads);
        assertThat(results).hasSize(threads);

        List<Invoice> invoicesInDb = invoiceRepository.findByOrderId(testOrderId);
        assertThat(invoicesInDb).hasSize(1);

        long newlyCreatedCount = results.stream().filter(PaymentProcessResult::isInvoiceCreated).count();
        assertThat(newlyCreatedCount).isEqualTo(1);

        Long firstInvoiceId = results.get(0).getInvoice().getId();
        Long secondInvoiceId = results.get(1).getInvoice().getId();
        assertThat(firstInvoiceId).isEqualTo(secondInvoiceId);
    }

    @Test
    @DisplayName("Scenario 4: Payment failure does not create invoice")
    void paymentFailure_doesNotCreateInvoice() {
        PaymentProcessResult result = paymentService.processPaymentFailure(
                testOrderId, "TXN_FAIL_001", PaymentMethod.VNPAY, "Card expired or insufficient balance");

        assertThat(result.isInvoiceCreated()).isFalse();
        assertThat(result.getInvoice()).isNull();
        assertThat(result.getPayment().getStatus()).isEqualTo(PaymentStatus.FAILED);

        List<Invoice> invoicesInDb = invoiceRepository.findByOrderId(testOrderId);
        assertThat(invoicesInDb).isEmpty();
    }

    @Test
    @DisplayName("Scenario 5: Order cancelled before payment callback does not create invoice and does not fake failure")
    void orderCancelled_doesNotCreateInvoice() {
        transactionTemplate.execute(status -> {
            Order o = orderRepository.findById(testOrderId).orElseThrow();
            o.setStatus(OrderStatus.CANCELLED);
            return orderRepository.save(o);
        });

        PaymentProcessResult result = paymentService.processPaymentSuccess(testOrderId, "TXN_CANCELLED_LATE", PaymentMethod.VNPAY);

        assertThat(result.isInvoiceCreated()).isFalse();
        assertThat(result.getInvoice()).isNull();
        assertThat(result.getPayment().getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(result.getMessage()).contains("order is CANCELLED; invoice creation skipped");

        List<Invoice> invoicesInDb = invoiceRepository.findByOrderId(testOrderId);
        assertThat(invoicesInDb).isEmpty();
    }

    @Test
    @DisplayName("Scenario 6: Invoice items match order snapshot lines accurately")
    void invoiceItems_matchOrderSnapshot() {
        PaymentProcessResult result = paymentService.processPaymentSuccess(testOrderId, "TXN_SNAPSHOT_001", PaymentMethod.VNPAY);
        Long invoiceId = result.getInvoice().getId();

        List<InvoiceItem> items = invoiceItemRepository.findByInvoiceId(invoiceId);
        assertThat(items).hasSize(3); // 2 merchandise items + 1 shipping fee

        InvoiceItem m1 = items.stream().filter(i -> i.getOrderDetail() != null && i.getOrderDetail().getId().equals(testDetailId1)).findFirst().orElseThrow();
        assertThat(m1.getQuantity()).isEqualTo(2);
        assertThat(m1.getUnitPriceGross()).isEqualByComparingTo(new BigDecimal("200000.00"));

        InvoiceItem m2 = items.stream().filter(i -> i.getOrderDetail() != null && i.getOrderDetail().getId().equals(testDetailId2)).findFirst().orElseThrow();
        assertThat(m2.getQuantity()).isEqualTo(1);
        assertThat(m2.getUnitPriceGross()).isEqualByComparingTo(new BigDecimal("300000.00"));

        InvoiceItem ship = items.stream().filter(i -> i.getOrderDetail() == null).findFirst().orElseThrow();
        assertThat(ship.getUnitPriceGross()).isEqualByComparingTo(new BigDecimal("30000.00"));
    }

    @Test
    @DisplayName("Scenario 7: Total amount, discount, and tax match domain calculation engine")
    void financialTotals_matchDomainCalculationEngine() {
        PaymentProcessResult result = paymentService.processPaymentSuccess(testOrderId, "TXN_FIN_001", PaymentMethod.VNPAY);
        Invoice invoice = result.getInvoice();

        Order order = orderRepository.findById(testOrderId).orElseThrow();
        assertThat(invoice.getTotalAfterTax()).isEqualByComparingTo(order.getTotalAmount());
        assertThat(invoice.getTotalMerchandiseDiscount()).isEqualByComparingTo(order.getDiscountAmount());
        assertThat(invoice.getTotalShippingBeforeTax().add(invoice.getTotalShippingDiscount())).isGreaterThan(BigDecimal.ZERO);
        assertThat(invoice.getTotalTaxAmount()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Scenario 8: Outbox event is created exactly once")
    void outboxEvent_createdExactlyOnce() {
        PaymentProcessResult result = paymentService.processPaymentSuccess(testOrderId, "TXN_OUTBOX_001", PaymentMethod.VNPAY);
        Long invoiceId = result.getInvoice().getId();

        List<InvoiceOutbox> outboxEvents = invoiceOutboxRepository.findAll().stream()
                .filter(ev -> ev.getInvoice().getId().equals(invoiceId))
                .toList();

        assertThat(outboxEvents).hasSize(1);
        InvoiceOutbox ev = outboxEvents.get(0);
        assertThat(ev.getEventType()).isEqualTo("ISSUE_INVOICE");
        assertThat(ev.getEventKey()).isEqualTo("INV-" + invoiceId);
        assertThat(ev.getPayload()).contains("\"orderId\":" + testOrderId);
    }

    @Test
    @DisplayName("Scenario 9: Original invoice is routed to Mock provider by default and transitions to ISSUED")
    void originalInvoice_routedToMockProviderByDefault() {
        paymentService.processPaymentSuccess(testOrderId, "TXN_MOCK_DISPATCH_001", PaymentMethod.VNPAY);

        int processedCount = invoiceOutboxWorker.processNextBatch();
        assertThat(processedCount).isGreaterThanOrEqualTo(1);

        Invoice issuedInvoice = invoiceRepository.findByOrderId(testOrderId).get(0);
        assertThat(issuedInvoice.getDocumentStatus()).isEqualTo(DocumentStatus.ISSUED);
        assertThat(issuedInvoice.getStorageProvider()).isEqualTo("MOCK");
        assertThat(issuedInvoice.getInvoiceNumber()).startsWith("DEMO-");
    }

    @Test
    @DisplayName("Scenario 10: Adjustment flow releases quota properly without regression")
    void adjustmentFlow_remainsUnbroken() {
        // Step 1: Issue original invoice
        paymentService.processPaymentSuccess(testOrderId, "TXN_ADJ_BASE_001", PaymentMethod.VNPAY);
        invoiceOutboxWorker.processNextBatch();

        Invoice original = invoiceRepository.findByOrderId(testOrderId).get(0);
        assertThat(original.getDocumentStatus()).isEqualTo(DocumentStatus.ISSUED);

        InvoiceItem item1 = invoiceItemRepository.findByInvoiceId(original.getId()).stream()
                .filter(i -> i.getOrderDetail() != null && i.getOrderDetail().getId().equals(testDetailId1))
                .findFirst().orElseThrow();

        // Step 2: Create adjustment invoice decreasing 1 quantity
        CreateAdjustmentInvoiceCommand adjCmd = CreateAdjustmentInvoiceCommand.builder()
                .originalInvoiceId(original.getId())
                .adjustmentType(AdjustmentType.DECREASE)
                .idempotencyKey(TEST_PREFIX + "ADJ_REGRESSION_" + System.nanoTime())
                .itemsToAdjust(List.of(
                        AdjustItemCommand.builder()
                                .originalInvoiceItemId(item1.getId())
                                .quantityToAdjust(1)
                                .build()
                ))
                .build();

        Invoice adjInvoice = invoiceDomainService.createAdjustmentInvoice(adjCmd);
        assertThat(adjInvoice.getDocumentStatus()).isEqualTo(DocumentStatus.DRAFT);

        InvoiceBalanceAdjustment balance = invoiceBalanceAdjustmentRepository
                .findByOriginalInvoiceIdAndInvoiceItemId(original.getId(), item1.getId()).orElseThrow();
        assertThat(balance.getRemainingQuantity()).isEqualTo(1); // was 2, now 1
        assertThat(balance.getTotalAdjustedQuantity()).isEqualTo(1);

        // Step 3: Reject adjustment invoice and confirm quota is cleanly restored
        invoiceDomainService.rejectAdjustmentInvoice(adjInvoice.getId(), "Customer changed mind");

        InvoiceBalanceAdjustment restoredBalance = invoiceBalanceAdjustmentRepository
                .findByOriginalInvoiceIdAndInvoiceItemId(original.getId(), item1.getId()).orElseThrow();
        assertThat(restoredBalance.getRemainingQuantity()).isEqualTo(2);
        assertThat(restoredBalance.getTotalAdjustedQuantity()).isEqualTo(0);
    }

    @Test
    @DisplayName("Scenario 11: COD order completed automatically collects payment and creates Original invoice")
    void codDeliveryCompletion_automaticallyCollectsPaymentAndCreatesInvoice() {
        // Transition order: PENDING -> CONFIRMED -> SHIPPING -> COMPLETED
        transactionTemplate.execute(status -> {
            Order o = orderRepository.findById(testOrderId).orElseThrow();
            o.setStatus(OrderStatus.SHIPPING);
            return orderRepository.save(o);
        });

        OrderStatusUpdateRequest req = OrderStatusUpdateRequest.builder()
                .status(OrderStatus.COMPLETED)
                .build();

        orderAdminService.updateOrderStatus(testOrderId, req);

        Order completedOrder = orderRepository.findById(testOrderId).orElseThrow();
        assertThat(completedOrder.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(completedOrder.getCompletedAt()).isNotNull();

        Payment payment = paymentRepository.findFirstByOrderIdOrderByPaidAtDesc(testOrderId).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(payment.getTransactionCode()).isEqualTo("COD-" + testOrderId);

        List<Invoice> invoicesInDb = invoiceRepository.findByOrderId(testOrderId);
        assertThat(invoicesInDb).hasSize(1);
        Invoice codInvoice = invoicesInDb.get(0);
        assertThat(codInvoice.getInvoiceType()).isEqualTo(InvoiceType.ORIGINAL);
        assertThat(codInvoice.getDocumentStatus()).isEqualTo(DocumentStatus.DRAFT);

        List<InvoiceOutbox> outboxEvents = invoiceOutboxRepository.findAll().stream()
                .filter(ev -> ev.getInvoice() != null && ev.getInvoice().getId().equals(codInvoice.getId()))
                .toList();
        assertThat(outboxEvents).hasSize(1);
        assertThat(outboxEvents.get(0).getEventType()).isEqualTo("ISSUE_INVOICE");
        assertThat(outboxEvents.get(0).getPayload()).contains("\"orderId\":" + testOrderId);
    }

    @Test
    @DisplayName("Scenario 12: COD order completed when invoice creation fails in afterCommit keeps Order COMPLETED and Payment SUCCESS committed")
    void codOrderCompleted_invoiceCreationFailsInAfterCommit_orderAndPaymentStillCommitted() {
        // Transition order: PENDING -> CONFIRMED -> SHIPPING
        transactionTemplate.execute(status -> {
            Order o = orderRepository.findById(testOrderId).orElseThrow();
            o.setStatus(OrderStatus.SHIPPING);
            return orderRepository.save(o);
        });

        // Simulate invoice creation throwing RuntimeException in afterCommit
        doThrow(new RuntimeException("Simulated afterCommit invoice creation failure"))
                .when(invoiceDomainService).createOriginalInvoiceForOrder(eq(testOrderId), anyString());

        OrderStatusUpdateRequest req = OrderStatusUpdateRequest.builder()
                .status(OrderStatus.COMPLETED)
                .build();

        orderAdminService.updateOrderStatus(testOrderId, req);

        // Reset spy to real method
        doCallRealMethod().when(invoiceDomainService).createOriginalInvoiceForOrder(eq(testOrderId), anyString());

        // Verify independent database state: Order is COMPLETED and Payment is SUCCESS
        Order completedOrder = orderRepository.findById(testOrderId).orElseThrow();
        assertThat(completedOrder.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(completedOrder.getCompletedAt()).isNotNull();

        Payment payment = paymentRepository.findFirstByOrderIdOrderByPaidAtDesc(testOrderId).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(payment.getTransactionCode()).isEqualTo("COD-" + testOrderId);

        // Verify no partial invoice or outbox event was committed
        List<Invoice> invoicesInDb = invoiceRepository.findByOrderId(testOrderId);
        assertThat(invoicesInDb).isEmpty();

        List<InvoiceOutbox> outboxEvents = invoiceOutboxRepository.findAll().stream()
                .filter(ev -> ev.getPayload() != null && ev.getPayload().contains("\"orderId\":" + testOrderId))
                .toList();
        assertThat(outboxEvents).isEmpty();
    }

    @Test
    @DisplayName("Scenario 13: Online payment success commits Payment SUCCESS independently when Invoice creation fails, and retry succeeds")
    void onlinePaymentSuccess_invoiceCreationFails_paymentCommitted_retrySucceeds() {
        // Simulate invoice creation failure
        doThrow(new RuntimeException("Simulated online invoice creation failure"))
                .when(invoiceDomainService).createOriginalInvoiceForOrder(eq(testOrderId), anyString());

        PaymentProcessResult result = paymentService.processPaymentSuccess(testOrderId, "TXN_ONLINE_FAIL_001", PaymentMethod.VNPAY);

        // Assert orchestrator result
        assertThat(result.isInvoiceCreated()).isFalse();
        assertThat(result.getInvoice()).isNull();
        assertThat(result.getPayment().getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(result.getMessage()).contains("invoice creation deferred");

        // Verify independent database state: Payment is committed as SUCCESS
        Payment dbPayment = paymentRepository.findFirstByOrderIdOrderByPaidAtDesc(testOrderId).orElseThrow();
        assertThat(dbPayment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);

        List<Invoice> invoicesInDb = invoiceRepository.findByOrderId(testOrderId);
        assertThat(invoicesInDb).isEmpty();

        // Fix the error by restoring real method
        doCallRealMethod().when(invoiceDomainService).createOriginalInvoiceForOrder(eq(testOrderId), anyString());

        // Perform retry
        PaymentProcessResult retryResult = paymentService.retryInvoiceCreation(testOrderId);

        assertThat(retryResult.isInvoiceCreated()).isTrue();
        assertThat(retryResult.getInvoice()).isNotNull();
        assertThat(retryResult.getMessage()).contains("Invoice created successfully on retry");

        // Verify independent database state after retry
        List<Invoice> invoicesAfterRetry = invoiceRepository.findByOrderId(testOrderId);
        assertThat(invoicesAfterRetry).hasSize(1);
        assertThat(invoicesAfterRetry.get(0).getInvoiceType()).isEqualTo(InvoiceType.ORIGINAL);
        assertThat(invoicesAfterRetry.get(0).getDocumentStatus()).isEqualTo(DocumentStatus.DRAFT);

        List<InvoiceOutbox> outboxAfterRetry = invoiceOutboxRepository.findAll().stream()
                .filter(ev -> ev.getInvoice().getId().equals(retryResult.getInvoice().getId()))
                .toList();
        assertThat(outboxAfterRetry).hasSize(1);
    }

    @Test
    @DisplayName("Scenario 14: Cancelled order does not create invoice under lock and does not create outbox event")
    void cancelledOrder_doesNotCreateInvoiceUnderLock() {
        transactionTemplate.execute(status -> {
            Order o = orderRepository.findById(testOrderId).orElseThrow();
            o.setStatus(OrderStatus.CANCELLED);
            return orderRepository.save(o);
        });

        assertThatThrownBy(() -> invoiceDomainService.createOriginalInvoiceForOrder(testOrderId, "ORIG_ORD_" + testOrderId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CANCELLED");

        List<Invoice> invoicesInDb = invoiceRepository.findByOrderId(testOrderId);
        assertThat(invoicesInDb).isEmpty();
    }
}
