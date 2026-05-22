package com.sgtransport.backend.model;

import jakarta.persistence.*;

@Entity
@Table(name = "bus_stops")
public class BusStop {
    @Id
    private String code;
    private String name;
    private Double latitude;
    private Double longitude;
    private String roadName;

    public BusStop() {}

    public BusStop(String code, String name, Double latitude, Double longitude, String roadName) {
        this.code = code;
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.roadName = roadName;
    }

    // Getters and Setters
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public String getRoadName() { return roadName; }
    public void setRoadName(String roadName) { this.roadName = roadName; }
}
