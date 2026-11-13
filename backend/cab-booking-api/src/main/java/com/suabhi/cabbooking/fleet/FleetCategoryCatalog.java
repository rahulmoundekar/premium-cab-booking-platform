package com.suabhi.cabbooking.fleet;

import java.util.Optional;
import java.util.UUID;

/**
 * Public fleet catalog contract for modules that need to validate a vehicle category.
 * Does not expose Fleet persistence entities or repositories.
 */
public interface FleetCategoryCatalog {
    Optional<CategoryReference> findActiveCategory(String categoryCode);

    record CategoryReference(UUID id, String code) {
        public CategoryReference {
            if (id == null || code == null || code.isBlank()) {
                throw new IllegalArgumentException("A category identifier and code are required.");
            }
        }
    }
}
