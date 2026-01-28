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
    private float[] lastAccData = null;


    public interface OnWindowCompleteListener {
        void onWindowComplete(SensorWindow window);
    }

    /**
     * Constructor
     */
    public SensorDataCollector(Context context) {
        if (context == null) {
            Log.e(TAG, "Context is null!");
            return;
        }

        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager == null) {
            Log.e(TAG, "SensorManager is null");
        }

        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        hasGyroscope = (gyroscope != null);


        Log.d(TAG, "Accelerometer: " + (accelerometer != null));
        Log.d(TAG, "Gyroscope: " + hasGyroscope);
        
        if (accelerometer == null) {
            Log.e(TAG, "Required sensors not available!");
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

        sensorManager.registerListener(this, accelerometer, SAMPLING_RATE_US);
        if (hasGyroscope) {
            sensorManager.registerListener(this, gyroscope, SAMPLING_RATE_US);
        }

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
            float[] currentAcc = event.values.clone();
            accDataBuffer.add(currentAcc);

            if (!hasGyroscope) {
                if (lastAccData != null) {
                    float[] estimatedGyro = estimateGyroFromAccel(lastAccData, currentAcc);
                    gyroDataBuffer.add(estimatedGyro);
                }
                lastAccData = currentAcc;
            }
        } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE && hasGyroscope) {
            gyroDataBuffer.add(event.values.clone());
        }

        // Check if we have enough data for a window
        if (accDataBuffer.size() >= WINDOW_SIZE && gyroDataBuffer.size() >= WINDOW_SIZE) {
            processWindow();
        }
    }

    /**
     * Estimates angular velocity from accelerometer data.
     */
    private float[] estimateGyroFromAccel(float[] prevAcc, float[] currentAcc) {
        // Normalize both vectors
        float[] normPrevAcc = normalize(prevAcc);
        float[] normCurrentAcc = normalize(currentAcc);

        // Cross product
        float[] crossProd = new float[3];
        crossProd[0] = normPrevAcc[1] * normCurrentAcc[2] - normPrevAcc[2] * normCurrentAcc[1];
        crossProd[1] = normPrevAcc[2] * normCurrentAcc[0] - normPrevAcc[0] * normCurrentAcc[2];
        crossProd[2] = normPrevAcc[0] * normCurrentAcc[1] - normPrevAcc[1] * normCurrentAcc[0];

        // The magnitude of the cross product is sin(angle)
        float sinAngle = magnitude(crossProd);

        // Get the angle
        float angle = (float) Math.asin(sinAngle);

        // dt in seconds
        float dt = SAMPLING_RATE_US / 1_000_000.0f;

        // Angular velocity = angle / dt
        float angularVelocity = angle / dt;

        // Normalize the cross product to get the axis
        if (sinAngle > 0) {
            crossProd[0] /= sinAngle;
            crossProd[1] /= sinAngle;
            crossProd[2] /= sinAngle;
        }

        // The estimated gyro data is axis * angularVelocity
        crossProd[0] *= angularVelocity;
        crossProd[1] *= angularVelocity;
        crossProd[2] *= angularVelocity;

        return crossProd;
    }

    private float[] normalize(float[] v) {
        float mag = magnitude(v);
        if (mag == 0) return new float[3];
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
}