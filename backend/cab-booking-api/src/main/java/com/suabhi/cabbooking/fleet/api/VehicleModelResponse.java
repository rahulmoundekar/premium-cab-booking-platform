package com.suabhi.cabbooking.fleet.api;

import com.suabhi.cabbooking.fleet.domain.VehicleModel;

public record VehicleModelResponse(String modelCode, String manufacturer, String modelName,
        String fuelType, int minPassengers, int maxPassengers, int luggagePieces,
        String luggageDescription, boolean hybrid) {
    public static VehicleModelResponse from(VehicleModel model) {
        return new VehicleModelResponse(model.getModelCode(), model.getManufacturer(), model.getModelName(),
                model.getFuelType(), model.getMinPassengers(), model.getMaxPassengers(),
                model.getLuggagePieces(), model.getLuggageDescription(), model.isHybrid());
    }
}
