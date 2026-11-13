package com.suabhi.cabbooking.pricing.domain;

import java.math.BigDecimal;
import java.util.List;

public record FareCalculationResult(
        String currency,
        BigDecimal distanceKilometers,
        BigDecimal billableKilometers,
        BigDecimal baseFare,
        BigDecimal distanceCharge,
        BigDecimal usageCharge,
        BigDecimal perDayCharge,
        BigDecimal driverAllowance,
        BigDecimal minimumFareAdjustment,
        BigDecimal surchargeTotal,
        BigDecimal taxTotal,
        BigDecimal totalAmount,
        List<LineItem> components
) {
    public FareCalculationResult {
        components = List.copyOf(components);
    }

    public record LineItem(
            String code,
            String displayName,
            PricingComponentType componentType,
            BigDecimal amount,
            boolean taxable
    ) {}
}
