package com.example.ml;

import android.content.Context;
import android.util.Log;

import com.example.ml.FallDetectionModel;
import com.example.ml.FallDetectionResult;
import com.example.ml.DataPreprocessor;
import com.example.ml.SensorDataCollector;
import com.example.ml.SensorWindow;

import java.io.IOException;

/**
 * Main service that coordinates fall detection
 */
public class FallDetectionService {
    private static final String TAG = "FallDetectionService";

    private final Context context;
    private FallDetectionModel model;
    private SensorDataCollector sensorCollector;

    private OnFallDetectedListener fallDetectedListener;
    private OnPredictionListener predictionListener;

    // Thresholds
    private float fallConfidenceThreshold = 0.7f; // 70% confidence

    public interface OnFallDetectedListener {
        void onFallDetected(FallDetectionResult result);
    }

    public interface OnPredictionListener {
        void onPrediction(FallDetectionResult result);
    }

    /**
     * Constructor
     */
    public FallDetectionService(Context context) {
        this.context = context;
    }

    /**
     * Initialize the service
     */
    public void initialize() throws IOException {
        Log.i(TAG, "Initializing Fall Detection Service...");

        // Load model
        model = new FallDetectionModel(context);

        // Setup sensor collector
        sensorCollector = new SensorDataCollector(context);
        sensorCollector.setOnWindowCompleteListener(this::onSensorWindowComplete);

        Log.i(TAG, "Service initialized successfully");
    }

    /**
     * Start fall detection
     */
    public void start() {
        if (model == null) {
            Log.e(TAG, "Service not initialized!");
            return;
        }

        sensorCollector.startCollecting();
        Log.i(TAG, "Fall detection started");
    }

    /**
     * Stop fall detection
     */
    public void stop() {
        sensorCollector.stopCollecting();
        Log.i(TAG, "Fall detection stopped");
    }

    /**
     * Process a complete sensor window
     */
    private void onSensorWindowComplete(SensorWindow window) {
        // Convert to model input format
        float[][][] rawInput = window.toModelInput();

        // Preprocess (standardize)
        float[][][] preprocessedInput = DataPreprocessor.standardize(rawInput);

        // Run inference
        FallDetectionResult result = model.predict(preprocessedInput);

        Log.d(TAG, "Prediction: " + result.toString());

        // Notify prediction listener
        if (predictionListener != null) {
            predictionListener.onPrediction(result);
        }

        // Check for fall
        if (result.isFall() && result.getConfidence() >= fallConfidenceThreshold) {
            Log.w(TAG, "FALL DETECTED! Confidence: " + (result.getConfidence() * 100) + "%");

            if (fallDetectedListener != null) {
                fallDetectedListener.onFallDetected(result);
            }
        }
    }

    /**
     * Clean up resources
     */
    public void cleanup() {
        stop();
        if (model != null) {
            model.close();
            model = null;
        }
    }

    // Setters
    public void setOnFallDetectedListener(OnFallDetectedListener listener) {
        this.fallDetectedListener = listener;
    }

    public void setOnPredictionListener(OnPredictionListener listener) {
        this.predictionListener = listener;
    }

    public void setFallConfidenceThreshold(float threshold) {
        this.fallConfidenceThreshold = threshold;
    }

    // Getters
    public boolean isRunning() {
        return sensorCollector != null && sensorCollector.isCollecting();
    }
}