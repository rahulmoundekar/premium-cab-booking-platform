package com.suabhi.cabbooking.booking.api;

import java.time.Instant;
import java.util.UUID;

public record BookingAllocationResponse(
        UUID allocationId,
        UUID vehicleId,
        String vehicleNumber,
        Instant reservedFromAt,
        Instant reservedUntilAt
) {
}
