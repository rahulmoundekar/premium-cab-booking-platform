package com.suabhi.cabbooking.booking.api;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ConfirmBookingRequest(
        @NotNull UUID vehicleId,
        @NotNull @Future OffsetDateTime reservedUntilAt
) {
}
