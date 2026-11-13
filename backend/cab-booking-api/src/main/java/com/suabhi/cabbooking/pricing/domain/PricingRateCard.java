package com.suabhi.cabbooking.pricing.domain;

import com.suabhi.cabbooking.journey.provider.City;
import com.suabhi.cabbooking.journey.provider.ServiceType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PricingRateCard(
        UUID id,
        String code,
        int version,
        ServiceType serviceType,
        City operatingCity,
        UUID vehicleCategoryId,
        String vehicleCategoryCode,
        String currency,
        BigDecimal baseFare,
        BigDecimal minimumFare,
        BigDecimal perKilometerRate,
        BigDecimal perHourRate,
        BigDecimal perDayRate,
        BigDecimal includedKilometers,
        BigDecimal includedHours,
        BigDecimal driverAllowancePerDay,
        Instant effectiveFrom,
        Instant effectiveUntil,
        boolean active,
        List<PricingComponent> components
) {
    public PricingRateCard {
        components = components == null ? List.of() : components.stream()
                .sorted(java.util.Comparator.comparingInt(PricingComponent::displayOrder)
                        .thenComparing(PricingComponent::code))
                .toList();
    }
}
