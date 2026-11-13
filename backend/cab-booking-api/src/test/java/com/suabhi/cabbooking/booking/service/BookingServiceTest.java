package com.suabhi.cabbooking.booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.suabhi.cabbooking.booking.api.BookingResponse;
import com.suabhi.cabbooking.booking.domain.BookingStatus;
import com.suabhi.cabbooking.booking.repository.BookingRepository;
import com.suabhi.cabbooking.common.exception.ResourceNotFoundException;
import com.suabhi.cabbooking.journey.api.JourneyPreviewResponse;
import com.suabhi.cabbooking.journey.provider.ServiceType;
import com.suabhi.cabbooking.pricing.api.FareQuoteResponse;
import com.suabhi.cabbooking.pricing.api.FareQuoteResponse.RateCardSnapshot;
import com.suabhi.cabbooking.pricing.domain.FareCalculationResult;
import com.suabhi.cabbooking.pricing.service.FareQuoteService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class BookingServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-13T12:00:00Z");

    private FareQuoteService fareQuoteService;
    private BookingRepository bookingRepository;
    private BookingService service;

    @BeforeEach
    void setUp() {
        fareQuoteService = mock(FareQuoteService.class);
        bookingRepository = mock(BookingRepository.class);
        service = new BookingService(
                fareQuoteService,
                bookingRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createsPendingBookingFromOwnedUnexpiredQuoteAndRecordsEvent() {
        UUID customerId = UUID.randomUUID();
        UUID quoteId = UUID.randomUUID();
        String key = "booking-test-001";
        FareQuoteResponse quote = quote(quoteId, NOW.plusSeconds(900));
        when(bookingRepository.findByCustomerAndIdempotencyKey(customerId, key)).thenReturn(Optional.empty());
        when(fareQuoteService.findOwnedQuote(customerId, quoteId)).thenReturn(quote);
        when(bookingRepository.insertIfAbsent(
                org.mockito.ArgumentMatchers.eq(customerId),
                org.mockito.ArgumentMatchers.eq(key),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(quote),
                org.mockito.ArgumentMatchers.any(BookingResponse.class))).thenReturn(true);

        var result = service.create(customerId, quoteId, key);

        assertThat(result.replayed()).isFalse();
        assertThat(result.booking().status()).isEqualTo(BookingStatus.PENDING_CONFIRMATION);
        assertThat(result.booking().quote()).isSameAs(quote);
        assertThat(result.booking().bookingReference()).startsWith("SB-");
        verify(bookingRepository).recordCreated(customerId, result.booking());
    }

    @Test
    void replaysTheOriginalBookingWhenTheSameIdempotencyKeyAndRequestAreRepeated() {
        UUID customerId = UUID.randomUUID();
        UUID quoteId = UUID.randomUUID();
        String key = "booking-test-replay";
        BookingResponse original = booking(quote(quoteId, NOW.plusSeconds(900)), BookingStatus.PENDING_CONFIRMATION);
        when(bookingRepository.findByCustomerAndIdempotencyKey(customerId, key))
                .thenReturn(Optional.of(original));
        when(bookingRepository.findRequestHash(original.id()))
                .thenReturn(sha256(quoteId.toString()));

        var result = service.create(customerId, quoteId, key);

        assertThat(result.replayed()).isTrue();
        assertThat(result.booking()).isSameAs(original);
        verify(fareQuoteService, never()).findOwnedQuote(customerId, quoteId);
        verify(bookingRepository, never()).insertIfAbsent(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsReusingIdempotencyKeyForADifferentQuote() {
        UUID customerId = UUID.randomUUID();
        UUID requestedQuoteId = UUID.randomUUID();
        UUID originalQuoteId = UUID.randomUUID();
        String key = "booking-test-reuse";
        BookingResponse original = booking(quote(originalQuoteId, NOW.plusSeconds(900)), BookingStatus.PENDING_CONFIRMATION);
        when(bookingRepository.findByCustomerAndIdempotencyKey(customerId, key))
                .thenReturn(Optional.of(original));
        when(bookingRepository.findRequestHash(original.id()))
                .thenReturn(sha256(originalQuoteId.toString()));

        assertThatThrownBy(() -> service.create(customerId, requestedQuoteId, key))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void rejectsExpiredQuoteWithoutCreatingBooking() {
        UUID customerId = UUID.randomUUID();
        UUID quoteId = UUID.randomUUID();
        String key = "booking-test-expired";
        when(bookingRepository.findByCustomerAndIdempotencyKey(customerId, key)).thenReturn(Optional.empty());
        when(fareQuoteService.findOwnedQuote(customerId, quoteId)).thenReturn(quote(quoteId, NOW));

        assertThatThrownBy(() -> service.create(customerId, quoteId, key))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(bookingRepository, never()).insertIfAbsent(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsBookingWhenScheduledDepartureHasPassed() {
        UUID customerId = UUID.randomUUID();
        UUID quoteId = UUID.randomUUID();
        String key = "booking-test-departed";
        when(bookingRepository.findByCustomerAndIdempotencyKey(customerId, key)).thenReturn(Optional.empty());
        when(fareQuoteService.findOwnedQuote(customerId, quoteId))
                .thenReturn(quote(quoteId, NOW.plusSeconds(900), NOW.minusSeconds(1)));

        assertThatThrownBy(() -> service.create(customerId, quoteId, key))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(bookingRepository, never()).insertIfAbsent(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsAQuoteThatWonAConcurrentBookingRace() {
        UUID customerId = UUID.randomUUID();
        UUID quoteId = UUID.randomUUID();
        String key = "booking-test-duplicate-quote";
        FareQuoteResponse quote = quote(quoteId, NOW.plusSeconds(900));
        when(bookingRepository.findByCustomerAndIdempotencyKey(customerId, key))
                .thenReturn(Optional.empty(), Optional.empty());
        when(fareQuoteService.findOwnedQuote(customerId, quoteId)).thenReturn(quote);
        when(bookingRepository.insertIfAbsent(
                org.mockito.ArgumentMatchers.eq(customerId),
                org.mockito.ArgumentMatchers.eq(key),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(quote),
                org.mockito.ArgumentMatchers.any(BookingResponse.class))).thenReturn(false);
        when(bookingRepository.findByQuoteId(quoteId))
                .thenReturn(Optional.of(booking(quote, BookingStatus.PENDING_CONFIRMATION)));

        assertThatThrownBy(() -> service.create(customerId, quoteId, key))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void cancelsOnlyPendingBookingAndRecordsStateChange() {
        UUID customerId = UUID.randomUUID();
        BookingResponse pending = booking(quote(UUID.randomUUID(), NOW.plusSeconds(900)),
                BookingStatus.PENDING_CONFIRMATION);
        when(bookingRepository.findOwnedForUpdate(pending.id(), customerId)).thenReturn(Optional.of(pending));

        BookingResponse cancelled = service.cancel(customerId, pending.id());

        assertThat(cancelled.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(cancelled.cancelledAt()).isEqualTo(NOW);
        verify(bookingRepository).updateStatus(pending.id(), customerId, cancelled);
        verify(bookingRepository).recordCancelled(customerId, cancelled, BookingStatus.PENDING_CONFIRMATION);
    }

    @Test
    void returnsAlreadyCancelledBookingWithoutWritingAnotherEvent() {
        UUID customerId = UUID.randomUUID();
        BookingResponse cancelled = booking(quote(UUID.randomUUID(), NOW.plusSeconds(900)), BookingStatus.CANCELLED);
        when(bookingRepository.findOwnedForUpdate(cancelled.id(), customerId)).thenReturn(Optional.of(cancelled));

        assertThat(service.cancel(customerId, cancelled.id())).isSameAs(cancelled);
        verify(bookingRepository, never()).recordCancelled(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void doesNotAllowCustomerToCancelConfirmedBooking() {
        UUID customerId = UUID.randomUUID();
        BookingResponse confirmed = booking(quote(UUID.randomUUID(), NOW.plusSeconds(900)), BookingStatus.CONFIRMED);
        when(bookingRepository.findOwnedForUpdate(confirmed.id(), customerId)).thenReturn(Optional.of(confirmed));

        assertThatThrownBy(() -> service.cancel(customerId, confirmed.id()))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void doesNotRevealBookingOwnedByAnotherCustomer() {
        UUID customerId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        when(bookingRepository.findOwned(bookingId, customerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findOwned(customerId, bookingId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private static FareQuoteResponse quote(UUID quoteId, Instant expiresAt) {
        return quote(quoteId, expiresAt, NOW.plusSeconds(86_400));
    }

    private static FareQuoteResponse quote(UUID quoteId, Instant expiresAt, Instant departureAt) {
        Instant created = NOW.minusSeconds(60);
        RateCardSnapshot card = new RateCardSnapshot(
                UUID.randomUUID(), "TEST_PUNE_AIRPORT_SEDAN", 2, ServiceType.AIRPORT_TRANSFER,
                com.suabhi.cabbooking.journey.provider.City.PUNE, UUID.randomUUID(),
                "EXECUTIVE_SEDAN", "INR", new BigDecimal("100"), new BigDecimal("150"),
                new BigDecimal("10"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, created, null, List.of());
        JourneyPreviewResponse journey = new JourneyPreviewResponse(
                ServiceType.AIRPORT_TRANSFER, "PUNE", departureAt, List.of(), 10_000, 1_800,
                "route-polyline", "route-reference");
        FareCalculationResult fare = new FareCalculationResult(
                "INR", new BigDecimal("10.000"), new BigDecimal("10.000"),
                new BigDecimal("100.00"), new BigDecimal("100.00"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("10.00"),
                new BigDecimal("10.50"), new BigDecimal("220.50"), List.of());
        return new FareQuoteResponse(quoteId, created, expiresAt, null, journey, card, fare);
    }

    private static BookingResponse booking(FareQuoteResponse quote, BookingStatus status) {
        Instant created = NOW.minusSeconds(30);
        Instant cancelled = status == BookingStatus.CANCELLED ? NOW.minusSeconds(10) : null;
        return new BookingResponse(UUID.randomUUID(), "SB-261113-ABCDEF1234",
                status, created, cancelled == null ? created : cancelled, cancelled, quote);
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
