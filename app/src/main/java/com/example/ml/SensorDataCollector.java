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

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor gyroscope;

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

    public interface OnWindowCompleteListener {
        void onWindowComplete(SensorWindow window);
    }

    /**
     * Constructor
     */
    public SensorDataCollector(Context context) {
        if (context == null) {
            Log.e(TAG, "Context is null!");
            throw new IllegalArgumentException("Context cannot be null");
        }

        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager == null) {
            Log.e(TAG, "SensorManager is null");
            throw new IllegalStateException("SensorManager not available");
        }
        List<Sensor> allSensors = sensorManager.getSensorList(Sensor.TYPE_ALL);
        Log.d(TAG, "Total sensors available: " + allSensors.size());
        Log.d(TAG, "Sensors: " + allSensors);

        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

        Log.d(TAG, "Accelerometer: " + (accelerometer != null));
        Log.d(TAG, "Gyroscope: " + (gyroscope != null));
        
        if (accelerometer == null || gyroscope == null) {
            Log.e(TAG, "Required sensors not available!");
            throw new IllegalStateException("Required sensors not available on this device");
        }
    }

    /**
     * Start collecting sensor data
     */
    public void startCollecting() {
        if (accelerometer == null || gyroscope == null) {
            Log.e(TAG, "Cannot start - sensors not available");
            return;
        }

        isCollecting = true;
        accDataBuffer.clear();
        gyroDataBuffer.clear();

        sensorManager.registerListener(this, accelerometer, SAMPLING_RATE_US);
        sensorManager.registerListener(this, gyroscope, SAMPLING_RATE_US);

        Log.i(TAG, "Started collecting sensor data");
    }

    /**
     * Stop collecting sensor data
     */
    public void stopCollecting() {
        isCollecting = false;
        sensorManager.unregisterListener(this);
        Log.i(TAG, "Stopped collecting sensor data");
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!isCollecting) return;

        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            accDataBuffer.add(new float[]{event.values[0], event.values[1], event.values[2]});
        } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            gyroDataBuffer.add(new float[]{event.values[0], event.values[1], event.values[2]});
        }

        // Check if we have enough data for a window
        if (accDataBuffer.size() >= WINDOW_SIZE && gyroDataBuffer.size() >= WINDOW_SIZE) {
            processWindow();
        }
    }

    /**
     * Process a complete window of data
     */
    private void processWindow() {
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
}