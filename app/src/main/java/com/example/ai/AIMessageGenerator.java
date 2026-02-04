package com.example.ai;

import android.content.Context;
import android.location.Location;
import android.util.Log;
import androidx.annotation.NonNull;
import com.example.core.LocationGeocoder;
import com.google.firebase.ai.FirebaseAI;
import com.google.firebase.ai.GenerativeModel;
import com.google.firebase.ai.java.GenerativeModelFutures;
import com.google.firebase.ai.type.Content;
import com.google.firebase.ai.type.GenerateContentResponse;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AIMessageGenerator {
    private static final String TAG = "AIMessageGenerator";
    private static final String MODEL_NAME = "gemini-2.5-flash";
    private final ExecutorService executor;
    private final GenerativeModelFutures model;
    private final LocationGeocoder locationGeocoder;

    public interface MessageCallback {
        void onMessageGenerated(String message);
        void onError(String error);
    }

    public AIMessageGenerator(Context context) {
        this.executor = Executors.newSingleThreadExecutor();
        this.locationGeocoder = new LocationGeocoder(context);

        try {
            GenerativeModel ai = FirebaseAI.getInstance().generativeModel(MODEL_NAME);

            this.model = GenerativeModelFutures.from(ai);

            Log.d(TAG, "Firebase AI with Gemini Developer API ('" + MODEL_NAME + "') initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize Firebase AI", e);
            throw new IllegalStateException("Failed to initialize AI model. Check Firebase setup and dependencies.", e);
        }
    }

    /**
     * Generate an intelligent emergency message based on context.
     * @param emergencyType The type of emergency (e.g., "Car Accident", "Fall Detected").
     * @param location The user's current location.
     * @param userName The name of the user in distress.
     * @param customMessage An additional note from the user.
     * @param callback The callback to handle the generated message or an error.
     */
    public void generateEmergencyMessage(
            String emergencyType,
            Location location,
            String userName,
            String customMessage,
            @NonNull MessageCallback callback) {

        // If location is available, geocode it first, then generate the message
        if (location != null && locationGeocoder.isGeocoderAvailable()) {
            locationGeocoder.getLocationName(location, new LocationGeocoder.GeocoderCallback() {
                @Override
                public void onLocationResolved(LocationGeocoder.LocationInfo locationInfo) {
                    // Generate message with geocoded location info
                    generateMessageWithLocationInfo(emergencyType, location, locationInfo,
                            userName, customMessage, callback);
                }
                @Override
                public void onGeocoderError(String error) {
                    Log.w(TAG, "Geocoding failed, generating message without location name: " + error);
                    // Generate message with basic location data only
                    generateMessageWithLocationInfo(emergencyType, location, null,
                            userName, customMessage, callback);
                }
            });
        } else {
            // No location available, generate message without it
            generateMessageWithLocationInfo(emergencyType, null, null,
                    userName, customMessage, callback);
        }
    }

    /**
     * Internal method that actually generates the message after location info is resolved.
     */
    private void generateMessageWithLocationInfo(
            String emergencyType,
            Location location,
            LocationGeocoder.LocationInfo locationInfo,
            String userName,
            String customMessage,
            @NonNull MessageCallback callback) {

        // 1. Build the prompt text with all available information
        String contextInfo = buildContextInfo(location, locationInfo);
        String promptText = buildPrompt(emergencyType, contextInfo, userName, customMessage);

        Log.d(TAG, "Generating message with prompt: " + promptText);

        // 2. Create Content object for the API
        Content content = new Content.Builder()
                .addText(promptText)
                .build();

        // 3. Call the API using the ListenableFuture wrapper
        ListenableFuture<GenerateContentResponse> responseFuture = model.generateContent(content);

        // 4. Handle the result asynchronously on our dedicated executor
        Futures.addCallback(responseFuture, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                try {
                    String generatedMessage = result.getText();

                    if (generatedMessage == null || generatedMessage.trim().isEmpty()) {
                        throw new IllegalStateException("Received an empty or null response from the AI model.");
                    }

                    Log.i(TAG, "AI message generated successfully (length: " + generatedMessage.length() + ")");

                    // Switch to Main Thread for the UI callback
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                            callback.onMessageGenerated(generatedMessage.trim())
                    );
                } catch (Exception e) {
                    handleError(e, callback);
                }
            }

            @Override
            public void onFailure(@NonNull Throwable t) {
                handleError(t, callback);
            }
        }, executor);
    }

    private void handleError(Throwable t, @NonNull MessageCallback callback) {
        Log.e(TAG, "Error generating AI message", t);
        // Ensure UI updates happen on the main thread
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                callback.onError(t.getMessage() != null ? t.getMessage() : "An unknown error occurred.")
        );
    }

    private String buildContextInfo(Location location, LocationGeocoder.LocationInfo locationInfo) {
        StringBuilder context = new StringBuilder();
        SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
        String currentTime = timeFormat.format(new Date());

        context.append("Time: ").append(currentTime).append("\n");

        if (location != null) {
            // Add basic location data
            context.append(String.format(Locale.US,
                    "GPS Coordinates: %.6f, %.6f\n",
                    location.getLatitude(),
                    location.getLongitude()
            ));
            //context.append(String.format(Locale.US, "Location Accuracy: %.1f meters\n", location.getAccuracy()));
            context.append(String.format(Locale.US,
                    "Location Accuracy: %.1f meters\n",
                    location.getAccuracy()
            ));

            if (location.hasSpeed() && location.getSpeed() > 0) {
                context.append(String.format(Locale.US,
                        "Speed: %.1f m/s\n",
                        location.getSpeed()
                ));
            }

            // Add geocoded location details if available
            if (locationInfo != null) {
                context.append("--- LOCATION DETAILS ---\n");

                if (locationInfo.getPremises() != null) {
                    context.append("Building/Complex: ").append(locationInfo.getPremises()).append("\n");
                }

                if (locationInfo.getFeatureName() != null) {
                    context.append("Feature/Building: ").append(locationInfo.getFeatureName()).append("\n");
                }

                if (locationInfo.getThoroughfare() != null) {
                    context.append("Street: ");
                    if (locationInfo.getSubThoroughfare() != null) {
                        context.append(locationInfo.getSubThoroughfare()).append(" ");
                    }
                    context.append(locationInfo.getThoroughfare()).append("\n");
                }

                if (locationInfo.getSubLocality() != null) {
                    context.append("Neighborhood: ").append(locationInfo.getSubLocality()).append("\n");
                }

                if (locationInfo.getLocality() != null) {
                    context.append("City: ").append(locationInfo.getLocality()).append("\n");
                }

                if (locationInfo.getSubAdminArea() != null) {
                    context.append("County/District: ").append(locationInfo.getSubAdminArea()).append("\n");
                }

                if (locationInfo.getPostalCode() != null) {
                    context.append("Postal Code: ").append(locationInfo.getPostalCode()).append("\n");
                }

                if (locationInfo.getCountryName() != null) {
                    context.append("Country: ").append(locationInfo.getCountryName()).append("\n");
                }

                // Add short description for easy reference
                context.append("Full Address: ").append(locationInfo.getFullDescription()).append("\n");
            }
        } else {
            context.append("Location: Not available\n");
        }

        return context.toString();
    }

    private String buildPrompt(String emergencyType, String contextInfo, String userName, String customMessage) {
        // Using a more descriptive persona for better results
        return "You are an AI assistant for an emergency alert app called Sentinel. Your task is to generate a single, concise SMS message (not less than 300 characters) to be sent to an emergency contact.\n\n" +
                "Follow these rules strictly:\n" +
                "1. The tone must be urgent and clear. 🚨\n" +
                "2. Start with the user's name if available.\n" +
                "3. State the emergency clearly.\n" +
                "4. If detailed location information (address, street name, city) is provided, include all location details in a natural way.\n" +
                "5. Do NOT include GPS coordinates - the app sends a map link separately.\n" +
                "6. If the time is provided, include it.\n" +
                "7. Output ONLY the raw text for the SMS message. No extra explanations, labels, or quotation marks.\n\n" +
                "---\n" +
                "EMERGENCY DETAILS:\n" +
                (userName != null && !userName.isEmpty() ? "User's Name: " + userName + "\n" : "") +
                "Emergency Type: " + emergencyType + "\n" +
                (customMessage != null && !customMessage.isEmpty() ? "User's Note: \"" + customMessage + "\"\n" : "") +
                "Context:\n" + contextInfo +
                "---\n\n" +
                "Generated SMS:";
    }

    /**
     * Shuts down the executor service and geocoder to prevent resource leaks.
     * This should be called when the component owning this generator is destroyed.
     */
    public void shutdown() {
        Log.d(TAG, "Shutting down AIMessageGenerator executor and geocoder.");
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
        if (locationGeocoder != null) {
            locationGeocoder.shutdown();
        }
    }
}