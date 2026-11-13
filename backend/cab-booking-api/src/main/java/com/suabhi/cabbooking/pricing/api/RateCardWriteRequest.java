package com.suabhi.cabbooking.pricing.api;

import com.suabhi.cabbooking.journey.provider.City;
import com.suabhi.cabbooking.journey.provider.ServiceType;
import com.suabhi.cabbooking.pricing.domain.PricingCalculationType;
import com.suabhi.cabbooking.pricing.domain.PricingComponentType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record RateCardWriteRequest(
        @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,79}$") String code,
        @Positive int version,
        @NotNull ServiceType serviceType,
        @NotNull City operatingCity,
        @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,79}$") String vehicleCategoryCode,
        @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency,
        @NotNull @DecimalMin("0.00") BigDecimal baseFare,
        @NotNull @DecimalMin("0.00") BigDecimal minimumFare,
        @NotNull @DecimalMin("0.00") BigDecimal perKilometerRate,
        @NotNull @DecimalMin("0.00") BigDecimal perHourRate,
        @NotNull @DecimalMin("0.00") BigDecimal perDayRate,
        @NotNull @DecimalMin("0.00") BigDecimal includedKilometers,
        @NotNull @DecimalMin("0.00") BigDecimal includedHours,
        @NotNull @DecimalMin("0.00") BigDecimal driverAllowancePerDay,
        @NotNull OffsetDateTime effectiveFrom,
        OffsetDateTime effectiveUntil,
        @Size(max = 20) List<@NotNull @Valid ComponentRequest> components
) {
    public RateCardWriteRequest {
        components = components == null ? List.of() : List.copyOf(components);
    }

    @AssertTrue(message = "Effective-until must be later than effective-from.")
    public boolean isEffectiveRangeValid() {
        return effectiveFrom == null || effectiveUntil == null
                || effectiveUntil.isAfter(effectiveFrom);
    }

    public record ComponentRequest(
            @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,59}$") String code,
            @NotBlank @Size(max = 120) String displayName,
            @NotNull PricingComponentType componentType,
            @NotNull PricingCalculationType calculationType,
            @NotNull @DecimalMin("0.00") BigDecimal amount,
            boolean taxable,
            int displayOrder
    ) {
    }
}
