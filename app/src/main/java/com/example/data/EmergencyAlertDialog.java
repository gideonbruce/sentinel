package com.example.data;


import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.telephony.SmsManager;
import android.widget.Toast;

public class EmergencyAlertDialog {

    public interface OnAlertActionListener {
        void onAlertSent();
        void onAlertCancelled();
    }

    public static void show(Context context, OnAlertActionListener listener) {
        EmergencyContactManager contactManager = new EmergencyContactManager(context);

        if (!contactManager.hasEmergencyContact()) {
            showSetupContactDialog(context);
            return;
        }

        String contactName = contactManager.getContactName();
        String contactPhone = contactManager.getContactPhone();

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Emergency Alert");
        builder.setMessage("Send emergency notification to " +
                (contactName != null ? contactName : contactPhone) + "?");

        builder.setPositiveButton("Send Alert", (dialog, which) -> {
            sendEmergencyAlert(context, contactPhone);
            if (listener != null) {
                listener.onAlertSent();
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> {
            if (listener != null) {
                listener.onAlertCancelled();
            }
        });

        builder.setCancelable(false);
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private static void showSetupContactDialog(Context context) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("No Emergency Contact");
        builder.setMessage("Please set up an emergency contact first.");
        builder.setPositiveButton("OK", null);
        builder.show();
    }

    private static void sendEmergencyAlert(Context context, String phoneNumber) {
        String message = "EMERGENCY ALERT: I need help! This is an automated message from my emergency app.";

        try {
            // Try to send SMS
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(phoneNumber, null, message, null, null);
            Toast.makeText(context, "Emergency alert sent!", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            // Fallback to SMS intent if direct SMS fails
            Intent smsIntent = new Intent(Intent.ACTION_VIEW);
            smsIntent.setData(Uri.parse("sms:" + phoneNumber));
            smsIntent.putExtra("sms_body", message);
            context.startActivity(smsIntent);
        }
    }
}
