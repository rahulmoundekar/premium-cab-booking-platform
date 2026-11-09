package com.suabhi.cabbooking.fleet.service;

import com.suabhi.cabbooking.common.exception.ResourceNotFoundException;
import com.suabhi.cabbooking.fleet.api.VehicleCategoryResponse;
import com.suabhi.cabbooking.fleet.api.VehicleModelResponse;
import com.suabhi.cabbooking.fleet.repository.VehicleCategoryRepository;
import com.suabhi.cabbooking.fleet.repository.VehicleModelRepository;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class FleetCatalogService {
    private final VehicleCategoryRepository categoryRepository;
    private final VehicleModelRepository modelRepository;
    public FleetCatalogService(VehicleCategoryRepository categoryRepository, VehicleModelRepository modelRepository) {
        this.categoryRepository = categoryRepository;
        this.modelRepository = modelRepository;
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
