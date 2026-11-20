package com.suabhi.cabbooking.booking.api;

public record BookingManagementResponse(
        BookingResponse booking,
        BookingAllocationResponse allocation,
        String decisionReason
) {
}
