package com.suabhi.cabbooking.booking.api;

import com.suabhi.cabbooking.booking.domain.BookingStatus;
import com.suabhi.cabbooking.pricing.api.FareQuoteResponse;
import java.time.Instant;
import java.util.UUID;

/**
 * Booking view contains the accepted fare quote snapshot, not a live reference to mutable tariff data.
 */
@org.springframework.modulith.NamedInterface("api")
public record BookingResponse(
        UUID id,
        String bookingReference,
        BookingStatus status,
        Instant createdAt,
        Instant updatedAt,
        Instant cancelledAt,
        FareQuoteResponse quote
) {
}
