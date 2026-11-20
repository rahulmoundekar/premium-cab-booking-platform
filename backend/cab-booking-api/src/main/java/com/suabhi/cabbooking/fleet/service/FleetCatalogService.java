package com.suabhi.cabbooking.fleet.service;

import com.suabhi.cabbooking.common.exception.ResourceNotFoundException;
import com.suabhi.cabbooking.fleet.FleetCategoryCatalog;
import com.suabhi.cabbooking.fleet.FleetVehicleCatalog;
import com.suabhi.cabbooking.fleet.api.VehicleCategoryResponse;
import com.suabhi.cabbooking.fleet.api.VehicleModelResponse;
import com.suabhi.cabbooking.fleet.repository.VehicleCategoryRepository;
import com.suabhi.cabbooking.fleet.repository.VehicleModelRepository;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class FleetCatalogService implements FleetCategoryCatalog, FleetVehicleCatalog {
    private final VehicleCategoryRepository categoryRepository;
    private final VehicleModelRepository modelRepository;
    private final JdbcTemplate jdbcTemplate;

    public FleetCatalogService(VehicleCategoryRepository categoryRepository,
                               VehicleModelRepository modelRepository,
                               JdbcTemplate jdbcTemplate) {
        this.categoryRepository = categoryRepository;
        this.modelRepository = modelRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<FleetCategoryCatalog.CategoryReference> findActiveCategory(String categoryCode) {
        String code = categoryCode.strip().toUpperCase(Locale.ROOT);
        return categoryRepository.findByCodeAndActiveTrue(code)
                .map(category -> new FleetCategoryCatalog.CategoryReference(category.getId(), category.getCode()));
    }

    @Override
    public Optional<FleetVehicleCatalog.VehicleReference> findVehicle(UUID vehicleId) {
        List<FleetVehicleCatalog.VehicleReference> vehicles = jdbcTemplate.query("""
                SELECT v.id, v.vehicle_number, v.category_id, v.operational_city, v.status
                FROM vehicles v
                JOIN vehicle_categories c ON c.id = v.category_id
                WHERE v.id = ? AND c.active = TRUE
                """, (rs, rowNum) -> new FleetVehicleCatalog.VehicleReference(
                rs.getObject("id", UUID.class),
                rs.getString("vehicle_number"),
                rs.getObject("category_id", UUID.class),
                rs.getString("operational_city"),
                rs.getString("status")), vehicleId);
        return vehicles.stream().findFirst();
    }

    public List<VehicleCategoryResponse> getActiveCategories() {
        return categoryRepository.findByActiveTrueOrderByDisplayOrderAsc().stream()
                .map(VehicleCategoryResponse::from).toList();
    }

    public List<VehicleModelResponse> getActiveModels(String categoryCode) {
        String code = categoryCode.trim().toUpperCase(Locale.ROOT);
        categoryRepository.findByCodeAndActiveTrue(code)
                .orElseThrow(() -> new ResourceNotFoundException("Active vehicle category not found: " + code));
        return modelRepository.findByCategory_CodeAndActiveTrueOrderByModelNameAsc(code).stream()
                .map(VehicleModelResponse::from).toList();
    }
}
