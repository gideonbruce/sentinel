package com.example.ml;

import android.content.Context;
import android.util.Log;
import java.io.IOException;
import java.util.List;


public class FallDetectionService {
    private static final String TAG = "FallDetectionService";

    private final Context context;
    private FallDetectionModel model;
    private SensorDataCollector sensorCollector;

    private OnFallDetectedListener fallDetectedListener;
    private OnPredictionListener predictionListener;

    // Thresholds
    private float fallConfidenceThreshold = 0.3f;

    private OnFallDetectedCallback fallDetectedCallback;

    public interface OnFallDetectedCallback {
        void onFallDetected(FallDetectionResult result);
    }
    public interface OnFallDetectedListener {
        void onFallDetected(FallDetectionResult result);
    }

    public void setOnFallDetectedCallback(OnFallDetectedCallback callback) {
        this.fallDetectedCallback = callback;
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

        if (!sensorCollector.hasGyroscope()) {
            fallConfidenceThreshold = 0.3f; // Higher threshold for estimated gyro
            Log.w(TAG, "No hardware gyroscope - using higher confidence threshold: " + fallConfidenceThreshold);
        } else {
            fallConfidenceThreshold = 0.3f;
        }

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

    private void onSensorWindowComplete(SensorWindow window) {
        float[][][] rawInput = window.toModelInput();
        float[][][] preprocessedInput = DataPreprocessor.standardize(rawInput);

        //running inference
        FallDetectionResult result = model.predict(preprocessedInput);

        //Additional validation for devices without gyroscope
        if (!sensorCollector.hasGyroscope()) {
            boolean isLikelyFall = validateFallWithAccelerometer(window);

            //only triggers if both ML model and heiristics agree
            if (result.isFall() && !isLikelyFall) {
                Log.d(TAG, "ML detected fall but heuristics disagree - likely false positive");
                result = new FallDetectionResult(
                        0,
                        "Not a Fall (Heuristic Rejected)",
                        result.getConfidence() * 0.3f,
                        new float[]{1.0f, 0.0f}
                );
            }
        }
        Log.d(TAG, "Prediction: " + result.toString());

        if (predictionListener != null) {
            predictionListener.onPrediction(result);
        }

        // Check for fall
        if (result.isFall() && result.getConfidence() >= fallConfidenceThreshold) {
            Log.w(TAG, "FALL DETECTED! Confidence: " + (result.getConfidence() * 100) + "%");

            if (fallDetectedListener != null) {
                fallDetectedListener.onFallDetected(result);
            }
            if (fallDetectedCallback != null) {
                fallDetectedCallback.onFallDetected(result);
            }
        }
    }

    /**
     * Validate fall using accelerometer-based heuristics
     * Returns true if acceleration pattern matches a fall
     */
    private boolean validateFallWithAccelerometer(SensorWindow window) {
        List<float[]> accData = window.getAccelerometerData();

        // Check for high impact (sudden deceleration)
        float maxMagnitude = 0;
        float minMagnitude = Float.MAX_VALUE;

        for (float[] acc : accData) {
            float magnitude = (float) Math.sqrt(acc[0]*acc[0] + acc[1]*acc[1] + acc[2]*acc[2]);
            maxMagnitude = Math.max(maxMagnitude, magnitude);
            minMagnitude = Math.min(minMagnitude, magnitude);
        }

        // Fall characteristics:
        // 1. Free fall period (low acceleration, < 5 m/s²)
        // 2. Impact (high acceleration, > 20 m/s²)
        boolean hasFreeFall = minMagnitude < 5.0f;
        boolean hasImpact = maxMagnitude > 20.0f;

        return hasFreeFall && hasImpact;
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