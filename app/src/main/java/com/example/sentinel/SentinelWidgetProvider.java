package com.example.sentinel;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;

import com.example.core.EmergencyShakeService;
import com.example.data.EmergencyContactManager;

/**
 * Emergency Widget for Sentinel App
 * Displays emergency contact info and provides quick toggle for emergency service
 */
public class SentinelWidgetProvider extends AppWidgetProvider {

    private static final String ACTION_TOGGLE_SERVICE = "com.example.sentinel.TOGGLE_SERVICE";
    private static final String ACTION_OPEN_APP = "com.example.sentinel.OPEN_APP";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        // Update all widget instances
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);

        String action = intent.getAction();

        if (ACTION_TOGGLE_SERVICE.equals(action)) {
            toggleEmergencyService(context);

            // Update all widgets to reflect new state
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(
                    new ComponentName(context, SentinelWidgetProvider.class));
            onUpdate(context, appWidgetManager, appWidgetIds);

        } else if (ACTION_OPEN_APP.equals(action)) {
            // Open main activity
            Intent openIntent = new Intent(context, MainActivity.class);
            openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            context.startActivity(openIntent);
        }
    }

    private void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_sentinel);

        EmergencyContactManager contactManager = new EmergencyContactManager(context);
        boolean hasContact = contactManager.hasEmergencyContact();
        boolean isServiceRunning = isServiceRunning(context);

        // Update contact info
        if (hasContact) {
            String contactName = contactManager.getContactName();
            String contactPhone = contactManager.getContactPhone();

            views.setTextViewText(R.id.widget_contact_name,
                    contactName != null && !contactName.isEmpty() ? contactName : "Emergency Contact");
            views.setTextViewText(R.id.widget_contact_phone, contactPhone);
            views.setViewVisibility(R.id.widget_contact_container, android.view.View.VISIBLE);
            views.setViewVisibility(R.id.widget_no_contact_text, android.view.View.GONE);
        } else {
            views.setViewVisibility(R.id.widget_contact_container, android.view.View.GONE);
            views.setViewVisibility(R.id.widget_no_contact_text, android.view.View.VISIBLE);
        }

        // Update status indicator
        if (isServiceRunning && hasContact) {
            views.setInt(R.id.widget_status_indicator, "setColorFilter",
                    context.getResources().getColor(android.R.color.holo_green_dark));
            views.setTextViewText(R.id.widget_status_text, "ACTIVE");
            views.setTextViewText(R.id.widget_toggle_button, "Stop Protection");
            views.setInt(R.id.widget_toggle_button, "setBackgroundResource",
                    R.drawable.widget_button_stop);
        } else if (hasContact) {
            views.setInt(R.id.widget_status_indicator, "setColorFilter",
                    context.getResources().getColor(android.R.color.holo_orange_light));
            views.setTextViewText(R.id.widget_status_text, "READY");
            views.setTextViewText(R.id.widget_toggle_button, "Start Protection");
            views.setInt(R.id.widget_toggle_button, "setBackgroundResource",
                    R.drawable.widget_button_start);
        } else {
            views.setInt(R.id.widget_status_indicator, "setColorFilter",
                    context.getResources().getColor(android.R.color.darker_gray));
            views.setTextViewText(R.id.widget_status_text, "NOT CONFIGURED");
            views.setTextViewText(R.id.widget_toggle_button, "Open App");
            views.setInt(R.id.widget_toggle_button, "setBackgroundResource",
                    R.drawable.widget_button_neutral);
        }

        // Setup click intents
        setupClickIntents(context, views, hasContact);

        // Update the widget
        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    private void setupClickIntents(Context context, RemoteViews views, boolean hasContact) {
        // Toggle button click
        Intent toggleIntent = new Intent(context, SentinelWidgetProvider.class);
        if (hasContact) {
            toggleIntent.setAction(ACTION_TOGGLE_SERVICE);
        } else {
            toggleIntent.setAction(ACTION_OPEN_APP);
        }
        PendingIntent togglePendingIntent = PendingIntent.getBroadcast(
                context, 0, toggleIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_toggle_button, togglePendingIntent);

        // Widget container click - opens app
        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent openPendingIntent = PendingIntent.getActivity(
                context, 1, openIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_container, openPendingIntent);
    }

    private void toggleEmergencyService(Context context) {
        EmergencyContactManager contactManager = new EmergencyContactManager(context);

        if (!contactManager.hasEmergencyContact()) {
            return;
        }

        boolean isRunning = isServiceRunning(context);

        if (isRunning) {
            // Stop service
            Intent serviceIntent = new Intent(context, EmergencyShakeService.class);
            context.stopService(serviceIntent);
            saveServiceState(context, false);
        } else {
            // Start service
            Intent serviceIntent = new Intent(context, EmergencyShakeService.class);
            context.startForegroundService(serviceIntent);
            saveServiceState(context, true);
        }
    }

    private boolean isServiceRunning(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("sentinel_prefs", Context.MODE_PRIVATE);
        return prefs.getBoolean("service_running", false);
    }

    private void saveServiceState(Context context, boolean isRunning) {
        SharedPreferences prefs = context.getSharedPreferences("sentinel_prefs", Context.MODE_PRIVATE);
        prefs.edit().putBoolean("service_running", isRunning).apply();
    }

    @Override
    public void onEnabled(Context context) {
        // Called when the first widget is created
        super.onEnabled(context);
    }

    @Override
    public void onDisabled(Context context) {
        // Called when the last widget is removed
        super.onDisabled(context);
    }

    /**
     * Call this method to update all widgets when contact or service state changes
     */
    public static void updateAllWidgets(Context context) {
        Intent intent = new Intent(context, SentinelWidgetProvider.class);
        intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);

        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(
                new ComponentName(context, SentinelWidgetProvider.class));

        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds);
        context.sendBroadcast(intent);
    }
}