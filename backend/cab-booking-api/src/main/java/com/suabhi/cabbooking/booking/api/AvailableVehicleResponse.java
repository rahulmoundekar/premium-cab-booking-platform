package com.suabhi.cabbooking.booking.api;

import java.util.UUID;

public record AvailableVehicleResponse(
        UUID vehicleId,
        String vehicleNumber,
        String operatingCity
) {
}
