package com.example.core;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;
import ai.picovoice.porcupine.Porcupine;
import ai.picovoice.porcupine.PorcupineActivationException;
import ai.picovoice.porcupine.PorcupineActivationLimitException;
import ai.picovoice.porcupine.PorcupineActivationRefusedException;
import ai.picovoice.porcupine.PorcupineActivationThrottledException;
import ai.picovoice.porcupine.PorcupineException;
import ai.picovoice.porcupine.PorcupineManager;
import ai.picovoice.porcupine.PorcupineManagerErrorCallback;

public class VoiceDetector {
    private static final String TAG = "VoiceDetector";
    private static final String KEYWORD_FILE_NAME = "sentinel_en_android_v4_0_0.ppn";
    private static final String ACCESS_KEY = "9elovMoeXn7uDPPKwSQX3Ns2U9hMir1teWB5C7E9Y/YWrh0hkHd/Xw==";
    private static final long TRIGGER_COOLDOWN_MS = 10_000;
    private static final float SENSITIVITY = 0.6F;

    public interface OnVoiceEmergencyListener {
        void onEmergencyDetected(String emergencyType);
    }

    private final Context context;
    private final OnVoiceEmergencyListener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private PorcupineManager porcupineManager;
    private SpeechRecognizer speechRecognizer;
    private boolean isListening = false;
    private boolean isConfirming = false;
    private long lastTriggerTime = 0;

    public VoiceDetector(Context context, OnVoiceEmergencyListener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    // ─── Start / Stop ────────────────────────────────────────────────────────

    public void start() {
        if (isListening) return;

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w(TAG, "Speech recognition not available on this device");
            return;
        }

        String keywordPath = copyAssetToInternalStorage(KEYWORD_FILE_NAME);
        if (keywordPath == null) {
            Log.e(TAG, "Failed to load keyword file '" + KEYWORD_FILE_NAME);
            return;
        }

        try {
            porcupineManager = new PorcupineManager.Builder()
                    .setAccessKey(ACCESS_KEY)
                    .setKeywordPath(keywordPath) // swap for custom .ppn file if available
                    .setSensitivity(SENSITIVITY)
                    .build(context, keywordIndex -> {
                        // Called on a background thread by Porcupine
                        Log.i(TAG, "Keyword detected — index: " + keywordIndex);
                        mainHandler.post(this::onKeywordDetected);
                    }, errorCallback);

            porcupineManager.start();
            isListening = true;
            Log.i(TAG, "VoiceDetector started - keyword: " + KEYWORD_FILE_NAME);

        } catch (PorcupineActivationException e) {
            Log.e(TAG, "Porcupine activation error (invalid/expired key): " + e.getMessage());
        } catch (PorcupineActivationLimitException e) {
            Log.e(TAG, "Porcupine activation limit reached: " + e.getMessage());
        } catch (PorcupineActivationRefusedException e) {
            Log.e(TAG, "Porcupine activation refused: " + e.getMessage());
        } catch (PorcupineActivationThrottledException e) {
            Log.e(TAG, "Porcupine activation throttled: " + e.getMessage());
        } catch (PorcupineException e) {
            Log.e(TAG, "Porcupine failed to start: " + e.getMessage());
        }
    }

    public void stop() {
        isListening = false;
        isConfirming = false;

        if (porcupineManager != null) {
            try {
                porcupineManager.stop();
                porcupineManager.delete();
            } catch (PorcupineException e) {
                Log.e(TAG, "Error stopping Porcupine: " + e.getMessage());
            }
            porcupineManager = null;
        }

        destroySpeechRecognizer();
        Log.i(TAG, "VoiceDetector stopped");
    }

    // ─── Stage 1: Keyword detected ───────────────────────────────────────────

    private void onKeywordDetected() {
        long now = System.currentTimeMillis();

        // Ignore if still in cooldown from a previous trigger
        if (now - lastTriggerTime < TRIGGER_COOLDOWN_MS) {
            Log.d(TAG, "Keyword detected but in cooldown — ignoring");
            return;
        }

        // Ignore if already in the middle of confirming a phrase
        if (isConfirming) {
            Log.d(TAG, "Keyword detected but already confirming — ignoring");
            return;
        }

        Log.i(TAG, "Keyword confirmed — starting phrase confirmation");
        isConfirming = true;
        startPhraseConfirmation();
    }

    // ─── Stage 2: Full phrase confirmation ───────────────────────────────────

    private void startPhraseConfirmation() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {

            @Override
            public void onResults(Bundle results) {
                isConfirming = false;
                ArrayList<String> matches = results
                        .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    Log.d(TAG, "Speech results: " + matches);
                    String emergencyType = classifyPhrase(matches);
                    if (emergencyType != null) {
                        lastTriggerTime = System.currentTimeMillis();
                        Log.i(TAG, "Emergency phrase confirmed: " + emergencyType);
                        listener.onEmergencyDetected(emergencyType);
                    } else {
                        Log.d(TAG, "No emergency phrase matched — no alert triggered");
                    }
                }
                destroySpeechRecognizer();
            }

            @Override
            public void onError(int error) {
                isConfirming = false;
                Log.w(TAG, "Speech recognition error: " + speechErrorString(error));
                destroySpeechRecognizer();
            }

            @Override public void onReadyForSpeech(Bundle params) { Log.d(TAG, "Ready for speech"); }
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onPartialResults(Bundle partialResults) {}
            @Override public void onEvent(int eventType, Bundle params) {}
        });

        Intent recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        // Keep listening window short — this is confirmation, not dictation
        recognizerIntent.putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L);
        recognizerIntent.putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L);
        // Prefer on-device recognition — no internet needed
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);

        speechRecognizer.startListening(recognizerIntent);
    }

    // ─── Phrase classification ────────────────────────────────────────────────

    /**
     * Maps spoken phrases to emergency types.
     * Returns null if no emergency phrase is found — no alert is triggered.
     */
    private String classifyPhrase(List<String> phrases) {
        for (String phrase : phrases) {
            String p = phrase.toLowerCase().trim();

            if (containsAny(p, "police", "robbery", "robber", "thief", "stolen", "mugging")) {
                return "POLICE NEEDED";
            }
            if (containsAny(p, "fire", "burning", "smoke", "flames")) {
                return "FIRE";
            }
            if (containsAny(p, "medical", "ambulance", "doctor", "heart", "breathing",
                    "unconscious", "fainted", "seizure")) {
                return "MEDICAL EMERGENCY";
            }
            if (containsAny(p, "accident", "crash", "hit", "injured", "bleeding")) {
                return "ACCIDENT";
            }
            if (containsAny(p, "help", "emergency", "danger", "attacked", "scared")) {
                return "EMERGENCY";
            }
        }
        return null; // No match — don't trigger
    }

    private boolean containsAny(String phrase, String... keywords) {
        for (String keyword : keywords) {
            if (phrase.contains(keyword)) return true;
        }
        return false;
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private void destroySpeechRecognizer() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
    }

    private final PorcupineManagerErrorCallback errorCallback = error ->
            Log.e(TAG, "Porcupine runtime error: " + error.getMessage());

    private String speechErrorString(int error) {
        switch (error) {
            case SpeechRecognizer.ERROR_AUDIO:              return "audio recording error";
            case SpeechRecognizer.ERROR_CLIENT:             return "client side error";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: return "insufficient permissions";
            case SpeechRecognizer.ERROR_NETWORK:            return "network error";
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:    return "network timeout";
            case SpeechRecognizer.ERROR_NO_MATCH:           return "no match";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:    return "recognizer busy";
            case SpeechRecognizer.ERROR_SERVER:             return "server error";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:     return "no speech input";
            default:                                        return "unknown error " + error;
        }
    }
}