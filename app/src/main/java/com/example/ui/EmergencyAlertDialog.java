package com.example.ui;

import android.Manifest;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.telephony.SmsManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;
import android.app.Activity;
import android.content.SharedPreferences;
import androidx.core.content.ContextCompat;

import com.example.data.EmergencyContactManager;
import com.example.ai.AIMessageGenerator;
import java.util.ArrayList;
import java.util.List;

public class EmergencyAlertDialog {
    private static final String TAG = "EmergencyAlertDialog";
    private static AlertDialog currentDialog;
    private static AIMessageGenerator aiGenerator;

    public interface OnAlertActionListener {
        void onAlertSent();
        void onAlertCancelled();
    }

    public static void show(Context context, OnAlertActionListener listener, android.location.Location location) {
        show(context, listener, location, null, false);  // Call overloaded version
    }

    public static void show(Context context, OnAlertActionListener listener, android.location.Location location, String emergencyType, boolean useAI) {
        Log.d(TAG, "show() called");

        if (currentDialog != null && currentDialog.isShowing()) {
            currentDialog.dismiss();
        }
        Log.d(TAG, "show() called - AI enabled: " + useAI);
        EmergencyContactManager contactManager = new EmergencyContactManager(context);

        if (!contactManager.hasEmergencyContact()) {
            Log.w(TAG, "No emergency contact configured");
            showSetupContactDialog(context);
            return;
        }

        String contactName = contactManager.getContactName();
        String contactPhone = contactManager.getContactPhone();

        String title = emergencyType != null ? emergencyType : "Emergency Alert";
        String message = useAI ?
                "Preparing intelligent emergency message..." :
                "Send emergency alert to " + (contactName != null ? contactName : contactPhone) + "?";

        Log.d(TAG, "Emergency contact - Name: " + contactName + ", Phone: " +
                (contactPhone != null ? contactPhone : "null"));

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setCancelable(false);

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

        currentDialog = builder.create();
        currentDialog.show();

        if (useAI) {
            generateAndShowAIMessage(context, contactManager, location, emergencyType, listener);
        } else {
            //trad flow - add send button immediately
            currentDialog.setButton(AlertDialog.BUTTON_POSITIVE, "Send Alert", (dialog, which) -> {
                Log.i(TAG, "User confirmed emergency alert");
                sendEmergencyAlert(context, contactPhone, location);
                if (listener != null) {
                    listener.onAlertSent();
                }
            });
        }
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

        // Validate phone number format
        phoneNumber = normalizePhoneNumber(phoneNumber);
        if (phoneNumber == null) {
            throw new IllegalArgumentException("Invalid phone number format");
        }

        // Double-check SMS permission
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("SMS permission not granted");
        }

        SmsManager smsManager = null;
        boolean usedDualSim = false;

        // Try dual SIM first (API 22+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            // Check READ_PHONE_STATE permission (required for SubscriptionManager on API 29+)
            boolean hasPhoneStatePermission = true;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                hasPhoneStatePermission = ContextCompat.checkSelfPermission(context,
                        Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED;

                if (!hasPhoneStatePermission) {
                    Log.w(TAG, "READ_PHONE_STATE permission not granted on Android 10+, " +
                            "skipping dual SIM detection");
                }
            }

            // Only try dual SIM if we have permission (or don't need it on older Android)
            if (hasPhoneStatePermission) {
                try {
                    SubscriptionManager subscriptionManager =
                            (SubscriptionManager) context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);

                    if (subscriptionManager != null) {
                        List<SubscriptionInfo> subscriptionInfoList =
                                subscriptionManager.getActiveSubscriptionInfoList();

                        if (subscriptionInfoList != null && !subscriptionInfoList.isEmpty()) {
                            int defaultSmsSubscriptionId = SmsManager.getDefaultSmsSubscriptionId();

                            if (defaultSmsSubscriptionId != -1) {
                                Log.d(TAG, "Using default SIM with ID: " + defaultSmsSubscriptionId);
                                smsManager = SmsManager.getSmsManagerForSubscriptionId(defaultSmsSubscriptionId);
                                usedDualSim = true;
                            } else if (subscriptionInfoList.size() > 0) {
                                // Use first available SIM
                                int subscriptionId = subscriptionInfoList.get(0).getSubscriptionId();
                                Log.d(TAG, "Using first available SIM with ID: " + subscriptionId);
                                smsManager = SmsManager.getSmsManagerForSubscriptionId(subscriptionId);
                                usedDualSim = true;
                            }
                        } else {
                            Log.d(TAG, "No active subscriptions found");
                        }
                    }
                } catch (SecurityException e) {
                    Log.e(TAG, "SecurityException accessing subscriptions", e);
                    // Will fall back to default
                } catch (Exception e) {
                    Log.e(TAG, "Exception in dual SIM setup: " + e.getMessage(), e);
                    // Will fall back to default
                }
            }
        }

        // Fallback to default SmsManager if dual SIM setup failed
        if (smsManager == null) {
            Log.d(TAG, "Using default SmsManager (fallback)");
            smsManager = SmsManager.getDefault();
        }

        // Verify smsManager is not null before proceeding
        if (smsManager == null) {
            throw new IllegalStateException("Failed to get SmsManager instance");
        }

        // Create pending intents
        Intent sentIntent = new Intent("SMS_SENT");
        sentIntent.putExtra("phone_number", phoneNumber);
        Intent deliveredIntent = new Intent("SMS_DELIVERED");

        PendingIntent sentPI = PendingIntent.getBroadcast(
                context, 0, sentIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );
        PendingIntent deliveredPI = PendingIntent.getBroadcast(
                context, 0, deliveredIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        // Send SMS using multipart
        try {
            ArrayList<String> parts = smsManager.divideMessage(message);
            Log.d(TAG, "Message divided into " + parts.size() + " part(s)");

            ArrayList<PendingIntent> sentIntents = new ArrayList<>();
            ArrayList<PendingIntent> deliveryIntents = new ArrayList<>();

            for (int i = 0; i < parts.size(); i++) {
                sentIntents.add(sentPI);
                deliveryIntents.add(deliveredPI);
            }

            smsManager.sendMultipartTextMessage(
                    phoneNumber,
                    null,
                    parts,
                    sentIntents,
                    deliveryIntents
            );

            Log.i(TAG, "SMS queued successfully (" + parts.size() + " parts, " +
                    (usedDualSim ? "dual SIM" : "default") + ")");

        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Invalid SMS parameters: " + e.getMessage(), e);
            throw new IllegalArgumentException("Invalid phone number or message: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Failed to send SMS: " + e.getMessage(), e);
            throw e;
        }
    }

    private static String normalizePhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return null;
        }

        // Remove all spaces, dashes, parentheses
        phoneNumber = phoneNumber.replaceAll("[\\s()\\-]", "");

        // Handle Kenyan numbers
        // Convert 0712345678 to +254712345678
        if (phoneNumber.startsWith("0") && phoneNumber.length() == 10) {
            phoneNumber = "+254" + phoneNumber.substring(1);
        }
        // Add + if missing but has country code
        else if (phoneNumber.startsWith("254") && !phoneNumber.startsWith("+")) {
            phoneNumber = "+" + phoneNumber;
        }

        // Validate format
        if (phoneNumber.length() < 10) {
            Log.e(TAG, "Phone number too short: " + phoneNumber);
            return null;
        }

        Log.d(TAG, "Normalized phone number format");
        return phoneNumber;
    }

    public static class SmsBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            int networkType = tm.getNetworkType();
            Log.d(TAG, "Network type: " + networkType);
            Log.d(TAG, "SIM state: " + tm.getSimState());

            if ("SMS_SENT".equals(action)) {
                switch (getResultCode()) {
                    case android.app.Activity.RESULT_OK:
                        Log.i(TAG, "SMS sent successfully");
                        Toast.makeText(context, "Message sent!", Toast.LENGTH_SHORT).show();
                        break;
                    case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                        String errorDetails = intent.getStringExtra("errorCode");
                        Log.e(TAG, "SMS generic failure: " + errorDetails);
                        Log.e(TAG, "Phone number: " + intent.getStringExtra("phone_number"));
                        Toast.makeText(context, "Failed to send message", Toast.LENGTH_SHORT).show();
                        break;
                    case SmsManager.RESULT_ERROR_NO_SERVICE:
                        Log.e(TAG, "SMS no service");
                        Toast.makeText(context, "No service - message not sent", Toast.LENGTH_SHORT).show();
                        break;
                    case SmsManager.RESULT_ERROR_NULL_PDU:
                        Log.e(TAG, "SMS null PDU");
                        Toast.makeText(context, "Null PDU error", Toast.LENGTH_SHORT).show();
                        break;
                    case SmsManager.RESULT_ERROR_RADIO_OFF:
                        Log.e(TAG, "SMS radio off");
                        Toast.makeText(context, "Radio off - message not sent", Toast.LENGTH_SHORT).show();
                        break;
                }
            } else if ("SMS_DELIVERED".equals(action)) {
                switch (getResultCode()) {
                    case android.app.Activity.RESULT_OK:
                        Log.i(TAG, "SMS delivered successfully");
                        Toast.makeText(context, "Message delivered!", Toast.LENGTH_SHORT).show();
                        break;
                    case android.app.Activity.RESULT_CANCELED:
                        Log.e(TAG, "SMS not delivered");
                        Toast.makeText(context, "Message not delivered", Toast.LENGTH_SHORT).show();
                        break;
                }
            }
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

    private static void generateAndShowAIMessage(Context context, EmergencyContactManager contactManager, android.location.Location location, String emergencyType, OnAlertActionListener listener) {
        //init ai generator
        if (aiGenerator == null) {
            try {
                aiGenerator = new AIMessageGenerator(context);
            } catch (Exception e) {
                Log.e(TAG, "Failed to initialize AI: " + e.getMessage());
                Toast.makeText(context, "AI not available, using standard message", Toast.LENGTH_SHORT).show();
                useFallbackMessage(context, contactManager, location, emergencyType, listener);
                return;
            }
        }

        String userName = contactManager.getContactName();
        String customMessage = contactManager.getEmergencyMessage();

        //generate ai message
        aiGenerator.generateEmergencyMessage(
                emergencyType,
                location,
                userName,
                customMessage,
                new AIMessageGenerator.MessageCallback() {
                    @Override
                    public void onMessageGenerated(String aiMessage) {
                        if (context instanceof Activity) {
                            ((Activity) context).runOnUiThread(() -> {
                                updateDialogWithMessage(context, contactManager, location, emergencyType, aiMessage, listener, "AI-generated message:\n\n");
                            });
                        }
                    }
                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "AI generation failed: " + error);
                        if (context instanceof Activity) {
                            ((Activity) context).runOnUiThread(() -> {
                                Toast.makeText(context, "AI failed, using standard message", Toast.LENGTH_SHORT).show();
                                useFallbackMessage(context, contactManager, location, emergencyType, listener);
                            });
                        }
                    }
                }
        );
    }

    private static void useFallbackMessage(Context context, EmergencyContactManager contactManager, android.location.Location location, String emergencyType, OnAlertActionListener listener) {
        String fallbackMessage = contactManager.getEmergencyMessage();
        if (emergencyType != null && !emergencyType.isEmpty()) {
            fallbackMessage = "🚨 " + emergencyType + "! " + fallbackMessage;
        }

        Toast.makeText(context, "Using standard message", Toast.LENGTH_SHORT).show();
        updateDialogWithMessage(context, contactManager, location, emergencyType, fallbackMessage, listener, "Message:\n\n");
    }

    private static void updateDialogWithMessage(Context context, EmergencyContactManager contactManager, android.location.Location location, String emergencyType, String messageText, OnAlertActionListener listener, String prefix) {
        if (currentDialog != null && currentDialog.isShowing()) {
            String preview = prefix + messageText + "\n\nSend to " + contactManager.getContactName() + "?";
            currentDialog.setMessage(preview);

            //Add/update Send button
            currentDialog.setButton(AlertDialog.BUTTON_POSITIVE, "Send Alert", (dialog, which) -> {
                Log.i(TAG, "User confirmed emergency alert with custom message");
                sendEmergencyAlertWithCustomMessage(context, contactManager.getContactPhone(), location, emergencyType, messageText);
                if (listener != null) {
                    listener.onAlertSent();
                }
                currentDialog.dismiss();
            });
        }
    }

    private static void sendEmergencyAlertWithCustomMessage(Context context, String phoneNumber, android.location.Location location, String emergencyType, String customMessage) {
        Log.i(TAG, "sendEmergencyAlertWithCustomMessage() called");

        // Build complete message with location
        StringBuilder message = new StringBuilder();
        message.append(customMessage);

        if (location != null) {
            double latitude = location.getLatitude();
            double longitude = location.getLongitude();
            Log.d(TAG, "Adding location to message");
            message.append("\n\n📍 https://maps.google.com/?q=")
                    .append(latitude)
                    .append(",")
                    .append(longitude);
        } else {
            Log.w(TAG, "Location unavailable");
            message.append("\n\n(Location unavailable)");
        }

        String finalMessage = message.toString();

        try {
            Log.d(TAG, "Attempting to send SMS with custom AI message");
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

    public static void cleanup() {
        if (aiGenerator != null) {
            aiGenerator.shutdown();
            aiGenerator = null;
        }
    }
}