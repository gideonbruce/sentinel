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
        // Use a single thread executor for background callback execution

        try {
            // Initialize Firebase AI
            GenerativeModel ai = FirebaseAI.getInstance().generativeModel(MODEL_NAME);

            // Create GenerativeModel instance
            //GenerativeModel ai = firebaseVertexAI.generativeModel(MODEL_NAME);

            // Use the GenerativeModelFutures Java compatibility layer
            this.model = GenerativeModelFutures.from(ai);

            Log.d(TAG, "Firebase AI with Gemini Developer API initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize Firebase AI", e);
            throw new RuntimeException("Failed to initialize AI", e);
        }
    }

    /**
     * Generate an intelligent emergency message based on context
     */
    public void generateEmergencyMessage(
            String emergencyType,
            Location location,
            String userName,
            String customMessage,
            MessageCallback callback) {

        // 1. Build the prompt text
        String contextInfo = buildContextInfo(emergencyType, location);
        String promptText = buildPrompt(emergencyType, contextInfo, userName, customMessage);

        Log.d(TAG, "Generating message with prompt length: " + promptText.length());

        // 2. Create Content object
        Content content = new Content.Builder()
                .addText(promptText)
                .build();

        // 3. Call the API using ListenableFuture
        ListenableFuture<GenerateContentResponse> responseFuture = model.generateContent(content);

        // 4. Handle the result asynchronously
        Futures.addCallback(responseFuture, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                try {
                    String generatedMessage = result.getText();

                    if (generatedMessage == null || generatedMessage.isEmpty()) {
                        throw new Exception("Empty response from AI");
                    }

                    Log.i(TAG, "AI message generated successfully (length: " + generatedMessage.length() + ")");

                    // Switch to Main Thread for the callback
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

    private void handleError(Throwable t, MessageCallback callback) {
        Log.e(TAG, "Error generating message", t);
        if (callback != null) {
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    callback.onError(t.getMessage())
            );
        }
    }

    /**
     * Build context information from available data
     */
    private String buildContextInfo(String emergencyType, Location location) {
        StringBuilder context = new StringBuilder();

        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        String currentTime = timeFormat.format(new Date());

        context.append("Time: ").append(currentTime).append("\n");

        if (location != null) {
            context.append("Location: Available\n");
        }

        if (emergencyType != null && !emergencyType.isEmpty()) {
            context.append("Emergency Type: ").append(emergencyType).append("\n");
        }

        return context.toString();
    }

    /**
     * Build the prompt for Gemini
     */
    private String buildPrompt(String emergencyType, String contextInfo, String userName, String customMessage) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("Generate a concise emergency SMS (max 160 chars). ");
        prompt.append("\nContext: ").append(contextInfo);

        if (userName != null && !userName.isEmpty()) {
            prompt.append("\nRecipient: ").append(userName);
        }
        if (customMessage != null && !customMessage.isEmpty()) {
            prompt.append("\nTone/Style: ").append(customMessage);
        }

        prompt.append("\n\nRequirements:");
        prompt.append("\n- Urgent & clear");
        prompt.append("\n- Do NOT include coordinates in text");
        prompt.append("\n- Use appropriate emojis (🚨, 🆘)");
        prompt.append("\n- Output ONLY the message text, no explanations");

        return prompt.toString();
    }

    /**
     * Shutdown the executor service properly
     * Call this when the activity/service is destroyed
     */
    public void shutdown() {
        Log.d(TAG, "shutdown() called");
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
            Log.d(TAG, "Executor shutdown initiated");
        }
    }
}