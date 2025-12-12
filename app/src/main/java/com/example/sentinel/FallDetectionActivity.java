package com.example.sentinel;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.ml.FallDetectionModel;
import com.example.ml.FallDetectionResult;
import com.example.ml.FallDetectionService;

import java.io.IOException;

/**
 * Dedicated screen for Fall Detection monitoring
 */
public class FallDetectionActivity extends AppCompatActivity {
    private static final String TAG = "FallDetectionActivity";
    private static final int PERMISSION_REQUEST_CODE = 200;

    private FallDetectionService detectionService;

    // UI Components
    private Button btnStartStop;
    private TextView tvStatus;
    private TextView tvPrediction;
    private TextView tvConfidence;
    private TextView tvIdleProb;
    private TextView tvFallProb;
    private TextView tvStepProb;
    private TextView tvMotionProb;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fall_detection);

        // Enable back button
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Fall Detection");
        }

        initializeViews();

        if (!checkSensorPermissions()) {
            requestSensorPermissions();
        } else {
            initializeService();
        }

        btnStartStop.setOnClickListener(v -> toggleDetection());
    }

    private void initializeViews() {
        btnStartStop = findViewById(R.id.btn_fall_start_stop);
        tvStatus = findViewById(R.id.tv_fall_status);
        tvPrediction = findViewById(R.id.tv_fall_prediction);
        tvConfidence = findViewById(R.id.tv_fall_confidence);
        tvIdleProb = findViewById(R.id.tv_idle_prob);
        tvFallProb = findViewById(R.id.tv_fall_prob);
        tvStepProb = findViewById(R.id.tv_step_prob);
        tvMotionProb = findViewById(R.id.tv_motion_prob);

        updateUI();
    }

    private void initializeService() {
        try {
            detectionService = new FallDetectionService(this);
            detectionService.initialize();

            detectionService.setOnPredictionListener(this::onPrediction);
            detectionService.setOnFallDetectedListener(this::onFallDetected);
            detectionService.setFallConfidenceThreshold(0.7f);

            Toast.makeText(this, "Fall detection initialized", Toast.LENGTH_SHORT).show();

        } catch (IOException e) {
            showErrorDialog("Failed to initialize fall detection: " + e.getMessage());
        }
    }

    private void toggleDetection() {
        if (detectionService == null) {
            Toast.makeText(this, "Service not initialized", Toast.LENGTH_SHORT).show();
            return;
        }

        if (detectionService.isRunning()) {
            detectionService.stop();
            Toast.makeText(this, "Detection stopped", Toast.LENGTH_SHORT).show();
        } else {
            detectionService.start();
            Toast.makeText(this, "Detection started", Toast.LENGTH_SHORT).show();
        }

        updateUI();
    }

    private void updateUI() {
        if (detectionService != null && detectionService.isRunning()) {
            btnStartStop.setText("Stop Detection");
            tvStatus.setText("Status: Running");
            btnStartStop.setBackgroundColor(getColor(android.R.color.holo_red_dark));
        } else {
            btnStartStop.setText("Start Detection");
            tvStatus.setText("Status: Stopped");
            btnStartStop.setBackgroundColor(getColor(android.R.color.holo_green_dark));
            resetProbabilityDisplay();
        }
    }

    private void resetProbabilityDisplay() {
        tvPrediction.setText("Activity: --");
        tvConfidence.setText("Confidence: --");
        tvIdleProb.setText("Idle: 0%");
        tvFallProb.setText("Fall: 0%");
        tvStepProb.setText("Step: 0%");
        tvMotionProb.setText("Motion: 0%");
    }

    private void onPrediction(FallDetectionResult result) {
        runOnUiThread(() -> {
            tvPrediction.setText("Activity: " + capitalizeFirst(result.getClassName()));
            tvConfidence.setText(String.format("Confidence: %.1f%%", result.getConfidence() * 100));

            float[] probs = result.getProbabilities();
            tvIdleProb.setText(String.format("Idle: %.1f%%", probs[0] * 100));
            tvFallProb.setText(String.format("Fall: %.1f%%", probs[1] * 100));
            tvStepProb.setText(String.format("Step: %.1f%%", probs[2] * 100));
            tvMotionProb.setText(String.format("Motion: %.1f%%", probs[3] * 100));

            highlightPrediction(result.getClassIndex());
        });
    }

    private void highlightPrediction(int classIndex) {
        tvIdleProb.setTextColor(getColor(android.R.color.black));
        tvFallProb.setTextColor(getColor(android.R.color.black));
        tvStepProb.setTextColor(getColor(android.R.color.black));
        tvMotionProb.setTextColor(getColor(android.R.color.black));

        tvIdleProb.setTextSize(14);
        tvFallProb.setTextSize(14);
        tvStepProb.setTextSize(14);
        tvMotionProb.setTextSize(14);

        TextView selectedView = null;
        int color = getColor(android.R.color.holo_blue_dark);

        switch (classIndex) {
            case 0:
                selectedView = tvIdleProb;
                color = getColor(android.R.color.holo_green_dark);
                break;
            case 1:
                selectedView = tvFallProb;
                color = getColor(android.R.color.holo_red_dark);
                break;
            case 2:
                selectedView = tvStepProb;
                color = getColor(android.R.color.holo_blue_dark);
                break;
            case 3:
                selectedView = tvMotionProb;
                color = getColor(android.R.color.holo_orange_dark);
                break;
        }

        if (selectedView != null) {
            selectedView.setTextColor(color);
            selectedView.setTextSize(16);
        }
    }

    private void onFallDetected(FallDetectionResult result) {
        runOnUiThread(() -> {
            new AlertDialog.Builder(this)
                    .setTitle("⚠️ FALL DETECTED!")
                    .setMessage(String.format("A fall has been detected with %.1f%% confidence.\n\nDo you need help?",
                            result.getConfidence() * 100))
                    .setPositiveButton("I'm OK", (dialog, which) -> {
                        Toast.makeText(this, "False alarm dismissed", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("SEND ALERT", (dialog, which) -> {
                        // Trigger emergency alert through existing system
                        triggerEmergencyAlert();
                    })
                    .setCancelable(false)
                    .show();
        });
    }

    private void triggerEmergencyAlert() {
        // Send broadcast to EmergencyShakeService to trigger alert
        android.content.Intent intent = new android.content.Intent("com.example.sentinel.TRIGGER_EMERGENCY");
        intent.putExtra("EMERGENCY_TYPE", "FALL");
        sendBroadcast(intent);

        Toast.makeText(this, "Emergency alert triggered!", Toast.LENGTH_SHORT).show();
    }

    private boolean checkSensorPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestSensorPermissions() {
        new AlertDialog.Builder(this)
                .setTitle("Sensor Permission Required")
                .setMessage("Fall detection needs access to device sensors (accelerometer and gyroscope).")
                .setPositiveButton("Grant", (dialog, which) -> {
                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.BODY_SENSORS},
                            PERMISSION_REQUEST_CODE);
                })
                .setNegativeButton("Cancel", (dialog, which) -> finish())
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initializeService();
            } else {
                showErrorDialog("Sensor permission is required for fall detection.");
            }
        }
    }

    private void showErrorDialog(String message) {
        new AlertDialog.Builder(this)
                .setTitle("Error")
                .setMessage(message)
                .setPositiveButton("OK", (dialog, which) -> finish())
                .setCancelable(false)
                .show();
    }

    private String capitalizeFirst(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (detectionService != null) {
            detectionService.cleanup();
        }
    }
}
