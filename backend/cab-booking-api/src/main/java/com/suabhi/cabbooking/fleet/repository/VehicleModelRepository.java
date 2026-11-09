package com.suabhi.cabbooking.fleet.repository;

import com.suabhi.cabbooking.fleet.domain.VehicleModel;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleModelRepository extends JpaRepository<VehicleModel, UUID> {
    List<VehicleModel> findByCategory_CodeAndActiveTrueOrderByModelNameAsc(String categoryCode);
}
