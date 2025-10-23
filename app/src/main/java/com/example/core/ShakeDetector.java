package com.example.core;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

public class ShakeDetector implements SensorEventListener {
    private static final float SHAKE_THRESHOLD = 15.0f;
    private static final int SHAKE_TIME_WINDOW = 3000; // 3 seconds
    private static final int REQUIRED_SHAKES = 3;

    private OnShakeListener listener;
    private long lastShakeTime = 0;
    private int shakeCount = 0;
    private long firstShakeTime = 0;

    public interface OnShakeListener {
        void onShake(int count);
    }

    public void setOnShakeListener(OnShakeListener listener) {
        this.listener = listener;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];

            double acceleration = Math.sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH;

            if (acceleration > SHAKE_THRESHOLD) {
                long currentTime = System.currentTimeMillis();

                // Reset if too much time has passed
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

                    // Reset after reaching required shakes
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