package com.suabhi.cabbooking.booking.service;

import com.suabhi.cabbooking.booking.api.BookingResponse;
import com.suabhi.cabbooking.booking.api.DriverOptionResponse;
import com.suabhi.cabbooking.booking.api.DriverTripResponse;
import com.suabhi.cabbooking.booking.domain.BookingStatus;
import com.suabhi.cabbooking.booking.repository.BookingRepository;
import com.suabhi.cabbooking.booking.repository.DriverAssignmentRepository;
import com.suabhi.cabbooking.common.exception.ResourceNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DriverAssignmentService {
    private static final ZoneId OPERATING_ZONE = ZoneId.of("Asia/Kolkata");

    private final BookingRepository bookingRepository;
    private final DriverAssignmentRepository driverRepository;
    private final Clock clock;

    @Autowired
    public DriverAssignmentService(BookingRepository bookingRepository,
                                   DriverAssignmentRepository driverRepository) {
        this(bookingRepository, driverRepository, Clock.systemUTC());
    }

    DriverAssignmentService(BookingRepository bookingRepository,
                            DriverAssignmentRepository driverRepository, Clock clock) {
        this.bookingRepository = bookingRepository;
        this.driverRepository = driverRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<DriverOptionResponse> findAvailableDrivers(UUID bookingId) {
        BookingResponse booking = bookingRepository.findForManagement(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking was not found."));
        requireStatus(booking, BookingStatus.CONFIRMED,
                "Drivers can only be shortlisted for a confirmed booking.");
        if (bookingRepository.findActiveAllocation(bookingId).isEmpty()) {
            throw conflict("The confirmed booking has no active vehicle reservation.");
        }
        return driverRepository.findAvailableDrivers(bookingId).stream()
                .map(driver -> new DriverOptionResponse(
                        driver.id(), driver.driverCode(), driver.fullName(), driver.licenseExpiry()))
                .toList();
    }

    @Transactional
    public DriverTripResponse assignDriver(UUID actorId, UUID bookingId, UUID driverProfileId) {
        requireActor(actorId, "An authenticated operations user is required.");
        BookingResponse current = bookingRepository.findForManagementForUpdate(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking was not found."));

        if (current.status() == BookingStatus.ASSIGNED) {
            DriverAssignmentRepository.DriverTripRow existing = findTripForBooking(bookingId);
            if (existing.driverProfileId().equals(driverProfileId)) {
                return toResponse(existing);
            }
            throw conflict("The booking already has a driver. Reassignment is not supported in this workflow.");
        }
        requireStatus(current, BookingStatus.CONFIRMED,
                "Only confirmed bookings can be assigned to a driver.");

        BookingRepository.AllocationRow allocation = bookingRepository.findActiveAllocation(bookingId)
                .orElseThrow(() -> conflict("The confirmed booking has no active vehicle reservation."));
        DriverAssignmentRepository.DriverProfileRow driver = driverRepository.findDriverProfile(driverProfileId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver profile was not found."));
        validateDriver(driver, allocation.reservedUntilAt());

        Instant assignedAt = clock.instant();
        try {
            driverRepository.insertAssignment(
                    bookingId, driver.id(), actorId,
                    allocation.reservedFromAt(), allocation.reservedUntilAt(), assignedAt);
        } catch (DataIntegrityViolationException exception) {
            throw conflict("The driver is already assigned to an overlapping reservation or this booking was assigned concurrently.");
        }

        BookingResponse assigned = withStatus(current, BookingStatus.ASSIGNED, assignedAt);
        bookingRepository.updateManagementStatus(bookingId, assigned, null);
        bookingRepository.recordDriverAssigned(
                actorId, assigned, driver.id(), driver.driverCode(),
                allocation.reservedFromAt(), allocation.reservedUntilAt());
        return toResponse(findTripForBooking(bookingId));
    }

    @Transactional(readOnly = true)
    public List<DriverTripResponse> findTrips(UUID driverUserId) {
        DriverAssignmentRepository.DriverProfileRow driver = requireDriverUser(driverUserId);
        return driverRepository.findTripsForDriver(driver.userId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public DriverTripResponse findTrip(UUID driverUserId, UUID bookingId) {
        DriverAssignmentRepository.DriverProfileRow driver = requireDriverUser(driverUserId);
        return driverRepository.findTripForDriver(bookingId, driver.userId())
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Driver trip was not found."));
    }

    @Transactional
    public DriverTripResponse startTrip(UUID driverUserId, UUID bookingId) {
        DriverAssignmentRepository.DriverProfileRow driver = requireDriverUser(driverUserId);
        BookingResponse current = bookingRepository.findForManagementForUpdate(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver trip was not found."));
        DriverAssignmentRepository.DriverTripRow trip = driverRepository.findTripForDriver(bookingId, driver.userId())
                .orElseThrow(() -> new ResourceNotFoundException("Driver trip was not found."));

        if (current.status() == BookingStatus.IN_PROGRESS) {
            return toResponse(trip);
        }
        requireStatus(current, BookingStatus.ASSIGNED,
                "Only an assigned trip can be started.");

        Instant now = clock.instant();
        if (now.isBefore(trip.reservedFromAt()) || !now.isBefore(trip.reservedUntilAt())) {
            throw conflict("The trip can only be started within its reserved vehicle and driver time window.");
        }
        if (driverRepository.markDriverOnTrip(driver.id()) != 1) {
            throw conflict("The driver is not currently available to start this trip.");
        }
        if (driverRepository.markTripStarted(bookingId, driver.id(), now) != 1) {
            throw conflict("The trip has already started or is no longer assigned to this driver.");
        }

        BookingResponse started = withStatus(current, BookingStatus.IN_PROGRESS, now);
        bookingRepository.updateManagementStatus(bookingId, started, null);
        bookingRepository.recordTripStarted(driver.userId(), started, driver.id(), driver.driverCode(), now);
        return toResponse(driverRepository.findTripForDriver(bookingId, driver.userId())
                .orElseThrow(() -> new IllegalStateException("Started trip could not be reloaded.")));
    }

    @Transactional
    public DriverTripResponse completeTrip(UUID driverUserId, UUID bookingId) {
        DriverAssignmentRepository.DriverProfileRow driver = requireDriverUser(driverUserId);
        BookingResponse current = bookingRepository.findForManagementForUpdate(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver trip was not found."));
        DriverAssignmentRepository.DriverTripRow trip = driverRepository.findTripForDriver(bookingId, driver.userId())
                .orElseThrow(() -> new ResourceNotFoundException("Driver trip was not found."));

        if (current.status() == BookingStatus.COMPLETED) {
            return toResponse(trip);
        }
        requireStatus(current, BookingStatus.IN_PROGRESS,
                "Only an in-progress trip can be completed.");

        Instant completedAt = clock.instant();
        if (driverRepository.markTripCompleted(bookingId, driver.id(), completedAt) != 1) {
            throw conflict("The trip has not started or has already been completed.");
        }
        if (driverRepository.markDriverAvailable(driver.id()) != 1) {
            throw conflict("The driver's trip presence could not be released safely.");
        }

        BookingResponse completed = withStatus(current, BookingStatus.COMPLETED, completedAt);
        bookingRepository.updateManagementStatus(bookingId, completed, null);
        bookingRepository.recordTripCompleted(
                driver.userId(), completed, driver.id(), driver.driverCode(), completedAt);
        return toResponse(driverRepository.findTripForDriver(bookingId, driver.userId())
                .orElseThrow(() -> new IllegalStateException("Completed trip could not be reloaded.")));
    }

    private DriverAssignmentRepository.DriverProfileRow requireDriverUser(UUID driverUserId) {
        requireActor(driverUserId, "An authenticated driver is required.");
        DriverAssignmentRepository.DriverProfileRow driver = driverRepository.findDriverProfileByUserId(driverUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver profile was not found."));
        if (!driver.hasDriverRole() || !"ACTIVE".equals(driver.accountStatus())) {
            throw new ResourceNotFoundException("Driver profile was not found.");
        }
        if (!"VERIFIED".equals(driver.verificationStatus())) {
            throw conflict("The driver profile is not verified.");
        }
        return driver;
    }

    private void validateDriver(DriverAssignmentRepository.DriverProfileRow driver, Instant reservedUntilAt) {
        if (!driver.hasDriverRole() || !"ACTIVE".equals(driver.accountStatus())) {
            throw new ResourceNotFoundException("An active driver account with the DRIVER role is required.");
        }
        if (!"VERIFIED".equals(driver.verificationStatus())) {
            throw conflict("Only verified drivers can be assigned.");
        }
        if (!"AVAILABLE".equals(driver.availabilityStatus())) {
            throw conflict("The driver is not currently available.");
        }
        LocalDate requiredLicenseDate = reservedUntilAt.atZone(OPERATING_ZONE).toLocalDate();
        if (driver.licenseExpiry().isBefore(requiredLicenseDate)) {
            throw conflict("The driver's licence must remain valid through the reserved trip date.");
        }
    }

    private DriverAssignmentRepository.DriverTripRow findTripForBooking(UUID bookingId) {
        return driverRepository.findTripForBooking(bookingId)
                .orElseThrow(() -> new IllegalStateException("The booking assignment could not be reloaded."));
    }

    private DriverTripResponse toResponse(DriverAssignmentRepository.DriverTripRow trip) {
        return new DriverTripResponse(
                trip.driverProfileId(), trip.driverCode(), trip.booking(),
                trip.vehicleId(), trip.vehicleNumber(), trip.reservedFromAt(), trip.reservedUntilAt(),
                trip.assignedAt(), trip.startedAt(), trip.completedAt());
    }

    private static BookingResponse withStatus(BookingResponse booking, BookingStatus status, Instant updatedAt) {
        return new BookingResponse(
                booking.id(), booking.bookingReference(), status,
                booking.createdAt(), updatedAt, null, booking.quote());
    }

    private static void requireStatus(BookingResponse booking, BookingStatus expected, String message) {
        if (booking.status() != expected) {
            throw conflict(message);
        }
    }

    private static void requireActor(UUID actorId, String message) {
        if (actorId == null) {
            throw new IllegalArgumentException(message);
        }
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
