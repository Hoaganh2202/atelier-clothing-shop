package com.shop.backend.voucher;

public interface VoucherService {

    Voucher getVoucherByCodeForUpdate(String code);

    Voucher getVoucherByCode(String code);
}
