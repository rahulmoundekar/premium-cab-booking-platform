package com.suabhi.cabbooking.fleet;

import java.util.Optional;
import java.util.UUID;

/**
 * Narrow fleet contract used by booking operations to validate a candidate vehicle.
 * Fleet entities and persistence repositories remain internal to the Fleet module.
 */
public interface FleetVehicleCatalog {
    Optional<VehicleReference> findVehicle(UUID vehicleId);

    record VehicleReference(
            UUID id,
            String vehicleNumber,
            UUID categoryId,
            String operationalCity,
            String status
    ) {
        public VehicleReference {
            if (id == null || vehicleNumber == null || vehicleNumber.isBlank()
                    || categoryId == null || operationalCity == null || operationalCity.isBlank()
                    || status == null || status.isBlank()) {
                throw new IllegalArgumentException("A complete vehicle reference is required.");
            }
        }
    }
}
