package com.suabhi.cabbooking.booking.api;

import java.time.LocalDate;
import java.util.UUID;

public record DriverOptionResponse(
        UUID driverProfileId,
        String driverCode,
        String fullName,
        LocalDate licenseExpiry
) {
}
