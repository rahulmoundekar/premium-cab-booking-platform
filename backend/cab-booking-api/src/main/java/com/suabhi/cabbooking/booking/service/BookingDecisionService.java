package com.suabhi.cabbooking.booking.service;

import com.suabhi.cabbooking.booking.api.AvailableVehicleResponse;
import com.suabhi.cabbooking.booking.api.BookingAllocationResponse;
import com.suabhi.cabbooking.booking.api.BookingManagementResponse;
import com.suabhi.cabbooking.booking.api.BookingResponse;
import com.suabhi.cabbooking.booking.domain.BookingStatus;
import com.suabhi.cabbooking.booking.repository.BookingRepository;
import com.suabhi.cabbooking.common.exception.ResourceNotFoundException;
import com.suabhi.cabbooking.fleet.FleetVehicleCatalog;
import com.suabhi.cabbooking.journey.provider.ServiceType;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BookingDecisionService {
    private final BookingRepository bookingRepository;
    private final FleetVehicleCatalog fleetVehicleCatalog;
    private final Clock clock;

    @Autowired
    public BookingDecisionService(BookingRepository bookingRepository,
                                  FleetVehicleCatalog fleetVehicleCatalog) {
        this(bookingRepository, fleetVehicleCatalog, Clock.systemUTC());
    }

    BookingDecisionService(BookingRepository bookingRepository,
                           FleetVehicleCatalog fleetVehicleCatalog, Clock clock) {
        this.bookingRepository = bookingRepository;
        this.fleetVehicleCatalog = fleetVehicleCatalog;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<BookingManagementResponse> findAll(BookingStatus status) {
        return bookingRepository.findAllForManagement(status).stream()
                .map(this::toManagementResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public BookingManagementResponse findOne(UUID bookingId) {
        BookingResponse booking = bookingRepository.findForManagement(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking was not found."));
        return toManagementResponse(booking);
    }

    @Transactional(readOnly = true)
    public List<AvailableVehicleResponse> findAvailableVehicles(UUID bookingId,
                                                                 OffsetDateTime reservedUntilAt) {
        BookingResponse booking = bookingRepository.findForManagement(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking was not found."));
        requirePending(booking);
        ScheduleWindow window = resolveSchedule(booking, reservedUntilAt);
        return bookingRepository.findAvailableVehicles(
                        booking.quote().rateCard().vehicleCategoryId(),
                        booking.quote().journey().originCity(),
                        window.reservedFromAt(), window.reservedUntilAt())
                .stream()
                .map(vehicle -> new AvailableVehicleResponse(
                        vehicle.id(), vehicle.vehicleNumber(), vehicle.operatingCity()))
                .toList();
    }

    @Transactional
    public BookingManagementResponse confirm(UUID actorId, UUID bookingId, UUID vehicleId,
                                              OffsetDateTime reservedUntilAt) {
        requireActor(actorId);
        BookingResponse current = bookingRepository.findForManagementForUpdate(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking was not found."));

        if (current.status() == BookingStatus.CONFIRMED) {
            BookingRepository.AllocationRow existing = bookingRepository.findActiveAllocation(bookingId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Confirmed booking has no active vehicle allocation."));
            Instant requestedEnd = reservedUntilAt == null ? null : databasePrecision(reservedUntilAt.toInstant());
            Instant requestedStart = databasePrecision(current.quote().journey().departureAt());
            if (vehicleId != null && vehicleId.equals(existing.vehicleId())
                    && existing.reservedFromAt().equals(requestedStart)
                    && existing.reservedUntilAt().equals(requestedEnd)) {
                return toManagementResponse(current);
            }
            throw conflict("The booking is already confirmed with a different vehicle or reservation window.");
        }
        requirePending(current);
        ScheduleWindow window = resolveSchedule(current, reservedUntilAt);

        FleetVehicleCatalog.VehicleReference vehicle = fleetVehicleCatalog.findVehicle(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Active fleet vehicle was not found."));

        if (!"AVAILABLE".equalsIgnoreCase(vehicle.status())) {
            throw conflict("The selected vehicle is not in AVAILABLE status.");
        }
        if (!vehicle.categoryId().equals(current.quote().rateCard().vehicleCategoryId())) {
            throw conflict("The selected vehicle does not match the quoted vehicle category.");
        }
        if (!vehicle.operationalCity().strip().toUpperCase(Locale.ROOT)
                .equals(current.quote().journey().originCity().strip().toUpperCase(Locale.ROOT))) {
            throw conflict("The selected vehicle is not registered for the booking's operating city.");
        }

        BookingRepository.AllocationRow allocation;
        try {
            allocation = bookingRepository.insertActiveAllocation(
                    bookingId, vehicle.id(), vehicle.vehicleNumber(), actorId,
                    window.reservedFromAt(), window.reservedUntilAt());
        } catch (DataIntegrityViolationException exception) {
            throw conflict("The selected vehicle is no longer available for the requested time window.");
        }

        Instant now = clock.instant();
        BookingResponse confirmed = new BookingResponse(
                current.id(), current.bookingReference(), BookingStatus.CONFIRMED,
                current.createdAt(), now, null, current.quote());
        bookingRepository.updateManagementStatus(bookingId, confirmed, null);
        bookingRepository.recordConfirmed(actorId, confirmed, vehicle.id(), vehicle.vehicleNumber(),
                window.reservedFromAt(), window.reservedUntilAt());
        return new BookingManagementResponse(confirmed, toAllocationResponse(allocation), null);
    }

    @Transactional
    public BookingManagementResponse reject(UUID actorId, UUID bookingId, String reasonInput) {
        requireActor(actorId);
        String reason = reasonInput == null ? "" : reasonInput.strip();
        if (reason.length() < 5 || reason.length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A rejection reason containing 5 to 500 characters is required.");
        }

        BookingResponse current = bookingRepository.findForManagementForUpdate(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking was not found."));

        if (current.status() == BookingStatus.REJECTED) {
            String existingReason = bookingRepository.findDecisionReason(bookingId);
            if (reason.equals(existingReason)) {
                return new BookingManagementResponse(current, null, existingReason);
            }
            throw conflict("The booking was already rejected with a different reason.");
        }
        requirePending(current);

        Instant now = clock.instant();
        BookingResponse rejected = new BookingResponse(
                current.id(), current.bookingReference(), BookingStatus.REJECTED,
                current.createdAt(), now, null, current.quote());
        bookingRepository.updateManagementStatus(bookingId, rejected, reason);
        bookingRepository.recordRejected(actorId, rejected, reason);
        return new BookingManagementResponse(rejected, null, reason);
    }

    private ScheduleWindow resolveSchedule(BookingResponse booking, OffsetDateTime requestedEnd) {
        requirePendingOrConfirmed(booking);
        Instant reservedFromAt = databasePrecision(booking.quote().journey().departureAt());
        if (reservedFromAt == null || !reservedFromAt.isAfter(clock.instant())) {
            throw conflict("The scheduled departure is no longer in the future.");
        }
        if (requestedEnd == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "reservedUntilAt is required.");
        }
        Instant reservedUntilAt = databasePrecision(requestedEnd.toInstant());
        if (!reservedUntilAt.isAfter(reservedFromAt)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "reservedUntilAt must be after the scheduled departure.");
        }

        if (booking.quote().journey().serviceType() == ServiceType.LOCAL_RENTAL) {
            Integer rentalHours = booking.quote().rentalHours();
            if (rentalHours == null || rentalHours <= 0) {
                throw conflict("The local rental quote has no valid package duration.");
            }
            Instant packageEnd = reservedFromAt.plusSeconds(rentalHours.longValue() * 3_600L);
            if (reservedUntilAt.isBefore(packageEnd)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "The vehicle reservation must cover the full local rental package.");
            }
        } else {
            long routeDuration = booking.quote().journey().durationSeconds();
            if (routeDuration <= 0) {
                throw conflict("The quote does not contain a valid route duration.");
            }
            Instant earliestArrival = reservedFromAt.plusSeconds(routeDuration);
            if (reservedUntilAt.isBefore(earliestArrival)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "The vehicle reservation must cover at least the quoted route duration.");
            }
        }
        return new ScheduleWindow(reservedFromAt, reservedUntilAt);
    }

    private BookingManagementResponse toManagementResponse(BookingResponse booking) {
        BookingAllocationResponse allocation = bookingRepository.findActiveAllocation(booking.id())
                .map(this::toAllocationResponse)
                .orElse(null);
        String decisionReason = bookingRepository.findDecisionReason(booking.id());
        return new BookingManagementResponse(booking, allocation, decisionReason);
    }

    private BookingAllocationResponse toAllocationResponse(BookingRepository.AllocationRow allocation) {
        return new BookingAllocationResponse(
                allocation.id(), allocation.vehicleId(), allocation.vehicleNumber(),
                allocation.reservedFromAt(), allocation.reservedUntilAt());
    }

    private static Instant databasePrecision(Instant value) {
        return value.truncatedTo(ChronoUnit.MICROS);
    }

    private void requirePending(BookingResponse booking) {
        if (booking.status() != BookingStatus.PENDING_CONFIRMATION) {
            throw conflict("Only bookings pending confirmation can be decided by operations.");
        }
    }

    private void requirePendingOrConfirmed(BookingResponse booking) {
        if (booking.status() != BookingStatus.PENDING_CONFIRMATION
                && booking.status() != BookingStatus.CONFIRMED) {
            throw conflict("Vehicle availability can only be checked for pending or confirmed bookings.");
        }
    }

    private void requireActor(UUID actorId) {
        if (actorId == null) {
            throw new IllegalArgumentException("An authenticated operations user is required.");
        }
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private record ScheduleWindow(Instant reservedFromAt, Instant reservedUntilAt) {
    }
}
