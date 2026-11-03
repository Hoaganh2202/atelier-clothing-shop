package com.shop.backend.einvoice.service.impl;

import com.shop.backend.common.dto.PageResponse;
import com.shop.backend.common.exception.BadRequestException;
import com.shop.backend.common.exception.ResourceNotFoundException;
import com.shop.backend.einvoice.dto.response.InvoiceBuyerResponse;
import com.shop.backend.einvoice.dto.response.InvoiceDetailResponse;
import com.shop.backend.einvoice.dto.response.InvoiceItemResponse;
import com.shop.backend.einvoice.dto.response.InvoiceRetryResponse;
import com.shop.backend.einvoice.dto.response.InvoiceSellerResponse;
import com.shop.backend.einvoice.dto.response.InvoiceSummaryResponse;
import com.shop.backend.einvoice.model.BuyerInvoiceProfile;
import com.shop.backend.einvoice.model.DocumentStatus;
import com.shop.backend.einvoice.model.Invoice;
import com.shop.backend.einvoice.model.InvoiceItem;
import com.shop.backend.einvoice.model.InvoiceType;
import com.shop.backend.einvoice.model.PricingBasis;
import com.shop.backend.einvoice.model.SellerInvoiceProfile;
import com.shop.backend.einvoice.provider.InvoiceProviderProperties;
import com.shop.backend.einvoice.repository.InvoiceItemRepository;
import com.shop.backend.einvoice.repository.InvoiceRepository;
import com.shop.backend.einvoice.repository.InvoiceSpecifications;
import com.shop.backend.einvoice.service.InvoiceQueryService;
import com.shop.backend.order.Order;
import com.shop.backend.order.OrderRepository;
import com.shop.backend.payment.PaymentService;
import com.shop.backend.payment.dto.PaymentProcessResult;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceQueryServiceImpl implements InvoiceQueryService {

    private static final String DEMO_NOTICE_ISSUED = "Hóa đơn điện tử mô phỏng phục vụ đồ án tốt nghiệp, không có giá trị pháp lý về thuế.";
    private static final String DEMO_NOTICE_DRAFT = "Hóa đơn đang ở trạng thái nháp (chưa phát hành). Hệ thống đang vận hành ở chế độ mô phỏng (Demo Mode).";
    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of("createdAt", "taxPointDate", "totalAfterTax", "id");

    private final InvoiceRepository invoiceRepository;
    private final InvoiceItemRepository invoiceItemRepository;
    private final OrderRepository orderRepository;
    private final PaymentService paymentService;
    private final InvoiceProviderProperties providerProperties;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<InvoiceSummaryResponse> getAdminInvoices(
            String keyword,
            Long orderId,
            DocumentStatus status,
            InvoiceType type,
            Boolean isDemo,
            LocalDate startDate,
            LocalDate endDate,
            Pageable pageable
    ) {
        Pageable safePageable = sanitizePageable(pageable);

        Specification<Invoice> spec = Specification.where(InvoiceSpecifications.withKeyword(keyword))
                .and(InvoiceSpecifications.withOrderId(orderId))
                .and(InvoiceSpecifications.withDocumentStatus(status))
                .and(InvoiceSpecifications.withInvoiceType(type))
                .and(InvoiceSpecifications.withIsDemo(isDemo))
                .and(InvoiceSpecifications.withDateRange(startDate, endDate));

        Page<Invoice> page = invoiceRepository.findAll(spec, safePageable);
        return PageResponse.of(page.map(this::toSummaryResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public InvoiceDetailResponse getAdminInvoiceDetail(Long invoiceId) {
        Invoice invoice = invoiceRepository.findByIdWithProfilesAndOrder(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hóa đơn với mã: " + invoiceId));

        List<InvoiceItem> items = invoiceItemRepository.findByInvoiceId(invoice.getId());
        return toDetailResponse(invoice, items);
    }

    @Override
    public InvoiceRetryResponse retryInvoiceForOrder(Long orderId) {
        if (orderId == null) {
            throw new BadRequestException("Mã đơn hàng không được để trống");
        }

        try {
            PaymentProcessResult result = paymentService.retryInvoiceCreation(orderId);
            Invoice invoice = result.getInvoice();
            if (invoice == null) {
                throw new BadRequestException("Không thể tạo hóa đơn cho đơn hàng " + orderId);
            }

            Invoice freshInvoice = invoiceRepository.findByIdWithProfilesAndOrder(invoice.getId())
                    .orElse(invoice);
            List<InvoiceItem> items = invoiceItemRepository.findByInvoiceId(freshInvoice.getId());

            return InvoiceRetryResponse.builder()
                    .invoice(toDetailResponse(freshInvoice, items))
                    .invoiceCreated(result.isInvoiceCreated())
                    .message(result.isInvoiceCreated()
                            ? "Đã tạo hóa đơn và đưa vào hàng đợi xử lý."
                            : "Đơn hàng đã có hóa đơn, không cần tạo lại.")
                    .build();
        } catch (IllegalStateException ex) {
            log.warn("Lỗi nghiệp vụ khi retry tạo hóa đơn cho đơn hàng {}: {}", orderId, ex.getMessage());
            throw new BadRequestException(ex.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public InvoiceDetailResponse getCustomerInvoiceByOrderId(Long customerUserId, Long orderId) {
        if (customerUserId == null || orderId == null) {
            throw new ResourceNotFoundException("Đơn hàng không tồn tại: " + orderId);
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Đơn hàng không tồn tại: " + orderId));

        if (order.getUser() == null || !order.getUser().getId().equals(customerUserId)) {
            // Anti-IDOR: return 404 instead of 403 to prevent enumeration
            throw new ResourceNotFoundException("Đơn hàng không tồn tại: " + orderId);
        }

        List<Invoice> invoices = invoiceRepository.findByOrderIdWithProfilesAndOrder(orderId);
        CurrentInvoiceResolution resolution = resolveCurrentInvoice(invoices);
        Invoice currentInvoice = resolution.activeInvoice();
        if (currentInvoice == null) {
            throw new ResourceNotFoundException("Đơn hàng chưa có hóa đơn điện tử");
        }

        List<InvoiceItem> items = invoiceItemRepository.findByInvoiceId(currentInvoice.getId());
        InvoiceDetailResponse response = toDetailResponse(currentInvoice, items);

        Invoice pendingReplacement = resolution.pendingReplacement();
        if (pendingReplacement != null) {
            response.setReplacementStatus(pendingReplacement.getDocumentStatus());
            if (pendingReplacement.getDocumentStatus() == DocumentStatus.ISSUE_FAILED) {
                response.setReplacementNotice("Yêu cầu thay thế hóa đơn phát sinh lỗi (ISSUE_FAILED). Hóa đơn gốc hiện vẫn giữ hiệu lực.");
            } else {
                response.setReplacementNotice("Đơn hàng đang có hóa đơn thay thế đang được xử lý (" +
                        pendingReplacement.getDocumentStatus() + "). Hóa đơn hiện tại vẫn có giá trị cho đến khi hóa đơn thay thế được phát hành thành công.");
            }
        }

        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public InvoiceDetailResponse getCustomerInvoiceDetail(Long customerUserId, Long invoiceId) {
        if (customerUserId == null || invoiceId == null) {
            throw new ResourceNotFoundException("Hóa đơn không tồn tại: " + invoiceId);
        }

        Invoice invoice = invoiceRepository.findByIdWithProfilesAndOrder(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Hóa đơn không tồn tại: " + invoiceId));

        if (invoice.getOrder() == null || invoice.getOrder().getUser() == null ||
                !invoice.getOrder().getUser().getId().equals(customerUserId)) {
            // Anti-IDOR: return 404 instead of 403
            throw new ResourceNotFoundException("Hóa đơn không tồn tại: " + invoiceId);
        }

        List<InvoiceItem> items = invoiceItemRepository.findByInvoiceId(invoice.getId());
        return toDetailResponse(invoice, items);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<InvoiceSummaryResponse> getCustomerInvoices(Long customerUserId, int page, int size) {
        if (customerUserId == null) {
            return PageResponse.<InvoiceSummaryResponse>builder()
                    .content(Collections.emptyList())
                    .page(0)
                    .size(20)
                    .totalElements(0)
                    .totalPages(0)
                    .build();
        }

        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);
        Pageable safePageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        Specification<Invoice> spec = Specification.where(InvoiceSpecifications.withCustomerUserId(customerUserId));
        Page<Invoice> invoicePage = invoiceRepository.findAll(spec, safePageable);

        return PageResponse.of(invoicePage.map(this::toSummaryResponse));
    }

    private boolean isMockEnvironment() {
        if (providerProperties == null || providerProperties.getType() == null) {
            return true;
        }
        return "mock".equalsIgnoreCase(providerProperties.getType().trim());
    }

    private record CurrentInvoiceResolution(
            Invoice activeInvoice,
            Invoice pendingReplacement
    ) {}

    private CurrentInvoiceResolution resolveCurrentInvoice(List<Invoice> invoices) {
        if (invoices == null || invoices.isEmpty()) {
            return new CurrentInvoiceResolution(null, null);
        }

        // 1. Tìm Original invoice của đơn hàng
        Optional<Invoice> originalOpt = invoices.stream()
                .filter(inv -> inv.getInvoiceType() == InvoiceType.ORIGINAL)
                .findFirst();

        if (originalOpt.isEmpty()) {
            Invoice fallback = invoices.stream()
                    .filter(inv -> inv.getDocumentStatus() == DocumentStatus.ISSUED)
                    .findFirst()
                    .orElse(invoices.get(invoices.size() - 1));
            return new CurrentInvoiceResolution(fallback, null);
        }

        Invoice original = originalOpt.get();
        Set<Long> visitedIds = new HashSet<>();
        visitedIds.add(original.getId());

        Invoice current = original;
        Invoice latestIssuedValid = (original.getDocumentStatus() == DocumentStatus.ISSUED) ? original : null;
        Invoice pendingReplacement = null;

        while (true) {
            Invoice next = current.getReplacedByInvoice();

            // Nếu con trỏ replacedByInvoice bị thiếu, kiểm tra qua replacingInvoice
            if (next == null) {
                final Long currentId = current.getId();
                List<Invoice> replacers = invoices.stream()
                        .filter(inv -> inv.getReplacingInvoice() != null && currentId.equals(inv.getReplacingInvoice().getId()))
                        .filter(inv -> !visitedIds.contains(inv.getId()))
                        .toList();
                if (replacers.size() == 1) {
                    next = replacers.get(0);
                }
            }

            if (next == null) {
                break;
            }

            // Cycle detection
            if (!visitedIds.add(next.getId())) {
                log.warn("Phát hiện chu kỳ thay thế hóa đơn (cycle loop) tại invoice ID: {}", next.getId());
                break;
            }

            // Xác thực: phải là REPLACEMENT và cùng đơn hàng
            if (next.getInvoiceType() != InvoiceType.REPLACEMENT ||
                    (next.getOrder() != null && !next.getOrder().getId().equals(original.getOrder().getId()))) {
                log.warn("Bỏ qua hóa đơn không hợp lệ trong chuỗi thay thế: ID {}", next.getId());
                break;
            }

            // Đánh giá trạng thái
            if (next.getDocumentStatus() == DocumentStatus.ISSUED) {
                latestIssuedValid = next;
                pendingReplacement = null; // Đã thay thế thành công bản trước
            } else if (next.getDocumentStatus() == DocumentStatus.DRAFT ||
                    next.getDocumentStatus() == DocumentStatus.PENDING_ISSUE ||
                    next.getDocumentStatus() == DocumentStatus.ISSUING ||
                    next.getDocumentStatus() == DocumentStatus.RECONCILING ||
                    next.getDocumentStatus() == DocumentStatus.ISSUE_FAILED) {
                pendingReplacement = next;
            }

            current = next;
        }

        Invoice active = latestIssuedValid != null ? latestIssuedValid : original;
        return new CurrentInvoiceResolution(active, pendingReplacement);
    }

    private Pageable sanitizePageable(Pageable pageable) {
        int size = Math.min(Math.max(pageable.getPageSize(), 1), 100);
        int page = Math.max(pageable.getPageNumber(), 0);

        Sort sort = pageable.getSort();
        List<Sort.Order> validOrders = new ArrayList<>();

        for (Sort.Order order : sort) {
            if (ALLOWED_SORT_PROPERTIES.contains(order.getProperty())) {
                validOrders.add(order);
            }
        }

        Sort finalSort = validOrders.isEmpty() ? Sort.by(Sort.Direction.DESC, "createdAt") : Sort.by(validOrders);
        return PageRequest.of(page, size, finalSort);
    }

    private InvoiceSummaryResponse toSummaryResponse(Invoice invoice) {
        boolean isDemo = invoice.isDemoIssued();
        boolean demoMode = isDemo || (invoice.getDocumentStatus() == DocumentStatus.DRAFT && isMockEnvironment());
        Long orderId = invoice.getOrder() != null ? invoice.getOrder().getId() : null;
        String orderNumber = orderId != null ? "ORD-" + orderId : null;

        return InvoiceSummaryResponse.builder()
                .id(invoice.getId())
                .orderId(orderId)
                .orderNumber(orderNumber)
                .invoiceType(invoice.getInvoiceType())
                .adjustmentType(invoice.getAdjustmentType())
                .documentStatus(invoice.getDocumentStatus())
                .invoiceSeries(invoice.getInvoiceSeries())
                .invoiceNumber(invoice.getInvoiceNumber())
                .taxAuthorityCode(invoice.getTaxAuthorityCode())
                .taxPointDate(invoice.getTaxPointDate())
                .officiallyIssuedAt(invoice.getOfficiallyIssuedAt())
                .totalTaxableAmount(toVndLong(invoice.getTotalTaxableAmount()))
                .totalTaxAmount(toVndLong(invoice.getTotalTaxAmount()))
                .totalAfterTax(toVndLong(invoice.getTotalAfterTax()))
                .buyerName(invoice.getBuyerProfile() != null ? invoice.getBuyerProfile().getBuyerName() : null)
                .isDemo(isDemo)
                .demoMode(demoMode)
                .createdAt(invoice.getCreatedAt())
                .build();
    }

    private InvoiceDetailResponse toDetailResponse(Invoice invoice, List<InvoiceItem> items) {
        boolean isDemo = invoice.isDemoIssued();
        boolean demoMode = isDemo || (invoice.getDocumentStatus() == DocumentStatus.DRAFT && isMockEnvironment());
        String demoNotice = null;
        if (isDemo) {
            demoNotice = DEMO_NOTICE_ISSUED;
        } else if (demoMode) {
            demoNotice = DEMO_NOTICE_DRAFT;
        }
        Long orderId = invoice.getOrder() != null ? invoice.getOrder().getId() : null;
        String orderNumber = orderId != null ? "ORD-" + orderId : null;

        return InvoiceDetailResponse.builder()
                .id(invoice.getId())
                .orderId(orderId)
                .orderNumber(orderNumber)
                .invoiceType(invoice.getInvoiceType())
                .adjustmentType(invoice.getAdjustmentType())
                .documentStatus(invoice.getDocumentStatus())
                .invoiceSeries(invoice.getInvoiceSeries())
                .invoiceNumber(invoice.getInvoiceNumber())
                .taxAuthorityCode(invoice.getTaxAuthorityCode())
                .taxPointDate(invoice.getTaxPointDate())
                .officiallyIssuedAt(invoice.getOfficiallyIssuedAt())
                .pricingBasis(invoice.getPricingBasis())
                .totalMerchandiseBeforeTax(toVndLong(invoice.getTotalMerchandiseBeforeTax()))
                .totalMerchandiseDiscount(toVndLong(invoice.getTotalMerchandiseDiscount()))
                .totalShippingBeforeTax(toVndLong(invoice.getTotalShippingBeforeTax()))
                .totalShippingDiscount(toVndLong(invoice.getTotalShippingDiscount()))
                .totalTaxableAmount(toVndLong(invoice.getTotalTaxableAmount()))
                .totalTaxAmount(toVndLong(invoice.getTotalTaxAmount()))
                .totalAfterTax(toVndLong(invoice.getTotalAfterTax()))
                .storageProvider(invoice.getStorageProvider())
                .isDemo(isDemo)
                .demoMode(demoMode)
                .demoNotice(demoNotice)
                .isReplaced(invoice.getIsReplaced())
                .originalInvoiceId(invoice.getOriginalInvoice() != null ? invoice.getOriginalInvoice().getId() : null)
                .replacedByInvoiceId(invoice.getReplacedByInvoice() != null ? invoice.getReplacedByInvoice().getId() : null)
                .replacingInvoiceId(invoice.getReplacingInvoice() != null ? invoice.getReplacingInvoice().getId() : null)
                .buyer(toBuyerResponse(invoice.getBuyerProfile()))
                .seller(toSellerResponse(invoice.getSellerProfile()))
                .items(items != null ? items.stream().map(this::toItemResponse).toList() : Collections.emptyList())
                .createdAt(invoice.getCreatedAt())
                .updatedAt(invoice.getUpdatedAt())
                .build();
    }

    private InvoiceBuyerResponse toBuyerResponse(BuyerInvoiceProfile buyer) {
        if (buyer == null) return null;
        return InvoiceBuyerResponse.builder()
                .customerType(buyer.getCustomerType())
                .buyerName(buyer.getBuyerName())
                .companyName(buyer.getCompanyName())
                .taxCode(buyer.getTaxCode())
                .email(buyer.getEmail())
                .address(buyer.getAddress())
                .phoneNumber(buyer.getPhoneNumber())
                .build();
    }

    private InvoiceSellerResponse toSellerResponse(SellerInvoiceProfile seller) {
        if (seller == null) return null;
        return InvoiceSellerResponse.builder()
                .sellerTaxCode(seller.getSellerTaxCode())
                .sellerLegalName(seller.getSellerLegalName())
                .sellerBrandName(seller.getSellerBrandName())
                .sellerAddress(seller.getSellerAddress())
                .sellerPhone(seller.getSellerPhone())
                .sellerEmail(seller.getSellerEmail())
                .sellerBankAccount(seller.getSellerBankAccount())
                .sellerBankName(seller.getSellerBankName())
                .build();
    }

    private InvoiceItemResponse toItemResponse(InvoiceItem item) {
        if (item == null) return null;
        return InvoiceItemResponse.builder()
                .id(item.getId())
                .itemType(item.getItemType())
                .itemName(item.getItemName())
                .unit(item.getUnit())
                .quantity(item.getQuantity())
                .originalQuantity(item.getOriginalQuantity())
                .unitPriceGross(toVndLong(item.getUnitPriceGross()))
                .unitPriceNet(toVndLong(item.getUnitPriceNet()))
                .discountAmount(toVndLong(item.getDiscountAmount()))
                .taxableAmount(toVndLong(item.getTaxableAmount()))
                .taxCategory(item.getTaxCategory())
                .taxTreatment(item.getTaxTreatment())
                .taxRate(item.getTaxRate())
                .taxCode(item.getTaxCode())
                .taxAmount(toVndLong(item.getTaxAmount()))
                .lineTotalGross(toVndLong(item.getLineTotalGross()))
                .build();
    }

    private Long toVndLong(BigDecimal amount) {
        return amount != null ? amount.longValue() : 0L;
    }
}
