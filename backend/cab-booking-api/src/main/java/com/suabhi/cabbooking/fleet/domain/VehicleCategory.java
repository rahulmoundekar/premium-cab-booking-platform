package com.suabhi.cabbooking.fleet.domain;

import com.suabhi.cabbooking.common.audit.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "vehicle_categories")
public class VehicleCategory extends AuditableEntity {
    @Column(nullable = false, unique = true, length = 60) private String code;
    @Column(name = "display_name", nullable = false, length = 120) private String displayName;
    @Column(length = 500) private String description;
    @Column(name = "min_passengers", nullable = false) private int minPassengers;
    @Column(name = "max_passengers", nullable = false) private int maxPassengers;
    @Column(name = "luggage_pieces", nullable = false) private int luggagePieces;
    @Column(name = "luggage_description", length = 160) private String luggageDescription;
    @Column(name = "display_order", nullable = false) private int displayOrder;
    @Column(nullable = false) private boolean active;
    protected VehicleCategory() {}
    public String getCode() { return code; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public int getMinPassengers() { return minPassengers; }
    public int getMaxPassengers() { return maxPassengers; }
    public int getLuggagePieces() { return luggagePieces; }
    public String getLuggageDescription() { return luggageDescription; }
    public int getDisplayOrder() { return displayOrder; }
    public boolean isActive() { return active; }
}
