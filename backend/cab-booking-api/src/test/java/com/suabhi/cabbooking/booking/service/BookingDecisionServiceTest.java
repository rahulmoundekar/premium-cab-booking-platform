package com.suabhi.cabbooking.booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.suabhi.cabbooking.booking.api.BookingResponse;
import com.suabhi.cabbooking.booking.domain.BookingStatus;
import com.suabhi.cabbooking.booking.repository.BookingRepository;
import com.suabhi.cabbooking.fleet.FleetVehicleCatalog;
import com.suabhi.cabbooking.journey.api.JourneyPreviewResponse;
import com.suabhi.cabbooking.journey.provider.City;
import com.suabhi.cabbooking.journey.provider.ServiceType;
import com.suabhi.cabbooking.pricing.api.FareQuoteResponse;
import com.suabhi.cabbooking.pricing.api.FareQuoteResponse.RateCardSnapshot;
import com.suabhi.cabbooking.pricing.domain.FareCalculationResult;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class BookingDecisionServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-20T12:00:00Z");
    private static final Instant DEPARTURE = NOW.plusSeconds(86_400);
    private static final UUID CATEGORY_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID VEHICLE_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID ACTOR_ID = UUID.randomUUID();

    private BookingRepository bookingRepository;
    private FleetVehicleCatalog fleetVehicleCatalog;
    private BookingDecisionService service;

    @BeforeEach
    void setUp() {
        bookingRepository = mock(BookingRepository.class);
        fleetVehicleCatalog = mock(FleetVehicleCatalog.class);
        service = new BookingDecisionService(
                bookingRepository, fleetVehicleCatalog, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void confirmsPendingBookingWithMatchingAvailableVehicleAndRecordsDecision() {
        BookingResponse pending = booking(BookingStatus.PENDING_CONFIRMATION, ServiceType.AIRPORT_TRANSFER, null);
        FleetVehicleCatalog.VehicleReference vehicle = vehicle("AVAILABLE", CATEGORY_ID, "PUNE");
        Instant reservedUntil = DEPARTURE.plusSeconds(7_200);
        BookingRepository.AllocationRow allocation = new BookingRepository.AllocationRow(
                UUID.randomUUID(), pending.id(), VEHICLE_ID, "MH12AB1234", DEPARTURE, reservedUntil);

        when(bookingRepository.findForManagementForUpdate(pending.id())).thenReturn(Optional.of(pending));
        when(fleetVehicleCatalog.findVehicle(VEHICLE_ID)).thenReturn(Optional.of(vehicle));
        when(bookingRepository.insertActiveAllocation(
                pending.id(), VEHICLE_ID, vehicle.vehicleNumber(), ACTOR_ID, DEPARTURE, reservedUntil))
                .thenReturn(allocation);

        var result = service.confirm(ACTOR_ID, pending.id(), VEHICLE_ID,
                OffsetDateTime.ofInstant(reservedUntil, ZoneOffset.UTC));

        assertThat(result.booking().status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(result.allocation()).isNotNull();
        assertThat(result.allocation().vehicleId()).isEqualTo(VEHICLE_ID);
        assertThat(result.allocation().reservedFromAt()).isEqualTo(DEPARTURE);
        assertThat(result.allocation().reservedUntilAt()).isEqualTo(reservedUntil);
        verify(bookingRepository).updateManagementStatus(pending.id(), result.booking(), null);
        verify(bookingRepository).recordConfirmed(
                ACTOR_ID, result.booking(), VEHICLE_ID, vehicle.vehicleNumber(), DEPARTURE, reservedUntil);
    }

    @Test
    void rejectsVehicleScheduleConflictWithoutConfirmingBooking() {
        BookingResponse pending = booking(BookingStatus.PENDING_CONFIRMATION, ServiceType.AIRPORT_TRANSFER, null);
        FleetVehicleCatalog.VehicleReference vehicle = vehicle("AVAILABLE", CATEGORY_ID, "PUNE");
        Instant reservedUntil = DEPARTURE.plusSeconds(7_200);

        when(bookingRepository.findForManagementForUpdate(pending.id())).thenReturn(Optional.of(pending));
        when(fleetVehicleCatalog.findVehicle(VEHICLE_ID)).thenReturn(Optional.of(vehicle));
        doThrow(new DataIntegrityViolationException("vehicle allocation overlap"))
                .when(bookingRepository).insertActiveAllocation(
                        pending.id(), VEHICLE_ID, vehicle.vehicleNumber(), ACTOR_ID, DEPARTURE, reservedUntil);

        assertThatThrownBy(() -> service.confirm(ACTOR_ID, pending.id(), VEHICLE_ID,
                OffsetDateTime.ofInstant(reservedUntil, ZoneOffset.UTC)))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        verify(bookingRepository, never()).updateManagementStatus(eq(pending.id()), any(), any());
        verify(bookingRepository, never()).recordConfirmed(any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsVehicleFromDifferentCategoryOrCityBeforeAllocating() {
        BookingResponse pending = booking(BookingStatus.PENDING_CONFIRMATION, ServiceType.AIRPORT_TRANSFER, null);
        FleetVehicleCatalog.VehicleReference wrongVehicle =
                vehicle("AVAILABLE", UUID.randomUUID(), "MUMBAI");
        when(bookingRepository.findForManagementForUpdate(pending.id())).thenReturn(Optional.of(pending));
        when(fleetVehicleCatalog.findVehicle(VEHICLE_ID)).thenReturn(Optional.of(wrongVehicle));

        assertThatThrownBy(() -> service.confirm(ACTOR_ID, pending.id(), VEHICLE_ID,
                OffsetDateTime.ofInstant(DEPARTURE.plusSeconds(7_200), ZoneOffset.UTC)))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        verify(bookingRepository, never()).insertActiveAllocation(
                any(), any(), any(), any(), any(), any());
    }

    @Test
    void requiresReservationToCoverTheFullLocalRentalPackage() {
        BookingResponse pending = booking(BookingStatus.PENDING_CONFIRMATION, ServiceType.LOCAL_RENTAL, 4);
        Instant tooEarly = DEPARTURE.plusSeconds(3 * 3_600L);
        when(bookingRepository.findForManagement(pending.id())).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.findAvailableVehicles(
                pending.id(), OffsetDateTime.ofInstant(tooEarly, ZoneOffset.UTC)))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void returnsOnlyVehiclesAvailableForTheRequestedWindow() {
        BookingResponse pending = booking(BookingStatus.PENDING_CONFIRMATION, ServiceType.AIRPORT_TRANSFER, null);
        Instant reservedUntil = DEPARTURE.plusSeconds(7_200);
        when(bookingRepository.findForManagement(pending.id())).thenReturn(Optional.of(pending));
        when(bookingRepository.findAvailableVehicles(CATEGORY_ID, "PUNE", DEPARTURE, reservedUntil))
                .thenReturn(List.of(new BookingRepository.AvailableVehicle(
                        VEHICLE_ID, "MH12AB1234", "PUNE")));

        var result = service.findAvailableVehicles(
                pending.id(), OffsetDateTime.ofInstant(reservedUntil, ZoneOffset.UTC));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().vehicleId()).isEqualTo(VEHICLE_ID);
    }

    @Test
    void rejectsPendingBookingWithReasonAndRecordsDecision() {
        BookingResponse pending = booking(BookingStatus.PENDING_CONFIRMATION, ServiceType.AIRPORT_TRANSFER, null);
        when(bookingRepository.findForManagementForUpdate(pending.id())).thenReturn(Optional.of(pending));

        var result = service.reject(ACTOR_ID, pending.id(), "No vehicle available");

        assertThat(result.booking().status()).isEqualTo(BookingStatus.REJECTED);
        assertThat(result.decisionReason()).isEqualTo("No vehicle available");
        verify(bookingRepository).updateManagementStatus(pending.id(), result.booking(), "No vehicle available");
        verify(bookingRepository).recordRejected(ACTOR_ID, result.booking(), "No vehicle available");
    }

    @Test
    void repeatsRejectionIdempotentlyWhenReasonIsUnchanged() {
        BookingResponse rejected = booking(BookingStatus.REJECTED, ServiceType.AIRPORT_TRANSFER, null);
        when(bookingRepository.findForManagementForUpdate(rejected.id())).thenReturn(Optional.of(rejected));
        when(bookingRepository.findDecisionReason(rejected.id())).thenReturn("No vehicle available");

        var result = service.reject(ACTOR_ID, rejected.id(), "No vehicle available");

        assertThat(result.booking()).isSameAs(rejected);
        verify(bookingRepository, never()).updateManagementStatus(any(), any(), any());
        verify(bookingRepository, never()).recordRejected(any(), any(), any());
    }

    @Test
    void repeatsConfirmationIdempotentlyForTheSameVehicleAndWindow() {
        BookingResponse confirmed = booking(BookingStatus.CONFIRMED, ServiceType.AIRPORT_TRANSFER, null);
        Instant reservedUntil = DEPARTURE.plusSeconds(7_200);
        BookingRepository.AllocationRow allocation = new BookingRepository.AllocationRow(
                UUID.randomUUID(), confirmed.id(), VEHICLE_ID, "MH12AB1234", DEPARTURE, reservedUntil);
        when(bookingRepository.findForManagementForUpdate(confirmed.id())).thenReturn(Optional.of(confirmed));
        when(bookingRepository.findActiveAllocation(confirmed.id()))
                .thenReturn(Optional.of(allocation), Optional.of(allocation));
        when(bookingRepository.findDecisionReason(confirmed.id())).thenReturn(null);

        var result = service.confirm(ACTOR_ID, confirmed.id(), VEHICLE_ID,
                OffsetDateTime.ofInstant(reservedUntil, ZoneOffset.UTC));

        assertThat(result.booking()).isSameAs(confirmed);
        assertThat(result.allocation().vehicleId()).isEqualTo(VEHICLE_ID);
        verify(bookingRepository, never()).insertActiveAllocation(
                any(), any(), any(), any(), any(), any());
    }

    private static FleetVehicleCatalog.VehicleReference vehicle(
            String status, UUID categoryId, String city) {
        return new FleetVehicleCatalog.VehicleReference(
                VEHICLE_ID, "MH12AB1234", categoryId, city, status);
    }

    private static BookingResponse booking(BookingStatus status, ServiceType serviceType, Integer rentalHours) {
        UUID quoteId = UUID.randomUUID();
        Instant created = NOW.minusSeconds(60);
        RateCardSnapshot rateCard = new RateCardSnapshot(
                UUID.randomUUID(), "TEST_PUNE_AIRPORT_SEDAN", 1, serviceType, City.PUNE,
                CATEGORY_ID, "EXECUTIVE_SEDAN", "INR",
                new BigDecimal("100"), new BigDecimal("150"), new BigDecimal("10"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, created, null, List.of());
        JourneyPreviewResponse journey = new JourneyPreviewResponse(
                serviceType, "PUNE", DEPARTURE, List.of(), 10_000, 1_800,
                "test-polyline", "test-route-reference");
        FareCalculationResult fare = new FareCalculationResult(
                "INR", new BigDecimal("10.000"), new BigDecimal("10.000"),
                new BigDecimal("100.00"), new BigDecimal("100.00"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("10.00"),
                new BigDecimal("10.50"), new BigDecimal("220.50"), List.of());
        FareQuoteResponse quote = new FareQuoteResponse(
                quoteId, created, NOW.plusSeconds(900), rentalHours, journey, rateCard, fare);
        return new BookingResponse(UUID.randomUUID(), "SB-261120-ABCDEF1234",
                status, created, created, null, quote);
    }
}
