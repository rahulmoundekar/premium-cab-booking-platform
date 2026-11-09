package com.suabhi.cabbooking.fleet.api;

import com.suabhi.cabbooking.fleet.domain.VehicleCategory;

public record VehicleCategoryResponse(String code, String name, String description,
        int minPassengers, int maxPassengers, int luggagePieces, String luggageDescription) {
    public static VehicleCategoryResponse from(VehicleCategory category) {
        return new VehicleCategoryResponse(category.getCode(), category.getDisplayName(),
                category.getDescription(), category.getMinPassengers(), category.getMaxPassengers(),
                category.getLuggagePieces(), category.getLuggageDescription());
    }
}
