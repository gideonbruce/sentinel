package com.example.ai;

import android.content.Context;
import android.location.Location;
import android.util.Log;

import androidx.annotation.NonNull;

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

/**
 * AI-powered emergency message generator using Firebase AI with Gemini Developer API.
 * Uses GenerativeModelFutures to bridge the Kotlin SDK to Java.
 */
public class AIMessageGenerator {
    private static final String TAG = "AIMessageGenerator";

    // Use "gemini-2.5-flash" for speed (critical in emergencies)
    private static final String MODEL_NAME = "gemini-2.5-flash";

    private final ExecutorService executor;
    private final GenerativeModelFutures model;

    public interface MessageCallback {
        void onMessageGenerated(String message);
        void onError(String error);
    }

    public AIMessageGenerator(Context context) {
        this.executor = Executors.newSingleThreadExecutor();

        try {
            // Initialize Firebase AI
            GenerativeModel ai = FirebaseAI.getInstance().generativeModel(MODEL_NAME);

            // Use the GenerativeModelFutures Java compatibility layer
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

        // 1. Build the prompt text from structured helper methods
        String contextInfo = buildContextInfo(location);
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

    /**
     * Build a detailed context string from available data.
     * This method includes more location details for a richer prompt.
     */
    private String buildContextInfo(Location location) {
        StringBuilder context = new StringBuilder();
        SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
        String currentTime = timeFormat.format(new Date());

        context.append("Time: ").append(currentTime).append("\n");

        if (location != null) {
            context.append(String.format(Locale.US,
                    "Location: Available (Accuracy: %.1f meters, Speed: %.1f m/s)\n",
                    location.getAccuracy(),
                    location.getSpeed()
            ));
        } else {
            context.append("Location: Not available\n");
        }
        return context.toString();
    }

    /**
     * Build the final prompt for the Gemini model with clear instructions.
     */
    private String buildPrompt(String emergencyType, String contextInfo, String userName, String customMessage) {
        // Using a more descriptive persona for better results
        return "You are an AI assistant for an emergency alert app called Sentinel. Your task is to generate a single, concise SMS message (under 160 characters) to be sent to an emergency contact.\n\n" +
                "Follow these rules strictly:\n" +
                "1. The tone must be urgent and clear. 🚨\n" +
                "2. Start with the user's name if available.\n" +
                "3. State the emergency clearly.\n" +
                "4. Do NOT include latitude/longitude coordinates. The app sends a map link separately.\n" +
                "5. If the user provided a custom note, integrate its meaning naturally.\n" +
                "6. Output ONLY the raw text for the SMS message. No extra explanations, labels, or quotation marks.\n\n" +
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
     * Shuts down the executor service to prevent resource leaks.
     * This should be called when the component owning this generator is destroyed.
     */
    public void shutdown() {
        Log.d(TAG, "Shutting down AIMessageGenerator executor.");
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
}