package com.suabhi.cabbooking.pricing.api;

import com.suabhi.cabbooking.journey.api.JourneyPreviewRequest;
import com.suabhi.cabbooking.journey.provider.ServiceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * A quote request contains journey intent and a catalogue category, never client-supplied
 * distance, duration or monetary values.
 */
public record FareQuoteRequest(
        @NotNull @Valid JourneyPreviewRequest journey,
        @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,59}$") String vehicleCategoryCode,
        @Positive Integer rentalHours
) {
    @AssertTrue(message = "Rental hours are required only for LOCAL_RENTAL journeys.")
    public boolean isRentalHoursValid() {
        if (journey == null || journey.serviceType() == null) {
            return true;
        }
        return journey.serviceType() == ServiceType.LOCAL_RENTAL
                ? rentalHours != null && rentalHours > 0
                : rentalHours == null;
    }
}
