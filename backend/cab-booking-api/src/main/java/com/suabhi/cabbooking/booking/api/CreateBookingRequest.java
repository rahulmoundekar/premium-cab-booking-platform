package com.suabhi.cabbooking.booking.api;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateBookingRequest(@NotNull UUID quoteId) {
}
