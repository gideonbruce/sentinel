package com.example.core;

import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.List;

/**
 * Detects volume button gesture patterns for emergency alerts
 */
public class VolumeButtonGestureDetector {

    private static final int VOLUME_DOWN_RAPID_COUNT = 5; // 5 quick presses
    private static final int VOLUME_UP_RAPID_COUNT = 3;   // 3 quick presses
    private static final long RAPID_PRESS_WINDOW_MS = 3000; // Within 3 seconds
    private static final long LONG_PRESS_DURATION_MS = 5000; // Hold for 5 seconds

    private final List<Long> volumeDownPresses = new ArrayList<>();
    private final List<Long> volumeUpPresses = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private long volumeDownPressStartTime = 0;
    private boolean volumeDownPressed = false;

    private OnVolumeGestureListener listener;

    public interface OnVolumeGestureListener {
        void onSilentEmergency();    // 5x Volume Down rapidly
        void onPoliceNeeded();       // 3x Volume Up rapidly
        void onMedicalEmergency();   // Hold Volume Down 5 seconds

        void onPanicAlert();
    }

    public VolumeButtonGestureDetector(OnVolumeGestureListener listener) {
        this.listener = listener;
    }

    /**
     * Call this when volume button is pressed down
     */
    public boolean onVolumeDown() {
        long currentTime = System.currentTimeMillis();

        if (!volumeDownPressed) {
            volumeDownPressed = true;
            volumeDownPressStartTime = currentTime;
            volumeDownPresses.add(currentTime);

            // Start checking for long press
            handler.postDelayed(longPressCheck, LONG_PRESS_DURATION_MS);

            // Check for rapid presses
            checkRapidPresses();
        }

        return true; // Consume event
    }

    /**
     * Call this when volume button is released
     */
    public boolean onVolumeUp() {
        volumeDownPressed = false;
        handler.removeCallbacks(longPressCheck);
        return true;
    }

    /**
     * Call this when volume UP button is pressed
     */
    public boolean onVolumeUpButton() {
        long currentTime = System.currentTimeMillis();
        volumeUpPresses.add(currentTime);
        checkRapidPresses();
        return true;
    }

    private void checkRapidPresses() {
        long currentTime = System.currentTimeMillis();

        // Remove old presses outside time window
        volumeDownPresses.removeIf(time -> (currentTime - time) > RAPID_PRESS_WINDOW_MS);
        volumeUpPresses.removeIf(time -> (currentTime - time) > RAPID_PRESS_WINDOW_MS);

        // Check Volume Down (Silent Emergency)
        if (volumeDownPresses.size() >= VOLUME_DOWN_RAPID_COUNT) {
            if (listener != null) {
                listener.onSilentEmergency();
            }
            reset();
        }

        // Check Volume Up (Police Alert)
        if (volumeUpPresses.size() >= VOLUME_UP_RAPID_COUNT) {
            if (listener != null) {
                listener.onPoliceNeeded();
            }
            reset();
        }
    }

    private final Runnable longPressCheck = new Runnable() {
        @Override
        public void run() {
            if (volumeDownPressed) {
                if (listener != null) {
                    listener.onMedicalEmergency();
                }
                reset();
            }
        }
    };

    public void reset() {
        volumeDownPresses.clear();
        volumeUpPresses.clear();
        volumeDownPressed = false;
        handler.removeCallbacks(longPressCheck);
    }

    public void cleanup() {
        handler.removeCallbacksAndMessages(null);
        reset();
    }
}