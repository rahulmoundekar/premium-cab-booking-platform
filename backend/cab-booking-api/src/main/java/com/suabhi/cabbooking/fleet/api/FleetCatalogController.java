package com.suabhi.cabbooking.fleet.api;

import com.suabhi.cabbooking.fleet.service.FleetCatalogService;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/public/fleet")
public class FleetCatalogController {
    private final FleetCatalogService service;
    public FleetCatalogController(FleetCatalogService service) { this.service = service; }
    @GetMapping("/categories")
    public List<VehicleCategoryResponse> getCategories() { return service.getActiveCategories(); }
    @GetMapping("/categories/{categoryCode}/models")
    public List<VehicleModelResponse> getModels(
            @PathVariable @Pattern(regexp = "[A-Za-z0-9_-]{2,80}") String categoryCode) {
        return service.getActiveModels(categoryCode);
    }
}
