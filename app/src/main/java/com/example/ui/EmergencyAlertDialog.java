package com.example.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.telephony.SmsManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.widget.Toast;

import com.example.data.EmergencyContactManager;

import java.util.List;

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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                // For Android 5.1 and above - handle dual SIM
                sendSMSWithDualSIMSupport(context, phoneNumber, message);
            } else {
                // For older Android versions
                SmsManager smsManager = SmsManager.getDefault();
                smsManager.sendTextMessage(phoneNumber, null, message, null, null);
                Toast.makeText(context, "Emergency alert sent!", Toast.LENGTH_LONG).show();
            }
        } catch (SecurityException e) {
            Toast.makeText(context, "SMS permission denied", Toast.LENGTH_LONG).show();
            openSMSAppAsFallback(context, phoneNumber, message);
        } catch (Exception e) {
            Toast.makeText(context, "Failed to send SMS: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
            openSMSAppAsFallback(context, phoneNumber, message);
        }
    }

    private static void sendSMSWithDualSIMSupport(Context context, String phoneNumber, String message) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            try {
                SubscriptionManager subscriptionManager =
                        (SubscriptionManager) context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);

                if (subscriptionManager != null) {
                    List<SubscriptionInfo> subscriptionInfoList =
                            subscriptionManager.getActiveSubscriptionInfoList();

                    if (subscriptionInfoList != null && subscriptionInfoList.size() > 0) {
                        // Get the default SMS subscription ID
                        int defaultSmsSubscriptionId = SmsManager.getDefaultSmsSubscriptionId();

                        SmsManager smsManager;
                        if (defaultSmsSubscriptionId != -1) {
                            // Use default SIM
                            smsManager = SmsManager.getSmsManagerForSubscriptionId(defaultSmsSubscriptionId);
                        } else {
                            // Use first available SIM
                            int subscriptionId = subscriptionInfoList.get(0).getSubscriptionId();
                            smsManager = SmsManager.getSmsManagerForSubscriptionId(subscriptionId);
                        }

                        smsManager.sendTextMessage(phoneNumber, null, message, null, null);
                        Toast.makeText(context, "Emergency alert sent!", Toast.LENGTH_LONG).show();
                        return;
                    }
                }
            } catch (SecurityException e) {
                // Permission denied - fallback to SMS app
                openSMSAppAsFallback(context, phoneNumber, message);
                return;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // Fallback to default SmsManager
        try {
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(phoneNumber, null, message, null, null);
            Toast.makeText(context, "Emergency alert sent!", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            openSMSAppAsFallback(context, phoneNumber, message);
        }
    }

    private static void openSMSAppAsFallback(Context context, String phoneNumber, String message) {
        try {
            Uri uri = Uri.parse("smsto:" + phoneNumber);
            Intent smsIntent = new Intent(Intent.ACTION_SENDTO, uri);
            smsIntent.putExtra("sms_body", message);
            smsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            if (smsIntent.resolveActivity(context.getPackageManager()) != null) {
                context.startActivity(smsIntent);
                Toast.makeText(context, "Please send the message manually",
                        Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(context, "No SMS app found", Toast.LENGTH_LONG).show();
            }
        } catch (Exception ex) {
            Toast.makeText(context, "Cannot open SMS app: " + ex.getMessage(),
                    Toast.LENGTH_LONG).show();
            ex.printStackTrace();
        }
    }
}