package com.shop.backend.einvoice.service.impl;

import com.shop.backend.common.exception.ResourceNotFoundException;
import com.shop.backend.einvoice.dto.CreateFulfillmentEventCommand;
import com.shop.backend.einvoice.dto.CreateFulfillmentItemCommand;
import com.shop.backend.einvoice.exception.FulfillmentValidationException;
import com.shop.backend.einvoice.model.FulfillmentEvent;
import com.shop.backend.einvoice.model.FulfillmentEventItem;
import com.shop.backend.einvoice.model.FulfillmentStatus;
import com.shop.backend.einvoice.repository.FulfillmentEventItemRepository;
import com.shop.backend.einvoice.repository.FulfillmentEventRepository;
import com.shop.backend.einvoice.service.FulfillmentService;
import com.shop.backend.order.Order;
import com.shop.backend.order.OrderDetail;
import com.shop.backend.order.OrderDetailRepository;
import com.shop.backend.order.OrderRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FulfillmentServiceImpl implements FulfillmentService {

    private final FulfillmentEventRepository fulfillmentEventRepository;
    private final FulfillmentEventItemRepository fulfillmentEventItemRepository;
    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;

    @Override
    @Transactional
    public FulfillmentEvent recordFulfillment(CreateFulfillmentEventCommand command) {
        validateCommand(command);

        if (fulfillmentEventRepository.existsByFulfillmentCode(command.getFulfillmentCode())) {
            throw new FulfillmentValidationException(
                    "Fulfillment code already exists: " + command.getFulfillmentCode());
        }

        Order order = orderRepository.findById(command.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng với ID: " + command.getOrderId()));

        FulfillmentEvent event = FulfillmentEvent.builder()
                .order(order)
                .fulfillmentCode(command.getFulfillmentCode())
                .carrierName(command.getCarrierName())
                .trackingNumber(command.getTrackingNumber())
                .status(FulfillmentStatus.DELIVERED)
                .deliveredAt(command.getDeliveredAt())
                .proofSource(command.getProofSource())
                .proofReference(command.getProofReference())
                .note(command.getNote())
                .build();

        FulfillmentEvent savedEvent = fulfillmentEventRepository.save(event);

        List<FulfillmentEventItem> eventItems = new ArrayList<>();
        for (CreateFulfillmentItemCommand itemCmd : command.getItems()) {
            OrderDetail detail = orderDetailRepository.findById(itemCmd.getOrderDetailId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chi tiết đơn hàng với ID: " + itemCmd.getOrderDetailId()));
            if (!detail.getOrder().getId().equals(order.getId())) {
                throw new FulfillmentValidationException(String.format(
                        "OrderDetail %d does not belong to Order %d", detail.getId(), order.getId()));
            }

            int alreadyDelivered = fulfillmentEventItemRepository.getDeliveredQuantityForOrderDetail(
                    detail.getId(), FulfillmentStatus.DELIVERED);

            int remainingAllowed = detail.getQuantity() - alreadyDelivered;
            if (itemCmd.getDeliveredQuantity() > remainingAllowed) {
                throw new FulfillmentValidationException(String.format(
                        "Cannot deliver quantity %d for OrderDetail %d: delivery quantity exceeds ordered quantity. Ordered: %d, Already Delivered: %d, Remaining: %d",
                        itemCmd.getDeliveredQuantity(), detail.getId(), detail.getQuantity(), alreadyDelivered, remainingAllowed));
            }

            FulfillmentEventItem eventItem = FulfillmentEventItem.builder()
                    .fulfillmentEvent(savedEvent)
                    .orderDetail(detail)
                    .deliveredQuantity(itemCmd.getDeliveredQuantity())
                    .unitPriceSnapshot(itemCmd.getUnitPriceSnapshot())
                    .discountAllocated(itemCmd.getDiscountAllocated() != null ? itemCmd.getDiscountAllocated() : java.math.BigDecimal.ZERO)
                    .build();

            eventItems.add(eventItem);
        }

        fulfillmentEventItemRepository.saveAll(eventItems);
        return savedEvent;
    }

    @Override
    @Transactional(readOnly = true)
    public FulfillmentEvent getFulfillmentEvent(Long id) {
        return fulfillmentEventRepository.findById(id)
                .orElseThrow(() -> new FulfillmentValidationException("Fulfillment event not found: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<FulfillmentEvent> getFulfillmentsForOrder(Long orderId) {
        return fulfillmentEventRepository.findByOrderId(orderId);
    }

    private void validateCommand(CreateFulfillmentEventCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        if (command.getOrderId() == null) {
            throw new IllegalArgumentException("orderId must not be null");
        }
        if (command.getFulfillmentCode() == null || command.getFulfillmentCode().isBlank()) {
            throw new IllegalArgumentException("fulfillmentCode must not be null or blank");
        }
        if (command.getDeliveredAt() == null) {
            throw new IllegalArgumentException("deliveredAt must not be null");
        }
        if (command.getProofSource() == null) {
            throw new IllegalArgumentException("proofSource must not be null");
        }
        if (command.getItems() == null || command.getItems().isEmpty()) {
            throw new IllegalArgumentException("items must not be null or empty");
        }
        for (CreateFulfillmentItemCommand item : command.getItems()) {
            if (item.getOrderDetailId() == null) {
                throw new IllegalArgumentException("orderDetailId must not be null");
            }
            if (item.getDeliveredQuantity() == null || item.getDeliveredQuantity() <= 0) {
                throw new IllegalArgumentException("deliveredQuantity must be greater than zero");
            }
            if (item.getUnitPriceSnapshot() == null || item.getUnitPriceSnapshot().compareTo(java.math.BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("unitPriceSnapshot must not be negative");
            }
        }
    }
}
