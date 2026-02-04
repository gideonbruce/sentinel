package com.example.core;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Utility class for converting latitude/longitude coordinates into readable location names
 * using Android's Geocoder API.
 */
public class LocationGeocoder {
    private static final String TAG = "LocationGeocoder";
    private final Context context;
    private final Geocoder geocoder;
    private final ExecutorService executorService;
    private final Handler mainHandler;

    public interface GeocoderCallback {
        void onLocationResolved(LocationInfo locationInfo);
        void onGeocoderError(String error);
    }

    /**
     * Data class containing location information
     */
    public static class LocationInfo {
        private final double latitude;
        private final double longitude;
        private final String addressLine;
        private final String featureName;
        private final String locality;
        private final String subLocality;
        private final String adminArea;
        private final String countryName;
        private final String postalCode;
        private final String premises;
        private final String thoroughfare;
        private final String subThoroughfare;
        private final String subAdminArea;

        public LocationInfo(Address address) {
            this.latitude = address.getLatitude();
            this.longitude = address.getLongitude();
            this.addressLine = address.getMaxAddressLineIndex() >= 0 ? address.getAddressLine(0) : null;
            this.featureName = address.getFeatureName();
            this.locality = address.getLocality();
            this.subLocality = address.getSubLocality();
            this.adminArea = address.getAdminArea();
            this.countryName = address.getCountryName();
            this.postalCode = address.getPostalCode();
            this.premises = address.getPremises();
            this.thoroughfare = address.getThoroughfare();
            this.subThoroughfare = address.getSubThoroughfare();
            this.subAdminArea = address.getSubAdminArea();
        }

        // Getters
        public double getLatitude() { return latitude; }
        public double getLongitude() { return longitude; }
        public String getAddressLine() { return addressLine; }
        public String getFeatureName() { return featureName; }
        public String getLocality() { return locality; }
        public String getSubLocality() { return subLocality; }
        public String getAdminArea() { return adminArea; }
        public String getCountryName() { return countryName; }
        public String getPostalCode() { return postalCode; }
        public String getPremises() { return premises; }
        public String getSubAdminArea() { return subAdminArea; }
        public String getThoroughfare() { return thoroughfare; }
        public String getSubThoroughfare() { return subThoroughfare; }

        /**
         * Returns a short, human-readable location description
         */
        public String getShortDescription() {
            StringBuilder sb = new StringBuilder();

            //trying specific point
            if (premises != null) {
                sb.append(premises);
            } else if (featureName != null) {
                sb.append(featureName);
            }
            if (thoroughfare != null) {
                if (sb.length() > 0) sb.append(", ");
                if (subThoroughfare != null) {
                    sb.append(subThoroughfare).append(" ");
                }
                sb.append(thoroughfare);
            }

            if (featureName != null && !featureName.matches(".*\\d+.*")) {
                // Only use feature name if it's not just a street number
                sb.append(featureName);
            } else if (thoroughfare != null) {
                if (subThoroughfare != null) {
                    sb.append(subThoroughfare).append(" ");
                }
                sb.append(thoroughfare);
            }

            if (locality != null) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(locality);
            } else if (subLocality != null) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(subLocality);
            }

            if (sb.length() == 0 && subAdminArea != null) {
                sb.append(subAdminArea);
                if (adminArea != null) sb.append(", ").append(adminArea);
            }

            return sb.length() > 0 ? sb.toString() : "Unknown location";
        }

        /**
         * Returns a full, detailed location description
         */
        public String getFullDescription() {
            if (addressLine != null && !addressLine.isEmpty()) {
                return addressLine;
            }

            StringBuilder sb = new StringBuilder(getShortDescription());

            if (subAdminArea != null && !sb.toString().contains(subAdminArea)) {
                sb.append(", ").append(subAdminArea);
            }
            if (adminArea != null && !sb.toString().contains(adminArea)) {
                sb.append(", ").append(adminArea);
            }
            return sb.toString();
        }

        @Override
        public String toString() {
            return "LocationInfo{" +
                    "lat=" + latitude +
                    ", lng=" + longitude +
                    ", featureName='" + featureName + '\'' +
                    ", locality='" + locality + '\'' +
                    ", adminArea='" + adminArea + '\'' +
                    ", county='" + subAdminArea + '\'' +
                    ", country='" + countryName + '\'' +
                    '}';
        }
    }

    /**
     * Constructor
     * @param context Application context
     */
    public LocationGeocoder(Context context) {
        this.context = context.getApplicationContext();
        this.geocoder = new Geocoder(this.context, Locale.getDefault());
        this.executorService = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Checks if Geocoder is available on this device
     */
    public boolean isGeocoderAvailable() {
        return Geocoder.isPresent();
    }

    /**
     * Converts Location object to readable address (async)
     */
    public void getLocationName(Location location, GeocoderCallback callback) {
        if (location == null) {
            mainHandler.post(() -> callback.onGeocoderError("Location is null"));
            return;
        }
        getLocationName(location.getLatitude(), location.getLongitude(), callback);
    }

    /**
     * Converts latitude/longitude to readable address (async)
     */
    public void getLocationName(double latitude, double longitude, GeocoderCallback callback) {
        if (!isGeocoderAvailable()) {
            mainHandler.post(() -> callback.onGeocoderError("Geocoder not available on this device"));
            return;
        }

        executorService.execute(() -> {
            try {
                List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);

                if (addresses != null && !addresses.isEmpty()) {
                    Address address = addresses.get(0);
                    LocationInfo locationInfo = new LocationInfo(address);

                    mainHandler.post(() -> callback.onLocationResolved(locationInfo));

                    Log.d(TAG, "Geocoded location: " + locationInfo);
                } else {
                    mainHandler.post(() -> callback.onGeocoderError("No address found for coordinates"));
                    Log.w(TAG, "No address found for: " + latitude + ", " + longitude);
                }
            } catch (IOException e) {
                String errorMsg = "Geocoding failed: " + e.getMessage();
                mainHandler.post(() -> callback.onGeocoderError(errorMsg));
                Log.e(TAG, errorMsg, e);
            } catch (IllegalArgumentException e) {
                String errorMsg = "Invalid coordinates: " + e.getMessage();
                mainHandler.post(() -> callback.onGeocoderError(errorMsg));
                Log.e(TAG, errorMsg, e);
            }
        });
    }

    /**
     * Synchronous version - use with caution, must be called from background thread
     */
    public LocationInfo getLocationNameSync(double latitude, double longitude) throws IOException {
        if (!isGeocoderAvailable()) {
            throw new IOException("Geocoder not available");
        }

        List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);

        if (addresses != null && !addresses.isEmpty()) {
            return new LocationInfo(addresses.get(0));
        } else {
            throw new IOException("No address found for coordinates");
        }
    }

    /**
     * Clean up resources
     */
    public void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
}