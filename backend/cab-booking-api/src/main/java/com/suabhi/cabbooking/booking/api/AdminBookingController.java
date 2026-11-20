package com.suabhi.cabbooking.booking.api;

import com.suabhi.cabbooking.booking.domain.BookingStatus;
import com.suabhi.cabbooking.booking.service.BookingDecisionService;
import com.suabhi.cabbooking.identity.domain.AppUserPrincipal;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/bookings")
public class AdminBookingController {
    private final BookingDecisionService bookingDecisionService;

    public AdminBookingController(BookingDecisionService bookingDecisionService) {
        this.bookingDecisionService = bookingDecisionService;
    }

    @GetMapping
    public List<BookingManagementResponse> list(@RequestParam(required = false) BookingStatus status) {
        return bookingDecisionService.findAll(status);
    }

    @GetMapping("/{bookingId}")
    public BookingManagementResponse get(@PathVariable UUID bookingId) {
        return bookingDecisionService.findOne(bookingId);
    }

    @GetMapping("/{bookingId}/available-vehicles")
    public List<AvailableVehicleResponse> availableVehicles(
            @PathVariable UUID bookingId,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime reservedUntilAt) {
        return bookingDecisionService.findAvailableVehicles(bookingId, reservedUntilAt);
    }

    @PostMapping("/{bookingId}/confirm")
    public BookingManagementResponse confirm(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID bookingId,
            @Valid @RequestBody ConfirmBookingRequest request) {
        return bookingDecisionService.confirm(
                principal.getId(), bookingId, request.vehicleId(), request.reservedUntilAt());
    }

    @PostMapping("/{bookingId}/reject")
    public BookingManagementResponse reject(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID bookingId,
            @Valid @RequestBody RejectBookingRequest request) {
        return bookingDecisionService.reject(principal.getId(), bookingId, request.reason());
    }
}
