package com.shop.backend.einvoice;

import com.shop.backend.einvoice.dto.CalculatedInvoiceLineResult;
import com.shop.backend.einvoice.dto.CalculatedInvoiceResult;
import com.shop.backend.einvoice.dto.InvoiceCalculationCommand;
import com.shop.backend.einvoice.dto.InvoiceLineCalculationCommand;
import com.shop.backend.einvoice.exception.InvalidVoucherAllocationException;
import com.shop.backend.einvoice.model.PricingBasis;
import com.shop.backend.einvoice.model.TaxConfiguration;
import com.shop.backend.einvoice.model.TaxTreatment;
import com.shop.backend.einvoice.service.TaxConfigurationService;
import com.shop.backend.einvoice.service.impl.TaxCalculationServiceImpl;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaxCalculationServiceTest {

    @Mock
    private TaxConfigurationService taxConfigurationService;

    private TaxCalculationServiceImpl taxCalculationService;

    private final LocalDateTime taxPoint = LocalDateTime.of(2026, 10, 31, 14, 30, 0);

    @BeforeEach
    void setUp() {
        taxCalculationService = new TaxCalculationServiceImpl(taxConfigurationService);
    }

    private TaxConfiguration createConfig(String category, TaxTreatment treatment, BigDecimal rate, PricingBasis basis) {
        return TaxConfiguration.builder()
                .taxCategory(category)
                .taxTreatment(treatment)
                .taxRate(rate)
                .taxCode(rate != null ? rate.multiply(BigDecimal.valueOf(100)).intValue() + "%" : "KCT")
                .legalReference("TEST_REF")
                .pricingBasis(basis)
                .effectiveFrom(taxPoint.minusDays(10))
                .effectiveTo(taxPoint.plusDays(10))
                .isActive(true)
                .build();
    }

    @Test
    @DisplayName("1. Không có voucher (null): thuế và tiền tính đúng, discount = 0")
    void calculateInvoice_noVoucher_success() {
        when(taxConfigurationService.getEffectiveConfiguration(eq("apparel"), eq(PricingBasis.INCLUSIVE), any()))
                .thenReturn(createConfig("apparel", TaxTreatment.TAXABLE, new BigDecimal("0.08"), PricingBasis.INCLUSIVE));

        InvoiceCalculationCommand cmd = InvoiceCalculationCommand.builder()
                .pricingBasis(PricingBasis.INCLUSIVE)
                .taxPoint(taxPoint)
                .merchandiseVoucherAmount(null)
                .items(List.of(
                        InvoiceLineCalculationCommand.builder()
                                .itemName("Áo thun")
                                .unit("cái")
                                .quantity(2)
                                .unitPrice(new BigDecimal("108000"))
                                .taxCategory("apparel")
                                .isDiscountable(true)
                                .build()
                ))
                .build();

        CalculatedInvoiceResult result = taxCalculationService.calculateInvoice(cmd);

        assertThat(result.getTotalMerchandiseBeforeTax()).isEqualByComparingTo("216000");
        assertThat(result.getTotalMerchandiseDiscount()).isEqualByComparingTo("0");
        assertThat(result.getTotalTaxableAmount()).isEqualByComparingTo("200000");
        assertThat(result.getTotalTaxAmount()).isEqualByComparingTo("16000");
        assertThat(result.getTotalAfterTax()).isEqualByComparingTo("216000");
    }

    @Test
    @DisplayName("2. Voucher bằng 0: hoạt động bình thường, discount = 0")
    void calculateInvoice_voucherZero_success() {
        when(taxConfigurationService.getEffectiveConfiguration(eq("apparel"), eq(PricingBasis.INCLUSIVE), any()))
                .thenReturn(createConfig("apparel", TaxTreatment.TAXABLE, new BigDecimal("0.08"), PricingBasis.INCLUSIVE));

        InvoiceCalculationCommand cmd = InvoiceCalculationCommand.builder()
                .pricingBasis(PricingBasis.INCLUSIVE)
                .taxPoint(taxPoint)
                .merchandiseVoucherAmount(BigDecimal.ZERO)
                .items(List.of(
                        InvoiceLineCalculationCommand.builder()
                                .itemName("Áo sơ mi")
                                .unit("cái")
                                .quantity(1)
                                .unitPrice(new BigDecimal("540000"))
                                .taxCategory("apparel")
                                .isDiscountable(true)
                                .build()
                ))
                .build();

        CalculatedInvoiceResult result = taxCalculationService.calculateInvoice(cmd);

        assertThat(result.getTotalMerchandiseDiscount()).isEqualByComparingTo("0");
        assertThat(result.getTotalTaxableAmount()).isEqualByComparingTo("500000");
        assertThat(result.getTotalTaxAmount()).isEqualByComparingTo("40000");
        assertThat(result.getTotalAfterTax()).isEqualByComparingTo("540000");
    }

    @Test
    @DisplayName("3. Voucher bằng đúng merchandise subtotal (100% discount): taxable = 0, tax = 0, total = 0")
    void calculateInvoice_voucherEqualsSubtotal_success() {
        when(taxConfigurationService.getEffectiveConfiguration(eq("apparel"), eq(PricingBasis.INCLUSIVE), any()))
                .thenReturn(createConfig("apparel", TaxTreatment.TAXABLE, new BigDecimal("0.08"), PricingBasis.INCLUSIVE));

        InvoiceCalculationCommand cmd = InvoiceCalculationCommand.builder()
                .pricingBasis(PricingBasis.INCLUSIVE)
                .taxPoint(taxPoint)
                .merchandiseVoucherAmount(new BigDecimal("200000"))
                .items(List.of(
                        InvoiceLineCalculationCommand.builder()
                                .itemName("Áo thun")
                                .unit("cái")
                                .quantity(2)
                                .unitPrice(new BigDecimal("100000"))
                                .taxCategory("apparel")
                                .isDiscountable(true)
                                .build()
                ))
                .build();

        CalculatedInvoiceResult result = taxCalculationService.calculateInvoice(cmd);

        assertThat(result.getTotalMerchandiseDiscount()).isEqualByComparingTo("200000");
        assertThat(result.getTotalTaxableAmount()).isEqualByComparingTo("0");
        assertThat(result.getTotalTaxAmount()).isEqualByComparingTo("0");
        assertThat(result.getTotalAfterTax()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("4. Voucher lớn hơn merchandise subtotal: ném InvalidVoucherAllocationException")
    void calculateInvoice_voucherExceedsSubtotal_throwsException() {
        when(taxConfigurationService.getEffectiveConfiguration(eq("apparel"), eq(PricingBasis.INCLUSIVE), any()))
                .thenReturn(createConfig("apparel", TaxTreatment.TAXABLE, new BigDecimal("0.08"), PricingBasis.INCLUSIVE));

        InvoiceCalculationCommand cmd = InvoiceCalculationCommand.builder()
                .pricingBasis(PricingBasis.INCLUSIVE)
                .taxPoint(taxPoint)
                .merchandiseVoucherAmount(new BigDecimal("250000"))
                .items(List.of(
                        InvoiceLineCalculationCommand.builder()
                                .itemName("Áo thun")
                                .unit("cái")
                                .quantity(2)
                                .unitPrice(new BigDecimal("100000"))
                                .taxCategory("apparel")
                                .isDiscountable(true)
                                .build()
                ))
                .build();

        assertThatThrownBy(() -> taxCalculationService.calculateInvoice(cmd))
                .isInstanceOf(InvalidVoucherAllocationException.class)
                .hasMessageContaining("exceeds total discountable merchandise");
    }

    @Test
    @DisplayName("5. Đa nhóm thuế (8% và 10%) + nhiều sản phẩm trong nhóm + phí ship riêng: kiểm tra khớp 100% không lệch penny")
    void calculateInvoice_multipleTaxBucketsAndShipping_zeroPennyDrift() {
        when(taxConfigurationService.getEffectiveConfiguration(eq("apparel"), eq(PricingBasis.INCLUSIVE), any()))
                .thenReturn(createConfig("apparel", TaxTreatment.TAXABLE, new BigDecimal("0.08"), PricingBasis.INCLUSIVE));
        when(taxConfigurationService.getEffectiveConfiguration(eq("accessory"), eq(PricingBasis.INCLUSIVE), any()))
                .thenReturn(createConfig("accessory", TaxTreatment.TAXABLE, new BigDecimal("0.10"), PricingBasis.INCLUSIVE));
        when(taxConfigurationService.getEffectiveConfiguration(eq("shipping"), eq(PricingBasis.INCLUSIVE), any()))
                .thenReturn(createConfig("shipping", TaxTreatment.TAXABLE, new BigDecimal("0.10"), PricingBasis.INCLUSIVE));

        // Merchandise gross:
        // Áo sơ mi: 2 * 400,000 = 800,000 (tax 8%)
        // Quần tây: 1 * 600,000 = 600,000 (tax 8%)
        // Ví da:    1 * 1,000,000 = 1,000,000 (tax 10%)
        // Total merchandise = 2,400,000
        // Merchandise voucher = 300,000
        // Shipping fee = 50,000, shipping promotion = 20,000 -> net shipping = 30,000
        // Expected total after tax = 2,400,000 - 300,000 + 30,000 = 2,130,000

        InvoiceCalculationCommand cmd = InvoiceCalculationCommand.builder()
                .pricingBasis(PricingBasis.INCLUSIVE)
                .taxPoint(taxPoint)
                .merchandiseVoucherAmount(new BigDecimal("300000"))
                .shippingFee(new BigDecimal("50000"))
                .shippingPromotionAmount(new BigDecimal("20000"))
                .shippingTaxCategory("shipping")
                .items(List.of(
                        InvoiceLineCalculationCommand.builder()
                                .itemName("Áo sơ mi nam")
                                .unit("cái")
                                .quantity(2)
                                .unitPrice(new BigDecimal("400000"))
                                .taxCategory("apparel")
                                .isDiscountable(true)
                                .build(),
                        InvoiceLineCalculationCommand.builder()
                                .itemName("Quần tây")
                                .unit("cái")
                                .quantity(1)
                                .unitPrice(new BigDecimal("600000"))
                                .taxCategory("apparel")
                                .isDiscountable(true)
                                .build(),
                        InvoiceLineCalculationCommand.builder()
                                .itemName("Ví da")
                                .unit("cái")
                                .quantity(1)
                                .unitPrice(new BigDecimal("1000000"))
                                .taxCategory("accessory")
                                .isDiscountable(true)
                                .build()
                ))
                .build();

        CalculatedInvoiceResult result = taxCalculationService.calculateInvoice(cmd);

        assertThat(result.getTotalMerchandiseBeforeTax()).isEqualByComparingTo("2400000");
        assertThat(result.getTotalMerchandiseDiscount()).isEqualByComparingTo("300000");
        assertThat(result.getTotalShippingBeforeTax()).isEqualByComparingTo("50000");
        assertThat(result.getTotalShippingDiscount()).isEqualByComparingTo("20000");

        // Sum of all lines gross after discount must exactly equal 2,130,000
        BigDecimal sumLinesGross = result.getLines().stream()
                .map(CalculatedInvoiceLineResult::getLineTotalGross)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal shippingGross = result.getShippingLine().getLineTotalGross();

        assertThat(sumLinesGross.add(shippingGross)).isEqualByComparingTo("2130000");
        assertThat(result.getTotalAfterTax()).isEqualByComparingTo("2130000");
        assertThat(result.getTotalTaxableAmount().add(result.getTotalTaxAmount()))
                .isEqualByComparingTo("2130000");
    }

    @Test
    @DisplayName("6. Giá Net (EXCLUSIVE pricing): tính thuế cộng thêm trên giá sau chiết khấu")
    void calculateInvoice_exclusivePricing_success() {
        when(taxConfigurationService.getEffectiveConfiguration(eq("apparel"), eq(PricingBasis.EXCLUSIVE), any()))
                .thenReturn(createConfig("apparel", TaxTreatment.TAXABLE, new BigDecimal("0.10"), PricingBasis.EXCLUSIVE));

        // Line net: 2 * 100,000 = 200,000
        // Voucher on net: 50,000
        // Net after discount: 150,000
        // Tax 10%: 15,000
        // Total gross: 165,000

        InvoiceCalculationCommand cmd = InvoiceCalculationCommand.builder()
                .pricingBasis(PricingBasis.EXCLUSIVE)
                .taxPoint(taxPoint)
                .merchandiseVoucherAmount(new BigDecimal("50000"))
                .items(List.of(
                        InvoiceLineCalculationCommand.builder()
                                .itemName("Áo khoác")
                                .unit("cái")
                                .quantity(2)
                                .unitPrice(new BigDecimal("100000"))
                                .taxCategory("apparel")
                                .isDiscountable(true)
                                .build()
                ))
                .build();

        CalculatedInvoiceResult result = taxCalculationService.calculateInvoice(cmd);

        assertThat(result.getTotalTaxableAmount()).isEqualByComparingTo("150000");
        assertThat(result.getTotalTaxAmount()).isEqualByComparingTo("15000");
        assertThat(result.getTotalAfterTax()).isEqualByComparingTo("165000");
    }

    @Test
    @DisplayName("7. Số tiền chia không hết (Indivisible divisions): deterministic penny allocation hấp thu số dư")
    void calculateInvoice_indivisibleFractions_deterministicPennyAllocation() {
        when(taxConfigurationService.getEffectiveConfiguration(eq("apparel"), eq(PricingBasis.INCLUSIVE), any()))
                .thenReturn(createConfig("apparel", TaxTreatment.TAXABLE, new BigDecimal("0.08"), PricingBasis.INCLUSIVE));

        // 3 items with equal prices: 100,000 each (total 300,000)
        // Voucher: 100,000 (100,000 / 3 = 33,333.333...)
        // Deterministic allocation must allocate: 33,334 to largest/first, and 33,333 to others
        // Sum of discounts MUST equal 100,000 exactly!

        InvoiceCalculationCommand cmd = InvoiceCalculationCommand.builder()
                .pricingBasis(PricingBasis.INCLUSIVE)
                .taxPoint(taxPoint)
                .merchandiseVoucherAmount(new BigDecimal("100000"))
                .items(List.of(
                        InvoiceLineCalculationCommand.builder()
                                .itemName("Mặt hàng A")
                                .unit("cái")
                                .quantity(1)
                                .unitPrice(new BigDecimal("100000"))
                                .taxCategory("apparel")
                                .isDiscountable(true)
                                .build(),
                        InvoiceLineCalculationCommand.builder()
                                .itemName("Mặt hàng B")
                                .unit("cái")
                                .quantity(1)
                                .unitPrice(new BigDecimal("100000"))
                                .taxCategory("apparel")
                                .isDiscountable(true)
                                .build(),
                        InvoiceLineCalculationCommand.builder()
                                .itemName("Mặt hàng C")
                                .unit("cái")
                                .quantity(1)
                                .unitPrice(new BigDecimal("100000"))
                                .taxCategory("apparel")
                                .isDiscountable(true)
                                .build()
                ))
                .build();

        CalculatedInvoiceResult result = taxCalculationService.calculateInvoice(cmd);

        BigDecimal totalDiscountAllocated = result.getLines().stream()
                .map(CalculatedInvoiceLineResult::getDiscountAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(totalDiscountAllocated).isEqualByComparingTo("100000");
        assertThat(result.getTotalAfterTax()).isEqualByComparingTo("200000");
        assertThat(result.getTotalTaxableAmount().add(result.getTotalTaxAmount())).isEqualByComparingTo("200000");
    }

    @Test
    @DisplayName("8. Mặt hàng không chịu voucher (isDiscountable = false): không bị phân bổ chiết khấu")
    void calculateInvoice_nonDiscountableItem_excludedFromVoucher() {
        when(taxConfigurationService.getEffectiveConfiguration(eq("apparel"), eq(PricingBasis.INCLUSIVE), any()))
                .thenReturn(createConfig("apparel", TaxTreatment.TAXABLE, new BigDecimal("0.08"), PricingBasis.INCLUSIVE));
        when(taxConfigurationService.getEffectiveConfiguration(eq("luxury"), eq(PricingBasis.INCLUSIVE), any()))
                .thenReturn(createConfig("luxury", TaxTreatment.TAXABLE, new BigDecimal("0.10"), PricingBasis.INCLUSIVE));

        InvoiceCalculationCommand cmd = InvoiceCalculationCommand.builder()
                .pricingBasis(PricingBasis.INCLUSIVE)
                .taxPoint(taxPoint)
                .merchandiseVoucherAmount(new BigDecimal("50000"))
                .items(List.of(
                        InvoiceLineCalculationCommand.builder()
                                .itemName("Áo thun cơ bản")
                                .unit("cái")
                                .quantity(1)
                                .unitPrice(new BigDecimal("200000"))
                                .taxCategory("apparel")
                                .isDiscountable(true)
                                .build(),
                        InvoiceLineCalculationCommand.builder()
                                .itemName("Hàng xa xỉ không giảm giá")
                                .unit("cái")
                                .quantity(1)
                                .unitPrice(new BigDecimal("500000"))
                                .taxCategory("luxury")
                                .isDiscountable(false)
                                .build()
                ))
                .build();

        CalculatedInvoiceResult result = taxCalculationService.calculateInvoice(cmd);

        CalculatedInvoiceLineResult discountableLine = result.getLines().get(0);
        CalculatedInvoiceLineResult nonDiscountableLine = result.getLines().get(1);

        assertThat(discountableLine.getDiscountAmount()).isEqualByComparingTo("50000");
        assertThat(nonDiscountableLine.getDiscountAmount()).isEqualByComparingTo("0");
        assertThat(result.getTotalAfterTax()).isEqualByComparingTo("650000");
    }

    @Test
    @DisplayName("9. Mặt hàng Miễn thuế (EXEMPT) / Không chịu thuế (NON_TAXABLE): tax_amount = 0")
    void calculateInvoice_exemptAndNonTaxable_zeroTax() {
        when(taxConfigurationService.getEffectiveConfiguration(eq("exempt_goods"), eq(PricingBasis.INCLUSIVE), any()))
                .thenReturn(createConfig("exempt_goods", TaxTreatment.EXEMPT, null, PricingBasis.INCLUSIVE));

        InvoiceCalculationCommand cmd = InvoiceCalculationCommand.builder()
                .pricingBasis(PricingBasis.INCLUSIVE)
                .taxPoint(taxPoint)
                .items(List.of(
                        InvoiceLineCalculationCommand.builder()
                                .itemName("Hàng miễn thuế")
                                .unit("cái")
                                .quantity(1)
                                .unitPrice(new BigDecimal("150000"))
                                .taxCategory("exempt_goods")
                                .build()
                ))
                .build();

        CalculatedInvoiceResult result = taxCalculationService.calculateInvoice(cmd);

        assertThat(result.getTotalTaxableAmount()).isEqualByComparingTo("150000");
        assertThat(result.getTotalTaxAmount()).isEqualByComparingTo("0");
        assertThat(result.getTotalAfterTax()).isEqualByComparingTo("150000");
    }

    @Test
    @DisplayName("10. Tax point thay đổi giữa 2 thời điểm: cấu hình thuế được tra cứu động theo ngày giao hàng")
    void calculateInvoice_taxPointLookup_dynamicConfiguration() {
        LocalDateTime deliveryDate2026 = LocalDateTime.of(2026, 12, 28, 10, 0);
        LocalDateTime deliveryDate2027 = LocalDateTime.of(2027, 1, 5, 10, 0);

        TaxConfiguration config2026 = createConfig("apparel", TaxTreatment.TAXABLE, new BigDecimal("0.08"), PricingBasis.INCLUSIVE);
        TaxConfiguration config2027 = createConfig("apparel", TaxTreatment.TAXABLE, new BigDecimal("0.10"), PricingBasis.INCLUSIVE);

        when(taxConfigurationService.getEffectiveConfiguration(eq("apparel"), eq(PricingBasis.INCLUSIVE), eq(deliveryDate2026)))
                .thenReturn(config2026);
        when(taxConfigurationService.getEffectiveConfiguration(eq("apparel"), eq(PricingBasis.INCLUSIVE), eq(deliveryDate2027)))
                .thenReturn(config2027);

        InvoiceCalculationCommand cmd2026 = InvoiceCalculationCommand.builder()
                .pricingBasis(PricingBasis.INCLUSIVE)
                .taxPoint(deliveryDate2026)
                .items(List.of(
                        InvoiceLineCalculationCommand.builder()
                                .itemName("Áo khoác")
                                .unit("cái")
                                .quantity(1)
                                .unitPrice(new BigDecimal("1080000"))
                                .taxCategory("apparel")
                                .build()
                ))
                .build();

        InvoiceCalculationCommand cmd2027 = InvoiceCalculationCommand.builder()
                .pricingBasis(PricingBasis.INCLUSIVE)
                .taxPoint(deliveryDate2027)
                .items(List.of(
                        InvoiceLineCalculationCommand.builder()
                                .itemName("Áo khoác")
                                .unit("cái")
                                .quantity(1)
                                .unitPrice(new BigDecimal("1100000"))
                                .taxCategory("apparel")
                                .build()
                ))
                .build();

        CalculatedInvoiceResult res2026 = taxCalculationService.calculateInvoice(cmd2026);
        CalculatedInvoiceResult res2027 = taxCalculationService.calculateInvoice(cmd2027);

        assertThat(res2026.getLines().get(0).getTaxRate()).isEqualByComparingTo("0.08");
        assertThat(res2026.getTotalTaxAmount()).isEqualByComparingTo("80000");

        assertThat(res2027.getLines().get(0).getTaxRate()).isEqualByComparingTo("0.10");
        assertThat(res2027.getTotalTaxAmount()).isEqualByComparingTo("100000");
    }
}
