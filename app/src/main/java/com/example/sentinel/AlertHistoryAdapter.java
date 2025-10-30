package com.example.sentinel;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.data.AlertEntity;

import java.util.List;

public class AlertHistoryAdapter extends RecyclerView.Adapter<AlertHistoryAdapter.AlertViewHolder> {

    private List<AlertEntity> alerts;
    private final AlertClickListener listener;

    public interface AlertClickListener {
        void onAlertClick(AlertEntity alert);
        void onLocationClick(AlertEntity alert);
        void onDeleteClick(AlertEntity alert);
    }

    public AlertHistoryAdapter(List<AlertEntity> alerts, AlertClickListener listener) {
        this.alerts = alerts;
        this.listener = listener;
    }

    public void updateAlerts(List<AlertEntity> newAlerts) {
        this.alerts = newAlerts;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public AlertViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_alert_history, parent, false);
        return new AlertViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AlertViewHolder holder, int position) {
        AlertEntity alert = alerts.get(position);
        holder.bind(alert, listener);
    }

    @Override
    public int getItemCount() {
        return alerts.size();
    }

    static class AlertViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvAlertType;
        private final TextView tvTimestamp;
        private final TextView tvContact;
        private final TextView tvLocation;
        private final ImageButton btnLocation;
        private final ImageButton btnDelete;

        public AlertViewHolder(@NonNull View itemView) {
            super(itemView);
            tvAlertType = itemView.findViewById(R.id.tv_alert_type);
            tvTimestamp = itemView.findViewById(R.id.tv_timestamp);
            tvContact = itemView.findViewById(R.id.tv_contact);
            tvLocation = itemView.findViewById(R.id.tv_location);
            btnLocation = itemView.findViewById(R.id.btn_location);
            btnDelete = itemView.findViewById(R.id.btn_delete);
        }

        @SuppressLint("SetTextI18n")
        public void bind(AlertEntity alert, AlertClickListener listener) {
            tvAlertType.setText(alert.getAlertType());
            tvTimestamp.setText(android.text.format.DateFormat.format(
                    "MMM dd, yyyy hh:mm a", alert.getTimestamp()));
            tvContact.setText(alert.getContactName() + " • " + alert.getContactPhone());

            if (alert.isLocationAvailable()) {
                tvLocation.setText("Location available");
                tvLocation.setVisibility(View.VISIBLE);
                btnLocation.setVisibility(View.VISIBLE);
                btnLocation.setOnClickListener(v -> listener.onLocationClick(alert));
            } else {
                tvLocation.setText("No location");
                tvLocation.setVisibility(View.VISIBLE);
                btnLocation.setVisibility(View.GONE);
            }

            itemView.setOnClickListener(v -> listener.onAlertClick(alert));
            btnDelete.setOnClickListener(v -> listener.onDeleteClick(alert));
        }
    }
}