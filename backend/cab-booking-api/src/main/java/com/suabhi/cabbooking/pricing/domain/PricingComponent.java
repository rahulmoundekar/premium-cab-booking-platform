package com.suabhi.cabbooking.pricing.domain;

import java.math.BigDecimal;

public record PricingComponent(
        String code,
        String displayName,
        PricingComponentType componentType,
        PricingCalculationType calculationType,
        BigDecimal amount,
        boolean taxable,
        int displayOrder
) {
    public PricingComponent {
        if (code == null || code.isBlank() || displayName == null || displayName.isBlank()
                || componentType == null || calculationType == null || amount == null
                || amount.signum() < 0 || displayOrder < 0) {
            throw new IllegalArgumentException("Pricing component details are invalid.");
        }
        if (componentType == PricingComponentType.TAX && taxable) {
            throw new IllegalArgumentException("Tax components cannot be marked as taxable.");
        }
    }
}
