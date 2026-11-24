package com.suabhi.cabbooking.booking.api;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AssignDriverRequest(
        @NotNull UUID driverProfileId
) {
}
