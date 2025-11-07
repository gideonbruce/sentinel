package com.example.sentinel;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.data.AlertEntity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AlertHistoryAdapter extends RecyclerView.Adapter<AlertHistoryAdapter.AlertViewHolder> {
    private List<AlertEntity> alerts;
    private Context context;
    private OnAlertActionListener listener;

    public interface OnAlertActionListener {
        void onDeleteAlert(AlertEntity alert);
        void onViewLocation(double latitude, double longitude);
        void onCallContact(String phoneNumber);
    }

    public AlertHistoryAdapter(Context context, OnAlertActionListener listener) {
        this.context = context;
        this.alerts = new ArrayList<>();
        this.listener = listener;
    }

    public void setAlerts(List<AlertEntity> alerts) {
        this.alerts = alerts;
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

        // Set alert type and styling
        AlertTypeInfo typeInfo = getAlertTypeInfo(alert.getAlertType());
        holder.tvAlertType.setText(typeInfo.displayName);
        holder.tvAlertIcon.setText(typeInfo.icon);

        // Set colored top bar
        holder.alertTypeBar.setBackgroundColor(typeInfo.color);

        // Set icon background gradient
        holder.iconContainer.setBackgroundResource(typeInfo.backgroundDrawable);

        // Set status dot color
        GradientDrawable statusDot = (GradientDrawable) holder.statusDot.getBackground();
        statusDot.setColor(typeInfo.color);

        // Format and set timestamp
        String formattedTime = formatTimestamp(alert.getTimestamp());
        holder.tvTimestamp.setText(formattedTime);

        // Set contact information
        String contactInfo = formatContactInfo(alert.getContactName(), alert.getContactPhone());
        holder.tvContact.setText(contactInfo);

        // Set location information
        if (alert.isLocationAvailable() && alert.getLatitude() != null && alert.getLongitude() != null) {
            holder.tvLocation.setText("Available - Tap to view");
            holder.btnLocation.setVisibility(View.VISIBLE);
            holder.btnLocation.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onViewLocation(alert.getLatitude(), alert.getLongitude());
                }
            });
        } else {
            holder.tvLocation.setText("Location not available");
            holder.tvLocation.setTextColor(ContextCompat.getColor(context, android.R.color.darker_gray));
            holder.btnLocation.setVisibility(View.GONE);
        }

        // Set call button listener
        holder.btnCall.setOnClickListener(v -> {
            if (listener != null && alert.getContactPhone() != null) {
                listener.onCallContact(alert.getContactPhone());
            }
        });

        // Set delete button listener
        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDeleteAlert(alert);
            }
        });

        // Set status text
        holder.tvStatus.setText("Alert Sent Successfully");
    }

    @Override
    public int getItemCount() {
        return alerts.size();
    }

    private String formatTimestamp(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }

    private String formatContactInfo(String name, String phone) {
        if (name != null && !name.isEmpty()) {
            return name + " • " + phone;
        }
        return phone != null ? phone : "Unknown";
    }

    private AlertTypeInfo getAlertTypeInfo(String alertType) {
        if (alertType == null) {
            alertType = "EMERGENCY";
        }

        switch (alertType.toUpperCase()) {
            case "EMERGENCY":
            case "SHAKE":
                return new AlertTypeInfo(
                        "Emergency Alert",
                        "🚨",
                        ContextCompat.getColor(context, android.R.color.holo_red_dark),
                        R.drawable.alert_icon_bg
                );
            case "POLICE":
                return new AlertTypeInfo(
                        "Police Alert",
                        "🚓",
                        ContextCompat.getColor(context, android.R.color.holo_blue_dark),
                        R.drawable.alert_icon_bg_police
                );
            case "SILENT":
                return new AlertTypeInfo(
                        "Silent Alert",
                        "🔇",
                        ContextCompat.getColor(context, android.R.color.holo_orange_dark),
                        R.drawable.alert_icon_bg_silent
                );
            default:
                return new AlertTypeInfo(
                        alertType,
                        "⚠️",
                        ContextCompat.getColor(context, android.R.color.darker_gray),
                        R.drawable.alert_icon_bg
                );
        }
    }

    static class AlertViewHolder extends RecyclerView.ViewHolder {
        View alertTypeBar;
        LinearLayout iconContainer;
        TextView tvAlertIcon;
        TextView tvAlertType;
        View statusDot;
        TextView tvTimestamp;
        TextView tvContact;
        TextView tvLocation;
        TextView tvStatus;
        ImageButton btnDelete;
        ImageButton btnLocation;
        ImageButton btnCall;

        public AlertViewHolder(@NonNull View itemView) {
            super(itemView);
            alertTypeBar = itemView.findViewById(R.id.alert_type_bar);
            iconContainer = itemView.findViewById(R.id.icon_container);
            tvAlertIcon = itemView.findViewById(R.id.tv_alert_icon);
            tvAlertType = itemView.findViewById(R.id.tv_alert_type);
            statusDot = itemView.findViewById(R.id.status_dot);
            tvTimestamp = itemView.findViewById(R.id.tv_timestamp);
            tvContact = itemView.findViewById(R.id.tv_contact);
            tvLocation = itemView.findViewById(R.id.tv_location);
            tvStatus = itemView.findViewById(R.id.tv_status);
            btnDelete = itemView.findViewById(R.id.btn_delete);
            btnLocation = itemView.findViewById(R.id.btn_location);
            btnCall = itemView.findViewById(R.id.btn_call);
        }
    }

    private static class AlertTypeInfo {
        String displayName;
        String icon;
        int color;
        int backgroundDrawable;

        AlertTypeInfo(String displayName, String icon, int color, int backgroundDrawable) {
            this.displayName = displayName;
            this.icon = icon;
            this.color = color;
            this.backgroundDrawable = backgroundDrawable;
        }
    }
}