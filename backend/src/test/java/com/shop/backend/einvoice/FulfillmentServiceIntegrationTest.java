package com.shop.backend.einvoice;

import com.shop.backend.common.model.DataSourceType;
import com.shop.backend.einvoice.dto.CreateFulfillmentEventCommand;
import com.shop.backend.einvoice.dto.CreateFulfillmentItemCommand;
import com.shop.backend.einvoice.exception.FulfillmentValidationException;
import com.shop.backend.einvoice.model.FulfillmentEvent;
import com.shop.backend.einvoice.model.FulfillmentEventItem;
import com.shop.backend.einvoice.model.FulfillmentStatus;
import com.shop.backend.einvoice.model.ProofSource;
import com.shop.backend.einvoice.repository.FulfillmentEventItemRepository;
import com.shop.backend.einvoice.repository.FulfillmentEventRepository;
import com.shop.backend.einvoice.service.FulfillmentService;
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
public class FulfillmentServiceIntegrationTest {

    private static final String TEST_PREFIX = "einv-ful-";

    @Autowired
    private FulfillmentService fulfillmentService;

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
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;
    private User testUser;
    private ProductVariant testVariant;
    private Order testOrder;
    private OrderDetail testDetail;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        cleanTestData();

        Role customerRole = roleRepository.findByName("customer")
                .orElseGet(() -> roleRepository.save(new Role(null, "customer")));

        testUser = userRepository.save(User.builder()
                .name(TEST_PREFIX + "Customer")
                .email(TEST_PREFIX + "cust-" + System.nanoTime() + "@shop.com")
                .passwordHash("hashed-pw")
                .status(UserStatus.ACTIVE)
                .role(customerRole)
                .dataSource(DataSourceType.WEBSITE)
                .build());

        Category cat = categoryRepository.save(Category.builder()
                .name(TEST_PREFIX + "Category")
                .slug(TEST_PREFIX + "cat-" + System.nanoTime())
                .isActive(true)
                .build());

        Product prod = productRepository.save(Product.builder()
                .category(cat)
                .name(TEST_PREFIX + "Product")
                .slug(TEST_PREFIX + "prod-" + System.nanoTime())
                .basePrice(new BigDecimal("200000.00"))
                .isActive(true)
                .dataSource(DataSourceType.WEBSITE)
                .build());

        testVariant = productVariantRepository.save(ProductVariant.builder()
                .product(prod)
                .sku(TEST_PREFIX + "SKU-" + System.nanoTime())
                .size("L")
                .color("Xanh")
                .stock(100)
                .price(new BigDecimal("200000.00"))
                .build());

        testOrder = transactionTemplate.execute(status -> {
            Order order = Order.builder()
                    .user(testUser)
                    .status(OrderStatus.SHIPPING)
                    .subtotal(new BigDecimal("1000000.00"))
                    .discountAmount(BigDecimal.ZERO)
                    .shippingFee(new BigDecimal("30000.00"))
                    .totalAmount(new BigDecimal("1030000.00"))
                    .paymentMethod(PaymentMethod.COD)
                    .receiverName(TEST_PREFIX + "Receiver")
                    .receiverPhone("0987654321")
                    .province("Hà Nội")
                    .dataSource(DataSourceType.WEBSITE)
                    .createdAt(LocalDateTime.now())
                    .build();

            OrderDetail d = OrderDetail.builder()
                    .variant(testVariant)
                    .productName(TEST_PREFIX + "Áo polo")
                    .variantSize("L")
                    .variantColor("Xanh")
                    .quantity(5)
                    .unitPrice(new BigDecimal("200000.00"))
                    .discountAmount(BigDecimal.ZERO)
                    .build();
            order.addOrderDetail(d);
            return orderRepository.save(order);
        });

        testDetail = testOrder.getOrderDetails().get(0);
    }

    @AfterEach
    void tearDown() {
        cleanTestData();
    }

    private void cleanTestData() {
        transactionTemplate.execute(status -> {
            entityManager.createQuery("DELETE FROM InvoiceItem ii WHERE ii.itemName LIKE :prefix")
                    .setParameter("prefix", TEST_PREFIX + "%")
                    .executeUpdate();
            entityManager.createQuery("DELETE FROM InvoiceBalanceAdjustment iba WHERE iba.originalInvoice.idempotencyKey LIKE :prefix")
                    .setParameter("prefix", TEST_PREFIX + "%")
                    .executeUpdate();
            entityManager.createQuery("DELETE FROM InvoiceOutbox io WHERE io.eventKey LIKE :prefix")
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
            return null;
        });
    }

    @Test
    @DisplayName("1. Ghi nhận giao hàng thành công trọn vẹn (Full fulfillment)")
    void recordFulfillment_fullQuantity_success() {
        CreateFulfillmentEventCommand cmd = CreateFulfillmentEventCommand.builder()
                .orderId(testOrder.getId())
                .fulfillmentCode(TEST_PREFIX + "FUL-001")
                .carrierName("GHN")
                .trackingNumber("GHN-TRACK-001")
                .deliveredAt(LocalDateTime.of(2026, 10, 31, 10, 0))
                .proofSource(ProofSource.CARRIER_WEBHOOK)
                .proofReference("SIG_HASH_001")
                .items(List.of(
                        CreateFulfillmentItemCommand.builder()
                                .orderDetailId(testDetail.getId())
                                .deliveredQuantity(5)
                                .unitPriceSnapshot(new BigDecimal("200000.00"))
                                .discountAllocated(BigDecimal.ZERO)
                                .build()
                ))
                .build();

        FulfillmentEvent event = fulfillmentService.recordFulfillment(cmd);

        assertThat(event.getId()).isNotNull();
        assertThat(event.getStatus()).isEqualTo(FulfillmentStatus.DELIVERED);

        List<FulfillmentEventItem> items = fulfillmentEventItemRepository.findByFulfillmentEventId(event.getId());
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getDeliveredQuantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("2. Giao hàng từng phần (Partial fulfillment): 2 đợt giao tổng bằng đúng số lượng đã đặt")
    void recordFulfillment_partialFulfillment_success() {
        // Đợt 1: giao 2 cái
        CreateFulfillmentEventCommand trip1 = CreateFulfillmentEventCommand.builder()
                .orderId(testOrder.getId())
                .fulfillmentCode(TEST_PREFIX + "FUL-TRIP-1")
                .carrierName("GHTK")
                .trackingNumber("TRIP1-TRACK")
                .deliveredAt(LocalDateTime.of(2026, 10, 30, 9, 0))
                .proofSource(ProofSource.CARRIER_WEBHOOK)
                .items(List.of(
                        CreateFulfillmentItemCommand.builder()
                                .orderDetailId(testDetail.getId())
                                .deliveredQuantity(2)
                                .unitPriceSnapshot(new BigDecimal("200000.00"))
                                .build()
                ))
                .build();
        fulfillmentService.recordFulfillment(trip1);

        // Đợt 2: giao tiếp 3 cái (tổng = 5)
        CreateFulfillmentEventCommand trip2 = CreateFulfillmentEventCommand.builder()
                .orderId(testOrder.getId())
                .fulfillmentCode(TEST_PREFIX + "FUL-TRIP-2")
                .carrierName("GHTK")
                .trackingNumber("TRIP2-TRACK")
                .deliveredAt(LocalDateTime.of(2026, 10, 31, 14, 0))
                .proofSource(ProofSource.CARRIER_WEBHOOK)
                .items(List.of(
                        CreateFulfillmentItemCommand.builder()
                                .orderDetailId(testDetail.getId())
                                .deliveredQuantity(3)
                                .unitPriceSnapshot(new BigDecimal("200000.00"))
                                .build()
                ))
                .build();
        fulfillmentService.recordFulfillment(trip2);

        int totalDelivered = fulfillmentEventItemRepository.getDeliveredQuantityForOrderDetail(
                testDetail.getId(), FulfillmentStatus.DELIVERED);
        assertThat(totalDelivered).isEqualTo(5);
    }

    @Test
    @DisplayName("3. Từ chối giao vượt quá số lượng đặt hàng: ném FulfillmentValidationException")
    void recordFulfillment_overDelivery_throwsException() {
        // Đợt 1: giao 3 cái
        CreateFulfillmentEventCommand trip1 = CreateFulfillmentEventCommand.builder()
                .orderId(testOrder.getId())
                .fulfillmentCode(TEST_PREFIX + "FUL-OVER-1")
                .deliveredAt(LocalDateTime.now())
                .proofSource(ProofSource.MANUAL_SIGNATURE)
                .items(List.of(
                        CreateFulfillmentItemCommand.builder()
                                .orderDetailId(testDetail.getId())
                                .deliveredQuantity(3)
                                .unitPriceSnapshot(new BigDecimal("200000.00"))
                                .build()
                ))
                .build();
        fulfillmentService.recordFulfillment(trip1);

        // Đợt 2: cố tình giao 3 cái nữa (tổng 6 > 5 đã đặt)
        CreateFulfillmentEventCommand trip2 = CreateFulfillmentEventCommand.builder()
                .orderId(testOrder.getId())
                .fulfillmentCode(TEST_PREFIX + "FUL-OVER-2")
                .deliveredAt(LocalDateTime.now())
                .proofSource(ProofSource.MANUAL_SIGNATURE)
                .items(List.of(
                        CreateFulfillmentItemCommand.builder()
                                .orderDetailId(testDetail.getId())
                                .deliveredQuantity(3)
                                .unitPriceSnapshot(new BigDecimal("200000.00"))
                                .build()
                ))
                .build();

        assertThatThrownBy(() -> fulfillmentService.recordFulfillment(trip2))
                .isInstanceOf(FulfillmentValidationException.class)
                .hasMessageContaining("exceeds ordered quantity");
    }

    @Test
    @DisplayName("4. Từ chối trùng mã fulfillmentCode: ném FulfillmentValidationException")
    void recordFulfillment_duplicateCode_throwsException() {
        String code = TEST_PREFIX + "DUP-CODE";
        CreateFulfillmentEventCommand trip1 = CreateFulfillmentEventCommand.builder()
                .orderId(testOrder.getId())
                .fulfillmentCode(code)
                .deliveredAt(LocalDateTime.now())
                .proofSource(ProofSource.MANUAL_SIGNATURE)
                .items(List.of(
                        CreateFulfillmentItemCommand.builder()
                                .orderDetailId(testDetail.getId())
                                .deliveredQuantity(1)
                                .unitPriceSnapshot(new BigDecimal("200000.00"))
                                .build()
                ))
                .build();
        fulfillmentService.recordFulfillment(trip1);

        CreateFulfillmentEventCommand trip2 = CreateFulfillmentEventCommand.builder()
                .orderId(testOrder.getId())
                .fulfillmentCode(code)
                .deliveredAt(LocalDateTime.now())
                .proofSource(ProofSource.MANUAL_SIGNATURE)
                .items(List.of(
                        CreateFulfillmentItemCommand.builder()
                                .orderDetailId(testDetail.getId())
                                .deliveredQuantity(1)
                                .unitPriceSnapshot(new BigDecimal("200000.00"))
                                .build()
                ))
                .build();

        assertThatThrownBy(() -> fulfillmentService.recordFulfillment(trip2))
                .isInstanceOf(FulfillmentValidationException.class)
                .hasMessageContaining("already exists");
    }
}
