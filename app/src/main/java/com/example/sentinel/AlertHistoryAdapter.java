package com.example.sentinel;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.data.AlertEntity;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AlertHistoryAdapter extends RecyclerView.Adapter<AlertHistoryAdapter.AlertViewHolder> {

    private List<AlertEntity> alerts = Collections.emptyList();
    private final Context context;
    private final OnDeleteClickListener onDeleteClickListener;

    public interface OnDeleteClickListener {
        void onDeleteClick(AlertEntity alert);
    }

    public AlertHistoryAdapter(Context context, OnDeleteClickListener onDeleteClickListener) {
        this.context = context;
        this.onDeleteClickListener = onDeleteClickListener;
    }

    @NonNull
    @Override
    public AlertViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_alert_history, parent, false);
        return new AlertViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull AlertViewHolder holder, int position) {
        AlertEntity currentAlert = alerts.get(position);
        holder.tvAlertType.setText(currentAlert.getAlertType());

        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault());
        holder.tvDateTime.setText(sdf.format(new Date(currentAlert.getTimestamp())));

        holder.tvContact.setText(String.format("%s • %s",
                currentAlert.getContactName(), currentAlert.getContactPhone()));

        if (currentAlert.isLocationAvailable()) {
            holder.tvLocation.setText(String.format(Locale.getDefault(), "%.6f, %.6f",
                    currentAlert.getLatitude(), currentAlert.getLongitude()));
            holder.btnViewLocation.setVisibility(View.VISIBLE);
            holder.btnViewLocation.setOnClickListener(v -> {
                String uri = String.format(Locale.ENGLISH, "geo:%f,%f",
                        currentAlert.getLatitude(), currentAlert.getLongitude());
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
                context.startActivity(intent);
            });
        } else {
            holder.tvLocation.setText("Location not available");
            holder.btnViewLocation.setVisibility(View.GONE);
        }

        holder.btnDelete.setOnClickListener(v -> {
            if (onDeleteClickListener != null) {
                onDeleteClickListener.onDeleteClick(currentAlert);
            }
        });
    }

    @Override
    public int getItemCount() {
        return alerts.size();
    }

    public void setAlerts(List<AlertEntity> alerts) {
        this.alerts = alerts;
        notifyDataSetChanged();
    }

    static class AlertViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvAlertType;
        private final TextView tvDateTime;
        private final TextView tvContact;
        private final TextView tvLocation;
        private final ImageButton btnDelete;
        private final ImageButton btnViewLocation;


        public AlertViewHolder(View itemView) {
            super(itemView);
            tvAlertType = itemView.findViewById(R.id.tv_alert_type);
            tvDateTime = itemView.findViewById(R.id.tv_date_time);
            tvContact = itemView.findViewById(R.id.tv_contact);
            tvLocation = itemView.findViewById(R.id.tv_location);
            btnDelete = itemView.findViewById(R.id.btn_delete);
            btnViewLocation = itemView.findViewById(R.id.btn_view_location);
        }
    }
}
