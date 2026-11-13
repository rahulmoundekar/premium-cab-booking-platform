package com.suabhi.cabbooking.booking.service;

import com.suabhi.cabbooking.booking.api.BookingCreationResult;
import com.suabhi.cabbooking.booking.api.BookingResponse;
import com.suabhi.cabbooking.booking.domain.BookingStatus;
import com.suabhi.cabbooking.booking.repository.BookingRepository;
import com.suabhi.cabbooking.common.exception.ResourceNotFoundException;
import com.suabhi.cabbooking.pricing.api.FareQuoteResponse;
import com.suabhi.cabbooking.pricing.service.FareQuoteService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BookingService {
    private static final DateTimeFormatter REFERENCE_DATE =
            DateTimeFormatter.ofPattern("yyMMdd").withZone(ZoneOffset.UTC);

    private final FareQuoteService fareQuoteService;
    private final BookingRepository bookingRepository;
    private final Clock clock;

    @Autowired
    public BookingService(FareQuoteService fareQuoteService, BookingRepository bookingRepository) {
        this(fareQuoteService, bookingRepository, Clock.systemUTC());
    }

    BookingService(FareQuoteService fareQuoteService, BookingRepository bookingRepository, Clock clock) {
        this.fareQuoteService = fareQuoteService;
        this.bookingRepository = bookingRepository;
        this.clock = clock;
    }

    @Transactional
    public BookingCreationResult create(UUID customerId, UUID quoteId, String idempotencyKey) {
        if (customerId == null) {
            throw new IllegalArgumentException("An authenticated customer is required to create a booking.");
        }
        String key = validateIdempotencyKey(idempotencyKey);
        String requestHash = sha256(quoteId == null ? "" : quoteId.toString());

        // Resolve an earlier request first. Replays return the original result even after quote expiry.
        var priorRequest = bookingRepository.findByCustomerAndIdempotencyKey(customerId, key);
        if (priorRequest.isPresent()) {
            return replayOrReject(priorRequest.get(), requestHash);
        }

        FareQuoteResponse quote = fareQuoteService.findOwnedQuote(customerId, quoteId);
        Instant now = clock.instant();
        if (quote.expiresAt() == null || !now.isBefore(quote.expiresAt())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "The fare quote has expired. Create a new quote before booking.");
        }
        if (quote.journey().departureAt() == null || !now.isBefore(quote.journey().departureAt())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "The scheduled departure is no longer in the future.");
        }

        UUID bookingId = UUID.randomUUID();
        String bookingReference = "SB-" + REFERENCE_DATE.format(now) + "-"
                + bookingId.toString().replace("-", "").substring(0, 10).toUpperCase();
        BookingResponse booking = new BookingResponse(
                bookingId, bookingReference, BookingStatus.PENDING_CONFIRMATION,
                now, now, null, quote);

        boolean inserted = bookingRepository.insertIfAbsent(
                customerId, key, requestHash, quote, booking);
        if (inserted) {
            bookingRepository.recordCreated(customerId, booking);
            return new BookingCreationResult(booking, false);
        }

        // A concurrent request may have won either the idempotency key or quote uniqueness race.
        var concurrentRequest = bookingRepository.findByCustomerAndIdempotencyKey(customerId, key);
        if (concurrentRequest.isPresent()) {
            return replayOrReject(concurrentRequest.get(), requestHash);
        }
        if (bookingRepository.findByQuoteId(quote.id()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This fare quote has already been converted into a booking.");
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT,
                "The booking request conflicted with another operation. Retry with the same Idempotency-Key.");
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> findAllOwned(UUID customerId) {
        return bookingRepository.findAllOwned(customerId);
    }

    @Transactional(readOnly = true)
    public BookingResponse findOwned(UUID customerId, UUID bookingId) {
        return bookingRepository.findOwned(bookingId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking was not found."));
    }

    @Transactional
    public BookingResponse cancel(UUID customerId, UUID bookingId) {
        BookingResponse current = bookingRepository.findOwnedForUpdate(bookingId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking was not found."));

        if (current.status() == BookingStatus.CANCELLED) {
            return current;
        }
        if (current.status() != BookingStatus.PENDING_CONFIRMATION) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only bookings pending confirmation can be cancelled by the customer.");
        }

        Instant now = clock.instant();
        BookingResponse cancelled = new BookingResponse(
                current.id(), current.bookingReference(), BookingStatus.CANCELLED,
                current.createdAt(), now, now, current.quote());
        bookingRepository.updateStatus(bookingId, customerId, cancelled);
        bookingRepository.recordCancelled(customerId, cancelled, current.status());
        return cancelled;
    }

    private BookingCreationResult replayOrReject(BookingResponse existing, String requestHash) {
        String storedHash = bookingRepository.findRequestHash(existing.id());
        if (storedHash == null || !MessageDigest.isEqual(
                storedHash.getBytes(StandardCharsets.US_ASCII),
                requestHash.getBytes(StandardCharsets.US_ASCII))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "The Idempotency-Key was already used with a different booking request.");
        }
        return new BookingCreationResult(existing, true);
    }

    private String validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()
                || idempotencyKey.length() > 160
                || !idempotencyKey.matches("[A-Za-z0-9._:-]{1,160}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A valid Idempotency-Key header containing 1 to 160 safe characters is required.");
        }
        return idempotencyKey;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }
}
