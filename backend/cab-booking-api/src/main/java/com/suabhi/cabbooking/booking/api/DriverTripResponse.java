package com.suabhi.cabbooking.booking.api;

import java.time.Instant;
import java.util.UUID;

public record DriverTripResponse(
        UUID driverProfileId,
        String driverCode,
        BookingResponse booking,
        UUID vehicleId,
        String vehicleNumber,
        Instant reservedFromAt,
        Instant reservedUntilAt,
        Instant assignedAt,
        Instant startedAt,
        Instant completedAt
) {
}
