package com.example.core;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;
import androidx.core.app.ActivityCompat;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.StorageService;
import java.io.IOException;

public class VoiceDetector {
    private static final String TAG = "VoiceDetector";
    private static final String WAKE_WORD = "sentinel";
    private static final long TRIGGER_COOLDOWN_MS = 10_000;
    private final Context context;
    private final OnVoiceEmergencyListener listener;
    private Model model;
    private Recognizer recognizer;
    private AudioRecord audioRecord;
    private Thread recognitionThread;
    private volatile boolean isListening = false;
    private long lastTriggerTime = 0;
    private static final int SAMPLE_RATE = 16000;
    private static final int BUFFER_SIZE = 4096;

    public interface OnVoiceEmergencyListener {
        void onEmergencyDetected(String emergencyType);
    }

    public VoiceDetector(Context context, OnVoiceEmergencyListener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    public void start() {
        if (isListening) {
            Log.w(TAG, "Already listening — ignoring duplicate start()");
            return;
        }

        StorageService.unpack(context, "model", "model",
                (model) -> {
                    try {
                        this.model = model;
                        this.recognizer = new Recognizer(model, SAMPLE_RATE, "[\"sentinel\", \"[unk]\"]");
                        startAudioCapture();
                        Log.i(TAG, "VoiceDetector started — listening for: " + WAKE_WORD);
                    } catch (IOException e) {
                        Log.e(TAG, "Failed to create recognizer: " + e.getMessage());
                    }
                },
                (exception) -> Log.e(TAG, "Failed to unpack model: " + exception.getMessage())
        );
    }

    private void startAudioCapture() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "RECORD_AUDIO permission not granted — cannot start audio capture");
            return;
        }
        try {
            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    BUFFER_SIZE * 2
            );
        } catch (SecurityException e) {
            Log.e(TAG, "Security Exception creating AudioRecord: " + e.getMessage());
            return;
        }

        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord failed to initialize");
            return;
        }

        isListening = true;
        audioRecord.startRecording();

        recognitionThread = new Thread(() -> {
            byte[] buffer = new byte[BUFFER_SIZE];
            Log.d(TAG, "Recognition thread started");

            while (isListening) {
                int bytesRead = audioRecord.read(buffer, 0, buffer.length);
                if (bytesRead > 0 && recognizer != null) {
                    if (recognizer.acceptWaveForm(buffer, bytesRead)) {
                        String result = recognizer.getResult();
                        handleResult(result);
                    }
                    // Also check partial results for faster response
                    String partial = recognizer.getPartialResult();
                    if (partial.contains(WAKE_WORD)) {
                        handleResult(partial);
                    }
                }
            }
            Log.d(TAG, "Recognition thread stopped");
        }, "VoiceDetectorThread");

        recognitionThread.start();
    }

    private void handleResult(String result) {
        if (result == null || !result.contains(WAKE_WORD)) return;

        long now = System.currentTimeMillis();
        if (now - lastTriggerTime < TRIGGER_COOLDOWN_MS) {
            Log.d(TAG, "Wake word detected but in cooldown — ignoring");
            return;
        }

        lastTriggerTime = now;
        Log.i(TAG, "Wake word '" + WAKE_WORD + "' detected!");
        listener.onEmergencyDetected("EMERGENCY");
    }

    public void stop() {
        isListening = false;

        if (recognitionThread != null) {
            recognitionThread.interrupt();
            recognitionThread = null;
        }
        if (audioRecord != null) {
            try {
                audioRecord.stop();
                audioRecord.release();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping AudioRecord: " + e.getMessage());
            }
            audioRecord = null;
        }
        if (recognizer != null) {
            recognizer.close();
            recognizer = null;
        }
        if (model != null) {
            model.close();
            model = null;
        }
        Log.i(TAG, "VoiceDetector stopped");
    }
}