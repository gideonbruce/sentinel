package com.example.sentinel;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.data.AlertEntity;
import com.example.data.AlertRepository;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

public class AlertHistoryActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private AlertHistoryAdapter adapter;
    private AlertRepository alertRepository;
    private TextView tvEmptyState;
    private FloatingActionButton fabClearHistory;

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
        loadAlertHistory();
    }

    private void initViews() {
        recyclerView = findViewById(R.id.rv_alert_history);
        tvEmptyState = findViewById(R.id.tv_empty_state);
        fabClearHistory = findViewById(R.id.fab_clear_history);

        alertRepository = new AlertRepository(getApplication());

        fabClearHistory.setOnClickListener(v -> showClearHistoryDialog());
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

    private void loadAlertHistory() {
        alertRepository.getAllAlerts(alerts -> runOnUiThread(() -> {
            if (alerts.isEmpty()) {
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

    private void showAlertDetails(AlertEntity alert) {
        String details = buildAlertDetails(alert);

        new AlertDialog.Builder(this)
                .setTitle("Alert Details")
                .setMessage(details)
                .setPositiveButton("Close", null)
                .setNeutralButton("View on Map", (dialog, which) -> {
                    if (alert.isLocationAvailable()) {
                        openMap(alert);
                    } else {
                        Toast.makeText(this, "Location not available",
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private String buildAlertDetails(AlertEntity alert) {
        StringBuilder details = new StringBuilder();
        details.append("Type: ").append(alert.getAlertType()).append("\n\n");
        details.append("Time: ").append(
                android.text.format.DateFormat.format("MMM dd, yyyy hh:mm:ss a",
                        alert.getTimestamp())).append("\n\n");
        details.append("Contact: ").append(alert.getContactName()).append("\n");
        details.append("Phone: ").append(alert.getContactPhone()).append("\n\n");

        if (alert.isLocationAvailable()) {
            details.append("Location:\n");
            details.append("Latitude: ").append(alert.getLatitude()).append("\n");
            details.append("Longitude: ").append(alert.getLongitude());
        } else {
            details.append("Location: Not available");
        }

        return details.toString();
    }

    private void openMap(AlertEntity alert) {
        if (!alert.isLocationAvailable()) {
            Toast.makeText(this, "Location not available for this alert",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        String uri = "https://maps.google.com/?q=" +
                alert.getLatitude() + "," + alert.getLongitude();
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
        startActivity(intent);
    }

    private void deleteAlert(AlertEntity alert) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Alert")
                .setMessage("Are you sure you want to delete this alert?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    alertRepository.deleteAlert(alert, (result) -> runOnUiThread(() -> {
                        Toast.makeText(this, "Alert deleted", Toast.LENGTH_SHORT).show();
                        loadAlertHistory();
                    }));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showClearHistoryDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Clear All History")
                .setMessage("Are you sure you want to delete all alert history? This action cannot be undone.")
                .setPositiveButton("Clear All", (dialog, which) -> {
                    alertRepository.deleteAllAlerts((result) -> runOnUiThread(() -> {
                        Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show();
                        loadAlertHistory();
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
}