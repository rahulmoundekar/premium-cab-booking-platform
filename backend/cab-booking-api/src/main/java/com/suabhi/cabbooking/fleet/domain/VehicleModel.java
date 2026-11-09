package com.suabhi.cabbooking.fleet.domain;

import com.suabhi.cabbooking.common.audit.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "vehicle_models")
public class VehicleModel extends AuditableEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private VehicleCategory category;
    @Column(name = "model_code", nullable = false, unique = true, length = 80) private String modelCode;
    @Column(nullable = false, length = 100) private String manufacturer;
    @Column(name = "model_name", nullable = false, length = 120) private String modelName;
    @Column(name = "fuel_type", nullable = false, length = 30) private String fuelType;
    @Column(name = "min_passengers", nullable = false) private int minPassengers;
    @Column(name = "max_passengers", nullable = false) private int maxPassengers;
    @Column(name = "luggage_pieces", nullable = false) private int luggagePieces;
    @Column(name = "luggage_description", length = 160) private String luggageDescription;
    @Column(nullable = false) private boolean hybrid;
    @Column(nullable = false) private boolean active;
    protected VehicleModel() {}
    public String getModelCode() { return modelCode; }
    public String getManufacturer() { return manufacturer; }
    public String getModelName() { return modelName; }
    public String getFuelType() { return fuelType; }
    public int getMinPassengers() { return minPassengers; }
    public int getMaxPassengers() { return maxPassengers; }
    public int getLuggagePieces() { return luggagePieces; }
    public String getLuggageDescription() { return luggageDescription; }
    public boolean isHybrid() { return hybrid; }
}
