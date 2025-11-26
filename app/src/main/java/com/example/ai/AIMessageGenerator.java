package com.example.ai;

import android.content.Context;
import android.location.Location;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AI-powered emergency message generator using Google Gemini API (Firebase AI Logic).
 * Generates context-aware messages based on time, location, and emergency type.
 */
public class AIMessageGenerator {
    private static final String TAG = "AIMessageGenerator";
    // Using the public endpoint for Gemini-2.5-flash.
    private static final String GEMINI_MODEL_NAME = "gemini-2.5-flash-preview-09-2025";
    private static final String GEMINI_API_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/" + GEMINI_MODEL_NAME + ":generateContent";

    // Store your API key securely (e.g., in Android Keystore or BuildConfig)
    private final String apiKey;
    private final ExecutorService executorService;
    private final Context context;

    public interface MessageCallback {
        void onMessageGenerated(String message);
        void onError(String error);
    }

    public AIMessageGenerator(Context context, String apiKey) {
        this.context = context;
        this.apiKey = apiKey;
        this.executorService = Executors.newSingleThreadExecutor();
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

        executorService.execute(() -> {
            try {
                String contextInfo = buildContextInfo(emergencyType, location);
                String prompt = buildPrompt(emergencyType, contextInfo, userName, customMessage);

                // Use the Gemini API call
                String generatedMessage = callGeminiAPI(prompt);

                // Run callback on main thread
                if (callback != null) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                            callback.onMessageGenerated(generatedMessage)
                    );
                }

            } catch (Exception e) {
                Log.e(TAG, "Error generating message", e);
                if (callback != null) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                            callback.onError(e.getMessage())
                    );
                }
            }
        });
    }

    /**
     * Build context information from available data
     */
    private String buildContextInfo(String emergencyType, Location location) {
        StringBuilder context = new StringBuilder();

        // Time context
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, MMMM d", Locale.getDefault());
        String currentTime = timeFormat.format(new Date());
        String currentDate = dateFormat.format(new Date());

        context.append("Time: ").append(currentTime).append("\n");
        context.append("Date: ").append(currentDate).append("\n");

        // Time of day context
        try {
            int hour = Integer.parseInt(currentTime.split(":")[0]);
            if (hour >= 22 || hour < 6) {
                context.append("Time of day: Late night/Early morning\n");
            } else if (hour >= 6 && hour < 12) {
                context.append("Time of day: Morning\n");
            } else if (hour >= 12 && hour < 17) {
                context.append("Time of day: Afternoon\n");
            } else {
                context.append("Time of day: Evening\n");
            }
        } catch (NumberFormatException ignored) {
            // Safe fallback if time parsing fails
        }

        // Location context
        if (location != null) {
            context.append("Location available: Yes\n");
            context.append("Coordinates: ").append(location.getLatitude())
                    .append(", ").append(location.getLongitude()).append("\n");

            // Location accuracy
            if (location.hasAccuracy()) {
                context.append("Location accuracy: ").append(location.getAccuracy()).append("m\n");
            }
        } else {
            context.append("Location available: No\n");
        }

        // Emergency type
        if (emergencyType != null && !emergencyType.isEmpty()) {
            context.append("Emergency type: ").append(emergencyType).append("\n");
        }

        return context.toString();
    }

    /**
     * Build the prompt for the Gemini API
     */
    private String buildPrompt(String emergencyType, String contextInfo,
                               String userName, String customMessage) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("You are generating an emergency alert message. ");
        prompt.append("Create a clear, urgent, and concise message. Your final output MUST NOT exceed 160 characters (for SMS compatibility). ");
        prompt.append("Generate ONLY the raw emergency message text, nothing else. Do not use markdown (e.g., no asterisks or headers).");
        prompt.append("\n\nContext:\n").append(contextInfo);

        if (userName != null && !userName.isEmpty()) {
            prompt.append("User name: ").append(userName).append("\n");
        }

        if (customMessage != null && !customMessage.isEmpty()) {
            prompt.append("\n\nUser's preferred message style/additional detail: ").append(customMessage);
        }

        prompt.append("\n\nConstraints:\n");
        prompt.append("1. Max length is 160 characters.\n");
        prompt.append("2. Be extremely clear and urgent.\n");
        prompt.append("3. Include relevant emojis for urgency (e.g., 🚨, 🔥).\n");
        prompt.append("4. Mention the emergency type if specified.\n");
        prompt.append("5. Be appropriate for the time of day.\n");
        prompt.append("6. Request immediate help.\n");
        prompt.append("7. DO NOT include location coordinates, only the urgent request for help.\n");

        return prompt.toString();
    }

    /**
     * Call the Gemini API to generate the message
     */
    private String callGeminiAPI(String prompt) throws Exception {
        // Append API key to the URL as a query parameter
        URL url = new URL(GEMINI_API_URL + "?key=" + apiKey);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(30000);

            // Build request body for Gemini API
            JSONObject requestBody = new JSONObject();
            requestBody.put("model", GEMINI_MODEL_NAME);

            JSONArray contents = new JSONArray();
            JSONObject userMessage = new JSONObject();
            userMessage.put("role", "user");

            JSONArray parts = new JSONArray();
            JSONObject textPart = new JSONObject();
            textPart.put("text", prompt);
            parts.put(textPart);

            userMessage.put("parts", parts);
            contents.put(userMessage);
            requestBody.put("contents", contents);

            // Set max tokens in generationConfig
            JSONObject generationConfig = new JSONObject();
            generationConfig.put("maxOutputTokens", 200);
            requestBody.put("generationConfig", generationConfig);


            // Send request
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            // Read response
            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                br.close();

                // Parse response: candidates[0].content.parts[0].text
                JSONObject jsonResponse = new JSONObject(response.toString());
                JSONArray candidates = jsonResponse.getJSONArray("candidates");

                if (candidates.length() > 0) {
                    JSONObject candidate = candidates.getJSONObject(0);
                    JSONObject content = candidate.getJSONObject("content");
                    JSONArray responseParts = content.getJSONArray("parts");

                    if (responseParts.length() > 0) {
                        return responseParts.getJSONObject(0).getString("text").trim();
                    }
                }

                throw new Exception("API response structure error: No text content found.");
            } else {
                BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8));
                StringBuilder errorResponse = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    errorResponse.append(line);
                }
                br.close();

                throw new Exception("API error: HTTP " + responseCode + " - " + errorResponse.toString());
            }
        } catch (JSONException e) {
            throw new Exception("Error processing API response JSON: " + e.getMessage(), e);
        } finally {
            conn.disconnect();
        }
    }

    /**
     * Generate a fallback message if AI fails
     */
    public static String getFallbackMessage(String emergencyType, String customMessage) {
        if (customMessage != null && !customMessage.isEmpty()) {
            // Use the custom message directly if provided
            return customMessage;
        }

        if (emergencyType != null && !emergencyType.isEmpty()) {
            return "🚨 " + emergencyType.toUpperCase() + "! I need immediate help. Please check on me urgently.";
        }

        return "🚨 EMERGENCY! I need immediate help. Please check on me urgently.";
    }

    /**
     * Cleanup resources
     */
    public void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
}