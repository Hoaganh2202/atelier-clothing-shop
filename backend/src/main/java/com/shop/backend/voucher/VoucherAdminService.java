package com.shop.backend.voucher;

import com.shop.backend.common.dto.PageResponse;
import com.shop.backend.common.model.DiscountType;
import com.shop.backend.voucher.dto.VoucherCreateRequest;
import com.shop.backend.voucher.dto.VoucherResponse;
import com.shop.backend.voucher.dto.VoucherStatusRequest;
import com.shop.backend.voucher.dto.VoucherUpdateRequest;

public interface VoucherAdminService {

    PageResponse<VoucherResponse> getVouchers(
            String keyword, DiscountType discountType, Boolean isActive, VoucherTimeStatus status, int page, int size);

    VoucherResponse getVoucherById(Long id);

    VoucherResponse createVoucher(VoucherCreateRequest request);

    VoucherResponse updateVoucher(Long id, VoucherUpdateRequest request);

    VoucherResponse updateVoucherStatus(Long id, VoucherStatusRequest request);
}
