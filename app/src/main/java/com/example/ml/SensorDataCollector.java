package com.example.ml;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects accelerometer and gyroscope data in windows
 */
public class SensorDataCollector implements SensorEventListener {
    private static final String TAG = "SensorDataCollector";

    private final SensorManager sensorManager;
    private final Sensor accelerometer;
    private final Sensor gyroscope;

    // Data collection parameters
    private static final int WINDOW_SIZE = 160;
    private static final int SAMPLING_RATE_US = 20000; // 50Hz (20ms intervals)

    // Data buffers
    private final List<float[]> accDataBuffer = new ArrayList<>();
    private final List<float[]> gyroDataBuffer = new ArrayList<>();

    // Listener for window completion
    private OnWindowCompleteListener windowCompleteListener;

    // State
    private boolean isCollecting = false;
    private boolean hasGyroscope = false;

    // For gyroscope estimation
    private float[] lastAccData = null;
    private long lastAccTimestamp = 0;

    public interface OnWindowCompleteListener {
        void onWindowComplete(SensorWindow window);
    }

    /**
     * Constructor
     */
    public SensorDataCollector(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Context cannot be null");
        }

        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager == null) {
            throw new IllegalStateException("SensorManager not available");
        }

        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        hasGyroscope = (gyroscope != null);

        Log.d(TAG, "Accelerometer: " + (accelerometer != null));
        Log.d(TAG, "Gyroscope: " + (hasGyroscope ? "hardware" : "estimated"));

        if (accelerometer == null) {
            throw new IllegalStateException("Accelerometer not available!");
        }
    }

    /**
     * Start collecting sensor data
     */
    public void startCollecting() {
        if (accelerometer == null) {
            Log.e(TAG, "Cannot start - sensors not available");
            return;
        }

        isCollecting = true;
        accDataBuffer.clear();
        gyroDataBuffer.clear();
        lastAccData = null;
        lastAccTimestamp = 0;

        sensorManager.registerListener(this, accelerometer, SAMPLING_RATE_US);
        if (hasGyroscope) {
            sensorManager.registerListener(this, gyroscope, SAMPLING_RATE_US);
        }

        Log.i(TAG, "Started collecting sensor data (mode: " +
                (hasGyroscope ? "hardware gyro" : "estimated gyro") + ")");
    }

    /**
     * Stop collecting sensor data
     */
    public void stopCollecting() {
        isCollecting = false;
        sensorManager.unregisterListener(this);
        lastAccData = null;
        lastAccTimestamp = 0;
        Log.i(TAG, "Stopped collecting sensor data");
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!isCollecting) return;

        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float[] currentAcc = new float[]{event.values[0], event.values[1], event.values[2]};
            accDataBuffer.add(currentAcc);

            // Estimate gyroscope data if hardware gyroscope not available
            if (!hasGyroscope) {
                if (lastAccData != null && lastAccTimestamp > 0) {
                    float dt = (event.timestamp - lastAccTimestamp) / 1_000_000_000.0f; // Convert to seconds
                    float[] estimatedGyro = estimateGyroFromAccel(lastAccData, currentAcc, dt);
                    gyroDataBuffer.add(estimatedGyro);
                } else {
                    // First sample - add zero gyro data to keep buffers in sync
                    gyroDataBuffer.add(new float[]{0.0f, 0.0f, 0.0f});
                }
                lastAccData = currentAcc;
                lastAccTimestamp = event.timestamp;
            }

        } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE && hasGyroscope) {
            gyroDataBuffer.add(new float[]{event.values[0], event.values[1], event.values[2]});
        }

        // Check if we have enough data for a window
        if (accDataBuffer.size() >= WINDOW_SIZE && gyroDataBuffer.size() >= WINDOW_SIZE) {
            processWindow();
        }
    }

    /**
     * Estimates angular velocity from accelerometer data using actual time delta
     */
    private float[] estimateGyroFromAccel(float[] prevAcc, float[] currentAcc, float dt) {
        // Avoid division by zero
        if (dt <= 0.0001f) {
            return new float[]{0.0f, 0.0f, 0.0f};
        }

        // Normalize both vectors
        float[] normPrevAcc = normalize(prevAcc);
        float[] normCurrentAcc = normalize(currentAcc);

        // Cross product gives rotation axis
        float[] axis = new float[3];
        axis[0] = normPrevAcc[1] * normCurrentAcc[2] - normPrevAcc[2] * normCurrentAcc[1];
        axis[1] = normPrevAcc[2] * normCurrentAcc[0] - normPrevAcc[0] * normCurrentAcc[2];
        axis[2] = normPrevAcc[0] * normCurrentAcc[1] - normPrevAcc[1] * normCurrentAcc[0];

        // Dot product gives cos(angle)
        float dotProduct = normPrevAcc[0] * normCurrentAcc[0] +
                normPrevAcc[1] * normCurrentAcc[1] +
                normPrevAcc[2] * normCurrentAcc[2];

        // Clamp dot product to valid range for acos
        dotProduct = Math.max(-1.0f, Math.min(1.0f, dotProduct));

        // Get rotation angle using atan2 for better numerical stability
        float sinAngle = magnitude(axis);
        float angle = (float) Math.atan2(sinAngle, dotProduct);

        // Avoid very small angles that might be noise
        if (Math.abs(angle) < 0.001f) {
            return new float[]{0.0f, 0.0f, 0.0f};
        }

        // Angular velocity = angle / time
        float angularSpeed = angle / dt;

        // Normalize axis and scale by angular speed
        float axisMag = magnitude(axis);
        if (axisMag > 0.0001f) {
            return new float[]{
                    (axis[0] / axisMag) * angularSpeed,
                    (axis[1] / axisMag) * angularSpeed,
                    (axis[2] / axisMag) * angularSpeed
            };
        }

        return new float[]{0.0f, 0.0f, 0.0f};
    }

    private float[] normalize(float[] v) {
        float mag = magnitude(v);
        if (mag < 0.0001f) {
            return new float[]{0.0f, 0.0f, 1.0f}; // Return default up vector
        }
        return new float[]{v[0] / mag, v[1] / mag, v[2] / mag};
    }

    private float magnitude(float[] v) {
        return (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
    }

    /**
     * Process a complete window of data
     */
    private void processWindow() {
        if (accDataBuffer.size() < WINDOW_SIZE || gyroDataBuffer.size() < WINDOW_SIZE) {
            Log.w(TAG, "Buffer size mismatch - acc: " + accDataBuffer.size() +
                    ", gyro: " + gyroDataBuffer.size());
            return;
        }

        // Extract window data
        List<float[]> accWindow = new ArrayList<>(accDataBuffer.subList(0, WINDOW_SIZE));
        List<float[]> gyroWindow = new ArrayList<>(gyroDataBuffer.subList(0, WINDOW_SIZE));

        // Create window object
        SensorWindow window = new SensorWindow(accWindow, gyroWindow);

        // Remove processed data (keep 50% overlap)
        int removeCount = WINDOW_SIZE / 2;
        accDataBuffer.subList(0, removeCount).clear();
        gyroDataBuffer.subList(0, removeCount).clear();

        // Notify listener
        if (windowCompleteListener != null) {
            windowCompleteListener.onWindowComplete(window);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not used
    }

    public void setOnWindowCompleteListener(OnWindowCompleteListener listener) {
        this.windowCompleteListener = listener;
    }

    public boolean isCollecting() {
        return isCollecting;
    }

    public boolean hasGyroscope() {
        return hasGyroscope;
    }
}