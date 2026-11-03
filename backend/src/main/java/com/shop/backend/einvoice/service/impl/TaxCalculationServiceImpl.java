package com.shop.backend.einvoice.service.impl;

import com.shop.backend.einvoice.dto.CalculatedInvoiceLineResult;
import com.shop.backend.einvoice.dto.CalculatedInvoiceResult;
import com.shop.backend.einvoice.dto.InvoiceCalculationCommand;
import com.shop.backend.einvoice.dto.InvoiceLineCalculationCommand;
import com.shop.backend.einvoice.exception.InvalidTaxCalculationException;
import com.shop.backend.einvoice.exception.InvalidVoucherAllocationException;
import com.shop.backend.einvoice.model.InvoiceItemType;
import com.shop.backend.einvoice.model.PricingBasis;
import com.shop.backend.einvoice.model.TaxConfiguration;
import com.shop.backend.einvoice.model.TaxTreatment;
import com.shop.backend.einvoice.service.TaxCalculationService;
import com.shop.backend.einvoice.service.TaxConfigurationService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TaxCalculationServiceImpl implements TaxCalculationService {

    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;
    private static final int CURRENCY_SCALE = 0;
    private static final int UNIT_PRICE_SCALE = 4;

    private final TaxConfigurationService taxConfigurationService;

    @Override
    public CalculatedInvoiceResult calculateInvoice(InvoiceCalculationCommand command) {
        validateCommand(command);

        PricingBasis pricingBasis = command.getPricingBasis();
        LocalDateTime taxPoint = command.getTaxPoint();
        List<InvoiceLineCalculationCommand> items = command.getItems();

        // Step 1: Resolve tax configuration for each line
        List<LineContext> lineContexts = new ArrayList<>();
        BigDecimal totalDiscountableMerchandise = BigDecimal.ZERO;
        BigDecimal totalMerchandisePreDiscount = BigDecimal.ZERO;

        for (InvoiceLineCalculationCommand item : items) {
            TaxConfiguration config = taxConfigurationService.getEffectiveConfiguration(
                    item.getTaxCategory(), pricingBasis, taxPoint);

            BigDecimal quantity = BigDecimal.valueOf(item.getQuantity());
            BigDecimal unitPrice = item.getUnitPrice().setScale(2, ROUNDING_MODE);
            BigDecimal linePreDiscount = unitPrice.multiply(quantity).setScale(CURRENCY_SCALE, ROUNDING_MODE);

            boolean isDiscountable = Boolean.TRUE.equals(item.getIsDiscountable());
            if (isDiscountable) {
                totalDiscountableMerchandise = totalDiscountableMerchandise.add(linePreDiscount);
            }
            totalMerchandisePreDiscount = totalMerchandisePreDiscount.add(linePreDiscount);

            lineContexts.add(new LineContext(item, config, linePreDiscount, isDiscountable));
        }

        // Step 2: Validate merchandise voucher
        BigDecimal voucherAmount = command.getMerchandiseVoucherAmount() != null
                ? command.getMerchandiseVoucherAmount().setScale(CURRENCY_SCALE, ROUNDING_MODE)
                : BigDecimal.ZERO;

        if (voucherAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidVoucherAllocationException("Merchandise voucher amount cannot be negative");
        }

        if (voucherAmount.compareTo(totalDiscountableMerchandise) > 0) {
            throw new InvalidVoucherAllocationException(String.format(
                    "Merchandise voucher (%s) exceeds total discountable merchandise (%s)",
                    voucherAmount, totalDiscountableMerchandise));
        }

        // Step 3: Allocate merchandise voucher across tax buckets and lines
        Map<LineContext, BigDecimal> allocatedDiscounts = allocateVoucher(
                lineContexts, voucherAmount, totalDiscountableMerchandise);

        // Step 4: Compute tax and amounts for each merchandise line
        List<CalculatedInvoiceLineResult> calculatedLines = new ArrayList<>();
        BigDecimal totalMerchandiseDiscount = BigDecimal.ZERO;
        BigDecimal totalTaxableAmount = BigDecimal.ZERO;
        BigDecimal totalTaxAmount = BigDecimal.ZERO;
        BigDecimal totalMerchandiseGrossAfterDiscount = BigDecimal.ZERO;

        for (LineContext ctx : lineContexts) {
            BigDecimal lineDiscount = allocatedDiscounts.getOrDefault(ctx, BigDecimal.ZERO);
            totalMerchandiseDiscount = totalMerchandiseDiscount.add(lineDiscount);

            CalculatedInvoiceLineResult lineResult = computeLineResult(ctx, lineDiscount, pricingBasis);
            calculatedLines.add(lineResult);

            totalTaxableAmount = totalTaxableAmount.add(lineResult.getTaxableAmount());
            totalTaxAmount = totalTaxAmount.add(lineResult.getTaxAmount());
            totalMerchandiseGrossAfterDiscount = totalMerchandiseGrossAfterDiscount.add(lineResult.getLineTotalGross());
        }

        // Step 5: Process Shipping Fee & Promotion independently
        CalculatedInvoiceLineResult shippingLine = null;
        BigDecimal totalShippingBeforeTax = BigDecimal.ZERO;
        BigDecimal totalShippingDiscount = BigDecimal.ZERO;
        BigDecimal shippingGrossAfterDiscount = BigDecimal.ZERO;

        if (command.getShippingFee() != null && command.getShippingFee().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal shippingGross = command.getShippingFee().setScale(CURRENCY_SCALE, ROUNDING_MODE);
            totalShippingBeforeTax = shippingGross;

            BigDecimal shippingPromotion = command.getShippingPromotionAmount() != null
                    ? command.getShippingPromotionAmount().setScale(CURRENCY_SCALE, ROUNDING_MODE)
                    : BigDecimal.ZERO;

            if (shippingPromotion.compareTo(BigDecimal.ZERO) < 0) {
                throw new InvalidVoucherAllocationException("Shipping promotion amount cannot be negative");
            }

            BigDecimal shippingDiscount = shippingPromotion.min(shippingGross);
            totalShippingDiscount = shippingDiscount;
            shippingGrossAfterDiscount = shippingGross.subtract(shippingDiscount);

            String shippingCategory = command.getShippingTaxCategory() != null && !command.getShippingTaxCategory().isBlank()
                    ? command.getShippingTaxCategory()
                    : "shipping";

            TaxConfiguration shippingConfig = taxConfigurationService.getEffectiveConfiguration(
                    shippingCategory, pricingBasis, taxPoint);

            shippingLine = computeShippingLineResult(
                    shippingGross, shippingDiscount, shippingGrossAfterDiscount, shippingConfig, pricingBasis);

            totalTaxableAmount = totalTaxableAmount.add(shippingLine.getTaxableAmount());
            totalTaxAmount = totalTaxAmount.add(shippingLine.getTaxAmount());
        }

        BigDecimal totalAfterTax = totalTaxableAmount.add(totalTaxAmount);

        // Step 6: Zero penny drift reconciliation check
        BigDecimal expectedTotalGross = totalMerchandiseGrossAfterDiscount.add(shippingGrossAfterDiscount);
        if (totalAfterTax.compareTo(expectedTotalGross) != 0) {
            throw new InvalidTaxCalculationException(String.format(
                    "Reconciliation failure: totalAfterTax (%s) does not match expected gross after discount (%s)",
                    totalAfterTax, expectedTotalGross));
        }

        return CalculatedInvoiceResult.builder()
                .pricingBasis(pricingBasis)
                .taxPoint(taxPoint)
                .lines(calculatedLines)
                .shippingLine(shippingLine)
                .totalMerchandiseBeforeTax(totalMerchandisePreDiscount)
                .totalMerchandiseDiscount(totalMerchandiseDiscount)
                .totalShippingBeforeTax(totalShippingBeforeTax)
                .totalShippingDiscount(totalShippingDiscount)
                .totalTaxableAmount(totalTaxableAmount)
                .totalTaxAmount(totalTaxAmount)
                .totalAfterTax(totalAfterTax)
                .build();
    }

    private Map<LineContext, BigDecimal> allocateVoucher(
            List<LineContext> lines, BigDecimal totalVoucher, BigDecimal totalDiscountable) {
        Map<LineContext, BigDecimal> result = new HashMap<>();
        for (LineContext line : lines) {
            result.put(line, BigDecimal.ZERO);
        }

        if (totalVoucher.compareTo(BigDecimal.ZERO) == 0 || totalDiscountable.compareTo(BigDecimal.ZERO) == 0) {
            return result;
        }

        // Group discountable lines by Tax Bucket (TaxCategory + TaxTreatment + TaxRate)
        Map<String, List<LineContext>> bucketMap = new HashMap<>();
        for (LineContext line : lines) {
            if (line.isDiscountable()) {
                String bucketKey = getBucketKey(line.getConfig());
                bucketMap.computeIfAbsent(bucketKey, k -> new ArrayList<>()).add(line);
            }
        }

        // Calculate Bucket-level subtotals
        Map<String, BigDecimal> bucketSubtotals = new HashMap<>();
        for (Map.Entry<String, List<LineContext>> entry : bucketMap.entrySet()) {
            BigDecimal sum = entry.getValue().stream()
                    .map(LineContext::getLinePreDiscount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            bucketSubtotals.put(entry.getKey(), sum);
        }

        // Allocate voucher across buckets
        Map<String, BigDecimal> bucketVouchers = new HashMap<>();
        BigDecimal allocatedToBucketsSum = BigDecimal.ZERO;
        String largestBucketKey = null;
        BigDecimal largestBucketSubtotal = BigDecimal.valueOf(-1);

        List<String> sortedBucketKeys = new ArrayList<>(bucketMap.keySet());
        Collections.sort(sortedBucketKeys);

        for (String bucketKey : sortedBucketKeys) {
            BigDecimal bucketSubtotal = bucketSubtotals.get(bucketKey);
            if (bucketSubtotal.compareTo(largestBucketSubtotal) > 0) {
                largestBucketSubtotal = bucketSubtotal;
                largestBucketKey = bucketKey;
            }

            BigDecimal bucketShare = totalVoucher
                    .multiply(bucketSubtotal)
                    .divide(totalDiscountable, CURRENCY_SCALE, ROUNDING_MODE);

            bucketVouchers.put(bucketKey, bucketShare);
            allocatedToBucketsSum = allocatedToBucketsSum.add(bucketShare);
        }

        // Penny adjustment across buckets
        BigDecimal bucketRemainder = totalVoucher.subtract(allocatedToBucketsSum);
        if (bucketRemainder.compareTo(BigDecimal.ZERO) != 0 && largestBucketKey != null) {
            BigDecimal adjusted = bucketVouchers.get(largestBucketKey).add(bucketRemainder);
            bucketVouchers.put(largestBucketKey, adjusted);
        }

        // Allocate within each bucket to lines
        for (String bucketKey : sortedBucketKeys) {
            List<LineContext> bucketLines = bucketMap.get(bucketKey);
            BigDecimal bucketVoucher = bucketVouchers.get(bucketKey);
            BigDecimal bucketSubtotal = bucketSubtotals.get(bucketKey);

            if (bucketVoucher.compareTo(BigDecimal.ZERO) == 0 || bucketSubtotal.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }

            // Sort lines in bucket deterministically by pre-discount descending
            bucketLines.sort(Comparator.comparing(LineContext::getLinePreDiscount).reversed()
                    .thenComparing(l -> l.getCommand().getItemName()));

            BigDecimal lineAllocatedSum = BigDecimal.ZERO;
            LineContext largestLine = bucketLines.get(0);

            for (LineContext line : bucketLines) {
                BigDecimal lineShare = bucketVoucher
                        .multiply(line.getLinePreDiscount())
                        .divide(bucketSubtotal, CURRENCY_SCALE, ROUNDING_MODE);

                // Cap to line amount
                lineShare = lineShare.min(line.getLinePreDiscount());

                result.put(line, lineShare);
                lineAllocatedSum = lineAllocatedSum.add(lineShare);
            }

            // Penny adjustment within bucket
            BigDecimal lineRemainder = bucketVoucher.subtract(lineAllocatedSum);
            if (lineRemainder.compareTo(BigDecimal.ZERO) != 0) {
                BigDecimal existing = result.get(largestLine);
                BigDecimal finalAmount = existing.add(lineRemainder).min(largestLine.getLinePreDiscount());
                result.put(largestLine, finalAmount);
            }
        }

        return result;
    }

    private CalculatedInvoiceLineResult computeLineResult(
            LineContext ctx, BigDecimal lineDiscount, PricingBasis pricingBasis) {
        InvoiceLineCalculationCommand cmd = ctx.getCommand();
        TaxConfiguration config = ctx.getConfig();
        BigDecimal quantity = BigDecimal.valueOf(cmd.getQuantity());

        BigDecimal linePreDiscount = ctx.getLinePreDiscount();
        BigDecimal taxableAmount;
        BigDecimal taxAmount;
        BigDecimal lineTotalGross;
        BigDecimal unitPriceGross;
        BigDecimal unitPriceNet;

        TaxTreatment treatment = config.getTaxTreatment();
        BigDecimal taxRate = config.getTaxRate() != null ? config.getTaxRate() : BigDecimal.ZERO;

        if (pricingBasis == PricingBasis.INCLUSIVE) {
            unitPriceGross = cmd.getUnitPrice().setScale(2, ROUNDING_MODE);
            lineTotalGross = linePreDiscount.subtract(lineDiscount);

            if (treatment == TaxTreatment.TAXABLE && taxRate.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal divisor = BigDecimal.ONE.add(taxRate);
                taxableAmount = lineTotalGross.divide(divisor, CURRENCY_SCALE, ROUNDING_MODE);
                taxAmount = lineTotalGross.subtract(taxableAmount);
            } else {
                taxableAmount = lineTotalGross;
                taxAmount = BigDecimal.ZERO;
            }
            unitPriceNet = quantity.compareTo(BigDecimal.ZERO) > 0
                    ? taxableAmount.divide(quantity, UNIT_PRICE_SCALE, ROUNDING_MODE)
                    : BigDecimal.ZERO;
        } else {
            unitPriceNet = cmd.getUnitPrice().setScale(UNIT_PRICE_SCALE, ROUNDING_MODE);
            BigDecimal lineNetAfterDiscount = linePreDiscount.subtract(lineDiscount);
            taxableAmount = lineNetAfterDiscount;

            if (treatment == TaxTreatment.TAXABLE && taxRate.compareTo(BigDecimal.ZERO) > 0) {
                taxAmount = taxableAmount.multiply(taxRate).setScale(CURRENCY_SCALE, ROUNDING_MODE);
                lineTotalGross = taxableAmount.add(taxAmount);
                unitPriceGross = unitPriceNet.multiply(BigDecimal.ONE.add(taxRate)).setScale(2, ROUNDING_MODE);
            } else {
                taxAmount = BigDecimal.ZERO;
                lineTotalGross = taxableAmount;
                unitPriceGross = unitPriceNet.setScale(2, ROUNDING_MODE);
            }
        }

        return CalculatedInvoiceLineResult.builder()
                .orderDetailId(cmd.getOrderDetailId())
                .fulfillmentEventItemId(cmd.getFulfillmentEventItemId())
                .itemType(InvoiceItemType.MERCHANDISE)
                .itemName(cmd.getItemName())
                .unit(cmd.getUnit())
                .quantity(cmd.getQuantity())
                .unitPriceGross(unitPriceGross)
                .unitPriceNet(unitPriceNet)
                .discountAmount(lineDiscount)
                .taxableAmount(taxableAmount)
                .taxCategory(config.getTaxCategory())
                .taxTreatment(config.getTaxTreatment())
                .taxRate(config.getTaxRate())
                .taxCode(config.getTaxCode())
                .legalReference(config.getLegalReference())
                .taxAmount(taxAmount)
                .lineTotalGross(lineTotalGross)
                .build();
    }

    private CalculatedInvoiceLineResult computeShippingLineResult(
            BigDecimal shippingGross, BigDecimal shippingDiscount, BigDecimal shippingGrossAfterDiscount,
            TaxConfiguration config, PricingBasis pricingBasis) {

        TaxTreatment treatment = config.getTaxTreatment();
        BigDecimal taxRate = config.getTaxRate() != null ? config.getTaxRate() : BigDecimal.ZERO;

        BigDecimal taxableAmount;
        BigDecimal taxAmount;
        BigDecimal lineTotalGross;
        BigDecimal unitPriceGross;
        BigDecimal unitPriceNet;

        if (pricingBasis == PricingBasis.INCLUSIVE) {
            unitPriceGross = shippingGross;
            lineTotalGross = shippingGrossAfterDiscount;

            if (treatment == TaxTreatment.TAXABLE && taxRate.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal divisor = BigDecimal.ONE.add(taxRate);
                taxableAmount = lineTotalGross.divide(divisor, CURRENCY_SCALE, ROUNDING_MODE);
                taxAmount = lineTotalGross.subtract(taxableAmount);
            } else {
                taxableAmount = lineTotalGross;
                taxAmount = BigDecimal.ZERO;
            }
            unitPriceNet = taxableAmount.setScale(UNIT_PRICE_SCALE, ROUNDING_MODE);
        } else {
            unitPriceNet = shippingGross.setScale(UNIT_PRICE_SCALE, ROUNDING_MODE);
            taxableAmount = shippingGrossAfterDiscount;

            if (treatment == TaxTreatment.TAXABLE && taxRate.compareTo(BigDecimal.ZERO) > 0) {
                taxAmount = taxableAmount.multiply(taxRate).setScale(CURRENCY_SCALE, ROUNDING_MODE);
                lineTotalGross = taxableAmount.add(taxAmount);
                unitPriceGross = unitPriceNet.multiply(BigDecimal.ONE.add(taxRate)).setScale(2, ROUNDING_MODE);
            } else {
                taxAmount = BigDecimal.ZERO;
                lineTotalGross = taxableAmount;
                unitPriceGross = unitPriceNet.setScale(2, ROUNDING_MODE);
            }
        }

        return CalculatedInvoiceLineResult.builder()
                .itemType(InvoiceItemType.SHIPPING_FEE)
                .itemName("Cước phí vận chuyển")
                .unit("chuyến")
                .quantity(1)
                .unitPriceGross(unitPriceGross)
                .unitPriceNet(unitPriceNet)
                .discountAmount(shippingDiscount)
                .taxableAmount(taxableAmount)
                .taxCategory(config.getTaxCategory())
                .taxTreatment(config.getTaxTreatment())
                .taxRate(config.getTaxRate())
                .taxCode(config.getTaxCode())
                .legalReference(config.getLegalReference())
                .taxAmount(taxAmount)
                .lineTotalGross(lineTotalGross)
                .build();
    }

    private String getBucketKey(TaxConfiguration config) {
        return String.format("%s:%s:%s",
                config.getTaxCategory().toLowerCase(),
                config.getTaxTreatment(),
                config.getTaxRate() != null ? config.getTaxRate().toPlainString() : "null");
    }

    private void validateCommand(InvoiceCalculationCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        if (command.getTaxPoint() == null) {
            throw new IllegalArgumentException("taxPoint must not be null");
        }
        if (command.getPricingBasis() == null) {
            throw new IllegalArgumentException("pricingBasis must not be null");
        }
        if (command.getItems() == null || command.getItems().isEmpty()) {
            throw new IllegalArgumentException("items must not be null or empty");
        }

        for (InvoiceLineCalculationCommand item : command.getItems()) {
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new IllegalArgumentException("Item quantity must be greater than zero");
            }
            if (item.getUnitPrice() == null || item.getUnitPrice().compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Item unit price cannot be negative");
            }
            if (item.getTaxCategory() == null || item.getTaxCategory().isBlank()) {
                throw new IllegalArgumentException("Item taxCategory must not be null or blank");
            }
        }
    }

    private static class LineContext {
        private final InvoiceLineCalculationCommand command;
        private final TaxConfiguration config;
        private final BigDecimal linePreDiscount;
        private final boolean discountable;

        public LineContext(InvoiceLineCalculationCommand command, TaxConfiguration config,
                           BigDecimal linePreDiscount, boolean discountable) {
            this.command = command;
            this.config = config;
            this.linePreDiscount = linePreDiscount;
            this.discountable = discountable;
        }

        public InvoiceLineCalculationCommand getCommand() { return command; }
        public TaxConfiguration getConfig() { return config; }
        public BigDecimal getLinePreDiscount() { return linePreDiscount; }
        public boolean isDiscountable() { return discountable; }
    }
}
