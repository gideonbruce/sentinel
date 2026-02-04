package com.example.core;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

public class ShakeDetector implements SensorEventListener {
    private static final float SHAKE_THRESHOLD = 15.0f;
    private static final int SHAKE_TIME_WINDOW = 3000; // 3 seconds
    private static final int REQUIRED_SHAKES = 3;
    private static final int SHAKE_SLOP_TIME_MS = 500;
    private static final int SHAKE_COUNT_RESET_TIME_MS = 3000;
    private OnShakeListener listener;
    private long lastShakeTime = 0;
    private int shakeCount = 0;
    private long firstShakeTime = 0;
    private float shakeThreshold = SHAKE_THRESHOLD;

    public interface OnShakeListener {
        void onShake(int count);
    }

    public void setOnShakeListener(OnShakeListener listener) {
        this.listener = listener;
    }

    //set sensivity
    public void setSensitivity(float threshold) {
        this.shakeThreshold = threshold;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];
            double acceleration = Math.sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH;
            if (acceleration > shakeThreshold) {
                long currentTime = System.currentTimeMillis();
                if (currentTime - firstShakeTime > SHAKE_TIME_WINDOW) {
                    shakeCount = 0;
                    firstShakeTime = currentTime;
                }
                // Debounce: ignore shakes too close together
                if (currentTime - lastShakeTime > 500) {
                    lastShakeTime = currentTime;
                    if (shakeCount == 0) {
                        firstShakeTime = currentTime;
                    }
                    shakeCount++;
                    if (listener != null) {
                        listener.onShake(shakeCount);
                    }
                    // Resets after reaching required shakes
                    if (shakeCount >= REQUIRED_SHAKES) {
                        shakeCount = 0;
                    }
                }
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not needed for this implementation
    }

    public void reset() {
        shakeCount = 0;
        firstShakeTime = 0;
        lastShakeTime = 0;
    }
}