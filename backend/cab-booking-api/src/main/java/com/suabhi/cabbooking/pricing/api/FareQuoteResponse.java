package com.suabhi.cabbooking.pricing.api;

import com.suabhi.cabbooking.journey.api.JourneyPreviewResponse;
import com.suabhi.cabbooking.journey.provider.City;
import com.suabhi.cabbooking.journey.provider.ServiceType;
import com.suabhi.cabbooking.pricing.domain.FareCalculationResult;
import com.suabhi.cabbooking.pricing.domain.PricingComponent;
import com.suabhi.cabbooking.pricing.domain.PricingRateCard;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Immutable quote view. Journey, tariff and calculation data are snapshots and do not
 * change when the catalogue or a rate card is subsequently modified.
 */
@org.springframework.modulith.NamedInterface("quotes")
public record FareQuoteResponse(
        UUID id,
        Instant createdAt,
        Instant expiresAt,
        Integer rentalHours,
        JourneyPreviewResponse journey,
        RateCardSnapshot rateCard,
        FareCalculationResult fare
) {
    public record RateCardSnapshot(
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
            List<PricingComponent> components
    ) {
        public RateCardSnapshot {
            components = List.copyOf(components);
        }

        public static RateCardSnapshot from(PricingRateCard card) {
            return new RateCardSnapshot(
                    card.id(), card.code(), card.version(), card.serviceType(), card.operatingCity(),
                    card.vehicleCategoryId(), card.vehicleCategoryCode(), card.currency(),
                    card.baseFare(), card.minimumFare(), card.perKilometerRate(), card.perHourRate(),
                    card.perDayRate(), card.includedKilometers(), card.includedHours(),
                    card.driverAllowancePerDay(), card.effectiveFrom(), card.effectiveUntil(),
                    card.components());
        }
    }
}
