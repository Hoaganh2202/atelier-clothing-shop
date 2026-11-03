package com.shop.backend.voucher;

import com.shop.backend.common.dto.PageResponse;
import com.shop.backend.common.exception.BadRequestException;
import com.shop.backend.common.exception.ConflictException;
import com.shop.backend.common.exception.ResourceNotFoundException;
import com.shop.backend.common.model.DiscountType;
import com.shop.backend.voucher.dto.VoucherCreateRequest;
import com.shop.backend.voucher.dto.VoucherResponse;
import com.shop.backend.voucher.dto.VoucherStatusRequest;
import com.shop.backend.voucher.dto.VoucherUpdateRequest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VoucherAdminServiceImpl implements VoucherAdminService {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z0-9_-]{3,50}$");
    private static final long MAX_PERCENT = 100L;
    private static final int DEFAULT_PER_USER_LIMIT = 1;
    private static final int MAX_PAGE_SIZE = 100;

    private final VoucherRepository voucherRepository;

    @Override
    public PageResponse<VoucherResponse> getVouchers(
            String keyword, DiscountType discountType, Boolean isActive, VoucherTimeStatus status, int page, int size) {
        LocalDateTime now = LocalDateTime.now();
        String normalizedKeyword = StringUtils.hasText(keyword) ? normalizeCode(keyword) : null;
        PageRequest pageable = PageRequest.of(
                Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), Sort.by(Sort.Direction.DESC, "id"));

        return PageResponse.of(voucherRepository
                .findAll(VoucherSpecifications.byFilter(normalizedKeyword, discountType, isActive, status, now), pageable)
                .map(voucher -> toResponse(voucher, now)));
    }

    @Override
    public VoucherResponse getVoucherById(Long id) {
        return toResponse(findVoucher(id), LocalDateTime.now());
    }

    @Override
    @Transactional
    public VoucherResponse createVoucher(VoucherCreateRequest request) {
        String code = normalizeCode(request.getCode());
        if (!CODE_PATTERN.matcher(code).matches()) {
            throw new BadRequestException("Mã voucher chỉ gồm chữ in hoa, số, dấu gạch ngang hoặc gạch dưới, dài 3-50 ký tự");
        }
        validateRules(request);
        LocalDateTime now = LocalDateTime.now();
        if (!request.getEndAt().isAfter(now)) {
            throw new BadRequestException("Thời gian kết thúc phải ở tương lai khi tạo voucher mới");
        }
        if (voucherRepository.existsByCode(code)) {
            throw new ConflictException("Mã voucher '" + code + "' đã tồn tại");
        }

        Voucher voucher = Voucher.builder().code(code).build();
        applyFields(voucher, request);
        voucher.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);
        return toResponse(voucherRepository.save(voucher), now);
    }

    @Override
    @Transactional
    public VoucherResponse updateVoucher(Long id, VoucherUpdateRequest request) {
        Voucher voucher = findVoucher(id);
        validateRules(request);
        applyFields(voucher, request);
        if (request.getIsActive() != null) {
            voucher.setIsActive(request.getIsActive());
        }
        return toResponse(voucherRepository.save(voucher), LocalDateTime.now());
    }

    @Override
    @Transactional
    public VoucherResponse updateVoucherStatus(Long id, VoucherStatusRequest request) {
        Voucher voucher = findVoucher(id);
        voucher.setIsActive(request.getIsActive());
        return toResponse(voucherRepository.save(voucher), LocalDateTime.now());
    }

    private Voucher findVoucher(Long id) {
        return voucherRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy voucher với ID " + id));
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private void validateRules(VoucherUpdateRequest request) {
        if (request.getDiscountType() == DiscountType.PERCENT) {
            if (request.getDiscountValue() > MAX_PERCENT) {
                throw new BadRequestException("Giá trị giảm theo phần trăm phải lớn hơn 0 và không quá 100");
            }
        } else if (request.getMaxDiscount() != null) {
            throw new BadRequestException("Mức giảm tối đa chỉ áp dụng cho loại giảm theo phần trăm");
        }
        if (!request.getStartAt().isBefore(request.getEndAt())) {
            throw new BadRequestException("Thời gian bắt đầu phải trước thời gian kết thúc");
        }
    }

    private void applyFields(Voucher voucher, VoucherUpdateRequest request) {
        voucher.setDiscountType(request.getDiscountType());
        voucher.setDiscountValue(BigDecimal.valueOf(request.getDiscountValue()));
        voucher.setMinOrder(toMoney(request.getMinOrder()));
        voucher.setMaxDiscount(toMoney(request.getMaxDiscount()));
        voucher.setUsageLimit(request.getUsageLimit());
        voucher.setPerUserLimit(
                request.getPerUserLimit() != null ? request.getPerUserLimit() : DEFAULT_PER_USER_LIMIT);
        voucher.setStartAt(request.getStartAt());
        voucher.setEndAt(request.getEndAt());
    }

    private BigDecimal toMoney(Long amount) {
        return amount != null ? BigDecimal.valueOf(amount) : null;
    }

    private Long toLong(BigDecimal amount) {
        return amount != null ? amount.longValue() : null;
    }

    private VoucherTimeStatus resolveTimeStatus(Voucher voucher, LocalDateTime now) {
        if (voucher.getStartAt().isAfter(now)) {
            return VoucherTimeStatus.SCHEDULED;
        }
        return voucher.getEndAt().isBefore(now) ? VoucherTimeStatus.EXPIRED : VoucherTimeStatus.RUNNING;
    }

    private VoucherResponse toResponse(Voucher voucher, LocalDateTime now) {
        return VoucherResponse.builder()
                .id(voucher.getId())
                .code(voucher.getCode())
                .discountType(voucher.getDiscountType())
                .discountValue(toLong(voucher.getDiscountValue()))
                .minOrder(toLong(voucher.getMinOrder()))
                .maxDiscount(toLong(voucher.getMaxDiscount()))
                .usageLimit(voucher.getUsageLimit())
                .perUserLimit(voucher.getPerUserLimit())
                .startAt(voucher.getStartAt())
                .endAt(voucher.getEndAt())
                .isActive(voucher.getIsActive())
                .status(resolveTimeStatus(voucher, now))
                .build();
    }
}
