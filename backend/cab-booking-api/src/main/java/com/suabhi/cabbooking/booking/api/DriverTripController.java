package com.suabhi.cabbooking.booking.api;

import com.suabhi.cabbooking.booking.service.DriverAssignmentService;
import com.suabhi.cabbooking.identity.domain.AppUserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/driver/trips")
public class DriverTripController {
    private final DriverAssignmentService driverAssignmentService;

    public DriverTripController(DriverAssignmentService driverAssignmentService) {
        this.driverAssignmentService = driverAssignmentService;
    }

    @GetMapping
    public List<DriverTripResponse> list(@AuthenticationPrincipal AppUserPrincipal principal) {
        return driverAssignmentService.findTrips(principal.getId());
    }

    @GetMapping("/{bookingId}")
    public DriverTripResponse get(@AuthenticationPrincipal AppUserPrincipal principal,
                                  @PathVariable UUID bookingId) {
        return driverAssignmentService.findTrip(principal.getId(), bookingId);
    }

    @PostMapping("/{bookingId}/start")
    public DriverTripResponse start(@AuthenticationPrincipal AppUserPrincipal principal,
                                    @PathVariable UUID bookingId) {
        return driverAssignmentService.startTrip(principal.getId(), bookingId);
    }

    @PostMapping("/{bookingId}/complete")
    public DriverTripResponse complete(@AuthenticationPrincipal AppUserPrincipal principal,
                                       @PathVariable UUID bookingId) {
        return driverAssignmentService.completeTrip(principal.getId(), bookingId);
    }
}
