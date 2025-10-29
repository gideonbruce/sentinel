package com.example.sentinel;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.data.AlertEntity;
import com.example.data.AlertRepository;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;

public class AlertHistoryActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private AlertHistoryAdapter adapter;
    private AlertRepository alertRepository;
    private View tvEmptyState;
    private FloatingActionButton fabClearHistory;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ProgressBar progressBar;
    private TextView tvSyncStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alert_history);

        // Enable back button in action bar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Alert History");
        }

        initViews();
        setupRecyclerView();
        checkFirebaseAuth();
        loadAlertHistory();
    }

    private void initViews() {
        recyclerView = findViewById(R.id.rv_alert_history);
        tvEmptyState = findViewById(R.id.tv_empty_state);
        fabClearHistory = findViewById(R.id.fab_clear_history);
        swipeRefreshLayout = findViewById(R.id.swipe_refresh);
        progressBar = findViewById(R.id.progress_bar);
        tvSyncStatus = findViewById(R.id.tv_sync_status);

        alertRepository = new AlertRepository(getApplication());

        fabClearHistory.setOnClickListener(v -> showClearHistoryDialog());

        // Setup swipe to refresh
        swipeRefreshLayout.setOnRefreshListener(() -> {
            loadAlertHistory();
            // Force sync from Firebase
            alertRepository.forceSyncFromFirebase(success -> runOnUiThread(() -> {
                swipeRefreshLayout.setRefreshing(false);
                if (success) {
                    Toast.makeText(this, "Synced with cloud", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Sync failed - showing local data",
                            Toast.LENGTH_SHORT).show();
                }
            }));
        });
    }

    private void setupRecyclerView() {
        adapter = new AlertHistoryAdapter(new ArrayList<>(), new AlertHistoryAdapter.AlertClickListener() {
            @Override
            public void onAlertClick(AlertEntity alert) {
                showAlertDetails(alert);
            }

            @Override
            public void onLocationClick(AlertEntity alert) {
                openMap(alert);
            }

            @Override
            public void onDeleteClick(AlertEntity alert) {
                deleteAlert(alert);
            }
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    private void checkFirebaseAuth() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            tvSyncStatus.setText("Synced with cloud ☁️");
            tvSyncStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
        } else {
            tvSyncStatus.setText("Local storage only");
            tvSyncStatus.setTextColor(getResources().getColor(android.R.color.holo_orange_dark));
        }
    }

    private void loadAlertHistory() {
        showLoading(true);

        alertRepository.getAllAlerts(alerts -> runOnUiThread(() -> {
            showLoading(false);

            if (alerts == null || alerts.isEmpty()) {
                tvEmptyState.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
                fabClearHistory.setVisibility(View.GONE);
            } else {
                tvEmptyState.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
                fabClearHistory.setVisibility(View.VISIBLE);
                adapter.updateAlerts(alerts);
            }
        }));
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void showAlertDetails(AlertEntity alert) {
        String details = buildAlertDetails(alert);

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Alert Details")
                .setMessage(details)
                .setPositiveButton("Close", null);

        if (alert.isLocationAvailable()) {
            builder.setNeutralButton("View on Map", (dialog, which) -> openMap(alert));
        }

        builder.show();
    }

    private String buildAlertDetails(AlertEntity alert) {
        StringBuilder details = new StringBuilder();

        details.append("🚨 Alert Type\n");
        details.append(alert.getAlertType()).append("\n\n");

        details.append("📅 Date & Time\n");
        details.append(android.text.format.DateFormat.format(
                "EEEE, MMM dd, yyyy\nhh:mm:ss a", alert.getTimestamp())).append("\n\n");

        details.append("👤 Emergency Contact\n");
        details.append(alert.getContactName()).append("\n");
        details.append("📞 ").append(alert.getContactPhone()).append("\n\n");

        if (alert.isLocationAvailable()) {
            details.append("📍 Location\n");
            details.append("Lat: ").append(String.format("%.6f", alert.getLatitude())).append("\n");
            details.append("Long: ").append(String.format("%.6f", alert.getLongitude()));
        } else {
            details.append("📍 Location\n");
            details.append("Not available");
        }

        return details.toString();
    }

    private void openMap(AlertEntity alert) {
        if (!alert.isLocationAvailable()) {
            Toast.makeText(this, "Location not available for this alert",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            String uri = alert.getGoogleMapsUrl();
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
            intent.setPackage("com.google.android.apps.maps");

            // Try to open Google Maps, fallback to browser if not installed
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
            } else {
                intent.setPackage(null);
                startActivity(intent);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Unable to open map", Toast.LENGTH_SHORT).show();
        }
    }

    private void deleteAlert(AlertEntity alert) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Alert")
                .setMessage("Are you sure you want to delete this alert? This will remove it from both local storage and cloud.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    showLoading(true);

                    alertRepository.deleteAlert(alert, success -> runOnUiThread(() -> {
                        showLoading(false);

                        if (success) {
                            Toast.makeText(this, "Alert deleted", Toast.LENGTH_SHORT).show();
                            loadAlertHistory();
                        } else {
                            Toast.makeText(this, "Failed to delete alert",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showClearHistoryDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Clear All History")
                .setMessage("Are you sure you want to delete all alert history? " +
                        "This will remove all alerts from both local storage and cloud. " +
                        "This action cannot be undone.")
                .setPositiveButton("Clear All", (dialog, which) -> {
                    showLoading(true);

                    alertRepository.deleteAllAlerts(success -> runOnUiThread(() -> {
                        showLoading(false);

                        if (success) {
                            Toast.makeText(this, "All history cleared",
                                    Toast.LENGTH_SHORT).show();
                            loadAlertHistory();
                        } else {
                            Toast.makeText(this, "Failed to clear history",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up repository resources if needed
        if (alertRepository != null) {
            alertRepository.stopListeningToFirebase();
            alertRepository.cleanup();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Don't reload automatically on resume to prevent constant loading
        // User can manually refresh using swipe-to-refresh
    }
}