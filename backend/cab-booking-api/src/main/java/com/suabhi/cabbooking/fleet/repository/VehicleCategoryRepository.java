package com.suabhi.cabbooking.fleet.repository;

import com.suabhi.cabbooking.fleet.domain.VehicleCategory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleCategoryRepository extends JpaRepository<VehicleCategory, UUID> {
    List<VehicleCategory> findByActiveTrueOrderByDisplayOrderAsc();
    Optional<VehicleCategory> findByCodeAndActiveTrue(String code);
}
