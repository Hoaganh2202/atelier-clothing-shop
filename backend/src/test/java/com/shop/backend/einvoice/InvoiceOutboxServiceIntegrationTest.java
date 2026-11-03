package com.shop.backend.einvoice;

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
import com.shop.backend.einvoice.model.InvoiceOutbox;
import com.shop.backend.einvoice.model.OutboxStatus;
import com.shop.backend.einvoice.model.PricingBasis;
import com.shop.backend.einvoice.model.ProofSource;
import com.shop.backend.einvoice.model.SellerInvoiceProfile;
import com.shop.backend.einvoice.model.TaxConfiguration;
import com.shop.backend.einvoice.model.TaxTreatment;
import com.shop.backend.einvoice.provider.dto.ProviderInquiryRequest;
import com.shop.backend.einvoice.provider.dto.ProviderInquiryResult;
import com.shop.backend.einvoice.provider.misa.MisaPayloadMapper;
import com.shop.backend.einvoice.repository.FulfillmentEventItemRepository;
import com.shop.backend.einvoice.repository.FulfillmentEventRepository;
import com.shop.backend.einvoice.repository.InvoiceBalanceAdjustmentRepository;
import com.shop.backend.einvoice.repository.InvoiceItemRepository;
import com.shop.backend.einvoice.repository.InvoiceOutboxRepository;
import com.shop.backend.einvoice.repository.InvoiceRepository;
import com.shop.backend.einvoice.repository.TaxConfigurationRepository;
import com.shop.backend.einvoice.service.FulfillmentService;
import com.shop.backend.einvoice.service.InvoiceDomainService;
import com.shop.backend.einvoice.service.InvoiceOutboxService;
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
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
public class InvoiceOutboxServiceIntegrationTest {

    private static final String TEST_PREFIX = "einv-out-";

    @Autowired
    private InvoiceOutboxService invoiceOutboxService;

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
    private Long testInvoiceId;

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
                    .name(TEST_PREFIX + "Outbox User")
                    .phone("0988776655")
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
                    .name(TEST_PREFIX + "Shirt")
                    .slug(TEST_PREFIX + "prod-" + System.nanoTime())
                    .basePrice(new BigDecimal("100000.00"))
                    .isActive(true)
                    .dataSource(DataSourceType.WEBSITE)
                    .build());

            ProductVariant variant = productVariantRepository.save(ProductVariant.builder()
                    .product(product)
                    .sku(TEST_PREFIX + "SKU-" + System.nanoTime())
                    .size("M")
                    .color("White")
                    .stock(100)
                    .price(new BigDecimal("100000.00"))
                    .build());

            Order order = Order.builder()
                    .user(user)
                    .receiverName(TEST_PREFIX + "Receiver")
                    .receiverPhone("0988776655")
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

            OrderDetail d1 = OrderDetail.builder()
                    .variant(variant)
                    .productName(TEST_PREFIX + "Shirt M")
                    .variantSize("M")
                    .variantColor("White")
                    .quantity(1)
                    .unitPrice(new BigDecimal("100000.00"))
                    .discountAmount(BigDecimal.ZERO)
                    .build();
            order.addOrderDetail(d1);

            Order savedOrder = orderRepository.save(order);
            testOrderId = savedOrder.getId();
            testDetailId1 = savedOrder.getOrderDetails().get(0).getId();

            return null;
        });

        // Fulfillment
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
                                .deliveredQuantity(1)
                                .unitPriceSnapshot(new BigDecimal("100000.00"))
                                .discountAllocated(BigDecimal.ZERO)
                                .build()
                ))
                .build();
        FulfillmentEvent fe = fulfillmentService.recordFulfillment(fCmd);
        testFulfillmentEventId = fe.getId();

        // Create ORIGINAL Invoice
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
                                .itemName(TEST_PREFIX + "Shirt M")
                                .unit("cai")
                                .quantity(1)
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
        testInvoiceId = orig.getId();
    }

    @AfterEach
    void tearDown() {
        cleanTestData();
    }

    private void cleanTestData() {
        transactionTemplate.execute(status -> {
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
    @DisplayName("claimEvents - concurrent workers claim with SKIP LOCKED: no duplicates or double claims")
    void claimEvents_concurrency_skipLocked_noDuplicateClaims() throws InterruptedException {
        // Clear initial outbox event from setUp so we control the exact count
        transactionTemplate.execute(status -> {
            invoiceOutboxRepository.deleteAll();
            return null;
        });

        // Seed 6 distinct outbox events
        int totalEvents = 6;
        for (int i = 0; i < totalEvents; i++) {
            invoiceOutboxService.createEvent(
                    "ISSUE_INVOICE",
                    TEST_PREFIX + "EVENT_" + i + "_" + System.nanoTime(),
                    testInvoiceId,
                    "{\"index\":" + i + "}"
            );
        }

        int workerCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(workerCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(workerCount);

        List<InvoiceOutbox> worker1Claimed = Collections.synchronizedList(new ArrayList<>());
        List<InvoiceOutbox> worker2Claimed = Collections.synchronizedList(new ArrayList<>());

        executor.submit(() -> {
            try {
                startLatch.await();
                List<InvoiceOutbox> claimed = invoiceOutboxService.claimEvents("worker-1", Duration.ofMinutes(5), totalEvents);
                worker1Claimed.addAll(claimed);
            } catch (Exception e) {
                // ignore
            } finally {
                doneLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                List<InvoiceOutbox> claimed = invoiceOutboxService.claimEvents("worker-2", Duration.ofMinutes(5), totalEvents);
                worker2Claimed.addAll(claimed);
            } catch (Exception e) {
                // ignore
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();

        // Check total claimed
        int totalClaimed = worker1Claimed.size() + worker2Claimed.size();
        assertThat(totalClaimed).isEqualTo(totalEvents);

        // Verify zero overlap in claimed IDs (SKIP LOCKED guarantees mutual exclusion)
        Set<Long> w1Ids = new HashSet<>();
        for (InvoiceOutbox ev : worker1Claimed) {
            w1Ids.add(ev.getId());
        }
        Set<Long> w2Ids = new HashSet<>();
        for (InvoiceOutbox ev : worker2Claimed) {
            w2Ids.add(ev.getId());
        }

        Set<Long> intersection = new HashSet<>(w1Ids);
        intersection.retainAll(w2Ids);
        assertThat(intersection).isEmpty();
    }

    @Test
    @DisplayName("completeEvent - succeeds with matching leaseToken, throws OutboxConcurrencyException on invalid token")
    void completeEvent_fencingTokenProtection() {
        // Find existing event created by setUp
        List<InvoiceOutbox> events = invoiceOutboxService.claimEvents("worker-test", Duration.ofMinutes(5), 1);
        assertThat(events).hasSize(1);
        InvoiceOutbox claimed = events.get(0);
        String validToken = claimed.getLeaseToken();
        Long eventId = claimed.getId();

        // 1. Try to complete with invalid token (fencing check)
        assertThatThrownBy(() -> invoiceOutboxService.completeEvent(eventId, "forged-lease-token-xyz"))
                .isInstanceOf(OutboxConcurrencyException.class)
                .hasMessageContaining("Lease token mismatch");

        // 2. Complete with valid token
        invoiceOutboxService.completeEvent(eventId, validToken);

        InvoiceOutbox completed = invoiceOutboxService.getEvent(eventId);
        assertThat(completed.getStatus()).isEqualTo(OutboxStatus.PROCESSED);
        assertThat(completed.getProcessedAt()).isNotNull();
        assertThat(completed.getLockedUntil()).isNull();
    }

    @Test
    @DisplayName("failEvent - retries with exponential/configured delay or moves to dead letter FAILED")
    void failEvent_retriesAndFails() {
        // Claim the event
        List<InvoiceOutbox> events = invoiceOutboxService.claimEvents("worker-fail-test", Duration.ofMinutes(5), 1);
        InvoiceOutbox claimed = events.get(0);
        String token = claimed.getLeaseToken();

        // 1. Fail with retryable = true
        invoiceOutboxService.failEvent(claimed.getId(), token, "Temporary network timeout", true, Duration.ofSeconds(30));

        InvoiceOutbox retryEvent = invoiceOutboxService.getEvent(claimed.getId());
        assertThat(retryEvent.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(retryEvent.getLastError()).isEqualTo("Temporary network timeout");
        assertThat(retryEvent.getAvailableAt()).isAfter(LocalDateTime.now());

        // Fast forward availableAt for re-claim
        transactionTemplate.execute(status -> {
            retryEvent.setAvailableAt(LocalDateTime.now().minusSeconds(1));
            return invoiceOutboxRepository.save(retryEvent);
        });

        // 2. Re-claim and fail with retryable = false (Dead letter)
        List<InvoiceOutbox> reclaimed = invoiceOutboxService.claimEvents("worker-fail-test", Duration.ofMinutes(5), 1);
        assertThat(reclaimed).hasSize(1);
        InvoiceOutbox secondAttempt = reclaimed.get(0);

        invoiceOutboxService.failEvent(secondAttempt.getId(), secondAttempt.getLeaseToken(), "Permanent 400 Bad Request", false, null);

        InvoiceOutbox deadLetterEvent = invoiceOutboxService.getEvent(secondAttempt.getId());
        assertThat(deadLetterEvent.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(deadLetterEvent.getLastError()).isEqualTo("Permanent 400 Bad Request");
    }

    @Test
    @DisplayName("reapExpiredLeases - transitions in-flight ISSUING invoice to ISSUANCE_UNKNOWN and spawns RECONCILE_STATUS event")
    void reapExpiredLeases_transitionsToIssuanceUnknown_andCreatesReconcileEvent() {
        // Set invoice to ISSUING (simulating in-flight provider dispatch)
        transactionTemplate.execute(status -> {
            Invoice inv = invoiceRepository.findById(testInvoiceId).orElseThrow();
            inv.setDocumentStatus(DocumentStatus.ISSUING);
            return invoiceRepository.save(inv);
        });

        // Claim event to put it in PROCESSING
        List<InvoiceOutbox> events = invoiceOutboxService.claimEvents("worker-reap", Duration.ofMinutes(5), 1);
        InvoiceOutbox claimed = events.get(0);

        // Manually expire the lease into the past
        transactionTemplate.execute(status -> {
            InvoiceOutbox ev = invoiceOutboxRepository.findById(claimed.getId()).orElseThrow();
            ev.setLockedUntil(LocalDateTime.now().minusMinutes(10));
            return invoiceOutboxRepository.save(ev);
        });

        // Trigger Reaper
        int reaped = invoiceOutboxService.reapExpiredLeases();
        assertThat(reaped).isEqualTo(1);

        // Verify Invoice transitioned to ISSUANCE_UNKNOWN
        Invoice updatedInvoice = invoiceRepository.findById(testInvoiceId).orElseThrow();
        assertThat(updatedInvoice.getDocumentStatus()).isEqualTo(DocumentStatus.ISSUANCE_UNKNOWN);

        // Verify old event marked FAILED
        InvoiceOutbox oldEvent = invoiceOutboxService.getEvent(claimed.getId());
        assertThat(oldEvent.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(oldEvent.getLastError()).contains("transitioned invoice to ISSUANCE_UNKNOWN");

        // Verify RECONCILE_STATUS outbox event created
        List<InvoiceOutbox> reconcileEvents = invoiceOutboxRepository.findAll().stream()
                .filter(ev -> "RECONCILE_STATUS".equals(ev.getEventType()) && ev.getInvoice().getId().equals(testInvoiceId))
                .toList();
        assertThat(reconcileEvents).hasSize(1);
        InvoiceOutbox rec = reconcileEvents.get(0);
        assertThat(rec.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(rec.getPayload()).contains("LEASE_EXPIRED_IN_FLIGHT_ISSUING");
        assertThat(rec.getEventKey()).isEqualTo("RECONCILE_INVOICE_" + testInvoiceId);
        assertThat(rec.getPayload()).contains(updatedInvoice.getIdempotencyKey());
    }

    @Test
    @DisplayName("finalizeReconcileOutcome - with MOCK provider sets storage_provider to MOCK")
    void finalizeReconcileOutcome_withMockProvider_setsStorageProviderMock() {
        transactionTemplate.execute(status -> {
            Invoice inv = invoiceRepository.findById(testInvoiceId).orElseThrow();
            inv.setDocumentStatus(DocumentStatus.ISSUANCE_UNKNOWN);
            return invoiceRepository.save(inv);
        });

        InvoiceOutbox reconcileEvent = transactionTemplate.execute(status -> {
            Invoice inv = invoiceRepository.findById(testInvoiceId).orElseThrow();
            InvoiceOutbox event = InvoiceOutbox.builder()
                    .invoice(inv)
                    .eventType("RECONCILE_STATUS")
                    .status(OutboxStatus.PENDING)
                    .payload("{\"invoiceId\":" + testInvoiceId + "}")
                    .eventKey("RECONCILE_INVOICE_" + testInvoiceId)
                    .availableAt(LocalDateTime.now())
                    .build();
            return invoiceOutboxRepository.save(event);
        });

        List<InvoiceOutbox> claimed = invoiceOutboxService.claimEvents("worker-reconcile-mock", Duration.ofMinutes(5), 10);
        InvoiceOutbox activeClaim = claimed.stream()
                .filter(e -> e.getId().equals(reconcileEvent.getId()))
                .findFirst()
                .orElseThrow();

        ProviderInquiryRequest request = invoiceOutboxService.prepareReconcileDispatch(activeClaim.getId(), activeClaim.getLeaseToken());

        ProviderInquiryResult result = ProviderInquiryResult.builder()
                .found(true)
                .isIssued(true)
                .status("ISSUED")
                .invoiceNo("MOCK-00000001")
                .invoiceSeries("1C26TAA")
                .taxAuthorityCode("MOCK-TAX-001")
                .transactionId("MOCK-TXN-001")
                .attemptOutcome(AttemptOutcome.SUCCESS)
                .refId(request.getRefId())
                .build();

        invoiceOutboxService.finalizeReconcileOutcome(
                activeClaim.getId(),
                activeClaim.getLeaseToken(),
                "MOCK",
                request,
                result,
                10,
                Duration.ofSeconds(10)
        );

        Invoice finalizedInvoice = invoiceRepository.findById(testInvoiceId).orElseThrow();
        assertThat(finalizedInvoice.getDocumentStatus()).isEqualTo(DocumentStatus.ISSUED);
        assertThat(finalizedInvoice.getStorageProvider()).isEqualTo("MOCK");
        assertThat(finalizedInvoice.getInvoiceNumber()).isEqualTo("MOCK-00000001");
    }

    @Test
    @DisplayName("finalizeReconcileOutcome - with MISA provider does not overwrite storage_provider")
    void finalizeReconcileOutcome_withMisaProvider_doesNotOverwriteStorageProvider() {
        transactionTemplate.execute(status -> {
            Invoice inv = invoiceRepository.findById(testInvoiceId).orElseThrow();
            inv.setDocumentStatus(DocumentStatus.ISSUANCE_UNKNOWN);
            inv.setStorageProvider(null);
            return invoiceRepository.save(inv);
        });

        InvoiceOutbox reconcileEvent = transactionTemplate.execute(status -> {
            Invoice inv = invoiceRepository.findById(testInvoiceId).orElseThrow();
            InvoiceOutbox event = InvoiceOutbox.builder()
                    .invoice(inv)
                    .eventType("RECONCILE_STATUS")
                    .status(OutboxStatus.PENDING)
                    .payload("{\"invoiceId\":" + testInvoiceId + "}")
                    .eventKey("RECONCILE_INVOICE_" + testInvoiceId + "_MISA")
                    .availableAt(LocalDateTime.now())
                    .build();
            return invoiceOutboxRepository.save(event);
        });

        List<InvoiceOutbox> claimed = invoiceOutboxService.claimEvents("worker-reconcile-misa", Duration.ofMinutes(5), 10);
        InvoiceOutbox activeClaim = claimed.stream()
                .filter(e -> e.getId().equals(reconcileEvent.getId()))
                .findFirst()
                .orElseThrow();

        ProviderInquiryRequest request = invoiceOutboxService.prepareReconcileDispatch(activeClaim.getId(), activeClaim.getLeaseToken());

        ProviderInquiryResult result = ProviderInquiryResult.builder()
                .found(true)
                .isIssued(true)
                .status("ISSUED")
                .invoiceNo("MISA-00000001")
                .invoiceSeries("1C26TAA")
                .taxAuthorityCode("MISA-TAX-001")
                .transactionId("MISA-TXN-001")
                .attemptOutcome(AttemptOutcome.SUCCESS)
                .refId(request.getRefId())
                .build();

        invoiceOutboxService.finalizeReconcileOutcome(
                activeClaim.getId(),
                activeClaim.getLeaseToken(),
                "MISA",
                request,
                result,
                10,
                Duration.ofSeconds(10)
        );

        Invoice finalizedInvoice = invoiceRepository.findById(testInvoiceId).orElseThrow();
        assertThat(finalizedInvoice.getDocumentStatus()).isEqualTo(DocumentStatus.ISSUED);
        assertThat(finalizedInvoice.getStorageProvider()).isNull();
        assertThat(finalizedInvoice.getInvoiceNumber()).isEqualTo("MISA-00000001");
    }
}
