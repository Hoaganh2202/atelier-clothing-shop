package com.shop.backend.order;

import com.shop.backend.address.AddressService;
import com.shop.backend.address.UserAddress;
import com.shop.backend.common.exception.BadRequestException;
import com.shop.backend.common.exception.OutOfStockException;
import com.shop.backend.common.model.DataSourceType;
import com.shop.backend.common.model.DiscountType;
import com.shop.backend.inventory.InventoryStockService;
import com.shop.backend.inventory.dto.OrderStockItem;
import com.shop.backend.order.dto.CheckoutItemRequest;
import com.shop.backend.order.dto.CheckoutItemResponse;
import com.shop.backend.order.dto.CheckoutRequest;
import com.shop.backend.order.dto.CheckoutResponse;
import com.shop.backend.settings.StoreSettingsReader;
import com.shop.backend.payment.Payment;
import com.shop.backend.payment.PaymentMethod;
import com.shop.backend.payment.PaymentStatus;
import com.shop.backend.product.ProductVariant;
import com.shop.backend.product.ProductVariantService;
import com.shop.backend.user.User;
import com.shop.backend.voucher.Voucher;
import com.shop.backend.voucher.VoucherService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class OrderCheckoutServiceImpl implements OrderCheckoutService {

    private static final BigDecimal FREE_SHIPPING_THRESHOLD = BigDecimal.valueOf(500000);
    private static final BigDecimal DEFAULT_SHIPPING_FEE = BigDecimal.valueOf(30000);
    private static final int MAX_IDEMPOTENCY_RETRY = 6;
    private static final long IDEMPOTENCY_RETRY_SLEEP_MS = 60L;

    private final OrderRepository orderRepository;
    private final AddressService addressService;
    private final StoreSettingsReader storeSettingsReader;
    private final ProductVariantService productVariantService;
    private final VoucherService voucherService;
    private final EntityManager entityManager;
    private final TransactionTemplate transactionTemplate;
    private final InventoryStockService inventoryStockService;

    public OrderCheckoutServiceImpl(
            OrderRepository orderRepository,
            AddressService addressService,
            ProductVariantService productVariantService,
            VoucherService voucherService,
            EntityManager entityManager,
            PlatformTransactionManager transactionManager,
            InventoryStockService inventoryStockService
    ) {
        this(orderRepository, addressService, null, productVariantService, voucherService, entityManager, transactionManager, inventoryStockService);
    }

    @Autowired
    public OrderCheckoutServiceImpl(
            OrderRepository orderRepository,
            AddressService addressService,
            StoreSettingsReader storeSettingsReader,
            ProductVariantService productVariantService,
            VoucherService voucherService,
            EntityManager entityManager,
            PlatformTransactionManager transactionManager,
            InventoryStockService inventoryStockService
    ) {
        this.orderRepository = orderRepository;
        this.addressService = addressService;
        this.storeSettingsReader = storeSettingsReader;
        this.productVariantService = productVariantService;
        this.voucherService = voucherService;
        this.entityManager = entityManager;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.inventoryStockService = inventoryStockService;
    }

    @Override
    public CheckoutResponse checkout(Long userId, CheckoutRequest request) {
        String cleanIdempotencyKey = normalizeText(request.getIdempotencyKey());
        if (cleanIdempotencyKey != null) {
            Optional<CheckoutResponse> existingOrder = findExistingCheckoutResponse(userId, cleanIdempotencyKey);
            if (existingOrder.isPresent()) {
                return existingOrder.get();
            }
        }

        try {
            return transactionTemplate.execute(status -> executeCheckout(userId, request, cleanIdempotencyKey));
        } catch (DataIntegrityViolationException ex) {
            if (cleanIdempotencyKey != null && isIdempotencyKeyViolation(ex)) {
                for (int attempt = 0; attempt < MAX_IDEMPOTENCY_RETRY; attempt++) {
                    Optional<CheckoutResponse> existingOrder = findExistingCheckoutResponse(userId, cleanIdempotencyKey);
                    if (existingOrder.isPresent()) {
                        return existingOrder.get();
                    }
                    try {
                        Thread.sleep(IDEMPOTENCY_RETRY_SLEEP_MS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            throw ex;
        }
    }

    private Optional<CheckoutResponse> findExistingCheckoutResponse(Long userId, String idempotencyKey) {
        return transactionTemplate.execute(status ->
                orderRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                        .map(this::toCheckoutResponse)
        );
    }

    private CheckoutResponse executeCheckout(Long userId, CheckoutRequest request, String cleanIdempotencyKey) {
        if (request.getPaymentMethod() != PaymentMethod.COD) {
            throw new BadRequestException("Phương thức thanh toán hiện tại chỉ hỗ trợ COD (Thanh toán khi nhận hàng)");
        }

        UserAddress address = addressService.getUserAddress(userId, request.getAddressId());

        Map<Long, Integer> consolidatedItems = new TreeMap<>();
        for (CheckoutItemRequest item : request.getItems()) {
            if (item.getQuantity() == null || item.getQuantity() < 1) {
                throw new BadRequestException("Số lượng sản phẩm phải từ 1 trở lên");
            }
            consolidatedItems.merge(item.getVariantId(), item.getQuantity(), Integer::sum);
        }

        BigDecimal subtotal = BigDecimal.ZERO;
        List<OrderDetail> details = new ArrayList<>();

        for (Map.Entry<Long, Integer> entry : consolidatedItems.entrySet()) {
            Long variantId = entry.getKey();
            Integer quantity = entry.getValue();

            ProductVariant variant = productVariantService.getVariantEntity(variantId);
            if (!Boolean.TRUE.equals(variant.getProduct().getIsActive())) {
                throw new BadRequestException("Sản phẩm '" + variant.getProduct().getName() + "' hiện đã ngừng kinh doanh");
            }

            int affected = productVariantService.deductStock(variantId, quantity);
            if (affected == 0) {
                throw new OutOfStockException("Sản phẩm '" + variant.getProduct().getName() + "' ("
                        + variant.getSize() + "/" + variant.getColor() + ") không đủ số lượng trong kho");
            }

            BigDecimal unitPrice = variant.getPrice() != null ? variant.getPrice() : variant.getProduct().getBasePrice();
            BigDecimal itemTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
            subtotal = subtotal.add(itemTotal);

            OrderDetail detail = OrderDetail.builder()
                    .variant(variant)
                    .productName(variant.getProduct().getName())
                    .variantSize(variant.getSize() != null ? variant.getSize() : "N/A")
                    .variantColor(variant.getColor())
                    .quantity(quantity)
                    .unitPrice(unitPrice)
                    .discountAmount(BigDecimal.ZERO)
                    .build();
            details.add(detail);
        }

        BigDecimal freeShippingThreshold = (storeSettingsReader != null)
                ? storeSettingsReader.getFreeShippingThreshold()
                : FREE_SHIPPING_THRESHOLD;
        BigDecimal defaultShippingFee = (storeSettingsReader != null)
                ? storeSettingsReader.getDefaultShippingFee()
                : DEFAULT_SHIPPING_FEE;
        BigDecimal shippingFee = subtotal.compareTo(freeShippingThreshold) >= 0
                ? BigDecimal.ZERO
                : defaultShippingFee;

        Voucher voucher = null;
        BigDecimal discountAmount = BigDecimal.ZERO;

        String cleanVoucherCode = normalizeText(request.getVoucherCode());
        if (cleanVoucherCode != null) {
            voucher = voucherService.getVoucherByCodeForUpdate(cleanVoucherCode);
            discountAmount = validateAndCalculateDiscount(voucher, userId, subtotal);
        }

        BigDecimal totalAmount = subtotal.subtract(discountAmount).add(shippingFee);
        if (totalAmount.compareTo(BigDecimal.ZERO) < 0) {
            totalAmount = BigDecimal.ZERO;
        }

        User userRef = entityManager.getReference(User.class, userId);

        Order order = Order.builder()
                .user(userRef)
                .voucherId(voucher != null ? voucher.getId() : null)
                .status(OrderStatus.PENDING)
                .subtotal(subtotal)
                .discountAmount(discountAmount)
                .shippingFee(shippingFee)
                .totalAmount(totalAmount)
                .paymentMethod(PaymentMethod.COD)
                .receiverName(address.getReceiverName())
                .receiverPhone(address.getPhone())
                .addressLine(address.getAddress())
                .ward(address.getWard())
                .district(address.getDistrict())
                .province(address.getProvince())
                .note(normalizeText(request.getNote()))
                .salesChannel("website")
                .idempotencyKey(cleanIdempotencyKey)
                .dataSource(DataSourceType.WEBSITE)
                .createdAt(LocalDateTime.now())
                .build();

        for (OrderDetail detail : details) {
            order.addOrderDetail(detail);
        }

        Payment payment = Payment.builder()
                .method(PaymentMethod.COD)
                .amount(totalAmount)
                .status(PaymentStatus.PENDING)
                .build();
        order.addPayment(payment);

        Order savedOrder = orderRepository.saveAndFlush(order);

        List<OrderStockItem> stockItems = savedOrder.getOrderDetails().stream()
                .map(d -> new OrderStockItem(d.getId(), d.getVariant().getId(), d.getQuantity()))
                .toList();
        inventoryStockService.recordCheckoutDeduction(savedOrder.getId(), userId, stockItems);

        return toCheckoutResponse(savedOrder);
    }

    private boolean isIdempotencyKeyViolation(DataIntegrityViolationException ex) {
        Throwable cause = ex.getRootCause() != null ? ex.getRootCause() : ex.getCause();
        while (cause != null) {
            String msg = cause.getMessage();
            if (msg != null && (msg.contains("uq_orders_user_idempotency") || msg.toLowerCase().contains("duplicate"))) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private BigDecimal validateAndCalculateDiscount(Voucher voucher, Long userId, BigDecimal subtotal) {
        if (!Boolean.TRUE.equals(voucher.getIsActive())) {
            throw new BadRequestException("Mã giảm giá đã ngừng áp dụng");
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(voucher.getStartAt()) || now.isAfter(voucher.getEndAt())) {
            throw new BadRequestException("Mã giảm giá chưa đến hạn hoặc đã hết hạn");
        }

        if (voucher.getMinOrder() != null && subtotal.compareTo(voucher.getMinOrder()) < 0) {
            throw new BadRequestException("Đơn hàng chưa đạt giá trị tối thiểu để áp dụng mã giảm giá");
        }

        if (voucher.getUsageLimit() != null) {
            long usedCount = orderRepository.countActiveOrdersByVoucherId(voucher.getId());
            if (usedCount >= voucher.getUsageLimit()) {
                throw new BadRequestException("Mã giảm giá đã hết lượt sử dụng");
            }
        }

        if (voucher.getPerUserLimit() != null) {
            long userUsedCount = orderRepository.countActiveOrdersByUserIdAndVoucherId(userId, voucher.getId());
            if (userUsedCount >= voucher.getPerUserLimit()) {
                throw new BadRequestException("Bạn đã sử dụng hết lượt cho mã giảm giá này");
            }
        }

        BigDecimal discount;
        if (voucher.getDiscountType() == DiscountType.PERCENT) {
            discount = subtotal.multiply(voucher.getDiscountValue())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            if (voucher.getMaxDiscount() != null && discount.compareTo(voucher.getMaxDiscount()) > 0) {
                discount = voucher.getMaxDiscount();
            }
        } else {
            discount = voucher.getDiscountValue();
        }

        return discount.min(subtotal);
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private CheckoutResponse toCheckoutResponse(Order order) {
        Payment latestPayment = order.getPayments().isEmpty() ? null : order.getPayments().get(0);

        List<CheckoutItemResponse> itemResponses = order.getOrderDetails().stream()
                .map(detail -> {
                    BigDecimal unit = detail.getUnitPrice();
                    BigDecimal qty = BigDecimal.valueOf(detail.getQuantity());
                    BigDecimal itemTotal = unit.multiply(qty);
                    return CheckoutItemResponse.builder()
                            .orderDetailId(detail.getId())
                            .variantId(detail.getVariant() != null ? detail.getVariant().getId() : null)
                            .productName(detail.getProductName())
                            .variantSize(detail.getVariantSize())
                            .variantColor(detail.getVariantColor())
                            .quantity(detail.getQuantity())
                            .unitPrice(detail.getUnitPrice().longValue())
                            .discountAmount(detail.getDiscountAmount().longValue())
                            .totalPrice(itemTotal.longValue())
                            .build();
                })
                .toList();

        return CheckoutResponse.builder()
                .orderId(order.getId())
                .status(order.getStatus())
                .subtotal(order.getSubtotal().longValue())
                .discountAmount(order.getDiscountAmount().longValue())
                .shippingFee(order.getShippingFee().longValue())
                .totalAmount(order.getTotalAmount().longValue())
                .paymentMethod(order.getPaymentMethod())
                .paymentStatus(latestPayment != null ? latestPayment.getStatus() : PaymentStatus.PENDING)
                .receiverName(order.getReceiverName())
                .receiverPhone(order.getReceiverPhone())
                .addressLine(order.getAddressLine())
                .ward(order.getWard())
                .district(order.getDistrict())
                .province(order.getProvince())
                .note(order.getNote())
                .voucherCode(order.getVoucherId() != null ? null : null)
                .idempotencyKey(order.getIdempotencyKey())
                .createdAt(order.getCreatedAt())
                .items(itemResponses)
                .build();
    }
}
