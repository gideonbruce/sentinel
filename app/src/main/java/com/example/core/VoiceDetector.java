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

import java.io.File;
import java.io.IOException;

public class VoiceDetector {
    private Model model;
    private Recognizer recognizer;
    private AudioRecord audioRecord;
    private Thread recognitionThread;
    private int detectionCount = 0;
    private long firstDetectionTime = 0;
    private long lastTriggerTime = 0;
    private final Context context;
    private final OnVoiceEmergencyListener listener;
    private static final String TAG = "VoiceDetector";
    private static final String WAKE_WORD = "sentinel";
    private static final int SAMPLE_RATE = 16000;
    private static final int BUFFER_SIZE = 4096;
    private static final int REQUIRED_DETECTIONS = 2;
    private static final long DETECTION_WINDOW_MS = 3000;
    private static final long TRIGGER_COOLDOWN_MS = 10_000;
    private volatile boolean isListening = false;
    private static final float MIN_CONFIDENCE = 0.85f;

    public interface OnVoiceEmergencyListener {
        void onEmergencyDetected(String emergencyType);
    }

    public VoiceDetector(Context context, OnVoiceEmergencyListener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    public void start() {
        if (isListening) return;

        new Thread(() -> {
            try {
                String modelPath = unpackModel();
                if (modelPath == null) {
                    Log.e(TAG, "Model unpack failed — aborting");
                    return;
                }
                this.model = new Model(modelPath);
                this.recognizer = new Recognizer(model, SAMPLE_RATE,
                        "[\"sentinel\", \"[unk]\"]");
                startAudioCapture();
                Log.i(TAG, "VoiceDetector started");
            } catch (IOException e) {
                Log.e(TAG, "Failed to initialize Vosk: " + e.getMessage());
            }
        }, "VoiceDetectorInit").start();
    }

    private String unpackModel() {
        File outputDir = new File(context.getFilesDir(), "vosk-model");

        // If already unpacked, reuse it
        if (outputDir.exists() && new File(outputDir, "am/final.mdl").exists()) {
            Log.d(TAG, "Using cached model at: " + outputDir.getAbsolutePath());
            return outputDir.getAbsolutePath();
        }

        Log.d(TAG, "Unpacking model from assets...");
        outputDir.mkdirs();

        try {
            copyAssetFolder(context.getAssets(), "model", outputDir.getAbsolutePath());
            String[] requiredFiles = {
                    "am/final.mdl",
                    "conf/model.conf",
                    "graph/HCLr.fst",
                    "graph/Gr.fst"
            };
            for (String f : requiredFiles) {
                boolean exists = new File(outputDir, f).exists();
                Log.d(TAG, "Check " + f + ": " + (exists ? "OK" : "MISSING"));
            }
            Log.i(TAG, "Model unpacked to: " + outputDir.getAbsolutePath());
            return outputDir.getAbsolutePath();
        } catch (IOException e) {
            Log.e(TAG, "Failed to unpack model: " + e.getMessage());
            // Clean up partial unpack
            deleteRecursive(outputDir);
            return null;
        }
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
                        //removing partial check
                        String result = recognizer.getResult();
                        handleResult(result);
                    }
                    /*String partial = recognizer.getPartialResult();
                    if (partial.contains(WAKE_WORD)) {
                        handleResult(partial);
                    }*/
                }
            }
            Log.d(TAG, "Recognition thread stopped");
        }, "VoiceDetectorThread");

        recognitionThread.start();
    }

    private void handleResult(String result) {
        if (result == null || !result.contains(WAKE_WORD)) return;
        //parsing conf threshold for vosks JSON result
        // Vosk returns: {"text": "sentinel", "result": [{"conf": 0.95, "word": "sentinel"}]}
        /*try {
            org.json.JSONObject json = new org.json.JSONObject(result);
            org.json.JSONArray words = json.optJSONArray("result");
            if (words != null) {
                for (int i = 0; i < words.length(); i++) {
                    org.json.JSONObject word = words.getJSONObject(i);
                    float conf = (float) word.optDouble("conf", 0.0);
                    String w = word.optString("word", "");
                    Log.d(TAG, "Word: " + w + " confidence: " + conf);
                    if (w.equals(WAKE_WORD) && conf >= MIN_CONFIDENCE) {
                        triggerEmergency();
                        return;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing result JSON: " + e.getMessage());
        }*/
        long now = System.currentTimeMillis();
        if (now - lastTriggerTime < TRIGGER_COOLDOWN_MS) {
            Log.d(TAG, "Wake word detected but in cooldown — ignoring");
            return;
        }

        lastTriggerTime = now;
        Log.i(TAG, "Wake word '" + WAKE_WORD + "' detected!");
        listener.onEmergencyDetected("EMERGENCY");
    }

    public void triggerEmergency() {
        long now = System.currentTimeMillis();
        if (now - lastTriggerTime < TRIGGER_COOLDOWN_MS) return;
        if (detectionCount == 0) {
            detectionCount = 1;
            firstDetectionTime = now;
            Log.d(TAG, "Wake word detected (1/" + REQUIRED_DETECTIONS + ") — waiting for confirmation");
            return;
        }
        //within window count
        if (now - firstDetectionTime <= DETECTION_WINDOW_MS) {
            detectionCount++;
            Log.d(TAG, "Wake word detected (" + detectionCount + "/" + REQUIRED_DETECTIONS + ")");
        }
        if (now - firstDetectionTime <= DETECTION_WINDOW_MS) {
            detectionCount++;
            Log.d(TAG, "Wake word detected (" + detectionCount + "/" + REQUIRED_DETECTIONS + ")");
            if (detectionCount >= REQUIRED_DETECTIONS) {
                detectionCount = 0;
                lastTriggerTime = now;
                Log.i(TAG, "Wake word confirmed — triggering emergency");
                listener.onEmergencyDetected("EMERGENCY");
            }
        } else {
            //window expired reset & start
            Log.d(TAG, "Detection window expired - resetting count ...");
            detectionCount = 1;
            firstDetectionTime = now;
        }
        /*lastTriggerTime = now;
        Log.i(TAG, "Wake word '" + WAKE_WORD + "' detected!");
        listener.onEmergencyDetected("EMERGENCY");*/
    }

    private void copyAssetFolder(android.content.res.AssetManager assets,
                                 String fromAsset, String toPath) throws IOException {
        String[] files = assets.list(fromAsset);
        if (files == null) throw new IOException("Asset folder not found: " + fromAsset);

        new File(toPath).mkdirs();

        for (String file : files) {
            String subAsset = fromAsset + "/" + file;
            String subPath  = toPath + "/" + file;

            String[] children = assets.list(subAsset);
            if (children != null && children.length > 0) {
                // It's a subfolder — recurse
                copyAssetFolder(assets, subAsset, subPath);
            } else {
                // It's a file — copy it
                try (java.io.InputStream in  = assets.open(subAsset);
                     java.io.OutputStream out = new java.io.FileOutputStream(subPath)) {
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
                }
                Log.d(TAG, "Copied: " + subAsset);
            }
        }
    }

    private void deleteRecursive(File file) {
        if (file.isDirectory()) {
            for (File child : file.listFiles()) deleteRecursive(child);
        }
        file.delete();
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