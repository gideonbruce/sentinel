package com.example.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "alert_history")
public class AlertEntity {
    @PrimaryKey(autoGenerate = true)
    private int id;

    private String alertType;
    private long timestamp;
    private Double latitude;
    private Double longitude;
    private String contactName;
    private String contactPhone;
    private boolean locationAvailable;

    public AlertEntity() {
        // Default constructor required for calls to DataSnapshot.getValue(AlertEntity.class)
    }

    public AlertEntity(String alertType, long timestamp, Double latitude,
                       Double longitude, String contactName, String contactPhone,
                       boolean locationAvailable) {
        this.alertType = alertType;
        this.timestamp = timestamp;
        this.latitude = latitude;
        this.longitude = longitude;
        this.contactName = contactName;
        this.contactPhone = contactPhone;
        this.locationAvailable = locationAvailable;
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getAlertType() {
        return alertType;
    }

    public void setAlertType(String alertType) {
        this.alertType = alertType;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public String getContactName() {
        return contactName;
    }

    public void setContactName(String contactName) {
        this.contactName = contactName;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
    }

    public boolean isLocationAvailable() {
        return locationAvailable;
    }

    public void setLocationAvailable(boolean locationAvailable) {
        this.locationAvailable = locationAvailable;
    }
}