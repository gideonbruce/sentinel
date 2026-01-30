package com.example.sentinel;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
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
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Dedicated screen for Fall Detection monitoring with real-time graph
 */
public class FallDetectionActivity extends AppCompatActivity {
    private static final String TAG = "FallDetectionActivity";
    private static final int PERMISSION_REQUEST_CODE = 200;
    private static final int MAX_DATA_POINTS = 50; // Show last 50 predictions

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
    private LineChart lineChart;

    // Graph data
    private List<Entry> idleEntries = new ArrayList<>();
    private List<Entry> fallEntries = new ArrayList<>();
    private List<Entry> stepEntries = new ArrayList<>();
    private List<Entry> motionEntries = new ArrayList<>();
    private int dataPointCounter = 0;

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
        initializeChart();

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
        lineChart = findViewById(R.id.chart_activity);

        updateUI();
    }

    private void initializeChart() {
        // Configure chart appearance
        lineChart.getDescription().setEnabled(false);
        lineChart.setTouchEnabled(true);
        lineChart.setDragEnabled(true);
        lineChart.setScaleEnabled(true);
        lineChart.setPinchZoom(true);
        lineChart.setDrawGridBackground(false);
        lineChart.setBackgroundColor(Color.WHITE);

        // Configure X axis
        XAxis xAxis = lineChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(true);
        xAxis.setGranularity(1f);
        xAxis.setLabelCount(5);

        // Configure left Y axis (probabilities)
        YAxis leftAxis = lineChart.getAxisLeft();
        leftAxis.setAxisMinimum(0f);
        leftAxis.setAxisMaximum(100f);
        leftAxis.setDrawGridLines(true);
        leftAxis.setGranularity(20f);

        // Disable right Y axis
        lineChart.getAxisRight().setEnabled(false);

        // Configure legend
        Legend legend = lineChart.getLegend();
        legend.setForm(Legend.LegendForm.LINE);
        legend.setTextSize(10f);
        legend.setVerticalAlignment(Legend.LegendVerticalAlignment.TOP);
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.CENTER);
        legend.setOrientation(Legend.LegendOrientation.HORIZONTAL);
        legend.setDrawInside(false);

        // Initialize with empty data
        updateChartData();
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
            // Clear previous data when starting fresh
            clearChartData();
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

    private void clearChartData() {
        idleEntries.clear();
        fallEntries.clear();
        stepEntries.clear();
        motionEntries.clear();
        dataPointCounter = 0;
        lineChart.clear();
        updateChartData();
    }

    private void onPrediction(FallDetectionResult result) {
        runOnUiThread(() -> {
            tvPrediction.setText("Activity: " + capitalizeFirst(result.getClassName()));
            tvConfidence.setText(String.format("Confidence: %.1f%%", result.getConfidence() * 100));

            float[] probs = result.getProbabilities();

            if (probs.length == 2) {
                tvIdleProb.setText(String.format("Not Fall: %.1f%%", probs[0] * 100));
                tvFallProb.setText(String.format("Fall: %.1f%%", probs[1] * 100));
                tvStepProb.setText("Step: N/A");
                tvMotionProb.setText("Motion: N/A");

                highlightPrediction(result.getClassIndex());
                updateGraphDataBinary(probs);

            } else if (probs.length == 4) {
                // Multi-class: Idle, Fall, Step, Motion
                tvIdleProb.setText(String.format("Idle: %.1f%%", probs[0] * 100));
                tvFallProb.setText(String.format("Fall: %.1f%%", probs[1] * 100));
                tvStepProb.setText(String.format("Step: %.1f%%", probs[2] * 100));
                tvMotionProb.setText(String.format("Motion: %.1f%%", probs[3] * 100));

                highlightPrediction(result.getClassIndex());

                // Update graph with new data
                updateGraphData(probs);
            }
        });
    }

    private void updateGraphDataBinary(float[] probs) {
        idleEntries.add(new Entry(dataPointCounter, probs[0] * 100));
        fallEntries.add(new Entry(dataPointCounter, probs[1] * 100));
        if (idleEntries.size() > MAX_DATA_POINTS) {
            idleEntries.remove(0);
            fallEntries.remove(0);
            shiftEntriesLeft(idleEntries);
            shiftEntriesLeft(fallEntries);
            dataPointCounter--;
        }
        dataPointCounter++;
        updateChartDataBinary();
    }

    private void updateChartDataBinary() {
        LineDataSet notFallDataSet = createDataSet(new ArrayList<>(idleEntries), "Not Fall", Color.rgb(76, 175, 80));
        LineDataSet fallDataSet = createDataSet(new ArrayList<>(fallEntries), "Fall", Color.rgb(244, 67, 54));
        LineData lineData = new LineData(notFallDataSet, fallDataSet);
        lineChart.setData(lineData);
        lineChart.notifyDataSetChanged();
        lineChart.invalidate();
        if (dataPointCounter > 10) {
            lineChart.moveViewToX(dataPointCounter - 10);
        }
    }

    private void updateGraphData(float[] probs) {
        // Add new data points
        idleEntries.add(new Entry(dataPointCounter, probs[0] * 100));
        fallEntries.add(new Entry(dataPointCounter, probs[1] * 100));
        stepEntries.add(new Entry(dataPointCounter, probs[2] * 100));
        motionEntries.add(new Entry(dataPointCounter, probs[3] * 100));

        // Remove old data points if exceeding max
        if (idleEntries.size() > MAX_DATA_POINTS) {
            idleEntries.remove(0);
            fallEntries.remove(0);
            stepEntries.remove(0);
            motionEntries.remove(0);

            // Shift all x values back
            shiftEntriesLeft(idleEntries);
            shiftEntriesLeft(fallEntries);
            shiftEntriesLeft(stepEntries);
            shiftEntriesLeft(motionEntries);
            dataPointCounter--;
        }

        dataPointCounter++;
        updateChartData();
    }

    private void shiftEntriesLeft(List<Entry> entries) {
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            entries.set(i, new Entry(entry.getX() - 1, entry.getY()));
        }
    }

    private void updateChartData() {
        // Create datasets
        LineDataSet idleDataSet = createDataSet(new ArrayList<>(idleEntries), "Idle",
                Color.rgb(76, 175, 80)); // Green
        LineDataSet fallDataSet = createDataSet(new ArrayList<>(fallEntries), "Fall",
                Color.rgb(244, 67, 54)); // Red
        LineDataSet stepDataSet = createDataSet(new ArrayList<>(stepEntries), "Step",
                Color.rgb(33, 150, 243)); // Blue
        LineDataSet motionDataSet = createDataSet(new ArrayList<>(motionEntries), "Motion",
                Color.rgb(255, 152, 0)); // Orange

        // Combine into LineData
        LineData lineData = new LineData(idleDataSet, fallDataSet, stepDataSet, motionDataSet);
        lineChart.setData(lineData);

        // Refresh chart
        lineChart.notifyDataSetChanged();
        lineChart.invalidate();

        // Auto-scroll to show latest data
        if (dataPointCounter > 10) {
            lineChart.moveViewToX(dataPointCounter - 10);
        }
    }

    private LineDataSet createDataSet(List<Entry> entries, String label, int color) {
        LineDataSet dataSet = new LineDataSet(entries, label);
        dataSet.setColor(color);
        dataSet.setLineWidth(2f);
        dataSet.setDrawCircles(false);
        dataSet.setDrawValues(false);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        dataSet.setCubicIntensity(0.2f);
        return dataSet;
    }

    {/*
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
    } */}

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

        // For binary classification (0 = Not Fall, 1 = Fall)
        if (classIndex == 0) {
            selectedView = tvIdleProb;
            color = getColor(android.R.color.holo_green_dark);
        } else if (classIndex == 1) {
            selectedView = tvFallProb;
            color = getColor(android.R.color.holo_red_dark);
        } else if (classIndex == 2) {
            selectedView = tvStepProb;
            color = getColor(android.R.color.holo_blue_dark);
        } else if (classIndex == 3) {
            selectedView = tvMotionProb;
            color = getColor(android.R.color.holo_orange_dark);
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