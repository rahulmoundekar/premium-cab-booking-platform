package com.suabhi.cabbooking.pricing.domain;

import com.suabhi.cabbooking.journey.provider.City;
import com.suabhi.cabbooking.journey.provider.ServiceType;

public record FareCalculationInput(
        ServiceType serviceType,
        City operatingCity,
        long distanceMeters,
        long durationSeconds,
        Integer rentalHours
) {
    public FareCalculationInput {
        if (serviceType == null || operatingCity == null) {
            throw new IllegalArgumentException("Service type and operating city are required.");
        }
        if (distanceMeters <= 0 || durationSeconds <= 0) {
            throw new IllegalArgumentException("Trusted route distance and duration must be positive.");
        }
        if (serviceType == ServiceType.LOCAL_RENTAL && (rentalHours == null || rentalHours <= 0)) {
            throw new IllegalArgumentException("A positive rental package duration is required for local rental.");
        }
        if (serviceType != ServiceType.LOCAL_RENTAL && rentalHours != null) {
            throw new IllegalArgumentException("Rental package duration is only valid for local rental.");
        }
    }
}
