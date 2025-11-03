package com.example.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.telephony.SmsManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.util.Log;
import android.widget.Toast;

import com.example.data.EmergencyContactManager;

import java.util.ArrayList;
import java.util.List;

public class EmergencyAlertDialog {
    private static final String TAG = "EmergencyAlertDialog";

    public interface OnAlertActionListener {
        void onAlertSent();
        void onAlertCancelled();
    }

    public static void show(Context context, OnAlertActionListener listener, android.location.Location location) {
        Log.d(TAG, "show() called");
        EmergencyContactManager contactManager = new EmergencyContactManager(context);

        if (!contactManager.hasEmergencyContact()) {
            Log.w(TAG, "No emergency contact configured");
            showSetupContactDialog(context);
            return;
        }

        String contactName = contactManager.getContactName();
        String contactPhone = contactManager.getContactPhone();
        Log.d(TAG, "Emergency contact - Name: " + contactName + ", Phone: " +
                (contactPhone != null ? "[REDACTED]" : "null"));

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Emergency Alert");
        builder.setMessage("Send emergency notification to " +
                (contactName != null ? contactName : contactPhone) + "?");

        builder.setPositiveButton("Send Alert", (dialog, which) -> {
            Log.i(TAG, "User confirmed emergency alert");
            sendEmergencyAlert(context, contactPhone, location);    // only notify listener
            if (listener != null) {
                Log.d(TAG, "Notifying listener: onAlertSent()");
                listener.onAlertSent();
            } else {
                Log.w(TAG, "No listener to notify");
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> {
            Log.i(TAG, "User cancelled emergency alert");
            if (listener != null) {
                Log.d(TAG, "Notifying listener: onAlertCancelled()");
                listener.onAlertCancelled();
            } else {
                Log.w(TAG, "No listener to notify");
            }
        });

        builder.setCancelable(false);
        AlertDialog dialog = builder.create();
        dialog.show();
        Log.d(TAG, "Emergency alert dialog displayed");
    }

    private static void showSetupContactDialog(Context context) {
        Log.d(TAG, "showSetupContactDialog() called");
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("No Emergency Contact");
        builder.setMessage("Please set up an emergency contact first.");
        builder.setPositiveButton("OK", null);
        builder.show();
        Log.d(TAG, "Setup contact dialog displayed");
    }

    private static void sendEmergencyAlert(Context context, String phoneNumber, android.location.Location location) {
        Log.i(TAG, "sendEmergencyAlert() called");
        Log.d(TAG, "Phone number: [REDACTED], Location: " + (location != null ? "available" : "null"));

        EmergencyContactManager contactManager = new EmergencyContactManager(context);
        String customMessage = contactManager.getEmergencyMessage();
        Log.d(TAG, "Custom message retrieved: " + (customMessage != null ? "yes" : "null"));

        //Building complete message
        StringBuilder message = new StringBuilder();
        message.append(customMessage);

        if (location != null) {
            double latitude = location.getLatitude();
            double longitude = location.getLongitude();
            Log.d(TAG, "Adding location to message - Lat: " + latitude + ", Lon: " + longitude);
            message.append("\n\n📍 https://maps.google.com/?q=")
                    .append(latitude)
                    .append(",")
                    .append(longitude);
        } else {
            Log.w(TAG, "Location unavailable, adding unavailable message");
            message.append("\n\n(Location unavailable)");
        }

        String finalMessage = message.toString();
        Log.d(TAG, "Final message length: " + finalMessage.length() + " characters");

        try {
            Log.d(TAG, "Attempting to send SMS with dual SIM support");
            sendSMSWithDualSIMSupport(context, phoneNumber, finalMessage);
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException - SMS permission denied", e);
            Toast.makeText(context, "SMS permission denied", Toast.LENGTH_LONG).show();
            openSMSAppAsFallback(context, phoneNumber, finalMessage);
        } catch (Exception e) {
            Log.e(TAG, "Exception while sending SMS: " + e.getMessage(), e);
            Toast.makeText(context, "Failed to send SMS: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
            openSMSAppAsFallback(context, phoneNumber, finalMessage);
        }
    }


    private static void sendSMSWithDualSIMSupport(Context context, String phoneNumber, String message) {
        Log.d(TAG, "sendSMSWithDualSIMSupport() called");

        try {
            SubscriptionManager subscriptionManager =
                    (SubscriptionManager) context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);

            if (subscriptionManager != null) {
                Log.d(TAG, "SubscriptionManager obtained");
                List<SubscriptionInfo> subscriptionInfoList =
                        subscriptionManager.getActiveSubscriptionInfoList();

                if (subscriptionInfoList != null && !subscriptionInfoList.isEmpty()) {
                    Log.d(TAG, "Active subscriptions found: " + subscriptionInfoList.size());

                    int defaultSmsSubscriptionId = SmsManager.getDefaultSmsSubscriptionId();
                    Log.d(TAG, "Default SMS subscription ID: " + defaultSmsSubscriptionId);

                    SmsManager smsManager;
                    if (defaultSmsSubscriptionId != -1) {
                        Log.d(TAG, "Using default SIM");
                        smsManager = SmsManager.getSmsManagerForSubscriptionId(defaultSmsSubscriptionId);
                    } else {
                        Log.d(TAG, "No default SIM, using first available SIM");
                        int subscriptionId = subscriptionInfoList.get(0).getSubscriptionId();
                        Log.d(TAG, "First SIM subscription ID: " + subscriptionId);
                        smsManager = SmsManager.getSmsManagerForSubscriptionId(subscriptionId);
                    }

                    Log.i(TAG, "Sending SMS via SmsManager");
                    // FIXED: Handle long messages properly
                    if (message.length() > 160) {
                        ArrayList<String> parts = smsManager.divideMessage(message);
                        smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null);
                    } else {
                        smsManager.sendTextMessage(phoneNumber, null, message, null, null);
                    }
                    Log.i(TAG, "SMS sent successfully");
                    Toast.makeText(context, "Emergency alert sent!", Toast.LENGTH_LONG).show();
                    return;
                } else {
                    Log.w(TAG, "No active subscriptions found");
                }
            } else {
                Log.w(TAG, "SubscriptionManager is null");
            }
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException in sendSMSWithDualSIMSupport", e);
            throw e;
        } catch (Exception e) {
            Log.e(TAG, "Exception in sendSMSWithDualSIMSupport: " + e.getMessage(), e);
            e.printStackTrace();
            throw e;
        }

        // Fallback to default SmsManager
        Log.d(TAG, "Falling back to default SmsManager");
        try {
            SmsManager smsManager = SmsManager.getDefault();
            Log.i(TAG, "Sending SMS via default SmsManager");
            // FIXED: Handle long messages in fallback too
            if (message.length() > 160) {
                ArrayList<String> parts = smsManager.divideMessage(message);
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null);
            } else {
                smsManager.sendTextMessage(phoneNumber, null, message, null, null);
            }
            Log.i(TAG, "SMS sent successfully via default SmsManager");
            Toast.makeText(context, "Emergency alert sent!", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Log.e(TAG, "Failed to send via default SmsManager: " + e.getMessage(), e);
            openSMSAppAsFallback(context, phoneNumber, message);
        }
    }

    private static void openSMSAppAsFallback(Context context, String phoneNumber, String message) {
        Log.d(TAG, "openSMSAppAsFallback() called");

        try {
            Uri uri = Uri.parse("smsto:" + phoneNumber);
            Intent smsIntent = new Intent(Intent.ACTION_SENDTO, uri);
            smsIntent.putExtra("sms_body", message);
            smsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            if (smsIntent.resolveActivity(context.getPackageManager()) != null) {
                Log.i(TAG, "Opening SMS app with pre-filled message");
                context.startActivity(smsIntent);
                Toast.makeText(context, "Please send the message manually",
                        Toast.LENGTH_LONG).show();
            } else {
                Log.e(TAG, "No SMS app found on device");
                Toast.makeText(context, "No SMS app found", Toast.LENGTH_LONG).show();
            }
        } catch (Exception ex) {
            Log.e(TAG, "Exception while opening SMS app: " + ex.getMessage(), ex);
            Toast.makeText(context, "Cannot open SMS app: " + ex.getMessage(),
                    Toast.LENGTH_LONG).show();
            ex.printStackTrace();
        }
    }
}