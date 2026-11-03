package com.shop.backend.inventory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.shop.backend.common.exception.BadRequestException;
import com.shop.backend.common.exception.ConflictException;
import com.shop.backend.common.exception.ResourceNotFoundException;
import com.shop.backend.inventory.dto.OrderReturnReceiptItem;
import com.shop.backend.inventory.dto.OrderReturnReceiptResult;
import com.shop.backend.inventory.dto.OrderStockItem;
import com.shop.backend.inventory.dto.OrderStockRestoreResult;
import com.shop.backend.inventory.dto.ReturnItemCondition;
import com.shop.backend.inventory.dto.ReturnReceiptItemResult;
import com.shop.backend.order.Order;
import com.shop.backend.order.OrderDetail;
import com.shop.backend.product.ProductVariant;
import com.shop.backend.user.User;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class InventoryStockServiceImpl implements InventoryStockService {

    private static final Pattern RECEIPT_NUMBER_PATTERN = Pattern.compile("^[A-Z0-9_-]+$");
    private static final int MAX_RECEIPT_NUMBER_LENGTH = 40;
    private static final int MAX_NOTE_LENGTH = 500;
    private static final int RECEIPT_STRIPE_COUNT = 64;

    private final InventoryVariantRepository inventoryVariantRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final OrderReturnReceiptRepository orderReturnReceiptRepository;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;

    private final ReentrantLock[] receiptStripes = new ReentrantLock[RECEIPT_STRIPE_COUNT];

    {
        for (int i = 0; i < RECEIPT_STRIPE_COUNT; i++) {
            receiptStripes[i] = new ReentrantLock();
        }
    }

    private ReentrantLock getReceiptStripe(Long orderId, String normalizedReceiptNumber) {
        int hash = Objects.hash(orderId, normalizedReceiptNumber);
        int index = Math.floorMod(hash, RECEIPT_STRIPE_COUNT);
        return receiptStripes[index];
    }

    /**
     * Records stock deduction transactions for an order checkout.
     * If items list is null or empty, this operation is a no-op.
     *
     * @param orderId the order ID (must not be null)
     * @param actorUserId the user performing the checkout (optional)
     * @param items the list of order stock items to deduct
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void recordCheckoutDeduction(Long orderId, Long actorUserId, List<OrderStockItem> items) {
        validateItems(orderId, items, "trừ kho");
        if (items == null || items.isEmpty()) {
            return;
        }

        Map<Long, Integer> currentStockMap = new HashMap<>();
        Map<Long, Integer> totalDeductedMap = new HashMap<>();
        for (OrderStockItem item : items) {
            totalDeductedMap.merge(item.variantId(), item.quantity(), Integer::sum);
        }

        for (Long variantId : totalDeductedMap.keySet()) {
            Integer currentStock = inventoryVariantRepository.findStockById(variantId);
            if (currentStock == null) {
                throw new ResourceNotFoundException("Biến thể sản phẩm không tồn tại với ID: " + variantId);
            }
            currentStockMap.put(variantId, currentStock + totalDeductedMap.get(variantId));
        }

        for (OrderStockItem item : items) {
            int before = currentStockMap.get(item.variantId());
            int after = before - item.quantity();
            currentStockMap.put(item.variantId(), after);

            ProductVariant variantRef = entityManager.getReference(ProductVariant.class, item.variantId());
            Order orderRef = orderId != null ? entityManager.getReference(Order.class, orderId) : null;
            OrderDetail detailRef = item.orderDetailId() != null
                    ? entityManager.getReference(OrderDetail.class, item.orderDetailId())
                    : null;
            User actorRef = actorUserId != null ? entityManager.getReference(User.class, actorUserId) : null;

            InventoryTransaction tx = InventoryTransaction.builder()
                    .variant(variantRef)
                    .order(orderRef)
                    .orderDetail(detailRef)
                    .movementType(InventoryMovementType.CHECKOUT_DEDUCT)
                    .quantity(item.quantity())
                    .stockBefore(before)
                    .stockAfter(after)
                    .actorUser(actorRef)
                    .note("Trừ tồn kho khi đặt hàng")
                    .idempotencyKey("ORDER_CHECKOUT:" + item.orderDetailId())
                    .build();

            inventoryTransactionRepository.save(tx);
        }
    }

    /**
     * Restores variant stocks and records cancel restore transactions for a cancelled order.
     * If items list is null or empty, returns an empty result (no-op).
     *
     * @param orderId the order ID (must not be null)
     * @param actorUserId the user performing the cancellation (optional)
     * @param reason the cancellation reason note (optional)
     * @param items the list of order stock items to restore
     * @return the result summary of restored and skipped items
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public OrderStockRestoreResult restoreStockForCancelledOrder(
            Long orderId,
            Long actorUserId,
            String reason,
            List<OrderStockItem> items
    ) {
        validateItems(orderId, items, "hoàn kho");
        if (items == null || items.isEmpty()) {
            return new OrderStockRestoreResult(0, 0, Collections.emptyList());
        }

        List<OrderStockItem> sortedItems = items.stream()
                .sorted(Comparator.comparing(OrderStockItem::variantId))
                .toList();

        List<InventoryTransaction> checkoutLogs = inventoryTransactionRepository.findByOrderIdAndMovementType(
                orderId,
                InventoryMovementType.CHECKOUT_DEDUCT
        );

        Map<Long, InventoryTransaction> deductLogMap = checkoutLogs.stream()
                .filter(tx -> tx.getOrderDetail() != null)
                .collect(Collectors.toMap(tx -> tx.getOrderDetail().getId(), Function.identity(), (a, b) -> a));

        int restoredCount = 0;
        int skippedCount = 0;
        List<Long> restoredVariantIds = new ArrayList<>();

        for (OrderStockItem item : sortedItems) {
            InventoryTransaction deductLog = deductLogMap.get(item.orderDetailId());
            if (deductLog == null) {
                skippedCount++;
                continue;
            }

            if (item.quantity() == null || !item.quantity().equals(deductLog.getQuantity())) {
                throw new BadRequestException("Số lượng hoàn kho không khớp với dữ liệu trừ kho của chi tiết đơn hàng: "
                        + item.orderDetailId());
            }

            int affected = inventoryVariantRepository.restoreStock(item.variantId(), item.quantity());
            if (affected != 1) {
                throw new ResourceNotFoundException("Biến thể sản phẩm không tồn tại với ID: " + item.variantId());
            }

            Integer stockAfter = inventoryVariantRepository.findStockById(item.variantId());
            int stockBefore = stockAfter - item.quantity();

            ProductVariant variantRef = entityManager.getReference(ProductVariant.class, item.variantId());
            Order orderRef = orderId != null ? entityManager.getReference(Order.class, orderId) : null;
            OrderDetail detailRef = item.orderDetailId() != null
                    ? entityManager.getReference(OrderDetail.class, item.orderDetailId())
                    : null;
            User actorRef = actorUserId != null ? entityManager.getReference(User.class, actorUserId) : null;

            InventoryTransaction restoreTx = InventoryTransaction.builder()
                    .variant(variantRef)
                    .order(orderRef)
                    .orderDetail(detailRef)
                    .movementType(InventoryMovementType.CANCEL_RESTORE)
                    .quantity(item.quantity())
                    .stockBefore(stockBefore)
                    .stockAfter(stockAfter)
                    .actorUser(actorRef)
                    .note(reason != null ? reason : "Hoàn tồn kho khi hủy đơn hàng")
                    .idempotencyKey("ORDER_CANCEL:" + item.orderDetailId())
                    .build();

            inventoryTransactionRepository.save(restoreTx);

            restoredCount++;
            restoredVariantIds.add(item.variantId());
        }

        return new OrderStockRestoreResult(restoredCount, skippedCount, restoredVariantIds);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasCancellationRestoreForOrder(Long orderId) {
        if (orderId == null) {
            return false;
        }
        return inventoryTransactionRepository.existsByOrderIdAndMovementType(
                orderId,
                InventoryMovementType.CANCEL_RESTORE
        );
    }

    /**
     * Processes physical return receipt and restores inventory stock when applicable.
     * <p>
     * Concurrency & caller contract:
     * - Local in-memory concurrency control is provided via a fixed 64-stripe ReentrantLock pool
     *   to serialize concurrent duplicate receipt submissions on a single JVM node without memory leaks.
     * - For distributed multi-instance deployment (PR-C2), callers MUST acquire a pessimistic write lock
     *   on the parent Order before invoking this method to ensure global atomicity.
     * - Movement idempotency keys are strictly scoped to the auto-generated receipt ID:
     *   {@code RETURN_RECEIPT:{receiptId}:{orderDetailId}:{condition}}.
     */
    @Override
    public OrderReturnReceiptResult processReturnReceipt(
            Long orderId,
            Long actorUserId,
            String receiptNumber,
            String note,
            List<OrderReturnReceiptItem> items
    ) {
        if (orderId == null) {
            throw new BadRequestException("Mã đơn hàng không được để trống");
        }
        if (receiptNumber == null || receiptNumber.isBlank()) {
            throw new BadRequestException("Mã biên bản tiếp nhận không được để trống");
        }
        String normalizedReceiptNumber = receiptNumber.trim().toUpperCase(Locale.ROOT);
        if (normalizedReceiptNumber.length() > MAX_RECEIPT_NUMBER_LENGTH
                || !RECEIPT_NUMBER_PATTERN.matcher(normalizedReceiptNumber).matches()) {
            throw new BadRequestException("Mã biên bản tiếp nhận không hợp lệ. Chỉ chấp nhận chữ cái, số, dấu '-' hoặc '_', tối đa "
                    + MAX_RECEIPT_NUMBER_LENGTH + " ký tự");
        }

        ReentrantLock lock = getReceiptStripe(orderId, normalizedReceiptNumber);
        lock.lock();
        try {
            TransactionTemplate tt = new TransactionTemplate(transactionManager);
            tt.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
            return tt.execute(status -> executeProcessReturnReceiptInTransaction(
                    orderId, actorUserId, normalizedReceiptNumber, note, items
            ));
        } finally {
            lock.unlock();
        }
    }

    private OrderReturnReceiptResult executeProcessReturnReceiptInTransaction(
            Long orderId,
            Long actorUserId,
            String normalizedReceiptNumber,
            String note,
            List<OrderReturnReceiptItem> items
    ) {
        if (note != null && note.length() > MAX_NOTE_LENGTH) {
            throw new BadRequestException("Ghi chú tiếp nhận tối đa " + MAX_NOTE_LENGTH + " ký tự");
        }
        if (items == null || items.isEmpty()) {
            throw new BadRequestException("Danh sách sản phẩm tiếp nhận không được để trống");
        }

        // Validate items and detect duplicate (orderDetailId, condition)
        Set<String> seenDetailConditionKeys = new HashSet<>();
        for (OrderReturnReceiptItem item : items) {
            if (item == null) {
                throw new BadRequestException("Chi tiết sản phẩm tiếp nhận không được để trống");
            }
            if (item.orderDetailId() == null) {
                throw new BadRequestException("Mã chi tiết đơn hàng không được để trống");
            }
            if (item.quantity() == null || item.quantity() <= 0) {
                throw new BadRequestException("Số lượng tiếp nhận phải lớn hơn 0");
            }
            if (item.condition() == null) {
                throw new BadRequestException("Tình trạng sản phẩm tiếp nhận không được để trống");
            }
            if (item.condition() == ReturnItemCondition.DAMAGED
                    && (item.reason() == null || item.reason().isBlank())) {
                throw new BadRequestException("Lý do tiếp nhận hàng hỏng/lỗi không được để trống cho chi tiết đơn hàng #"
                        + item.orderDetailId());
            }
            if (item.reason() != null && item.reason().length() > MAX_NOTE_LENGTH) {
                throw new BadRequestException("Lý do tiếp nhận tối đa " + MAX_NOTE_LENGTH + " ký tự");
            }
            String detailConditionKey = item.orderDetailId() + ":" + item.condition().name();
            if (!seenDetailConditionKeys.add(detailConditionKey)) {
                throw new BadRequestException("Chi tiết đơn hàng #" + item.orderDetailId()
                        + " với tình trạng " + item.condition() + " bị lặp lại trong cùng biên bản tiếp nhận");
            }
        }

        // Sort items deterministically: orderDetailId ASC, condition ASC
        List<OrderReturnReceiptItem> sortedItems = items.stream()
                .sorted(Comparator.comparing(OrderReturnReceiptItem::orderDetailId)
                        .thenComparing(i -> i.condition().name()))
                .toList();

        // Compute deterministic canonical JSON SHA-256 payload hash
        String payloadHash = computePayloadHash(orderId, normalizedReceiptNumber, note, sortedItems);

        // Check receipt-level idempotency
        Optional<OrderReturnReceipt> existingReceiptOpt = orderReturnReceiptRepository
                .findByOrderIdAndReceiptNumber(orderId, normalizedReceiptNumber);

        if (existingReceiptOpt.isPresent()) {
            OrderReturnReceipt existingReceipt = existingReceiptOpt.get();
            if (!existingReceipt.getPayloadHash().equals(payloadHash)) {
                throw new ConflictException("Biên bản tiếp nhận #" + normalizedReceiptNumber
                        + " đã tồn tại cho đơn hàng này với nội dung khác");
            }
            // Idempotent replay: recover results from saved movements without double-restocking
            return buildReplayResult(orderId, normalizedReceiptNumber, existingReceipt);
        }

        // Validate checkout deduction records (source of truth) and ceiling limit
        List<InventoryTransaction> checkoutLogs = inventoryTransactionRepository.findByOrderIdAndMovementType(
                orderId,
                InventoryMovementType.CHECKOUT_DEDUCT
        );
        Map<Long, InventoryTransaction> deductLogMap = checkoutLogs.stream()
                .filter(tx -> tx.getOrderDetail() != null)
                .collect(Collectors.toMap(tx -> tx.getOrderDetail().getId(), Function.identity(), (a, b) -> a));

        Map<Long, Integer> requestQtyByDetail = sortedItems.stream().collect(
                Collectors.groupingBy(OrderReturnReceiptItem::orderDetailId, Collectors.summingInt(OrderReturnReceiptItem::quantity))
        );

        for (Map.Entry<Long, Integer> entry : requestQtyByDetail.entrySet()) {
            Long detailId = entry.getKey();
            int requestQty = entry.getValue();

            InventoryTransaction deductLog = deductLogMap.get(detailId);
            if (deductLog == null) {
                throw new BadRequestException("Không tìm thấy dữ liệu trừ kho ban đầu cho chi tiết đơn hàng #" + detailId);
            }
            if (deductLog.getOrder() == null || !deductLog.getOrder().getId().equals(orderId)) {
                throw new BadRequestException("Dữ liệu trừ kho của chi tiết đơn hàng #" + detailId + " không thuộc về đơn hàng #" + orderId);
            }

            int previouslyReceived = inventoryTransactionRepository.getPreviouslyReceivedQuantity(detailId);
            int purchasedQty = deductLog.getQuantity();

            if (previouslyReceived + requestQty > purchasedQty) {
                throw new BadRequestException("Tổng số lượng tiếp nhận (" + (previouslyReceived + requestQty)
                        + ") vượt quá số lượng đã mua (" + purchasedQty + ") của chi tiết đơn hàng #" + detailId);
            }
        }

        // Pre-calculate totals for receipt header
        int totalRestocked = 0;
        int totalDiscarded = 0;
        for (OrderReturnReceiptItem item : sortedItems) {
            if (item.condition() == ReturnItemCondition.RESTOCKABLE) {
                totalRestocked += item.quantity();
            } else {
                totalDiscarded += item.quantity();
            }
        }

        // Save return receipt header first and flush to obtain the generated receiptId
        OrderReturnReceipt receipt = OrderReturnReceipt.builder()
                .receiptNumber(normalizedReceiptNumber)
                .order(entityManager.getReference(Order.class, orderId))
                .actorUser(actorUserId != null ? entityManager.getReference(User.class, actorUserId) : null)
                .payloadHash(payloadHash)
                .totalReceivedQuantity(totalRestocked + totalDiscarded)
                .restockedQuantity(totalRestocked)
                .discardedQuantity(totalDiscarded)
                .note(note)
                .build();
        receipt = orderReturnReceiptRepository.saveAndFlush(receipt);
        Long receiptId = receipt.getId();

        // Process each return item and record scoped inventory movements
        List<ReturnReceiptItemResult> itemResults = new ArrayList<>();

        for (OrderReturnReceiptItem item : sortedItems) {
            InventoryTransaction deductLog = deductLogMap.get(item.orderDetailId());
            Long variantId = deductLog.getVariant().getId();

            int stockBefore;
            int stockAfter;
            InventoryMovementType movementType;
            String movementNote;

            if (item.condition() == ReturnItemCondition.RESTOCKABLE) {
                int affected = inventoryVariantRepository.restoreStock(variantId, item.quantity());
                if (affected != 1) {
                    throw new ResourceNotFoundException("Biến thể sản phẩm không tồn tại với ID: " + variantId);
                }
                stockAfter = inventoryVariantRepository.findStockById(variantId);
                stockBefore = stockAfter - item.quantity();
                movementType = InventoryMovementType.RETURN_RESTOCK;
                movementNote = (item.reason() != null && !item.reason().isBlank())
                        ? item.reason().trim()
                        : ("Nhập kho hàng trả về - Biên bản #" + normalizedReceiptNumber);
            } else {
                Integer currentStock = inventoryVariantRepository.findStockById(variantId);
                if (currentStock == null) {
                    throw new ResourceNotFoundException("Biến thể sản phẩm không tồn tại với ID: " + variantId);
                }
                stockBefore = currentStock;
                stockAfter = currentStock;
                movementType = InventoryMovementType.RETURN_DISCARD;
                movementNote = item.reason().trim();
            }

            String movementKey = "RETURN_RECEIPT:" + receiptId + ":" + item.orderDetailId() + ":" + item.condition().name();

            ProductVariant variantRef = entityManager.getReference(ProductVariant.class, variantId);
            Order orderRef = entityManager.getReference(Order.class, orderId);
            OrderDetail detailRef = entityManager.getReference(OrderDetail.class, item.orderDetailId());
            User actorRef = actorUserId != null ? entityManager.getReference(User.class, actorUserId) : null;

            InventoryTransaction tx = InventoryTransaction.builder()
                    .variant(variantRef)
                    .order(orderRef)
                    .orderDetail(detailRef)
                    .movementType(movementType)
                    .quantity(item.quantity())
                    .stockBefore(stockBefore)
                    .stockAfter(stockAfter)
                    .actorUser(actorRef)
                    .note(movementNote)
                    .idempotencyKey(movementKey)
                    .build();

            inventoryTransactionRepository.save(tx);

            itemResults.add(new ReturnReceiptItemResult(
                    item.orderDetailId(),
                    variantId,
                    item.quantity(),
                    item.condition(),
                    stockBefore,
                    stockAfter,
                    movementKey
            ));
        }

        return new OrderReturnReceiptResult(
                orderId,
                normalizedReceiptNumber,
                totalRestocked + totalDiscarded,
                totalRestocked,
                totalDiscarded,
                itemResults
        );
    }

    private OrderReturnReceiptResult buildReplayResult(
            Long orderId,
            String normalizedReceiptNumber,
            OrderReturnReceipt existingReceipt
    ) {
        String prefix = "RETURN_RECEIPT:" + existingReceipt.getId() + ":";
        List<InventoryTransaction> savedTxs = inventoryTransactionRepository.findByIdempotencyKeyStartingWith(prefix);

        List<ReturnReceiptItemResult> itemResults = savedTxs.stream()
                .sorted(Comparator.comparing((InventoryTransaction tx) -> tx.getOrderDetail().getId())
                        .thenComparing(tx -> tx.getMovementType().name()))
                .map(tx -> new ReturnReceiptItemResult(
                        tx.getOrderDetail().getId(),
                        tx.getVariant().getId(),
                        tx.getQuantity(),
                        tx.getMovementType() == InventoryMovementType.RETURN_RESTOCK
                                ? ReturnItemCondition.RESTOCKABLE
                                : ReturnItemCondition.DAMAGED,
                        tx.getStockBefore(),
                        tx.getStockAfter(),
                        tx.getIdempotencyKey()
                ))
                .toList();

        return new OrderReturnReceiptResult(
                orderId,
                normalizedReceiptNumber,
                existingReceipt.getTotalReceivedQuantity(),
                existingReceipt.getRestockedQuantity(),
                existingReceipt.getDiscardedQuantity(),
                itemResults
        );
    }

    private String computePayloadHash(
            Long orderId,
            String normalizedReceiptNumber,
            String note,
            List<OrderReturnReceiptItem> sortedItems
    ) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("orderId", orderId);
            root.put("receiptNumber", normalizedReceiptNumber);
            root.put("note", note != null ? note : "");

            ArrayNode itemsArray = root.putArray("items");
            for (OrderReturnReceiptItem item : sortedItems) {
                ObjectNode itemNode = itemsArray.addObject();
                itemNode.put("orderDetailId", item.orderDetailId());
                itemNode.put("quantity", item.quantity());
                itemNode.put("condition", item.condition().name());
                itemNode.put("reason", item.reason() != null ? item.reason() : "");
            }

            String canonicalJson = objectMapper.writeValueAsString(root);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonicalJson.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Không thể tính toán payload hash cho biên bản tiếp nhận", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public OrderReturnReceipt getReturnReceiptEntity(Long id) {
        return orderReturnReceiptRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy biên bản hoàn hàng với ID: " + id));
    }

    private void validateItems(Long orderId, List<OrderStockItem> items, String actionName) {
        if (orderId == null) {
            throw new BadRequestException("Mã đơn hàng không được để trống");
        }
        if (items == null || items.isEmpty()) {
            return;
        }
        for (OrderStockItem item : items) {
            if (item == null) {
                throw new BadRequestException("Chi tiết sản phẩm " + actionName + " không được để trống");
            }
            if (item.orderDetailId() == null) {
                throw new BadRequestException("Mã chi tiết đơn hàng không được để trống");
            }
            if (item.variantId() == null) {
                throw new BadRequestException("Mã biến thể không được để trống");
            }
            if (item.quantity() == null || item.quantity() <= 0) {
                throw new BadRequestException("Số lượng phải lớn hơn 0");
            }
        }
    }
}
