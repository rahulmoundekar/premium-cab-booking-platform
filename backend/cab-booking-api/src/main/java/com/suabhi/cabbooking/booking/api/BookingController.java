package com.suabhi.cabbooking.booking.api;

import com.suabhi.cabbooking.booking.service.BookingService;
import com.suabhi.cabbooking.identity.domain.AppUserPrincipal;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/customer/bookings")
public class BookingController {
    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    public ResponseEntity<BookingResponse> create(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateBookingRequest request) {
        BookingCreationResult result = bookingService.create(
                principal.getId(), request.quoteId(), idempotencyKey);
        if (result.replayed()) {
            return ResponseEntity.ok(result.booking());
        }
        URI location = UriComponentsBuilder.fromPath("/api/v1/customer/bookings/{bookingId}")
                .buildAndExpand(result.booking().id()).toUri();
        return ResponseEntity.created(location).body(result.booking());
    }

    @GetMapping
    public List<BookingResponse> list(@AuthenticationPrincipal AppUserPrincipal principal) {
        return bookingService.findAllOwned(principal.getId());
    }

    @GetMapping("/{bookingId}")
    public BookingResponse get(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID bookingId) {
        return bookingService.findOwned(principal.getId(), bookingId);
    }

    @PostMapping("/{bookingId}/cancel")
    public BookingResponse cancel(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID bookingId) {
        return bookingService.cancel(principal.getId(), bookingId);
    }
}
