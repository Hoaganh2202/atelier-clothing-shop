package com.shop.backend.einvoice.service.impl;

import com.shop.backend.einvoice.dto.AdjustItemCommand;
import com.shop.backend.einvoice.dto.CalculatedInvoiceLineResult;
import com.shop.backend.einvoice.dto.CalculatedInvoiceResult;
import com.shop.backend.einvoice.dto.CreateAdjustmentInvoiceCommand;
import com.shop.backend.einvoice.dto.CreateReplacementInvoiceCommand;
import com.shop.backend.einvoice.dto.InvoiceCalculationCommand;
import com.shop.backend.einvoice.dto.InvoiceLineCalculationCommand;
import com.shop.backend.einvoice.exception.InvoiceBalanceExceededException;
import com.shop.backend.einvoice.exception.InvoiceIdempotencyException;
import com.shop.backend.einvoice.exception.InvoiceNotFoundException;
import com.shop.backend.einvoice.model.AdjustmentType;
import com.shop.backend.einvoice.model.BuyerInvoiceProfile;
import com.shop.backend.einvoice.model.CustomerType;
import com.shop.backend.einvoice.model.DocumentStatus;
import com.shop.backend.einvoice.model.FulfillmentEvent;
import com.shop.backend.einvoice.model.Invoice;
import com.shop.backend.einvoice.model.InvoiceBalanceAdjustment;
import com.shop.backend.einvoice.model.InvoiceItem;
import com.shop.backend.einvoice.model.InvoiceItemType;
import com.shop.backend.einvoice.model.InvoiceType;
import com.shop.backend.einvoice.model.PricingBasis;
import com.shop.backend.einvoice.model.SellerInvoiceProfile;
import com.shop.backend.einvoice.repository.BuyerInvoiceProfileRepository;
import com.shop.backend.einvoice.repository.FulfillmentEventItemRepository;
import com.shop.backend.einvoice.repository.InvoiceBalanceAdjustmentRepository;
import com.shop.backend.einvoice.repository.InvoiceItemRepository;
import com.shop.backend.einvoice.repository.InvoiceRepository;
import com.shop.backend.einvoice.repository.SellerInvoiceProfileRepository;
import com.shop.backend.einvoice.repository.TaxConfigurationRepository;
import com.shop.backend.einvoice.service.FulfillmentService;
import com.shop.backend.einvoice.service.InvoiceBalanceValidationService;
import com.shop.backend.einvoice.service.InvoiceDomainService;
import com.shop.backend.einvoice.service.InvoiceOutboxService;
import com.shop.backend.einvoice.service.TaxCalculationService;
import com.shop.backend.inventory.InventoryStockService;
import com.shop.backend.inventory.OrderReturnReceipt;
import com.shop.backend.common.exception.ResourceNotFoundException;
import com.shop.backend.order.Order;
import com.shop.backend.order.OrderDetail;
import com.shop.backend.order.OrderDetailRepository;
import com.shop.backend.order.OrderRepository;
import com.shop.backend.order.OrderStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InvoiceDomainServiceImpl implements InvoiceDomainService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceDomainServiceImpl.class);

    private final InvoiceRepository invoiceRepository;
    private final InvoiceItemRepository invoiceItemRepository;
    private final InvoiceBalanceAdjustmentRepository invoiceBalanceAdjustmentRepository;
    private final BuyerInvoiceProfileRepository buyerInvoiceProfileRepository;
    private final SellerInvoiceProfileRepository sellerInvoiceProfileRepository;
    private final FulfillmentEventItemRepository fulfillmentEventItemRepository;
    private final FulfillmentService fulfillmentService;
    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final InventoryStockService inventoryStockService;
    private final TaxCalculationService taxCalculationService;
    private final TaxConfigurationRepository taxConfigurationRepository;
    private final InvoiceBalanceValidationService invoiceBalanceValidationService;
    private final InvoiceOutboxService invoiceOutboxService;

    @Override
    @Transactional
    public Invoice createOriginalInvoice(
            Long fulfillmentEventId,
            String idempotencyKey,
            BuyerInvoiceProfile buyerProfile,
            SellerInvoiceProfile sellerProfile,
            InvoiceCalculationCommand calculationCommand) {

        if (fulfillmentEventId == null) {
            throw new IllegalArgumentException("fulfillmentEventId must not be null");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be null or blank");
        }
        if (buyerProfile == null) {
            throw new IllegalArgumentException("buyerProfile must not be null");
        }
        if (sellerProfile == null) {
            throw new IllegalArgumentException("sellerProfile must not be null");
        }
        if (calculationCommand == null) {
            throw new IllegalArgumentException("calculationCommand must not be null");
        }

        if (invoiceRepository.findByIdempotencyKey(idempotencyKey).isPresent()) {
            throw new InvoiceIdempotencyException("Idempotency key already exists: " + idempotencyKey);
        }

        if (invoiceRepository.findByFulfillmentEventIdAndInvoiceType(fulfillmentEventId, InvoiceType.ORIGINAL).isPresent()) {
            throw new InvoiceIdempotencyException(
                    "An ORIGINAL invoice already exists for fulfillment event: " + fulfillmentEventId);
        }

        FulfillmentEvent fulfillmentEvent = fulfillmentService.getFulfillmentEvent(fulfillmentEventId);

        calculationCommand.setTaxPoint(fulfillmentEvent.getDeliveredAt());
        CalculatedInvoiceResult calcResult = taxCalculationService.calculateInvoice(calculationCommand);

        buyerProfile.setIsImmutable(true);
        BuyerInvoiceProfile savedBuyer = buyerInvoiceProfileRepository.save(buyerProfile);

        sellerProfile.setIsImmutable(true);
        SellerInvoiceProfile savedSeller = sellerInvoiceProfileRepository.save(sellerProfile);

        Invoice invoice = Invoice.builder()
                .order(fulfillmentEvent.getOrder())
                .fulfillmentEvent(fulfillmentEvent)
                .buyerProfile(savedBuyer)
                .sellerProfile(savedSeller)
                .invoiceType(InvoiceType.ORIGINAL)
                .documentStatus(DocumentStatus.DRAFT)
                .taxPointDate(fulfillmentEvent.getDeliveredAt())
                .pricingBasis(calcResult.getPricingBasis())
                .totalMerchandiseBeforeTax(calcResult.getTotalMerchandiseBeforeTax())
                .totalMerchandiseDiscount(calcResult.getTotalMerchandiseDiscount())
                .totalShippingBeforeTax(calcResult.getTotalShippingBeforeTax())
                .totalShippingDiscount(calcResult.getTotalShippingDiscount())
                .totalTaxableAmount(calcResult.getTotalTaxableAmount())
                .totalTaxAmount(calcResult.getTotalTaxAmount())
                .totalAfterTax(calcResult.getTotalAfterTax())
                .idempotencyKey(idempotencyKey)
                .build();

        Invoice savedInvoice = invoiceRepository.save(invoice);
        savedInvoice.setNewlyCreated(true);

        List<InvoiceItem> items = new ArrayList<>();
        for (CalculatedInvoiceLineResult line : calcResult.getLines()) {
            InvoiceItem item = InvoiceItem.builder()
                    .invoice(savedInvoice)
                    .orderDetail(line.getOrderDetailId() != null
                            ? orderDetailRepository.findById(line.getOrderDetailId()).orElse(null)
                            : null)
                    .fulfillmentEventItem(line.getFulfillmentEventItemId() != null
                            ? fulfillmentEventItemRepository.findById(line.getFulfillmentEventItemId()).orElse(null)
                            : null)
                    .itemType(line.getItemType())
                    .itemName(line.getItemName())
                    .unit(line.getUnit())
                    .originalQuantity(line.getQuantity())
                    .quantity(line.getQuantity())
                    .unitPriceGross(line.getUnitPriceGross())
                    .unitPriceNet(line.getUnitPriceNet())
                    .discountAmount(line.getDiscountAmount())
                    .taxableAmount(line.getTaxableAmount())
                    .taxCategory(line.getTaxCategory())
                    .taxTreatment(line.getTaxTreatment())
                    .taxRate(line.getTaxRate())
                    .taxCode(line.getTaxCode())
                    .taxAmount(line.getTaxAmount())
                    .lineTotalGross(line.getLineTotalGross())
                    .build();
            items.add(item);
        }

        if (calcResult.getShippingLine() != null) {
            CalculatedInvoiceLineResult sLine = calcResult.getShippingLine();
            InvoiceItem shippingItem = InvoiceItem.builder()
                    .invoice(savedInvoice)
                    .itemType(InvoiceItemType.SHIPPING_FEE)
                    .itemName(sLine.getItemName())
                    .unit(sLine.getUnit())
                    .originalQuantity(1)
                    .quantity(1)
                    .unitPriceGross(sLine.getUnitPriceGross())
                    .unitPriceNet(sLine.getUnitPriceNet())
                    .discountAmount(sLine.getDiscountAmount())
                    .taxableAmount(sLine.getTaxableAmount())
                    .taxCategory(sLine.getTaxCategory())
                    .taxTreatment(sLine.getTaxTreatment())
                    .taxRate(sLine.getTaxRate())
                    .taxCode(sLine.getTaxCode())
                    .taxAmount(sLine.getTaxAmount())
                    .lineTotalGross(sLine.getLineTotalGross())
                    .build();
            items.add(shippingItem);
        }

        List<InvoiceItem> savedItems = invoiceItemRepository.saveAll(items);

        invoiceBalanceValidationService.initializeBalance(savedInvoice, savedItems);

        String outboxPayload = String.format("{\"invoiceId\":%d,\"invoiceType\":\"ORIGINAL\",\"fulfillmentEventId\":%d}",
                savedInvoice.getId(), fulfillmentEventId);
        invoiceOutboxService.createEvent("ISSUE_INVOICE", "INV-" + savedInvoice.getId(), savedInvoice.getId(), outboxPayload);

        return savedInvoice;
    }

    @Override
    @Transactional
    public Invoice createOriginalInvoiceForOrder(Long orderId, String idempotencyKey) {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId must not be null");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be null or blank");
        }

        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng với ID: " + orderId));
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalStateException("Cannot create original invoice for CANCELLED order: " + orderId);
        }

        Optional<Invoice> existingByOrder = invoiceRepository.findByOrderIdAndInvoiceType(orderId, InvoiceType.ORIGINAL);
        if (existingByOrder.isPresent()) {
            return existingByOrder.get();
        }

        Optional<Invoice> existingByKey = invoiceRepository.findByIdempotencyKey(idempotencyKey);
        if (existingByKey.isPresent()) {
            return existingByKey.get();
        }

        List<InvoiceLineCalculationCommand> lineCommands = new ArrayList<>();
        for (OrderDetail detail : order.getOrderDetails()) {
            lineCommands.add(InvoiceLineCalculationCommand.builder()
                    .orderDetailId(detail.getId())
                    .itemName(detail.getProductName())
                    .unit("cai")
                    .quantity(detail.getQuantity())
                    .unitPrice(detail.getUnitPrice())
                    .taxCategory("clothing")
                    .isDiscountable(true)
                    .build());
        }

        LocalDateTime taxPoint = order.getCompletedAt() != null ? order.getCompletedAt() : LocalDateTime.now();
        boolean clothingTaxConfigured = taxConfigurationRepository
                .findEffectiveConfiguration("clothing", PricingBasis.INCLUSIVE, taxPoint)
                .isPresent();
        if (!clothingTaxConfigured) {
            log.warn("Cannot create original invoice for order {}: no effective tax configuration found for 'clothing' at {}. Invoice creation deferred.",
                    orderId, taxPoint);
            return null;
        }

        if (order.getShippingFee() != null && order.getShippingFee().compareTo(BigDecimal.ZERO) > 0) {
            boolean shippingTaxConfigured = taxConfigurationRepository
                    .findEffectiveConfiguration("shipping", PricingBasis.INCLUSIVE, taxPoint)
                    .isPresent();
            if (!shippingTaxConfigured) {
                log.warn("Cannot create original invoice for order {}: no effective tax configuration found for 'shipping' at {}. Invoice creation deferred.",
                        orderId, taxPoint);
                return null;
            }
        }

        InvoiceCalculationCommand calcCmd = InvoiceCalculationCommand.builder()
                .orderId(order.getId())
                .fulfillmentEventId(null)
                .taxPoint(taxPoint)
                .pricingBasis(PricingBasis.INCLUSIVE)
                .merchandiseVoucherAmount(order.getDiscountAmount() != null ? order.getDiscountAmount() : BigDecimal.ZERO)
                .shippingFee(order.getShippingFee() != null ? order.getShippingFee() : BigDecimal.ZERO)
                .shippingPromotionAmount(BigDecimal.ZERO)
                .shippingTaxCategory("shipping")
                .items(lineCommands)
                .build();

        CalculatedInvoiceResult calcResult = taxCalculationService.calculateInvoice(calcCmd);

        String buyerEmail = (order.getUser() != null && order.getUser().getEmail() != null)
                ? order.getUser().getEmail()
                : "customer@closetshop.vn";
        String fullAddress = Stream.of(order.getAddressLine(), order.getWard(), order.getDistrict(), order.getProvince())
                .filter(p -> p != null && !p.isBlank())
                .collect(Collectors.joining(", "));

        BuyerInvoiceProfile buyerProfile = BuyerInvoiceProfile.builder()
                .customerType(CustomerType.INDIVIDUAL)
                .buyerName(order.getReceiverName() != null ? order.getReceiverName() : "Khach hang")
                .phoneNumber(order.getReceiverPhone())
                .address(fullAddress.isBlank() ? "Viet Nam" : fullAddress)
                .email(buyerEmail)
                .isImmutable(true)
                .build();
        BuyerInvoiceProfile savedBuyer = buyerInvoiceProfileRepository.save(buyerProfile);

        SellerInvoiceProfile sellerProfile = sellerInvoiceProfileRepository.findFirstByOrderByIdDesc()
                .orElseGet(() -> SellerInvoiceProfile.builder()
                        .sellerLegalName("CLOSET SHOP VIETNAM CO., LTD")
                        .sellerBrandName("Closet-Shop")
                        .sellerTaxCode("0101234567")
                        .sellerAddress("789 Nguyen Hue, Quan 1, TP.HCM")
                        .sellerPhone("02838222999")
                        .sellerEmail("einvoice@closetshop.vn")
                        .isImmutable(true)
                        .build());
        if (sellerProfile.getId() == null) {
            sellerProfile = sellerInvoiceProfileRepository.save(sellerProfile);
        }

        Invoice invoice = Invoice.builder()
                .order(order)
                .fulfillmentEvent(null)
                .buyerProfile(savedBuyer)
                .sellerProfile(sellerProfile)
                .invoiceType(InvoiceType.ORIGINAL)
                .documentStatus(DocumentStatus.DRAFT)
                .taxPointDate(taxPoint)
                .pricingBasis(calcResult.getPricingBasis())
                .totalMerchandiseBeforeTax(calcResult.getTotalMerchandiseBeforeTax())
                .totalMerchandiseDiscount(calcResult.getTotalMerchandiseDiscount())
                .totalShippingBeforeTax(calcResult.getTotalShippingBeforeTax())
                .totalShippingDiscount(calcResult.getTotalShippingDiscount())
                .totalTaxableAmount(calcResult.getTotalTaxableAmount())
                .totalTaxAmount(calcResult.getTotalTaxAmount())
                .totalAfterTax(calcResult.getTotalAfterTax())
                .idempotencyKey(idempotencyKey)
                .build();

        Invoice savedInvoice = invoiceRepository.save(invoice);
        savedInvoice.setNewlyCreated(true);

        List<InvoiceItem> items = new ArrayList<>();
        for (CalculatedInvoiceLineResult line : calcResult.getLines()) {
            InvoiceItem item = InvoiceItem.builder()
                    .invoice(savedInvoice)
                    .orderDetail(line.getOrderDetailId() != null
                            ? orderDetailRepository.findById(line.getOrderDetailId()).orElse(null)
                            : null)
                    .fulfillmentEventItem(null)
                    .itemType(line.getItemType())
                    .itemName(line.getItemName())
                    .unit(line.getUnit())
                    .originalQuantity(line.getQuantity())
                    .quantity(line.getQuantity())
                    .unitPriceGross(line.getUnitPriceGross())
                    .unitPriceNet(line.getUnitPriceNet())
                    .discountAmount(line.getDiscountAmount())
                    .taxableAmount(line.getTaxableAmount())
                    .taxCategory(line.getTaxCategory())
                    .taxTreatment(line.getTaxTreatment())
                    .taxRate(line.getTaxRate())
                    .taxCode(line.getTaxCode())
                    .taxAmount(line.getTaxAmount())
                    .lineTotalGross(line.getLineTotalGross())
                    .build();
            items.add(item);
        }

        if (calcResult.getShippingLine() != null) {
            CalculatedInvoiceLineResult sLine = calcResult.getShippingLine();
            InvoiceItem shippingItem = InvoiceItem.builder()
                    .invoice(savedInvoice)
                    .itemType(InvoiceItemType.SHIPPING_FEE)
                    .itemName(sLine.getItemName())
                    .unit(sLine.getUnit())
                    .originalQuantity(1)
                    .quantity(1)
                    .unitPriceGross(sLine.getUnitPriceGross())
                    .unitPriceNet(sLine.getUnitPriceNet())
                    .discountAmount(sLine.getDiscountAmount())
                    .taxableAmount(sLine.getTaxableAmount())
                    .taxCategory(sLine.getTaxCategory())
                    .taxTreatment(sLine.getTaxTreatment())
                    .taxRate(sLine.getTaxRate())
                    .taxCode(sLine.getTaxCode())
                    .taxAmount(sLine.getTaxAmount())
                    .lineTotalGross(sLine.getLineTotalGross())
                    .build();
            items.add(shippingItem);
        }

        List<InvoiceItem> savedItems = invoiceItemRepository.saveAll(items);

        invoiceBalanceValidationService.initializeBalance(savedInvoice, savedItems);

        String outboxPayload = String.format("{\"invoiceId\":%d,\"invoiceType\":\"ORIGINAL\",\"orderId\":%d}",
                savedInvoice.getId(), order.getId());
        invoiceOutboxService.createEvent("ISSUE_INVOICE", "INV-" + savedInvoice.getId(), savedInvoice.getId(), outboxPayload);

        return savedInvoice;
    }

    @Override
    @Transactional
    public Invoice createAdjustmentInvoice(CreateAdjustmentInvoiceCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        if (command.getOriginalInvoiceId() == null) {
            throw new IllegalArgumentException("originalInvoiceId must not be null");
        }
        if (command.getIdempotencyKey() == null || command.getIdempotencyKey().isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be null or blank");
        }
        if (command.getItemsToAdjust() == null || command.getItemsToAdjust().isEmpty()) {
            throw new IllegalArgumentException("itemsToAdjust must not be null or empty");
        }

        if (command.getReturnReceiptId() != null) {
            Optional<Invoice> existingByReceipt = invoiceRepository.findByReturnReceiptId(command.getReturnReceiptId());
            if (existingByReceipt.isPresent()) {
                return existingByReceipt.get();
            }
        }

        Optional<Invoice> existingByKey = invoiceRepository.findByIdempotencyKey(command.getIdempotencyKey());
        if (existingByKey.isPresent()) {
            Invoice existing = existingByKey.get();
            if (command.getReturnReceiptId() != null && existing.getReturnReceipt() != null
                    && existing.getReturnReceipt().getId().equals(command.getReturnReceiptId())) {
                return existing;
            }
            throw new InvoiceIdempotencyException("Idempotency key already exists: " + command.getIdempotencyKey());
        }

        Invoice original = invoiceRepository.findByIdForUpdate(command.getOriginalInvoiceId())
                .orElseThrow(() -> new InvoiceNotFoundException("Original invoice not found: " + command.getOriginalInvoiceId()));

        if (original.getDocumentStatus() != DocumentStatus.ISSUED) {
            throw new IllegalStateException("Only ISSUED invoices can be adjusted. Current status: " + original.getDocumentStatus());
        }
        if (Boolean.TRUE.equals(original.getIsReplaced())) {
            throw new IllegalStateException("Cannot adjust replaced invoice: " + original.getId());
        }

        OrderReturnReceipt returnReceipt = null;
        if (command.getReturnReceiptId() != null) {
            returnReceipt = inventoryStockService.getReturnReceiptEntity(command.getReturnReceiptId());
            if (!returnReceipt.getOrder().getId().equals(original.getOrder().getId())) {
                throw new IllegalArgumentException(String.format(
                        "Return receipt order %d does not match original invoice order %d",
                        returnReceipt.getOrder().getId(), original.getOrder().getId()));
            }
        }

        for (AdjustItemCommand itemCmd : command.getItemsToAdjust()) {
            InvoiceItem originalItem = invoiceItemRepository.findById(itemCmd.getOriginalInvoiceItemId())
                    .orElseThrow(() -> new IllegalArgumentException("Original invoice item not found: " + itemCmd.getOriginalInvoiceItemId()));
            if (!originalItem.getInvoice().getId().equals(original.getId())) {
                throw new IllegalArgumentException(String.format(
                        "Original invoice item %d belongs to invoice %d, not requested original invoice %d",
                        originalItem.getId(), originalItem.getInvoice().getId(), original.getId()));
            }
        }

        invoiceBalanceValidationService.reserveBalance(original.getId(), command.getItemsToAdjust());

        AdjustmentType adjType = command.getAdjustmentType() != null
                ? command.getAdjustmentType()
                : AdjustmentType.DECREASE;

        LocalDateTime taxPoint = (returnReceipt != null && returnReceipt.getCreatedAt() != null)
                ? returnReceipt.getCreatedAt()
                : LocalDateTime.now();

        Invoice adjustmentInvoice = Invoice.builder()
                .order(original.getOrder())
                .originalInvoice(original)
                .returnReceipt(returnReceipt)
                .financialSettlementReference(command.getFinancialSettlementReference())
                .buyerProfile(original.getBuyerProfile())
                .sellerProfile(original.getSellerProfile())
                .invoiceType(InvoiceType.ADJUSTMENT)
                .adjustmentType(adjType)
                .documentStatus(DocumentStatus.DRAFT)
                .taxPointDate(taxPoint)
                .pricingBasis(original.getPricingBasis())
                .idempotencyKey(command.getIdempotencyKey())
                .build();

        Invoice savedAdjInvoice = invoiceRepository.save(adjustmentInvoice);

        BigDecimal totalMerchandisePreDiscount = BigDecimal.ZERO;
        BigDecimal totalTaxableAmount = BigDecimal.ZERO;
        BigDecimal totalTaxAmount = BigDecimal.ZERO;
        BigDecimal totalGross = BigDecimal.ZERO;

        List<InvoiceItem> adjItems = new ArrayList<>();
        for (AdjustItemCommand itemCmd : command.getItemsToAdjust()) {
            InvoiceItem originalItem = invoiceItemRepository.findById(itemCmd.getOriginalInvoiceItemId())
                    .orElseThrow(() -> new IllegalArgumentException("Original invoice item not found: " + itemCmd.getOriginalInvoiceItemId()));

            int qty = itemCmd.getQuantityToAdjust();
            int signedQty = adjType == AdjustmentType.DECREASE ? -qty : qty;

            BigDecimal taxable = itemCmd.getTaxableAmountToAdjust();
            if (taxable == null) {
                taxable = originalItem.getTaxableAmount()
                        .multiply(BigDecimal.valueOf(qty))
                        .divide(BigDecimal.valueOf(originalItem.getOriginalQuantity()), 2, RoundingMode.HALF_UP);
            }

            BigDecimal tax = itemCmd.getTaxAmountToAdjust();
            if (tax == null) {
                tax = originalItem.getTaxAmount()
                        .multiply(BigDecimal.valueOf(qty))
                        .divide(BigDecimal.valueOf(originalItem.getOriginalQuantity()), 2, RoundingMode.HALF_UP);
            }

            BigDecimal lineGross = taxable.add(tax);

            BigDecimal signedTaxable = adjType == AdjustmentType.DECREASE ? taxable.negate() : taxable;
            BigDecimal signedTax = adjType == AdjustmentType.DECREASE ? tax.negate() : tax;
            BigDecimal signedGross = adjType == AdjustmentType.DECREASE ? lineGross.negate() : lineGross;

            totalTaxableAmount = totalTaxableAmount.add(signedTaxable);
            totalTaxAmount = totalTaxAmount.add(signedTax);
            totalGross = totalGross.add(signedGross);
            totalMerchandisePreDiscount = totalMerchandisePreDiscount.add(signedGross);

            InvoiceItem adjItem = InvoiceItem.builder()
                    .invoice(savedAdjInvoice)
                    .originalInvoiceItem(originalItem)
                    .orderDetail(originalItem.getOrderDetail())
                    .fulfillmentEventItem(originalItem.getFulfillmentEventItem())
                    .itemType(originalItem.getItemType())
                    .itemName(originalItem.getItemName())
                    .unit(originalItem.getUnit())
                    .originalQuantity(0)
                    .quantity(signedQty)
                    .unitPriceGross(originalItem.getUnitPriceGross())
                    .unitPriceNet(originalItem.getUnitPriceNet())
                    .discountAmount(BigDecimal.ZERO)
                    .taxableAmount(signedTaxable)
                    .taxCategory(originalItem.getTaxCategory())
                    .taxTreatment(originalItem.getTaxTreatment())
                    .taxRate(originalItem.getTaxRate())
                    .taxCode(originalItem.getTaxCode())
                    .taxAmount(signedTax)
                    .lineTotalGross(signedGross)
                    .build();

            adjItems.add(adjItem);
        }

        savedAdjInvoice.setTotalMerchandiseBeforeTax(totalMerchandisePreDiscount);
        savedAdjInvoice.setTotalTaxableAmount(totalTaxableAmount);
        savedAdjInvoice.setTotalTaxAmount(totalTaxAmount);
        savedAdjInvoice.setTotalAfterTax(totalGross);

        invoiceItemRepository.saveAll(adjItems);
        return invoiceRepository.save(savedAdjInvoice);
    }

    @Override
    @Transactional
    public Invoice approveAdjustmentInvoice(Long adjustmentInvoiceId) {
        if (adjustmentInvoiceId == null) {
            throw new IllegalArgumentException("adjustmentInvoiceId must not be null");
        }
        Invoice invoice = invoiceRepository.findByIdForUpdate(adjustmentInvoiceId)
                .orElseThrow(() -> new InvoiceNotFoundException("Adjustment invoice not found: " + adjustmentInvoiceId));

        if (invoice.getInvoiceType() != InvoiceType.ADJUSTMENT) {
            throw new IllegalArgumentException("Invoice " + adjustmentInvoiceId + " is not an ADJUSTMENT invoice");
        }

        if (invoice.getDocumentStatus() == DocumentStatus.PENDING_ISSUE
                || invoice.getDocumentStatus() == DocumentStatus.ISSUING
                || invoice.getDocumentStatus() == DocumentStatus.ISSUED) {
            return invoice;
        }

        if (invoice.getDocumentStatus() != DocumentStatus.DRAFT) {
            throw new IllegalStateException("Cannot approve adjustment invoice in status: " + invoice.getDocumentStatus() + ". Only DRAFT can be approved.");
        }

        invoice.setDocumentStatus(DocumentStatus.PENDING_ISSUE);
        return invoiceRepository.save(invoice);
    }

    @Override
    @Transactional
    public Invoice rejectAdjustmentInvoice(Long adjustmentInvoiceId, String rejectionReason) {
        if (adjustmentInvoiceId == null) {
            throw new IllegalArgumentException("adjustmentInvoiceId must not be null");
        }
        Invoice invoice = invoiceRepository.findById(adjustmentInvoiceId)
                .orElseThrow(() -> new InvoiceNotFoundException("Adjustment invoice not found: " + adjustmentInvoiceId));

        if (invoice.getInvoiceType() != InvoiceType.ADJUSTMENT) {
            throw new IllegalArgumentException("Invoice " + adjustmentInvoiceId + " is not an ADJUSTMENT invoice");
        }

        if (invoice.getDocumentStatus() == DocumentStatus.ISSUE_FAILED) {
            return invoice;
        }

        if (invoice.getDocumentStatus() != DocumentStatus.DRAFT) {
            throw new IllegalStateException("Cannot reject adjustment invoice in status: " + invoice.getDocumentStatus() + ". Only DRAFT can be rejected.");
        }

        invoiceBalanceValidationService.releaseAdjustmentBalance(adjustmentInvoiceId);
        return invoiceRepository.findById(adjustmentInvoiceId).orElseThrow();
    }

    @Override
    @Transactional
    public Invoice createAdjustmentDraftFromReturnReceipt(Long returnReceiptId) {
        if (returnReceiptId == null) {
            throw new IllegalArgumentException("returnReceiptId must not be null");
        }

        Optional<Invoice> existing = invoiceRepository.findByReturnReceiptId(returnReceiptId);
        if (existing.isPresent()) {
            return existing.get();
        }

        OrderReturnReceipt returnReceipt = inventoryStockService.getReturnReceiptEntity(returnReceiptId);
        Order order = returnReceipt.getOrder();

        List<Invoice> origInvoices = invoiceRepository.findByOrderId(order.getId()).stream()
                .filter(i -> i.getInvoiceType() == InvoiceType.ORIGINAL && i.getDocumentStatus() == DocumentStatus.ISSUED)
                .collect(Collectors.toList());

        if (origInvoices.isEmpty()) {
            throw new IllegalStateException("No ISSUED original invoice found for order: " + order.getId());
        }

        Invoice original = origInvoices.get(0);

        // Enforce unified lock hierarchy: invoices -> invoice_balance_adjustments
        Invoice lockedOriginal = invoiceRepository.findByIdForUpdate(original.getId())
                .orElseThrow(() -> new InvoiceNotFoundException("Original invoice not found: " + original.getId()));

        if (lockedOriginal.getInvoiceType() != InvoiceType.ORIGINAL) {
            throw new IllegalArgumentException("Referenced invoice " + lockedOriginal.getId() + " is not an ORIGINAL invoice");
        }
        if (lockedOriginal.getDocumentStatus() != DocumentStatus.ISSUED) {
            throw new IllegalStateException("Original invoice " + lockedOriginal.getId() + " is no longer in ISSUED status");
        }
        if (lockedOriginal.getOrder() == null || !lockedOriginal.getOrder().getId().equals(order.getId())) {
            throw new IllegalStateException("Locked original invoice " + lockedOriginal.getId() + " does not belong to order " + order.getId());
        }

        List<InvoiceBalanceAdjustment> balances = invoiceBalanceAdjustmentRepository
                .findByOriginalInvoiceIdForUpdate(lockedOriginal.getId());

        List<AdjustItemCommand> itemsToAdjust = new ArrayList<>();
        int remainingToAllocate = returnReceipt.getTotalReceivedQuantity();

        for (InvoiceBalanceAdjustment bal : balances) {
            if (remainingToAllocate <= 0) {
                break;
            }
            if (bal.getRemainingQuantity() > 0) {
                int takeQty = Math.min(bal.getRemainingQuantity(), remainingToAllocate);
                itemsToAdjust.add(AdjustItemCommand.builder()
                        .originalInvoiceItemId(bal.getInvoiceItem().getId())
                        .quantityToAdjust(takeQty)
                        .build());
                remainingToAllocate -= takeQty;
            }
        }

        if (itemsToAdjust.isEmpty() || remainingToAllocate > 0) {
            throw new InvoiceBalanceExceededException("Insufficient balance in original invoice " + lockedOriginal.getId() + " for return receipt " + returnReceiptId);
        }

        String idempotencyKey = "INV_ADJ_RET_" + lockedOriginal.getId() + "_" + returnReceipt.getId();

        CreateAdjustmentInvoiceCommand cmd = CreateAdjustmentInvoiceCommand.builder()
                .originalInvoiceId(lockedOriginal.getId())
                .returnReceiptId(returnReceipt.getId())
                .adjustmentType(AdjustmentType.DECREASE)
                .idempotencyKey(idempotencyKey)
                .itemsToAdjust(itemsToAdjust)
                .build();

        return createAdjustmentInvoice(cmd);
    }

    @Override
    @Transactional
    public Invoice createReplacementInvoice(CreateReplacementInvoiceCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        if (command.getTargetInvoiceId() == null) {
            throw new IllegalArgumentException("targetInvoiceId must not be null");
        }
        if (command.getIdempotencyKey() == null || command.getIdempotencyKey().isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be null or blank");
        }
        if (command.getCalculationCommand() == null) {
            throw new IllegalArgumentException("calculationCommand must not be null");
        }

        if (invoiceRepository.findByIdempotencyKey(command.getIdempotencyKey()).isPresent()) {
            throw new InvoiceIdempotencyException("Idempotency key already exists: " + command.getIdempotencyKey());
        }

        Invoice target = invoiceRepository.findById(command.getTargetInvoiceId())
                .orElseThrow(() -> new InvoiceNotFoundException("Target invoice not found: " + command.getTargetInvoiceId()));

        if (target.getDocumentStatus() != DocumentStatus.ISSUED) {
            throw new IllegalStateException("Only ISSUED invoices can be replaced. Current status: " + target.getDocumentStatus());
        }
        if (Boolean.TRUE.equals(target.getIsReplaced())) {
            throw new IllegalStateException("Invoice has already been replaced: " + target.getId());
        }

        BuyerInvoiceProfile buyer = target.getBuyerProfile();
        if (command.getBuyerProfile() != null) {
            command.getBuyerProfile().setIsImmutable(true);
            buyer = buyerInvoiceProfileRepository.save(command.getBuyerProfile());
        }

        command.getCalculationCommand().setTaxPoint(LocalDateTime.now());
        CalculatedInvoiceResult calcResult = taxCalculationService.calculateInvoice(command.getCalculationCommand());

        Invoice replacementInvoice = Invoice.builder()
                .order(target.getOrder())
                .fulfillmentEvent(target.getFulfillmentEvent())
                .replacingInvoice(target)
                .buyerProfile(buyer)
                .sellerProfile(target.getSellerProfile())
                .invoiceType(InvoiceType.REPLACEMENT)
                .documentStatus(DocumentStatus.DRAFT)
                .taxPointDate(LocalDateTime.now())
                .pricingBasis(calcResult.getPricingBasis())
                .totalMerchandiseBeforeTax(calcResult.getTotalMerchandiseBeforeTax())
                .totalMerchandiseDiscount(calcResult.getTotalMerchandiseDiscount())
                .totalShippingBeforeTax(calcResult.getTotalShippingBeforeTax())
                .totalShippingDiscount(calcResult.getTotalShippingDiscount())
                .totalTaxableAmount(calcResult.getTotalTaxableAmount())
                .totalTaxAmount(calcResult.getTotalTaxAmount())
                .totalAfterTax(calcResult.getTotalAfterTax())
                .idempotencyKey(command.getIdempotencyKey())
                .build();

        Invoice savedReplacement = invoiceRepository.save(replacementInvoice);

        target.setIsReplaced(true);
        target.setReplacedByInvoice(savedReplacement);
        invoiceRepository.save(target);

        List<InvoiceItem> items = new ArrayList<>();
        for (CalculatedInvoiceLineResult line : calcResult.getLines()) {
            InvoiceItem item = InvoiceItem.builder()
                    .invoice(savedReplacement)
                    .orderDetail(line.getOrderDetailId() != null
                            ? orderDetailRepository.findById(line.getOrderDetailId()).orElse(null)
                            : null)
                    .fulfillmentEventItem(line.getFulfillmentEventItemId() != null
                            ? fulfillmentEventItemRepository.findById(line.getFulfillmentEventItemId()).orElse(null)
                            : null)
                    .itemType(line.getItemType())
                    .itemName(line.getItemName())
                    .unit(line.getUnit())
                    .originalQuantity(line.getQuantity())
                    .quantity(line.getQuantity())
                    .unitPriceGross(line.getUnitPriceGross())
                    .unitPriceNet(line.getUnitPriceNet())
                    .discountAmount(line.getDiscountAmount())
                    .taxableAmount(line.getTaxableAmount())
                    .taxCategory(line.getTaxCategory())
                    .taxTreatment(line.getTaxTreatment())
                    .taxRate(line.getTaxRate())
                    .taxCode(line.getTaxCode())
                    .taxAmount(line.getTaxAmount())
                    .lineTotalGross(line.getLineTotalGross())
                    .build();
            items.add(item);
        }

        if (calcResult.getShippingLine() != null) {
            CalculatedInvoiceLineResult sLine = calcResult.getShippingLine();
            InvoiceItem shippingItem = InvoiceItem.builder()
                    .invoice(savedReplacement)
                    .itemType(InvoiceItemType.SHIPPING_FEE)
                    .itemName(sLine.getItemName())
                    .unit(sLine.getUnit())
                    .originalQuantity(1)
                    .quantity(1)
                    .unitPriceGross(sLine.getUnitPriceGross())
                    .unitPriceNet(sLine.getUnitPriceNet())
                    .discountAmount(sLine.getDiscountAmount())
                    .taxableAmount(sLine.getTaxableAmount())
                    .taxCategory(sLine.getTaxCategory())
                    .taxTreatment(sLine.getTaxTreatment())
                    .taxRate(sLine.getTaxRate())
                    .taxCode(sLine.getTaxCode())
                    .taxAmount(sLine.getTaxAmount())
                    .lineTotalGross(sLine.getLineTotalGross())
                    .build();
            items.add(shippingItem);
        }

        List<InvoiceItem> savedItems = invoiceItemRepository.saveAll(items);

        invoiceBalanceValidationService.initializeBalance(savedReplacement, savedItems);

        String outboxPayload = String.format("{\"invoiceId\":%d,\"invoiceType\":\"REPLACEMENT\",\"replacingInvoiceId\":%d}",
                savedReplacement.getId(), target.getId());
        invoiceOutboxService.createEvent("ISSUE_INVOICE", "INV-" + savedReplacement.getId(), savedReplacement.getId(), outboxPayload);

        return savedReplacement;
    }

    @Override
    @Transactional
    public Invoice transitionStatus(Long invoiceId, DocumentStatus targetStatus) {
        if (invoiceId == null) {
            throw new IllegalArgumentException("invoiceId must not be null");
        }
        if (targetStatus == null) {
            throw new IllegalArgumentException("targetStatus must not be null");
        }

        Invoice invoice = getInvoice(invoiceId);
        DocumentStatus current = invoice.getDocumentStatus();

        if (current == targetStatus) {
            return invoice;
        }

        boolean isValid = switch (current) {
            case DRAFT -> targetStatus == DocumentStatus.PENDING_ISSUE
                    || targetStatus == DocumentStatus.ISSUE_FAILED;
            case PENDING_ISSUE -> targetStatus == DocumentStatus.ISSUING
                    || targetStatus == DocumentStatus.DRAFT
                    || targetStatus == DocumentStatus.ISSUE_FAILED;
            case ISSUING -> targetStatus == DocumentStatus.ISSUED
                    || targetStatus == DocumentStatus.ISSUANCE_UNKNOWN
                    || targetStatus == DocumentStatus.ISSUE_FAILED
                    || targetStatus == DocumentStatus.PENDING_ISSUE;
            case ISSUANCE_UNKNOWN -> targetStatus == DocumentStatus.RECONCILING;
            case RECONCILING -> targetStatus == DocumentStatus.ISSUED
                    || targetStatus == DocumentStatus.ISSUANCE_UNKNOWN
                    || targetStatus == DocumentStatus.ISSUE_FAILED;
            case ISSUED -> false;
            case ISSUE_FAILED -> targetStatus == DocumentStatus.DRAFT || targetStatus == DocumentStatus.PENDING_ISSUE;
        };

        if (!isValid) {
            throw new IllegalStateException(String.format("Invalid status transition from %s to %s for invoice %d",
                    current, targetStatus, invoiceId));
        }

        if (invoice.getInvoiceType() == InvoiceType.ADJUSTMENT && targetStatus == DocumentStatus.ISSUE_FAILED) {
            invoiceBalanceValidationService.releaseAdjustmentBalance(invoiceId);
            return invoiceRepository.findById(invoiceId).orElseThrow();
        }

        invoice.setDocumentStatus(targetStatus);
        return invoiceRepository.save(invoice);
    }

    @Override
    @Transactional(readOnly = true)
    public Invoice getInvoice(Long invoiceId) {
        return invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new InvoiceNotFoundException("Invoice not found: " + invoiceId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<InvoiceItem> getInvoiceItems(Long invoiceId) {
        return invoiceItemRepository.findByInvoiceId(invoiceId);
    }
}
