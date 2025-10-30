package com.example.data;

import android.annotation.SuppressLint;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import com.google.firebase.database.Exclude;
import com.google.firebase.database.IgnoreExtraProperties;

@IgnoreExtraProperties
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

    //firebase key for syncing  not stored in room
    @Ignore
    private String firebaseKey;

    public AlertEntity() {
        // Default constructor required for calls to DataSnapshot.getValue(AlertEntity.class) and for firebase
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

    public String getFirebaseKey() {
        return firebaseKey;
    }

    public void setFirebaseKey(String firebaseKey) {
        this.firebaseKey = firebaseKey;
    }

    // Helper method to get formatted location string
    @SuppressLint("DefaultLocale")
    @Exclude
    public String getLocationString() {
        if (locationAvailable && latitude != null && longitude != null) {
            return String.format("%.6f, %.6f", latitude, longitude);
        }
        return "Location not available";
    }

    // Helper method to get Google Maps URL
    @Exclude
    public String getGoogleMapsUrl() {
        if (locationAvailable && latitude != null && longitude != null) {
            return "https://maps.google.com/?q=" + latitude + "," + longitude;
        }
        return null;
    }
}