package com.example.gestures;

import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.List;

public class VolumeButtonGestureDetector {

    private static final int VOLUME_DOWN_RAPID_COUNT = 5;
    private static final int VOLUME_UP_RAPID_COUNT = 3;
    private static final long RAPID_PRESS_WINDOW_MS = 3000; // 3 seconds
    private static final long LONG_PRESS_DURATION_MS = 5000; // 5 seconds
    private static final long COMBO_PRESS_DURATION_MS = 3000; // 3 seconds for both buttons

    private final List<Long> volumeDownPresses = new ArrayList<>();
    private final List<Long> volumeUpPresses = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private long volumeDownPressStartTime = 0;
    private long volumeUpPressStartTime = 0;
    private boolean volumeDownPressed = false;
    private boolean volumeUpPressed = false;

    private OnVolumeGestureListener listener;

    public interface OnVolumeGestureListener {
        void onSilentEmergency(); // 5x Volume Down rapidly
        void onPoliceNeeded(); // 3x Volume Up rapidly
        void onMedicalEmergency(); // Volume Down long press (5s)
        void onPanicAlert(); // Volume Up + Volume Down together (3s)
    }

    public VolumeButtonGestureDetector(OnVolumeGestureListener listener) {
        this.listener = listener;
    }

    /**
     * Call this from onKeyDown
     */
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        long currentTime = System.currentTimeMillis();

        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (!volumeDownPressed) {
                volumeDownPressed = true;
                volumeDownPressStartTime = currentTime;
                volumeDownPresses.add(currentTime);

                // Check for rapid presses
                checkRapidVolumePresses();

                // Start long press detection
                handler.postDelayed(volumeDownLongPressRunnable, LONG_PRESS_DURATION_MS);
            }

            // Check for combo press (both buttons together)
            if (volumeUpPressed) {
                checkComboPress();
            }

            return true; // Consume the event to prevent volume change
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (!volumeUpPressed) {
                volumeUpPressed = true;
                volumeUpPressStartTime = currentTime;
                volumeUpPresses.add(currentTime);

                // Check for rapid presses
                checkRapidVolumePresses();
            }

            // Check for combo press (both buttons together)
            if (volumeDownPressed) {
                checkComboPress();
            }

            return true; // Consume the event to prevent volume change
        }

        return false;
    }

    /**
     * Call this from onKeyUp
     */
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            volumeDownPressed = false;
            handler.removeCallbacks(volumeDownLongPressRunnable);
            handler.removeCallbacks(comboPressRunnable);
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            volumeUpPressed = false;
            handler.removeCallbacks(comboPressRunnable);
            return true;
        }

        return false;
    }

    private void checkRapidVolumePresses() {
        long currentTime = System.currentTimeMillis();

        // Clean old presses outside the time window
        cleanOldPresses(volumeDownPresses, currentTime);
        cleanOldPresses(volumeUpPresses, currentTime);

        // Check Volume Down rapid presses (Silent Emergency)
        if (volumeDownPresses.size() >= VOLUME_DOWN_RAPID_COUNT) {
            if (listener != null) {
                listener.onSilentEmergency();
            }
            volumeDownPresses.clear();
            volumeUpPresses.clear();
        }

        // Check Volume Up rapid presses (Police Needed)
        if (volumeUpPresses.size() >= VOLUME_UP_RAPID_COUNT) {
            if (listener != null) {
                listener.onPoliceNeeded();
            }
            volumeDownPresses.clear();
            volumeUpPresses.clear();
        }
    }

    private void cleanOldPresses(List<Long> presses, long currentTime) {
        presses.removeIf(pressTime ->
                (currentTime - pressTime) > RAPID_PRESS_WINDOW_MS);
    }

    private void checkComboPress() {
        // Both buttons are pressed, start timer for combo gesture
        handler.removeCallbacks(comboPressRunnable);
        handler.postDelayed(comboPressRunnable, COMBO_PRESS_DURATION_MS);
    }

    private final Runnable comboPressRunnable = new Runnable() {
        @Override
        public void run() {
            // If both buttons are still pressed after duration
            if (volumeDownPressed && volumeUpPressed) {
                if (listener != null) {
                    listener.onPanicAlert();
                }
                volumeDownPresses.clear();
                volumeUpPresses.clear();
            }
        }
    };

    private final Runnable volumeDownLongPressRunnable = new Runnable() {
        @Override
        public void run() {
            // Volume Down held for 5 seconds
            if (volumeDownPressed) {
                if (listener != null) {
                    listener.onMedicalEmergency();
                }
                volumeDownPresses.clear();
                volumeUpPresses.clear();
            }
        }
    };

    public void reset() {
        volumeDownPresses.clear();
        volumeUpPresses.clear();
        volumeDownPressed = false;
        volumeUpPressed = false;
        handler.removeCallbacks(volumeDownLongPressRunnable);
        handler.removeCallbacks(comboPressRunnable);
    }

    public void cleanup() {
        handler.removeCallbacksAndMessages(null);
        volumeDownPresses.clear();
        volumeUpPresses.clear();
    }
}