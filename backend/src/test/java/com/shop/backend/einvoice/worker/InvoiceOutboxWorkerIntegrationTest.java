package com.shop.backend.einvoice.worker;

import com.shop.backend.common.model.DataSourceType;
import com.shop.backend.einvoice.dto.CreateFulfillmentEventCommand;
import com.shop.backend.einvoice.dto.CreateFulfillmentItemCommand;
import com.shop.backend.einvoice.dto.InvoiceCalculationCommand;
import com.shop.backend.einvoice.dto.InvoiceLineCalculationCommand;
import com.shop.backend.einvoice.exception.OutboxConcurrencyException;
import com.shop.backend.einvoice.model.AttemptOutcome;
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
import com.shop.backend.einvoice.provider.InvoiceProvider;
import com.shop.backend.einvoice.provider.dto.ProviderInquiryRequest;
import com.shop.backend.einvoice.provider.dto.ProviderInquiryResult;
import com.shop.backend.einvoice.provider.dto.ProviderIssueRequest;
import com.shop.backend.einvoice.provider.dto.ProviderIssueResult;
import com.shop.backend.einvoice.provider.exception.InvoiceProviderFatalException;
import com.shop.backend.einvoice.provider.exception.InvoiceProviderRetryableException;
import com.shop.backend.einvoice.provider.exception.InvoiceProviderTimeoutException;
import com.shop.backend.einvoice.provider.misa.MisaPayloadMapper;
import com.shop.backend.einvoice.repository.InvoiceAttemptRepository;
import com.shop.backend.einvoice.repository.InvoiceOutboxRepository;
import com.shop.backend.einvoice.repository.InvoiceRepository;
import com.shop.backend.einvoice.repository.TaxConfigurationRepository;
import com.shop.backend.einvoice.service.FulfillmentService;
import com.shop.backend.einvoice.service.InvoiceDomainService;
import com.shop.backend.einvoice.service.InvoiceOutboxService;
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
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class InvoiceOutboxWorkerIntegrationTest {

    private static final String TEST_PREFIX = "einv-wrk-";

    @Autowired
    private InvoiceOutboxWorker invoiceOutboxWorker;

    @Autowired
    private InvoiceReconciliationWorker invoiceReconciliationWorker;

    @Autowired
    private InvoiceLeaseReaper invoiceLeaseReaper;

    @Autowired
    private InvoiceOutboxProperties outboxProperties;

    @Autowired
    private InvoiceOutboxService invoiceOutboxService;

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
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

    @MockBean
    private InvoiceProvider invoiceProvider;

    private TransactionTemplate transactionTemplate;
    private TransactionTemplate requiresNewTransactionTemplate;

    private Long testUserId;
    private Long testVariantId;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        requiresNewTransactionTemplate = new TransactionTemplate(transactionManager);
        requiresNewTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        reset(invoiceProvider);
        when(invoiceProvider.getProviderName()).thenReturn("MISA");

        invoiceOutboxWorker.resetShutdownState();
        invoiceReconciliationWorker.resetShutdownState();
        invoiceLeaseReaper.resetShutdownState();

        outboxProperties.setBatchSize(10);
        outboxProperties.setLeaseDurationSeconds(120L);
        outboxProperties.setMaxAttempts(5);
        outboxProperties.setMaxReconcileAttempts(10);
        outboxProperties.setInitialBackoffSeconds(10L);
        outboxProperties.setBackoffMultiplier(2.0);
        outboxProperties.setMaxBackoffSeconds(900L);

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
                    .name(TEST_PREFIX + "Worker User")
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
                    .name(TEST_PREFIX + "Shirt")
                    .slug(TEST_PREFIX + "prod-" + System.nanoTime())
                    .basePrice(new BigDecimal("108000.00"))
                    .isActive(true)
                    .dataSource(DataSourceType.WEBSITE)
                    .build());

            ProductVariant variant = productVariantRepository.save(ProductVariant.builder()
                    .product(product)
                    .sku(TEST_PREFIX + "SKU-" + System.nanoTime())
                    .size("M")
                    .color("White")
                    .stock(100)
                    .price(new BigDecimal("108000.00"))
                    .build());
            testVariantId = variant.getId();

            return null;
        });
    }

    @AfterEach
    void tearDown() {
        invoiceOutboxWorker.resetShutdownState();
        invoiceReconciliationWorker.resetShutdownState();
        invoiceLeaseReaper.resetShutdownState();
        cleanTestData();
    }

    private Invoice createTestInvoiceWithOutbox(String idempotencySuffix) {
        final Long[] ids = new Long[2];
        transactionTemplate.execute(status -> {
            User user = userRepository.findById(testUserId).orElseThrow();
            ProductVariant variant = productVariantRepository.findById(testVariantId).orElseThrow();

            Order order = Order.builder()
                    .user(user)
                    .receiverName(TEST_PREFIX + "Receiver")
                    .receiverPhone("0988776655")
                    .province("TP.HCM")
                    .paymentMethod(PaymentMethod.COD)
                    .subtotal(new BigDecimal("108000.00"))
                    .discountAmount(BigDecimal.ZERO)
                    .shippingFee(BigDecimal.ZERO)
                    .totalAmount(new BigDecimal("108000.00"))
                    .status(OrderStatus.SHIPPING)
                    .dataSource(DataSourceType.WEBSITE)
                    .createdAt(LocalDateTime.now().minusDays(1))
                    .build();

            OrderDetail detail = OrderDetail.builder()
                    .variant(variant)
                    .productName(TEST_PREFIX + "Shirt M")
                    .variantSize("M")
                    .variantColor("White")
                    .quantity(1)
                    .unitPrice(new BigDecimal("108000.00"))
                    .discountAmount(BigDecimal.ZERO)
                    .build();
            order.addOrderDetail(detail);

            Order savedOrder = orderRepository.save(order);
            ids[0] = savedOrder.getId();
            ids[1] = savedOrder.getOrderDetails().get(0).getId();
            return null;
        });

        CreateFulfillmentEventCommand fCmd = CreateFulfillmentEventCommand.builder()
                .orderId(ids[0])
                .fulfillmentCode(TEST_PREFIX + "FUL_" + idempotencySuffix + "_" + System.nanoTime())
                .carrierName("GHN")
                .trackingNumber(TEST_PREFIX + "TRK_" + idempotencySuffix + "_" + System.nanoTime())
                .deliveredAt(LocalDateTime.now())
                .proofSource(ProofSource.CARRIER_WEBHOOK)
                .proofReference("PROOF_" + idempotencySuffix)
                .items(List.of(
                        CreateFulfillmentItemCommand.builder()
                                .orderDetailId(ids[1])
                                .deliveredQuantity(1)
                                .unitPriceSnapshot(new BigDecimal("108000.00"))
                                .discountAllocated(BigDecimal.ZERO)
                                .build()
                ))
                .build();
        FulfillmentEvent fe = fulfillmentService.recordFulfillment(fCmd);

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
                .orderId(ids[0])
                .fulfillmentEventId(fe.getId())
                .taxPoint(LocalDateTime.now())
                .pricingBasis(PricingBasis.INCLUSIVE)
                .merchandiseVoucherAmount(BigDecimal.ZERO)
                .shippingFee(BigDecimal.ZERO)
                .shippingPromotionAmount(BigDecimal.ZERO)
                .shippingTaxCategory("shipping")
                .items(List.of(
                        InvoiceLineCalculationCommand.builder()
                                .orderDetailId(ids[1])
                                .itemName(TEST_PREFIX + "Shirt M")
                                .unit("cai")
                                .quantity(1)
                                .unitPrice(new BigDecimal("108000.00"))
                                .taxCategory("clothing")
                                .isDiscountable(true)
                                .build()
                ))
                .build();

        return invoiceDomainService.createOriginalInvoice(
                fe.getId(),
                TEST_PREFIX + "IDEM_" + idempotencySuffix + "_" + System.nanoTime(),
                buyer,
                seller,
                calcCmd
        );
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
            entityManager.createQuery("DELETE FROM InvoiceItem ii WHERE ii.originalInvoiceItem IS NOT NULL AND ii.itemName LIKE :prefix")
                    .setParameter("prefix", TEST_PREFIX + "%")
                    .executeUpdate();
            entityManager.createQuery("DELETE FROM InvoiceItem ii WHERE ii.itemName LIKE :prefix")
                    .setParameter("prefix", TEST_PREFIX + "%")
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
    @DisplayName("Worker processing - claims and issues single invoice outside DB transaction, saves provider metadata and attempt")
    void processNextBatch_singleRecordSuccess_outsideTransactionBoundary() {
        Invoice invoice = createTestInvoiceWithOutbox("SINGLE_OK");
        String expectedRefId = MisaPayloadMapper.toDeterministicRefId(invoice.getIdempotencyKey());

        AtomicBoolean txActiveDuringHttpCall = new AtomicBoolean(true);
        AtomicBoolean leaseCommittedInDbDuringHttpCall = new AtomicBoolean(false);

        when(invoiceProvider.issueInvoice(any(ProviderIssueRequest.class))).thenAnswer(invocation -> {
            ProviderIssueRequest req = invocation.getArgument(0);
            assertThat(req.getRefId()).isEqualTo(expectedRefId);

            // Prove no Spring transaction is open during the HTTP call
            txActiveDuringHttpCall.set(TransactionSynchronizationManager.isActualTransactionActive());

            // Prove in an independent transaction that the lease claim and ISSUING transition already committed
            requiresNewTransactionTemplate.execute(txStatus -> {
                List<InvoiceOutbox> events = invoiceOutboxRepository.findByInvoiceId(invoice.getId());
                Invoice dbInv = invoiceRepository.findById(invoice.getId()).orElseThrow();
                if (!events.isEmpty()
                        && events.get(0).getStatus() == OutboxStatus.PROCESSING
                        && events.get(0).getLeaseToken() != null
                        && dbInv.getDocumentStatus() == DocumentStatus.ISSUING) {
                    leaseCommittedInDbDuringHttpCall.set(true);
                }
                return null;
            });

            return ProviderIssueResult.builder()
                    .success(true)
                    .transactionId("MISA_TX_001")
                    .invoiceNo("0000101")
                    .invoiceSeries("1C26TYY")
                    .templateCode("1")
                    .issuedAt(LocalDateTime.now())
                    .taxAuthorityCode("CQT_CODE_001")
                    .attemptOutcome(AttemptOutcome.SUCCESS)
                    .rawResponse("{\"Success\":true}")
                    .retryable(false)
                    .build();
        });

        int processed = invoiceOutboxWorker.processNextBatch("worker-single");
        assertThat(processed).isEqualTo(1);
        assertThat(txActiveDuringHttpCall.get()).isFalse();
        assertThat(leaseCommittedInDbDuringHttpCall.get()).isTrue();

        Invoice updatedInvoice = invoiceRepository.findById(invoice.getId()).orElseThrow();
        assertThat(updatedInvoice.getDocumentStatus()).isEqualTo(DocumentStatus.ISSUED);
        assertThat(updatedInvoice.getProviderTransactionId()).isEqualTo("MISA_TX_001");
        assertThat(updatedInvoice.getInvoiceNumber()).isEqualTo("0000101");
        assertThat(updatedInvoice.getInvoiceSeries()).isEqualTo("1C26TYY");
        assertThat(updatedInvoice.getTaxAuthorityCode()).isEqualTo("CQT_CODE_001");
        assertThat(updatedInvoice.getOfficiallyIssuedAt()).isNotNull();

        List<InvoiceOutbox> outboxEvents = invoiceOutboxRepository.findByInvoiceId(invoice.getId());
        assertThat(outboxEvents).hasSize(1);
        assertThat(outboxEvents.get(0).getStatus()).isEqualTo(OutboxStatus.PROCESSED);
        assertThat(outboxEvents.get(0).getProcessedAt()).isNotNull();
        assertThat(outboxEvents.get(0).getLockedUntil()).isNull();
        assertThat(outboxEvents.get(0).getLeaseToken()).isNull();

        List<InvoiceAttempt> attempts = invoiceAttemptRepository.findByInvoiceIdOrderByAttemptedAtDesc(invoice.getId());
        assertThat(attempts).hasSize(1);
        assertThat(attempts.get(0).getOutcome()).isEqualTo(AttemptOutcome.SUCCESS);
        assertThat(attempts.get(0).getSanitizedRequestPayload()).contains(expectedRefId);
    }

    @Test
    @DisplayName("Worker processing - respects batchSize and skips future availableAt records")
    void processNextBatch_batchSizeAndFutureRecordSkipped() {
        Invoice due1 = createTestInvoiceWithOutbox("BATCH_1");
        Invoice due2 = createTestInvoiceWithOutbox("BATCH_2");
        Invoice futureInv = createTestInvoiceWithOutbox("FUTURE_3");

        // Schedule futureInv 10 minutes in the future
        transactionTemplate.execute(status -> {
            InvoiceOutbox futureOutbox = invoiceOutboxRepository.findByInvoiceId(futureInv.getId()).get(0);
            futureOutbox.setAvailableAt(LocalDateTime.now().plusMinutes(10));
            return invoiceOutboxRepository.save(futureOutbox);
        });

        outboxProperties.setBatchSize(2);

        when(invoiceProvider.issueInvoice(any(ProviderIssueRequest.class))).thenAnswer(invocation -> {
            ProviderIssueRequest req = invocation.getArgument(0);
            return ProviderIssueResult.builder()
                    .success(true)
                    .transactionId("TX_" + req.getInvoiceId())
                    .invoiceNo("0000" + req.getInvoiceId())
                    .invoiceSeries("1C26TYY")
                    .issuedAt(LocalDateTime.now())
                    .taxAuthorityCode("CQT_" + req.getInvoiceId())
                    .attemptOutcome(AttemptOutcome.SUCCESS)
                    .retryable(false)
                    .build();
        });

        int processed = invoiceOutboxWorker.processNextBatch("worker-batch");
        assertThat(processed).isEqualTo(2);

        assertThat(invoiceRepository.findById(due1.getId()).orElseThrow().getDocumentStatus()).isEqualTo(DocumentStatus.ISSUED);
        assertThat(invoiceRepository.findById(due2.getId()).orElseThrow().getDocumentStatus()).isEqualTo(DocumentStatus.ISSUED);

        // Future record must remain untouched in DRAFT and PENDING outbox
        assertThat(invoiceRepository.findById(futureInv.getId()).orElseThrow().getDocumentStatus()).isEqualTo(DocumentStatus.DRAFT);
        InvoiceOutbox futureOutboxReloaded = invoiceOutboxRepository.findByInvoiceId(futureInv.getId()).get(0);
        assertThat(futureOutboxReloaded.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(futureOutboxReloaded.getAttemptCount()).isZero();
    }

    @Test
    @DisplayName("Worker processing - retryable failure schedules backoff preserving RefID, non-retryable or maxAttempts moves to DEAD_LETTER")
    void processNextBatch_retryableBackoffAndNonRetryableTerminalFailure() {
        Invoice retryableInv = createTestInvoiceWithOutbox("RETRY_INV");
        Invoice fatalInv = createTestInvoiceWithOutbox("FATAL_INV");
        String expectedRetryRefId = MisaPayloadMapper.toDeterministicRefId(retryableInv.getIdempotencyKey());

        List<String> observedRefIds = new ArrayList<>();

        when(invoiceProvider.issueInvoice(any(ProviderIssueRequest.class))).thenAnswer(invocation -> {
            ProviderIssueRequest req = invocation.getArgument(0);
            if (req.getInvoiceId().equals(retryableInv.getId())) {
                observedRefIds.add(req.getRefId());
                if (observedRefIds.size() == 1) {
                    throw new InvoiceProviderRetryableException("MISA", "HTTP_503", "Service temporarily unavailable", "{\"Error\":\"503\"}");
                }
                return ProviderIssueResult.builder()
                        .success(true)
                        .transactionId("TX_RETRY_OK")
                        .invoiceNo("0000202")
                        .invoiceSeries("1C26TYY")
                        .issuedAt(LocalDateTime.now())
                        .taxAuthorityCode("CQT_202")
                        .attemptOutcome(AttemptOutcome.SUCCESS)
                        .retryable(false)
                        .build();
            } else {
                throw new InvoiceProviderFatalException("MISA", "INVALID_TAX_CODE", "Invalid buyer tax code", "{\"ErrorCode\":\"InvalidTaxCode\"}");
            }
        });

        // First batch run: retryableInv fails with retryable=true, fatalInv fails with retryable=false
        int processed = invoiceOutboxWorker.processNextBatch("worker-retry");
        assertThat(processed).isEqualTo(2);

        // Verify retryableInv is scheduled in PENDING with future availableAt and invoice is PENDING_ISSUE
        InvoiceOutbox retryOutbox = invoiceOutboxRepository.findByInvoiceId(retryableInv.getId()).get(0);
        assertThat(retryOutbox.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(retryOutbox.getAttemptCount()).isEqualTo(1);
        assertThat(retryOutbox.getAvailableAt()).isAfter(LocalDateTime.now());
        assertThat(invoiceRepository.findById(retryableInv.getId()).orElseThrow().getDocumentStatus())
                .isEqualTo(DocumentStatus.PENDING_ISSUE);

        // Verify fatalInv moved immediately to DEAD_LETTER and ISSUE_FAILED
        InvoiceOutbox fatalOutbox = invoiceOutboxRepository.findByInvoiceId(fatalInv.getId()).get(0);
        assertThat(fatalOutbox.getStatus()).isEqualTo(OutboxStatus.DEAD_LETTER);
        assertThat(fatalOutbox.getAttemptCount()).isEqualTo(1);
        assertThat(invoiceRepository.findById(fatalInv.getId()).orElseThrow().getDocumentStatus())
                .isEqualTo(DocumentStatus.ISSUE_FAILED);

        // Fast-forward retryOutbox availableAt to now so the second attempt runs
        transactionTemplate.execute(status -> {
            InvoiceOutbox ev = invoiceOutboxRepository.findById(retryOutbox.getId()).orElseThrow();
            ev.setAvailableAt(LocalDateTime.now().minusSeconds(2));
            return invoiceOutboxRepository.save(ev);
        });

        // Second batch run: fatalInv must NOT be picked up; retryableInv succeeds with the exact same RefID
        int secondProcessed = invoiceOutboxWorker.processNextBatch("worker-retry");
        assertThat(secondProcessed).isEqualTo(1);

        assertThat(observedRefIds).containsExactly(expectedRetryRefId, expectedRetryRefId);
        assertThat(invoiceRepository.findById(retryableInv.getId()).orElseThrow().getDocumentStatus())
                .isEqualTo(DocumentStatus.ISSUED);
        assertThat(invoiceOutboxRepository.findById(retryOutbox.getId()).orElseThrow().getStatus())
                .isEqualTo(OutboxStatus.PROCESSED);
        assertThat(invoiceAttemptRepository.findByInvoiceIdOrderByAttemptedAtDesc(retryableInv.getId())).hasSize(2);
    }

    @Test
    @DisplayName("Concurrency - two workers polling concurrently never claim or issue the same invoice twice")
    void multiWorkerConcurrency_skipLockedPreventsDuplicateClaimAndPublish() throws Exception {
        int invoiceCount = 6;
        List<Long> invoiceIds = new ArrayList<>();
        for (int i = 0; i < invoiceCount; i++) {
            invoiceIds.add(createTestInvoiceWithOutbox("CONC_" + i).getId());
        }

        Set<Long> dispatchedInvoiceIds = ConcurrentHashMap.newKeySet();
        AtomicInteger totalProviderCalls = new AtomicInteger(0);

        when(invoiceProvider.issueInvoice(any(ProviderIssueRequest.class))).thenAnswer(invocation -> {
            ProviderIssueRequest req = invocation.getArgument(0);
            totalProviderCalls.incrementAndGet();
            dispatchedInvoiceIds.add(req.getInvoiceId());
            return ProviderIssueResult.builder()
                    .success(true)
                    .transactionId("TX_CONC_" + req.getInvoiceId())
                    .invoiceNo("0000" + req.getInvoiceId())
                    .invoiceSeries("1C26TYY")
                    .issuedAt(LocalDateTime.now())
                    .taxAuthorityCode("CQT_" + req.getInvoiceId())
                    .attemptOutcome(AttemptOutcome.SUCCESS)
                    .retryable(false)
                    .build();
        });

        outboxProperties.setBatchSize(invoiceCount);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        Future<Integer> worker1Future = executor.submit(() -> {
            startLatch.await();
            return invoiceOutboxWorker.processNextBatch("worker-conc-1");
        });
        Future<Integer> worker2Future = executor.submit(() -> {
            startLatch.await();
            return invoiceOutboxWorker.processNextBatch("worker-conc-2");
        });

        startLatch.countDown();
        int w1Count = worker1Future.get(15, TimeUnit.SECONDS);
        int w2Count = worker2Future.get(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(w1Count + w2Count).isEqualTo(invoiceCount);
        assertThat(totalProviderCalls.get()).isEqualTo(invoiceCount);
        assertThat(dispatchedInvoiceIds).hasSize(invoiceCount);

        for (Long id : invoiceIds) {
            assertThat(invoiceRepository.findById(id).orElseThrow().getDocumentStatus()).isEqualTo(DocumentStatus.ISSUED);
            assertThat(invoiceOutboxRepository.findByInvoiceId(id).get(0).getStatus()).isEqualTo(OutboxStatus.PROCESSED);
        }
    }

    @Test
    @DisplayName("Concurrency & Fencing - Worker A lease expires during slow HTTP call, Reaper + Worker B reconcile to ISSUED, Worker A late return is rejected")
    void concurrency_staleWorkerCannotFinalizeAfterLeaseExpiredAndReconciled() throws Exception {
        Invoice invoice = createTestInvoiceWithOutbox("STALE_FENCE");
        String expectedRefId = MisaPayloadMapper.toDeterministicRefId(invoice.getIdempotencyKey());

        CountDownLatch providerEnteredLatch = new CountDownLatch(1);
        CountDownLatch allowWorkerAReturnLatch = new CountDownLatch(1);

        when(invoiceProvider.issueInvoice(any(ProviderIssueRequest.class))).thenAnswer(invocation -> {
            providerEnteredLatch.countDown();
            allowWorkerAReturnLatch.await(15, TimeUnit.SECONDS);
            // Worker A returns late with a failure that MUST NOT overwrite Worker B's reconciled ISSUED state
            return ProviderIssueResult.builder()
                    .success(false)
                    .attemptOutcome(AttemptOutcome.FAILED)
                    .errorCode("LATE_WORKER_A_FAILURE")
                    .errorMessage("Stale worker A failure")
                    .retryable(false)
                    .build();
        });

        when(invoiceProvider.inquiryInvoiceStatus(any(ProviderInquiryRequest.class))).thenReturn(
                ProviderInquiryResult.builder()
                        .found(true)
                        .refId(expectedRefId)
                        .transactionId("TX_RECONCILED_BY_B")
                        .invoiceNo("0000999")
                        .invoiceSeries("1C26TYY")
                        .templateCode("1")
                        .taxAuthorityCode("CQT_999")
                        .invoiceDate(LocalDateTime.now())
                        .attemptOutcome(AttemptOutcome.SUCCESS)
                        .isIssued(true)
                        .isRejected(false)
                        .build()
        );

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Integer> workerAFuture = executor.submit(() -> invoiceOutboxWorker.processNextBatch("worker-A-slow"));

        // Wait until Worker A has committed TX 1 (claim) and TX 2 (ISSUING) and is inside the HTTP call
        assertThat(providerEnteredLatch.await(10, TimeUnit.SECONDS)).isTrue();

        // Force Worker A's lease to expire in the database
        requiresNewTransactionTemplate.execute(status -> {
            InvoiceOutbox activeEvent = invoiceOutboxRepository.findByInvoiceId(invoice.getId()).get(0);
            activeEvent.setLockedUntil(LocalDateTime.now().minusMinutes(5));
            return invoiceOutboxRepository.save(activeEvent);
        });

        // Lease Reaper runs: detects expired lease on ISSUING invoice, moves to ISSUANCE_UNKNOWN and spawns RECONCILE_STATUS
        int reaped = invoiceLeaseReaper.reapExpiredLeases();
        assertThat(reaped).isEqualTo(1);
        assertThat(invoiceRepository.findById(invoice.getId()).orElseThrow().getDocumentStatus())
                .isEqualTo(DocumentStatus.ISSUANCE_UNKNOWN);

        // Worker B runs Reconciliation Worker and converges the invoice to ISSUED
        int reconciled = invoiceReconciliationWorker.processReconciliationBatch("worker-B-reconcile");
        assertThat(reconciled).isEqualTo(1);
        assertThat(invoiceRepository.findById(invoice.getId()).orElseThrow().getDocumentStatus())
                .isEqualTo(DocumentStatus.ISSUED);

        // Now release Worker A to try finalizing its stale lease
        allowWorkerAReturnLatch.countDown();
        int workerAProcessed = workerAFuture.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Worker A must be rejected by lease fencing (returns 0 processed)
        assertThat(workerAProcessed).isZero();

        // Verify Worker B's reconciled ISSUED state is 100% preserved
        Invoice finalInvoice = invoiceRepository.findById(invoice.getId()).orElseThrow();
        assertThat(finalInvoice.getDocumentStatus()).isEqualTo(DocumentStatus.ISSUED);
        assertThat(finalInvoice.getProviderTransactionId()).isEqualTo("TX_RECONCILED_BY_B");
        assertThat(finalInvoice.getInvoiceNumber()).isEqualTo("0000999");
    }

    @Test
    @DisplayName("Lease Reaper - crash right after claim (before provider dispatch) reclaims ISSUE_INVOICE to PENDING so new worker can issue")
    void leaseReaper_crashBeforeProviderCall_reclaimsToPendingAndNewWorkerProcesses() {
        Invoice invoice = createTestInvoiceWithOutbox("CRASH_PRE_CALL");

        // Worker A claims the event (TX 1), then crashes before prepareIssueDispatch (so invoice is still DRAFT)
        List<InvoiceOutbox> claimed = invoiceOutboxService.claimEvents("worker-A-crashed", Duration.ofMinutes(2), 1);
        assertThat(claimed).hasSize(1);
        InvoiceOutbox claimedEvent = claimed.get(0);
        String staleToken = claimedEvent.getLeaseToken();

        // Expire the lease
        transactionTemplate.execute(status -> {
            InvoiceOutbox ev = invoiceOutboxRepository.findById(claimedEvent.getId()).orElseThrow();
            ev.setLockedUntil(LocalDateTime.now().minusMinutes(5));
            return invoiceOutboxRepository.save(ev);
        });

        // Reaper runs: sees invoice is still DRAFT (never called MISA) -> reclaims ISSUE_INVOICE back to PENDING
        int reaped = invoiceLeaseReaper.reapExpiredLeases();
        assertThat(reaped).isEqualTo(1);

        InvoiceOutbox reclaimedEvent = invoiceOutboxRepository.findById(claimedEvent.getId()).orElseThrow();
        assertThat(reclaimedEvent.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(reclaimedEvent.getLeaseToken()).isNull();
        assertThat(reclaimedEvent.getLockedUntil()).isNull();

        // Stale Worker A cannot prepare dispatch with its old token
        assertThatThrownBy(() -> invoiceOutboxService.prepareIssueDispatch(claimedEvent.getId(), staleToken))
                .isInstanceOf(OutboxConcurrencyException.class);

        // Worker B now claims and processes the reclaimed event to ISSUED
        when(invoiceProvider.issueInvoice(any(ProviderIssueRequest.class))).thenReturn(
                ProviderIssueResult.builder()
                        .success(true)
                        .transactionId("TX_WORKER_B")
                        .invoiceNo("0000777")
                        .invoiceSeries("1C26TYY")
                        .issuedAt(LocalDateTime.now())
                        .taxAuthorityCode("CQT_777")
                        .attemptOutcome(AttemptOutcome.SUCCESS)
                        .retryable(false)
                        .build()
        );

        int processedByB = invoiceOutboxWorker.processNextBatch("worker-B");
        assertThat(processedByB).isEqualTo(1);
        assertThat(invoiceRepository.findById(invoice.getId()).orElseThrow().getDocumentStatus())
                .isEqualTo(DocumentStatus.ISSUED);
    }

    @Test
    @DisplayName("Unknown outcome & Reconciliation - publish timeout transitions to ISSUANCE_UNKNOWN, never re-publishes, and reconciles via inquiry")
    void unknownOutcome_publishTimeout_neverRepublishes_andReconcilesToIssued() {
        Invoice invoice = createTestInvoiceWithOutbox("TIMEOUT_RECON");
        String expectedRefId = MisaPayloadMapper.toDeterministicRefId(invoice.getIdempotencyKey());

        // 1. Publish times out (request may have reached MISA)
        when(invoiceProvider.issueInvoice(any(ProviderIssueRequest.class)))
                .thenThrow(new InvoiceProviderTimeoutException("MISA", "Read timed out after 25000ms", null));

        int processed = invoiceOutboxWorker.processNextBatch("worker-timeout");
        assertThat(processed).isEqualTo(1);

        // Invoice must be in ISSUANCE_UNKNOWN, and RECONCILE_STATUS event must be queued
        Invoice unknownInvoice = invoiceRepository.findById(invoice.getId()).orElseThrow();
        assertThat(unknownInvoice.getDocumentStatus()).isEqualTo(DocumentStatus.ISSUANCE_UNKNOWN);

        List<InvoiceOutbox> reconcileEvents = invoiceOutboxRepository.findByInvoiceIdAndEventType(invoice.getId(), "RECONCILE_STATUS");
        assertThat(reconcileEvents).hasSize(1);
        assertThat(reconcileEvents.get(0).getStatus()).isEqualTo(OutboxStatus.PENDING);

        // 2. Running InvoiceOutboxWorker again MUST NOT call issueInvoice again!
        reset(invoiceProvider);
        when(invoiceProvider.getProviderName()).thenReturn("MISA");
        int secondPublishPoll = invoiceOutboxWorker.processNextBatch("worker-timeout");
        assertThat(secondPublishPoll).isZero();
        verify(invoiceProvider, never()).issueInvoice(any());

        // 3. First reconciliation inquiry times out -> must stay ISSUANCE_UNKNOWN and reschedule RECONCILE_STATUS
        when(invoiceProvider.inquiryInvoiceStatus(any(ProviderInquiryRequest.class)))
                .thenThrow(new InvoiceProviderTimeoutException("MISA", "Inquiry timed out", null));

        int firstReconcile = invoiceReconciliationWorker.processReconciliationBatch("reconcile-worker");
        assertThat(firstReconcile).isEqualTo(1);
        assertThat(invoiceRepository.findById(invoice.getId()).orElseThrow().getDocumentStatus())
                .isEqualTo(DocumentStatus.ISSUANCE_UNKNOWN);

        InvoiceOutbox recAfterTimeout = invoiceOutboxRepository.findById(reconcileEvents.get(0).getId()).orElseThrow();
        assertThat(recAfterTimeout.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(recAfterTimeout.getAvailableAt()).isAfter(LocalDateTime.now());

        // Fast-forward availableAt for next reconciliation check
        transactionTemplate.execute(status -> {
            InvoiceOutbox ev = invoiceOutboxRepository.findById(recAfterTimeout.getId()).orElseThrow();
            ev.setAvailableAt(LocalDateTime.now().minusSeconds(2));
            return invoiceOutboxRepository.save(ev);
        });

        // 4. Second reconciliation inquiry returns found=false (not yet indexed on MISA) -> stays ISSUANCE_UNKNOWN
        reset(invoiceProvider);
        when(invoiceProvider.getProviderName()).thenReturn("MISA");
        when(invoiceProvider.inquiryInvoiceStatus(any(ProviderInquiryRequest.class))).thenReturn(
                ProviderInquiryResult.builder()
                        .found(false)
                        .refId(expectedRefId)
                        .attemptOutcome(AttemptOutcome.SUCCESS)
                        .isIssued(false)
                        .isRejected(false)
                        .build()
        );

        int secondReconcile = invoiceReconciliationWorker.processReconciliationBatch("reconcile-worker");
        assertThat(secondReconcile).isEqualTo(1);
        assertThat(invoiceRepository.findById(invoice.getId()).orElseThrow().getDocumentStatus())
                .isEqualTo(DocumentStatus.ISSUANCE_UNKNOWN);

        // Fast-forward availableAt for third reconciliation check
        transactionTemplate.execute(status -> {
            InvoiceOutbox ev = invoiceOutboxRepository.findById(recAfterTimeout.getId()).orElseThrow();
            ev.setAvailableAt(LocalDateTime.now().minusSeconds(2));
            return invoiceOutboxRepository.save(ev);
        });

        // 5. Third reconciliation inquiry confirms invoice was ISSUED on MISA with matching RefID
        reset(invoiceProvider);
        when(invoiceProvider.getProviderName()).thenReturn("MISA");
        when(invoiceProvider.inquiryInvoiceStatus(any(ProviderInquiryRequest.class))).thenAnswer(invocation -> {
            ProviderInquiryRequest req = invocation.getArgument(0);
            assertThat(req.getRefId()).isEqualTo(expectedRefId);
            return ProviderInquiryResult.builder()
                    .found(true)
                    .refId(expectedRefId)
                    .transactionId("TX_MISA_CONFIRMED")
                    .invoiceNo("0000555")
                    .invoiceSeries("1C26TYY")
                    .templateCode("1")
                    .taxAuthorityCode("CQT_555")
                    .invoiceDate(LocalDateTime.now())
                    .attemptOutcome(AttemptOutcome.SUCCESS)
                    .isIssued(true)
                    .isRejected(false)
                    .build();
        });

        int thirdReconcile = invoiceReconciliationWorker.processReconciliationBatch("reconcile-worker");
        assertThat(thirdReconcile).isEqualTo(1);
        verify(invoiceProvider, never()).issueInvoice(any());

        Invoice convergedInvoice = invoiceRepository.findById(invoice.getId()).orElseThrow();
        assertThat(convergedInvoice.getDocumentStatus()).isEqualTo(DocumentStatus.ISSUED);
        assertThat(convergedInvoice.getProviderTransactionId()).isEqualTo("TX_MISA_CONFIRMED");
        assertThat(convergedInvoice.getInvoiceNumber()).isEqualTo("0000555");
        assertThat(convergedInvoice.getTaxAuthorityCode()).isEqualTo("CQT_555");

        InvoiceOutbox finalRecOutbox = invoiceOutboxRepository.findById(recAfterTimeout.getId()).orElseThrow();
        assertThat(finalRecOutbox.getStatus()).isEqualTo(OutboxStatus.PROCESSED);
    }

    @Test
    @DisplayName("Unknown outcome - Publish body timeout calls publish exactly once, sets ISSUANCE_UNKNOWN, and creates RECONCILE_STATUS")
    void worker_publishBodyTimeout_callsPublishExactlyOnce_andSpawnsReconciliation() {
        Invoice invoice = createTestInvoiceWithOutbox("BODY_TIMEOUT_ONCE");

        when(invoiceProvider.issueInvoice(any(ProviderIssueRequest.class)))
                .thenThrow(new InvoiceProviderTimeoutException("MISA", "Body stream interrupted after headers received", null));

        int processed = invoiceOutboxWorker.processNextBatch("worker-body-timeout");
        assertThat(processed).isEqualTo(1);

        Invoice updatedInvoice = invoiceRepository.findById(invoice.getId()).orElseThrow();
        assertThat(updatedInvoice.getDocumentStatus()).isEqualTo(DocumentStatus.ISSUANCE_UNKNOWN);

        List<InvoiceOutbox> reconcileEvents = invoiceOutboxRepository.findByInvoiceIdAndEventType(invoice.getId(), "RECONCILE_STATUS");
        assertThat(reconcileEvents).hasSize(1);
        assertThat(reconcileEvents.get(0).getStatus()).isEqualTo(OutboxStatus.PENDING);

        verify(invoiceProvider, times(1)).issueInvoice(any(ProviderIssueRequest.class));

        int secondPoll = invoiceOutboxWorker.processNextBatch("worker-body-timeout");
        assertThat(secondPoll).isZero();
        verify(invoiceProvider, times(1)).issueInvoice(any(ProviderIssueRequest.class));
    }

    @Test
    @DisplayName("Lease Reaper - reclaims only expired leases, ignores active leases, and is idempotent on repeated runs")
    void leaseReaper_selectiveReclaimAndIdempotentExecution() {
        Invoice expiredInv = createTestInvoiceWithOutbox("REAP_EXPIRED");
        Invoice activeInv = createTestInvoiceWithOutbox("REAP_ACTIVE");

        // Set both invoices to ISSUING and claim both events
        transactionTemplate.execute(status -> {
            Invoice eInv = invoiceRepository.findById(expiredInv.getId()).orElseThrow();
            eInv.setDocumentStatus(DocumentStatus.ISSUING);
            invoiceRepository.save(eInv);

            Invoice aInv = invoiceRepository.findById(activeInv.getId()).orElseThrow();
            aInv.setDocumentStatus(DocumentStatus.ISSUING);
            invoiceRepository.save(aInv);
            return null;
        });

        List<InvoiceOutbox> claimed = invoiceOutboxService.claimEvents("worker-mix", Duration.ofMinutes(5), 2);
        assertThat(claimed).hasSize(2);

        InvoiceOutbox expiredEvent = invoiceOutboxRepository.findByInvoiceId(expiredInv.getId()).get(0);
        InvoiceOutbox activeEvent = invoiceOutboxRepository.findByInvoiceId(activeInv.getId()).get(0);
        String activeLeaseToken = activeEvent.getLeaseToken();

        // Expire only expiredEvent
        transactionTemplate.execute(status -> {
            InvoiceOutbox ev = invoiceOutboxRepository.findById(expiredEvent.getId()).orElseThrow();
            ev.setLockedUntil(LocalDateTime.now().minusMinutes(1));
            return invoiceOutboxRepository.save(ev);
        });

        // First reaper run: reclaims only expiredEvent (1)
        int firstReap = invoiceLeaseReaper.reapExpiredLeases();
        assertThat(firstReap).isEqualTo(1);

        // Second reaper run immediately after: idempotent (0 reaped, no duplicate RECONCILE_STATUS)
        int secondReap = invoiceLeaseReaper.reapExpiredLeases();
        assertThat(secondReap).isZero();

        // Verify expiredInv transitioned to ISSUANCE_UNKNOWN with exactly 1 RECONCILE_STATUS event
        assertThat(invoiceRepository.findById(expiredInv.getId()).orElseThrow().getDocumentStatus())
                .isEqualTo(DocumentStatus.ISSUANCE_UNKNOWN);
        assertThat(invoiceOutboxRepository.findByInvoiceIdAndEventType(expiredInv.getId(), "RECONCILE_STATUS"))
                .hasSize(1);

        // Verify activeEvent is completely untouched in PROCESSING with its original leaseToken
        InvoiceOutbox activeReloaded = invoiceOutboxRepository.findById(activeEvent.getId()).orElseThrow();
        assertThat(activeReloaded.getStatus()).isEqualTo(OutboxStatus.PROCESSING);
        assertThat(activeReloaded.getLeaseToken()).isEqualTo(activeLeaseToken);
        assertThat(invoiceRepository.findById(activeInv.getId()).orElseThrow().getDocumentStatus())
                .isEqualTo(DocumentStatus.ISSUING);
    }

    @Test
    @DisplayName("Reconciliation Identity Guard - rejects inquiry result with mismatched RefID and keeps invoice in ISSUANCE_UNKNOWN")
    void reconciliation_identityMismatchGuard_rejectsMismatchedRefId() {
        Invoice invoice = createTestInvoiceWithOutbox("ID_GUARD");

        // Simulate publish timeout so invoice is in ISSUANCE_UNKNOWN with a RECONCILE_STATUS event
        when(invoiceProvider.issueInvoice(any(ProviderIssueRequest.class)))
                .thenThrow(new InvoiceProviderTimeoutException("MISA", "Timeout", null));
        invoiceOutboxWorker.processNextBatch("worker-id-guard");

        // Provider inquiry returns found=true, isIssued=true, but with a DIFFERENT RefID
        when(invoiceProvider.inquiryInvoiceStatus(any(ProviderInquiryRequest.class))).thenReturn(
                ProviderInquiryResult.builder()
                        .found(true)
                        .refId("00000000-0000-0000-0000-999999999999")
                        .transactionId("TX_WRONG_INVOICE")
                        .invoiceNo("0000999")
                        .invoiceSeries("1C26TYY")
                        .attemptOutcome(AttemptOutcome.SUCCESS)
                        .isIssued(true)
                        .isRejected(false)
                        .build()
        );

        int reconciled = invoiceReconciliationWorker.processReconciliationBatch("reconcile-id-guard");
        assertThat(reconciled).isEqualTo(1);

        // Must NOT converge to ISSUED; must remain ISSUANCE_UNKNOWN with RECONCILE_STATUS rescheduled
        Invoice reloaded = invoiceRepository.findById(invoice.getId()).orElseThrow();
        assertThat(reloaded.getDocumentStatus()).isEqualTo(DocumentStatus.ISSUANCE_UNKNOWN);
        assertThat(reloaded.getInvoiceNumber()).isNull();

        InvoiceOutbox recEvent = invoiceOutboxRepository.findByInvoiceIdAndEventType(invoice.getId(), "RECONCILE_STATUS").get(0);
        assertThat(recEvent.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(recEvent.getLastError()).contains("mismatched identity");
    }

    @Test
    @DisplayName("Fencing - late provider response after lease expiry is rejected even before Lease Reaper runs")
    void fencing_lateProviderResponseAfterLeaseExpiry_rejectedBeforeReaperRuns() {
        Invoice invoice = createTestInvoiceWithOutbox("LATE_NO_REAPER");

        when(invoiceProvider.issueInvoice(any(ProviderIssueRequest.class))).thenAnswer(invocation -> {
            // Simulate lease expiring while HTTP call is still in flight (before Reaper runs)
            requiresNewTransactionTemplate.execute(status -> {
                InvoiceOutbox ev = invoiceOutboxRepository.findByInvoiceId(invoice.getId()).get(0);
                ev.setLockedUntil(LocalDateTime.now().minusSeconds(10));
                return invoiceOutboxRepository.save(ev);
            });

            return ProviderIssueResult.builder()
                    .success(true)
                    .transactionId("TX_LATE")
                    .invoiceNo("0000888")
                    .invoiceSeries("1C26TYY")
                    .issuedAt(LocalDateTime.now())
                    .attemptOutcome(AttemptOutcome.SUCCESS)
                    .retryable(false)
                    .build();
        });

        int processed = invoiceOutboxWorker.processNextBatch("worker-late");
        assertThat(processed).isZero();

        // Because lease expired before finalizeIssueOutcome, the stale worker was blocked from finalizing to ISSUED
        Invoice reloaded = invoiceRepository.findById(invoice.getId()).orElseThrow();
        assertThat(reloaded.getDocumentStatus()).isEqualTo(DocumentStatus.ISSUING);

        // Subsequent Reaper run transitions the expired ISSUING invoice safely to ISSUANCE_UNKNOWN + RECONCILE_STATUS
        int reaped = invoiceLeaseReaper.reapExpiredLeases();
        assertThat(reaped).isEqualTo(1);
        assertThat(invoiceRepository.findById(invoice.getId()).orElseThrow().getDocumentStatus())
                .isEqualTo(DocumentStatus.ISSUANCE_UNKNOWN);
    }

    @Test
    @DisplayName("Lease Reaper - lease expires while invoice is PENDING_ISSUE reclaims Outbox to PENDING without creating reconciliation event")
    void leaseReaper_whenPendingIssue_reclaimsToPendingWithoutReconcileEvent() {
        Invoice invoice = createTestInvoiceWithOutbox("REAP_PENDING_ISSUE");

        transactionTemplate.execute(status -> {
            Invoice inv = invoiceRepository.findById(invoice.getId()).orElseThrow();
            inv.setDocumentStatus(DocumentStatus.PENDING_ISSUE);
            return invoiceRepository.save(inv);
        });

        List<InvoiceOutbox> claimed = invoiceOutboxService.claimEvents("worker-pending-crash", Duration.ofMinutes(2), 1);
        assertThat(claimed).hasSize(1);
        InvoiceOutbox claimedEvent = claimed.get(0);

        transactionTemplate.execute(status -> {
            InvoiceOutbox ev = invoiceOutboxRepository.findById(claimedEvent.getId()).orElseThrow();
            ev.setLockedUntil(LocalDateTime.now().minusMinutes(5));
            return invoiceOutboxRepository.save(ev);
        });

        int reaped = invoiceLeaseReaper.reapExpiredLeases();
        assertThat(reaped).isEqualTo(1);

        Invoice reloaded = invoiceRepository.findById(invoice.getId()).orElseThrow();
        assertThat(reloaded.getDocumentStatus()).isEqualTo(DocumentStatus.PENDING_ISSUE);

        InvoiceOutbox reloadedOutbox = invoiceOutboxRepository.findById(claimedEvent.getId()).orElseThrow();
        assertThat(reloadedOutbox.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(reloadedOutbox.getLeaseToken()).isNull();
        assertThat(reloadedOutbox.getLockedUntil()).isNull();

        List<InvoiceOutbox> reconcileEvents = invoiceOutboxRepository.findByInvoiceIdAndEventType(invoice.getId(), "RECONCILE_STATUS");
        assertThat(reconcileEvents).isEmpty();
        verify(invoiceProvider, never()).inquiryInvoiceStatus(any());
    }

    @Test
    @DisplayName("Reconciliation Exhaustion - max reconciliation attempts moves Outbox to DEAD_LETTER while invoice remains ISSUANCE_UNKNOWN")
    void reconciliationExhaustion_movesOutboxToDeadLetter_keepsInvoiceInIssuanceUnknown() {
        Invoice invoice = createTestInvoiceWithOutbox("RECON_EXHAUST");
        String expectedRefId = MisaPayloadMapper.toDeterministicRefId(invoice.getIdempotencyKey());

        when(invoiceProvider.issueInvoice(any(ProviderIssueRequest.class)))
                .thenThrow(new InvoiceProviderTimeoutException("MISA", "Timeout", null));
        invoiceOutboxWorker.processNextBatch("worker-exhaust");

        InvoiceOutbox recEvent = invoiceOutboxRepository.findByInvoiceIdAndEventType(invoice.getId(), "RECONCILE_STATUS").get(0);

        outboxProperties.setMaxReconcileAttempts(3);
        transactionTemplate.execute(status -> {
            InvoiceOutbox ev = invoiceOutboxRepository.findById(recEvent.getId()).orElseThrow();
            ev.setAttemptCount(2);
            ev.setAvailableAt(LocalDateTime.now().minusSeconds(1));
            return invoiceOutboxRepository.save(ev);
        });

        when(invoiceProvider.inquiryInvoiceStatus(any(ProviderInquiryRequest.class))).thenReturn(
                ProviderInquiryResult.builder()
                        .found(false)
                        .refId(expectedRefId)
                        .attemptOutcome(AttemptOutcome.SUCCESS)
                        .isIssued(false)
                        .isRejected(false)
                        .build()
        );

        int reconciled = invoiceReconciliationWorker.processReconciliationBatch("worker-exhaust-rec");
        assertThat(reconciled).isEqualTo(1);

        InvoiceOutbox exhaustedEvent = invoiceOutboxRepository.findById(recEvent.getId()).orElseThrow();
        assertThat(exhaustedEvent.getStatus()).isEqualTo(OutboxStatus.DEAD_LETTER);
        assertThat(exhaustedEvent.getLastError()).contains("Max reconciliation attempts exhausted");

        Invoice reloadedInvoice = invoiceRepository.findById(invoice.getId()).orElseThrow();
        assertThat(reloadedInvoice.getDocumentStatus()).isEqualTo(DocumentStatus.ISSUANCE_UNKNOWN);
    }

    @Test
    @DisplayName("Reactivation 1 - Sequential reactivation twice results in exactly one PENDING event")
    void reactivation_sequentialTwice_createsExactlyOneEvent() {
        Invoice invoice = createTestInvoiceWithOutbox("REACT_SEQ");

        transactionTemplate.execute(status -> {
            Invoice inv = invoiceRepository.findById(invoice.getId()).orElseThrow();
            inv.setDocumentStatus(DocumentStatus.ISSUING);
            inv = invoiceRepository.save(inv);
            inv.setDocumentStatus(DocumentStatus.ISSUANCE_UNKNOWN);
            return invoiceRepository.save(inv);
        });

        InvoiceOutbox first = invoiceOutboxService.reactivateReconciliation(invoice.getId());
        InvoiceOutbox second = invoiceOutboxService.reactivateReconciliation(invoice.getId());

        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
        assertThat(first.getId()).isEqualTo(second.getId());
        assertThat(first.getStatus()).isEqualTo(OutboxStatus.PENDING);

        List<InvoiceOutbox> events = invoiceOutboxRepository.findByInvoiceIdAndEventType(invoice.getId(), "RECONCILE_STATUS");
        assertThat(events).hasSize(1);
    }

    @Test
    @DisplayName("Reactivation 2 - Concurrent reactivation requests from two threads converge to exactly one event")
    void reactivation_concurrentRequests_convergesToExactlyOneEvent() throws Exception {
        Invoice invoice = createTestInvoiceWithOutbox("REACT_CONC");

        transactionTemplate.execute(status -> {
            Invoice inv = invoiceRepository.findById(invoice.getId()).orElseThrow();
            inv.setDocumentStatus(DocumentStatus.ISSUING);
            inv = invoiceRepository.save(inv);
            inv.setDocumentStatus(DocumentStatus.ISSUANCE_UNKNOWN);
            return invoiceRepository.save(inv);
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        Future<InvoiceOutbox> f1 = executor.submit(() -> {
            startLatch.await();
            return invoiceOutboxService.reactivateReconciliation(invoice.getId());
        });
        Future<InvoiceOutbox> f2 = executor.submit(() -> {
            startLatch.await();
            return invoiceOutboxService.reactivateReconciliation(invoice.getId());
        });

        startLatch.countDown();
        InvoiceOutbox r1 = f1.get(10, TimeUnit.SECONDS);
        InvoiceOutbox r2 = f2.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(r1).isNotNull();
        assertThat(r2).isNotNull();
        assertThat(r1.getId()).isEqualTo(r2.getId());
        assertThat(r1.getStatus()).isEqualTo(OutboxStatus.PENDING);

        List<InvoiceOutbox> events = invoiceOutboxRepository.findByInvoiceIdAndEventType(invoice.getId(), "RECONCILE_STATUS");
        assertThat(events).hasSize(1);
    }

    @Test
    @DisplayName("Reactivation 3 - When worker is PROCESSING with valid lease, reactivation does NOT reset lease")
    void reactivation_whenWorkerProcessingWithValidLease_doesNotResetLease() {
        Invoice invoice = createTestInvoiceWithOutbox("REACT_VALID_LEASE");

        transactionTemplate.execute(status -> {
            Invoice inv = invoiceRepository.findById(invoice.getId()).orElseThrow();
            inv.setDocumentStatus(DocumentStatus.ISSUING);
            inv = invoiceRepository.save(inv);
            inv.setDocumentStatus(DocumentStatus.ISSUANCE_UNKNOWN);
            return invoiceRepository.save(inv);
        });

        InvoiceOutbox initial = invoiceOutboxService.reactivateReconciliation(invoice.getId());
        assertThat(initial).isNotNull();

        List<InvoiceOutbox> claimed = invoiceOutboxService.claimEventsByType("RECONCILE_STATUS", "worker-active-lease", Duration.ofMinutes(5), 1);
        assertThat(claimed).hasSize(1);
        String originalToken = claimed.get(0).getLeaseToken();
        assertThat(originalToken).isNotNull();

        // Reactivation while worker holds a valid unexpired lease
        InvoiceOutbox reloaded = invoiceOutboxService.reactivateReconciliation(invoice.getId());
        assertThat(reloaded.getStatus()).isEqualTo(OutboxStatus.PROCESSING);
        assertThat(reloaded.getLeaseToken()).isEqualTo(originalToken);
        assertThat(reloaded.getLockedBy()).isEqualTo("worker-active-lease");
    }

    @Test
    @DisplayName("Reactivation 4 - When Invoice is ISSUED, reactivation is safely ignored and returns null")
    void reactivation_whenInvoiceIsIssued_ignoredAndReturnsNull() {
        Invoice invoice = createTestInvoiceWithOutbox("REACT_ISSUED");

        transactionTemplate.execute(status -> {
            Invoice inv = invoiceRepository.findById(invoice.getId()).orElseThrow();
            inv.setDocumentStatus(DocumentStatus.ISSUING);
            inv = invoiceRepository.save(inv);
            inv.setDocumentStatus(DocumentStatus.ISSUED);
            inv.setInvoiceNumber("0000777");
            return invoiceRepository.save(inv);
        });

        InvoiceOutbox result = invoiceOutboxService.reactivateReconciliation(invoice.getId());
        assertThat(result).isNull();

        Invoice reloaded = invoiceRepository.findById(invoice.getId()).orElseThrow();
        assertThat(reloaded.getDocumentStatus()).isEqualTo(DocumentStatus.ISSUED);

        List<InvoiceOutbox> events = invoiceOutboxRepository.findByInvoiceIdAndEventType(invoice.getId(), "RECONCILE_STATUS");
        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("Reactivation 5 - When Invoice Order is CANCELLED but Invoice is ISSUANCE_UNKNOWN, reconciliation reactivation is permitted")
    void reactivation_whenInvoiceOrderCancelled_andIssuanceUnknown_allowsReactivation() {
        Invoice invoice = createTestInvoiceWithOutbox("REACT_CANCELLED");

        transactionTemplate.execute(status -> {
            Invoice inv = invoiceRepository.findById(invoice.getId()).orElseThrow();
            Order order = inv.getOrder();
            order.setStatus(OrderStatus.CANCELLED);
            orderRepository.save(order);

            inv.setDocumentStatus(DocumentStatus.ISSUING);
            inv = invoiceRepository.save(inv);
            inv.setDocumentStatus(DocumentStatus.ISSUANCE_UNKNOWN);
            return invoiceRepository.save(inv);
        });

        InvoiceOutbox result = invoiceOutboxService.reactivateReconciliation(invoice.getId());
        assertThat(result).isNotNull();
        assertThat(result.getEventType()).isEqualTo("RECONCILE_STATUS");
        assertThat(result.getStatus()).isEqualTo(OutboxStatus.PENDING);

        List<InvoiceOutbox> events = invoiceOutboxRepository.findByInvoiceIdAndEventType(invoice.getId(), "RECONCILE_STATUS");
        assertThat(events).hasSize(1);
    }

    @Test
    @DisplayName("Reconciliation - Order CANCELLED + Invoice ISSUANCE_UNKNOWN + Inquiry confirms issued converges to ISSUED")
    void orderCancelled_issuanceUnknown_inquiryFoundIssued_convergesToIssued() {
        Invoice invoice = createTestInvoiceWithOutbox("ORD_CAN_ISSUED");

        transactionTemplate.execute(status -> {
            Invoice inv = invoiceRepository.findById(invoice.getId()).orElseThrow();
            Order order = inv.getOrder();
            order.setStatus(OrderStatus.CANCELLED);
            orderRepository.save(order);

            inv.setDocumentStatus(DocumentStatus.ISSUING);
            inv = invoiceRepository.save(inv);
            inv.setDocumentStatus(DocumentStatus.ISSUANCE_UNKNOWN);
            return invoiceRepository.save(inv);
        });

        invoiceOutboxService.reactivateReconciliation(invoice.getId());

        when(invoiceProvider.inquiryInvoiceStatus(any())).thenReturn(ProviderInquiryResult.builder()
                .found(true)
                .isIssued(true)
                .refId(com.shop.backend.einvoice.provider.misa.MisaPayloadMapper.toDeterministicRefId(invoice.getIdempotencyKey()))
                .transactionId("TX_CANCELLED_ORD")
                .invoiceNo("0009988")
                .invoiceSeries("1C26TYY")
                .status("1")
                .attemptOutcome(AttemptOutcome.SUCCESS)
                .build());

        int processed = invoiceReconciliationWorker.processReconciliationBatch("worker-rec-ord-can");
        assertThat(processed).isEqualTo(1);

        Invoice reloaded = invoiceRepository.findById(invoice.getId()).orElseThrow();
        assertThat(reloaded.getDocumentStatus()).isEqualTo(DocumentStatus.ISSUED);
        assertThat(reloaded.getInvoiceNumber()).isEqualTo("0009988");
    }

    @Test
    @DisplayName("Reconciliation - Order CANCELLED + Invoice ISSUANCE_UNKNOWN + Inquiry not found remains ISSUANCE_UNKNOWN and retries")
    void orderCancelled_issuanceUnknown_inquiryNotFound_remainsIssuanceUnknownAndRetries() {
        Invoice invoice = createTestInvoiceWithOutbox("ORD_CAN_NOTFOUND");

        transactionTemplate.execute(status -> {
            Invoice inv = invoiceRepository.findById(invoice.getId()).orElseThrow();
            Order order = inv.getOrder();
            order.setStatus(OrderStatus.CANCELLED);
            orderRepository.save(order);

            inv.setDocumentStatus(DocumentStatus.ISSUING);
            inv = invoiceRepository.save(inv);
            inv.setDocumentStatus(DocumentStatus.ISSUANCE_UNKNOWN);
            return invoiceRepository.save(inv);
        });

        invoiceOutboxService.reactivateReconciliation(invoice.getId());

        when(invoiceProvider.inquiryInvoiceStatus(any())).thenReturn(ProviderInquiryResult.builder()
                .found(false)
                .isIssued(false)
                .refId(com.shop.backend.einvoice.provider.misa.MisaPayloadMapper.toDeterministicRefId(invoice.getIdempotencyKey()))
                .attemptOutcome(AttemptOutcome.SUCCESS)
                .build());

        int processed = invoiceReconciliationWorker.processReconciliationBatch("worker-rec-ord-notfound");
        assertThat(processed).isEqualTo(1);

        Invoice reloaded = invoiceRepository.findById(invoice.getId()).orElseThrow();
        assertThat(reloaded.getDocumentStatus()).isEqualTo(DocumentStatus.ISSUANCE_UNKNOWN);

        InvoiceOutbox outbox = invoiceOutboxRepository.findByInvoiceIdAndEventType(invoice.getId(), "RECONCILE_STATUS").get(0);
        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(outbox.getAttemptCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Order CANCELLED does not trigger a new Publish request for DRAFT/PENDING_ISSUE invoice")
    void orderCancelled_draftInvoice_doesNotTriggerNewPublish() {
        Invoice invoice = createTestInvoiceWithOutbox("ORD_CAN_NO_PUB");

        transactionTemplate.execute(status -> {
            Invoice inv = invoiceRepository.findById(invoice.getId()).orElseThrow();
            Order order = inv.getOrder();
            order.setStatus(OrderStatus.CANCELLED);
            return orderRepository.save(order);
        });

        int processed = invoiceOutboxWorker.processNextBatch("worker-no-pub");
        assertThat(processed).isEqualTo(1);

        verify(invoiceProvider, never()).issueInvoice(any());

        Invoice reloaded = invoiceRepository.findById(invoice.getId()).orElseThrow();
        assertThat(reloaded.getDocumentStatus()).isEqualTo(DocumentStatus.ISSUE_FAILED);

        InvoiceOutbox outbox = invoiceOutboxRepository.findByInvoiceId(invoice.getId()).get(0);
        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(outbox.getLastError()).contains("Order is CANCELLED");
    }

    @Test
    @DisplayName("Batch Claiming - Slow provider processing items one-by-one prevents lease aging and reaper reclamation")
    void batchClaiming_slowProvider_laterItemsDoNotExpireOrGetReaped() {
        Invoice inv1 = createTestInvoiceWithOutbox("SLOW_BATCH_1");
        Invoice inv2 = createTestInvoiceWithOutbox("SLOW_BATCH_2");
        Invoice inv3 = createTestInvoiceWithOutbox("SLOW_BATCH_3");

        outboxProperties.setBatchSize(3);

        when(invoiceProvider.issueInvoice(any())).thenAnswer(invocation -> {
            Thread.sleep(50L);
            ProviderIssueRequest req = invocation.getArgument(0);
            return ProviderIssueResult.builder()
                    .success(true)
                    .transactionId("TX_" + req.getInvoiceId())
                    .invoiceNo("0000" + req.getInvoiceId())
                    .invoiceSeries("1C26TYY")
                    .issuedAt(LocalDateTime.now())
                    .attemptOutcome(AttemptOutcome.SUCCESS)
                    .build();
        });

        int processed = invoiceOutboxWorker.processNextBatch("worker-slow-batch");
        assertThat(processed).isEqualTo(3);

        int reaped = invoiceLeaseReaper.reapExpiredLeases();
        assertThat(reaped).isZero();

        assertThat(invoiceRepository.findById(inv1.getId()).orElseThrow().getDocumentStatus()).isEqualTo(DocumentStatus.ISSUED);
        assertThat(invoiceRepository.findById(inv2.getId()).orElseThrow().getDocumentStatus()).isEqualTo(DocumentStatus.ISSUED);
        assertThat(invoiceRepository.findById(inv3.getId()).orElseThrow().getDocumentStatus()).isEqualTo(DocumentStatus.ISSUED);
    }

    @Test
    @DisplayName("Reactivation 6 - When Invoice is ISSUE_FAILED, reactivation does NOT automatically reopen")
    void reactivation_whenInvoiceIsIssueFailed_ignoredAndReturnsNull() {
        Invoice invoice = createTestInvoiceWithOutbox("REACT_FAILED");

        transactionTemplate.execute(status -> {
            Invoice inv = invoiceRepository.findById(invoice.getId()).orElseThrow();
            inv.setDocumentStatus(DocumentStatus.ISSUING);
            inv = invoiceRepository.save(inv);
            inv.setDocumentStatus(DocumentStatus.ISSUE_FAILED);
            return invoiceRepository.save(inv);
        });

        InvoiceOutbox result = invoiceOutboxService.reactivateReconciliation(invoice.getId());
        assertThat(result).isNull();

        Invoice reloaded = invoiceRepository.findById(invoice.getId()).orElseThrow();
        assertThat(reloaded.getDocumentStatus()).isEqualTo(DocumentStatus.ISSUE_FAILED);

        List<InvoiceOutbox> events = invoiceOutboxRepository.findByInvoiceIdAndEventType(invoice.getId(), "RECONCILE_STATUS");
        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("Reactivation 7 - Concurrent Lease Reaper and Reactivation never lose events or corrupt lease state")
    void reactivation_concurrentWithLeaseReaper_doesNotLoseEventOrCorruptState() throws Exception {
        Invoice invoice = createTestInvoiceWithOutbox("REACT_REAP_RACE");

        transactionTemplate.execute(status -> {
            Invoice inv = invoiceRepository.findById(invoice.getId()).orElseThrow();
            inv.setDocumentStatus(DocumentStatus.ISSUING);
            inv = invoiceRepository.save(inv);
            inv.setDocumentStatus(DocumentStatus.ISSUANCE_UNKNOWN);
            return invoiceRepository.save(inv);
        });

        // Create reconcile event and put it in PROCESSING with expired lease
        InvoiceOutbox initial = invoiceOutboxService.reactivateReconciliation(invoice.getId());
        List<InvoiceOutbox> claimed = invoiceOutboxService.claimEventsByType("RECONCILE_STATUS", "worker-to-expire", Duration.ofMinutes(5), 1);
        assertThat(claimed).hasSize(1);

        transactionTemplate.execute(status -> {
            InvoiceOutbox ev = invoiceOutboxRepository.findById(initial.getId()).orElseThrow();
            ev.setLockedUntil(LocalDateTime.now().minusMinutes(5));
            return invoiceOutboxRepository.save(ev);
        });

        // Run Lease Reaper and Reactivation concurrently
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        Future<Integer> reaperFuture = executor.submit(() -> {
            startLatch.await();
            return invoiceLeaseReaper.reapExpiredLeases();
        });
        Future<InvoiceOutbox> reactivateFuture = executor.submit(() -> {
            startLatch.await();
            return invoiceOutboxService.reactivateReconciliation(invoice.getId());
        });

        startLatch.countDown();
        int reaped = reaperFuture.get(10, TimeUnit.SECONDS);
        InvoiceOutbox reactivated = reactivateFuture.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        // State must safely converge to PENDING with attemptCount=0, invoice in ISSUANCE_UNKNOWN
        Invoice reloadedInvoice = invoiceRepository.findById(invoice.getId()).orElseThrow();
        assertThat(reloadedInvoice.getDocumentStatus()).isEqualTo(DocumentStatus.ISSUANCE_UNKNOWN);

        List<InvoiceOutbox> events = invoiceOutboxRepository.findByInvoiceIdAndEventType(invoice.getId(), "RECONCILE_STATUS");
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(events.get(0).getLockedUntil()).isNull();
        assertThat(events.get(0).getLeaseToken()).isNull();
    }

    @Test
    @DisplayName("Graceful Shutdown - stops claiming new batches and releases mid-batch unprocessed leases back to PENDING")
    void gracefulShutdown_releasesMidBatchUnprocessedLeasesAndStopsClaiming() {
        Invoice inv1 = createTestInvoiceWithOutbox("SHUT_1");
        Invoice inv2 = createTestInvoiceWithOutbox("SHUT_2");

        when(invoiceProvider.issueInvoice(any(ProviderIssueRequest.class))).thenAnswer(invocation -> {
            // Trigger shutdown during processing of the first item in the claimed batch
            invoiceOutboxWorker.initiateShutdown();
            return ProviderIssueResult.builder()
                    .success(true)
                    .transactionId("TX_SHUT_1")
                    .invoiceNo("0000301")
                    .invoiceSeries("1C26TYY")
                    .issuedAt(LocalDateTime.now())
                    .attemptOutcome(AttemptOutcome.SUCCESS)
                    .retryable(false)
                    .build();
        });

        int processed = invoiceOutboxWorker.processNextBatch("worker-shutdown");
        assertThat(processed).isEqualTo(1);
        verify(invoiceProvider, times(1)).issueInvoice(any());

        // First item completed normally; second item in the batch was released back to PENDING with attemptCount=0
        assertThat(invoiceRepository.findById(inv1.getId()).orElseThrow().getDocumentStatus()).isEqualTo(DocumentStatus.ISSUED);
        assertThat(invoiceRepository.findById(inv2.getId()).orElseThrow().getDocumentStatus()).isEqualTo(DocumentStatus.DRAFT);

        InvoiceOutbox outbox2 = invoiceOutboxRepository.findByInvoiceId(inv2.getId()).get(0);
        assertThat(outbox2.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(outbox2.getLeaseToken()).isNull();
        assertThat(outbox2.getLockedUntil()).isNull();
        assertThat(outbox2.getAttemptCount()).isZero();

        // Further calls while shuttingDown=true must claim 0 items
        assertThat(invoiceOutboxWorker.processNextBatch("worker-shutdown")).isZero();
    }
}
