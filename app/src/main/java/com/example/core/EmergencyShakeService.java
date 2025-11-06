package com.example.core;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Settings;
import android.telephony.SmsManager;
import android.os.Looper;
import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.Gravity;
import android.graphics.PixelFormat;
import android.content.IntentFilter;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ActivityCompat;

import com.example.data.AlertEntity;
import com.example.data.AlertRepository;
import com.example.data.EmergencyContactManager;
import com.example.sentinel.MainActivity;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.example.ui.EmergencyAlertDialog;

import java.util.ArrayList;

public class EmergencyShakeService extends Service {
    private static final String CHANNEL_ID = "EmergencyShakeChannel";
    private static final int NOTIFICATION_ID = 1;

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private ShakeDetector shakeDetector;
    private EmergencyContactManager contactManager;
    private PowerManager.WakeLock wakeLock;

    private FusedLocationProviderClient fusedLocationClient;
    private Location lastKnownLocation;
    private LocationCallback locationCallback;
    private WindowManager windowManager;
    private View overlayView;

    private BroadcastReceiver volumeButtonReceiver;
    private BroadcastReceiver smsConfirmReceiver;

    private BroadcastReceiver smsSentReceiver;
    private BroadcastReceiver smsDeliveredReceiver;

    private VolumeButtonGestureDetector volumeGestureDetector;
    private AlertRepository alertRepository;

    private String lastPhoneNumber;
    private String lastMessage;
    private String lastEmergencyType;
    private Location lastLocation;

    @Override
    public void onCreate() {
        super.onCreate();

        Log.d("EmergencyService", "=== Service onCreate ===");

        contactManager = new EmergencyContactManager(this);
        alertRepository = AlertRepository.getInstance(getApplication());


        registerSMSReceivers();

        // Initialize shake detection
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        shakeDetector = new ShakeDetector();

        shakeDetector.setOnShakeListener(count -> {
            if (count >= 3) {
                //sendEmergencySMS();
                //getLocationAndSendSMS();
                showEmergencyAlertDialog(null);
            }
        });

        if (Settings.canDrawOverlays(this)) {
            setupOverlayForVolumeDetection();
        }

        // Acquire wake lock to keep CPU running
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "Sentinel::ShakeDetectionWakeLock");
        wakeLock.acquire();

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        startLocationUpdates();

        // Initialize volume gesture detection
        volumeGestureDetector = new VolumeButtonGestureDetector(new VolumeButtonGestureDetector.OnVolumeGestureListener() {
            @Override
            public void onSilentEmergency() {
                showEmergencyAlertDialog("SILENT EMERGENCY");
            }
            @Override
            public void onPoliceNeeded() {

                showEmergencyAlertDialog("POLICE NEEDED");
            }
            @Override
            public void onMedicalEmergency() {
                showEmergencyAlertDialog("MEDICAL EMERGENCY");
            }
            @Override
            public void onPanicAlert() {
                showEmergencyAlertDialog("PANIC ALERT");
            }
        });

        if (Settings.canDrawOverlays(this)) {
            setupOverlayForVolumeDetection();
        }

        volumeButtonReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if ("com.example.sentinel.VOLUME_BUTTON_EVENT".equals(intent.getAction())) {
                    int keyCode = intent.getIntExtra("keyCode", -1);
                    boolean isKeyDown = intent.getBooleanExtra("isKeyDown", false);
                    handleVolumeButtonEvent(keyCode, isKeyDown);
                }
            }
        };

        smsConfirmReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if ("com.example.sentinel.SEND_EMERGENCY_SMS".equals(intent.getAction())) {
                    String emergencyType = intent.getStringExtra("EMERGENCY_TYPE");
                    //getLocationAndSendSMS(emergencyType);

                    //location from broadcast
                    Location location = intent.getParcelableExtra("LOCATION");

                    // Just save to database, SMS already sent by dialog
                    //sendEmergencySMS(location, emergencyType);
                    saveAlertToDatabase(emergencyType, location);
                }
            }
        };

        IntentFilter smsFilter = new IntentFilter("com.example.sentinel.SEND_EMERGENCY_SMS");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(smsConfirmReceiver, smsFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(smsConfirmReceiver, smsFilter, Context.RECEIVER_NOT_EXPORTED);
        }

        IntentFilter filter = new IntentFilter("com.example.sentinel.VOLUME_BUTTON_EVENT");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(volumeButtonReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(volumeButtonReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        }
    }

    private void registerSMSReceivers() {
        // SMS Sent receiver
        smsSentReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int resultCode = getResultCode();
                Log.d("EmergencyService", "SMS Sent result code: " + resultCode);

                switch (resultCode) {
                    case android.app.Activity.RESULT_OK:
                        Log.d("EmergencyService", "SMS sent successfully!");
                        showSMSSentNotification(true);
                        break;
                    case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                        Log.e("EmergencyService", "SMS generic failure - opening SMS app as fallback");
                        // Get the last message details and open SMS app
                        handleSMSFailure();
                        break;
                    case SmsManager.RESULT_ERROR_NO_SERVICE:
                        Log.e("EmergencyService", "SMS failed - No service");
                        handleSMSFailure();
                        break;
                    case SmsManager.RESULT_ERROR_NULL_PDU:
                        Log.e("EmergencyService", "SMS failed - Null PDU");
                        handleSMSFailure();
                        break;
                    case SmsManager.RESULT_ERROR_RADIO_OFF:
                        Log.e("EmergencyService", "SMS failed - Radio off");
                        handleSMSFailure();
                        break;
                }
            }
        };

        // SMS Delivered receiver
        smsDeliveredReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int resultCode = getResultCode();
                Log.d("EmergencyService", "SMS Delivery result code: " + resultCode);

                switch (resultCode) {
                    case android.app.Activity.RESULT_OK:
                        Log.d("EmergencyService", "SMS delivered successfully!");
                        break;
                    case android.app.Activity.RESULT_CANCELED:
                        Log.w("EmergencyService", "SMS not delivered");
                        break;
                }
            }
        };

        // Register receivers
        IntentFilter sentFilter = new IntentFilter("SMS_SENT");
        IntentFilter deliveredFilter = new IntentFilter("SMS_DELIVERED");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(smsSentReceiver, sentFilter, Context.RECEIVER_NOT_EXPORTED);
            registerReceiver(smsDeliveredReceiver, deliveredFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(smsSentReceiver, sentFilter, Context.RECEIVER_NOT_EXPORTED);
            registerReceiver(smsDeliveredReceiver, deliveredFilter, Context.RECEIVER_NOT_EXPORTED);
        }

        Log.d("EmergencyService", "SMS status receivers registered");
    }

    private void openSMSAppAsFallback(String phoneNumber, String message, String emergencyType, Location location) {
        Log.d("EmergencyService", "=== Opening SMS app as fallback ===");
        try {
            Uri uri = Uri.parse("smsto:" + phoneNumber);
            Intent smsIntent = new Intent(Intent.ACTION_SENDTO, uri);
            smsIntent.putExtra("sms_body", message);
            smsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            if (smsIntent.resolveActivity(getPackageManager()) != null) {
                Log.d("EmergencyService", "SMS app found, opening...");
                startActivity(smsIntent);
                showManualSendNotification();
                Log.d("EmergencyService", "SMS app opened successfully");
            } else {
                Log.e("EmergencyService", "No SMS app found on device!");
                showSMSFailedNotification();
            }
        } catch (Exception ex) {
            Log.e("EmergencyService", "Exception opening SMS app: " + ex.getMessage());
            ex.printStackTrace();
            showSMSFailedNotification();
        }
    }

    private void showManualSendNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Please Send Manually")
                .setContentText("Automatic SMS failed. Messaging app opened.")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(4, builder.build());
        }
    }

    private void handleSMSFailure() {
        Log.d("EmergencyService", "Handling SMS failure - opening SMS app");
        if (lastPhoneNumber != null && lastMessage != null) {
            openSMSAppAsFallback(lastPhoneNumber, lastMessage, lastEmergencyType, lastLocation);
        }
    }

    private void saveAlertToDatabase(String emergencyType, Location location) {
        String alertType = (emergencyType != null) ? emergencyType : "EMERGENCY";
        long timestamp = System.currentTimeMillis();
        Double latitude = (location != null) ? location.getLatitude() : null;
        Double longitude = (location != null) ? location.getLongitude() : null;
        String contactName = contactManager.getContactName();
        String contactPhone = contactManager.getContactPhone();
        boolean locationAvailable = (location != null);

        AlertEntity alert = new AlertEntity(
                alertType,
                timestamp,
                latitude,
                longitude,
                contactName,
                contactPhone,
                locationAvailable
        );

        alertRepository.insert(alert, firebaseKey -> {
            if (firebaseKey != null) {
                Log.d("EmergencyShakeService", "Alert saved with key: " + firebaseKey);
            }
        });
    }

    private void setupOverlayForVolumeDetection() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        overlayView = new View(this) {
            @Override
            public boolean dispatchKeyEvent(KeyEvent event) {
                int keyCode = event.getKeyCode();
                if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
                        keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                    handleVolumeButtonEvent(keyCode, event.getAction() == KeyEvent.ACTION_DOWN);
                    return true;
                }
                return super.dispatchKeyEvent(event);
            }
        };

        overlayView.setFocusableInTouchMode(true);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                1, 1,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.START;

        try {
            windowManager.addView(overlayView, params);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void handleVolumeButtonEvent(int keyCode, boolean isKeyDown) {
        if (volumeGestureDetector == null) {
            return;
        }

        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (isKeyDown) {
                volumeGestureDetector.onVolumeDown();
            } else {
                volumeGestureDetector.onVolumeUp();
            }
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP && isKeyDown) {
            volumeGestureDetector.onVolumeUpButton();
        }

    }

    private void getLocationAndSendSMS() {
        getLocationAndSendSMS(null);
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Sentinel Emergency")
                .setContentText("Shake detection is active")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();

        startForeground(NOTIFICATION_ID, notification);

        // Register sensor listener
        if (accelerometer != null) {
            sensorManager.registerListener(shakeDetector, accelerometer,
                    SensorManager.SENSOR_DELAY_UI);
        }

        return START_STICKY;
    }

    private void showEmergencyAlertDialog(String emergencyType) {
        // Create an Intent to bring MainActivity to foreground or start it
        Intent dialogIntent = new Intent(this, MainActivity.class);
        dialogIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        dialogIntent.putExtra("SHOW_EMERGENCY_DIALOG", true);
        dialogIntent.putExtra("EMERGENCY_TYPE", emergencyType);

        if (lastKnownLocation != null) {
            dialogIntent.putExtra("LOCATION", lastKnownLocation);
        }
        startActivity(dialogIntent);
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        LocationRequest locationRequest = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, 10000) // Update every 30 seconds
                .setMinUpdateIntervalMillis(5000) // Fastest update every 15 seconds
                .setWaitForAccurateLocation(false) // dont wait for perfect accuracy
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                if (locationResult != null && locationResult.getLastLocation() != null) {
                    lastKnownLocation = locationResult.getLastLocation();
                }
                //lastKnownLocation = locationResult.getLastLocation();
            }
        };

        fusedLocationClient.requestLocationUpdates(locationRequest,
                locationCallback, Looper.getMainLooper());

        // Also get last known location immediately
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        lastKnownLocation = location;
                    }
                });
    }

    private void getLocationAndSendSMS(String emergencyType) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
            sendEmergencySMS(null, emergencyType);
            return;
        }

        //get current location with timeout
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        sendEmergencySMS(location, emergencyType);
                    } else {
                        tryLastKnownLocation(emergencyType);
                    }
                })
                .addOnFailureListener(e -> {
                    tryLastKnownLocation(emergencyType);
                });
    }

    private void tryLastKnownLocation(String emergencyType) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED) {

            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            sendEmergencySMS(location, emergencyType);
                        } else if (lastKnownLocation != null) {
                            sendEmergencySMS(lastKnownLocation, emergencyType);
                        } else {
                            // No location available at all
                            sendEmergencySMS(null, emergencyType);
                        }
                    })
                    .addOnFailureListener(e -> {
                        // Use cached location or send without location
                        sendEmergencySMS(lastKnownLocation, emergencyType);
                    });
        } else {
            sendEmergencySMS(null, emergencyType);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        Log.d("EmergencyService", "=== Service onDestroy ===");

        // Unregister SMS receivers
        /*if (smsSentReceiver != null) {
            try {
                unregisterReceiver(smsSentReceiver);
                Log.d("EmergencyService", "SMS sent receiver unregistered");
            } catch (Exception e) {
                Log.e("EmergencyService", "Error unregistering SMS sent receiver: " + e.getMessage());
            }
        }*/

        /*if (smsDeliveredReceiver != null) {
            try {
                unregisterReceiver(smsDeliveredReceiver);
                Log.d("EmergencyService", "SMS delivered receiver unregistered");
            } catch (Exception e) {
                Log.e("EmergencyService", "Error unregistering SMS delivered receiver: " + e.getMessage());
            }
        }*/

        if (volumeGestureDetector != null) {
            volumeGestureDetector.cleanup();
        }

        // Unregister sensor listener
        if (sensorManager != null && shakeDetector != null) {
            sensorManager.unregisterListener(shakeDetector);
        }

        // stop location updates
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }

        // Release wake lock
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }

        //unregister broadcast reciever
        if (volumeButtonReceiver != null) {
            unregisterReceiver(volumeButtonReceiver);
        }

        if (smsConfirmReceiver != null) {
            unregisterReceiver(smsConfirmReceiver);
        }

        //removes overlay
        if (overlayView != null && windowManager != null) {
            try {
                windowManager.removeView(overlayView);
                overlayView = null;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Emergency Shake Detection",
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription("Monitors shake gestures for emergency alerts");

        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    private void sendEmergencySMS(Location location, String emergencyType) {
        Log.d("EmergencyService", "sendEmergencySMS called - but SMS sending now handled by dialog");
        saveAlertToDatabase(emergencyType, location);
    }

    /*
    private void sendEmergencySMS(Location location, String emergencyType) {
        Log.d("EmergencyService", "=== sendEmergencySMS called ===");

        if (!contactManager.hasEmergencyContact()) {
            Log.e("EmergencyService", "No emergency contact set!");
            return;
        }

        String phoneNumber = contactManager.getContactPhone();
        String message = getMessage(location, emergencyType);

        lastPhoneNumber = phoneNumber;
        lastMessage = message;
        lastEmergencyType = emergencyType;
        lastLocation = location;

        Log.d("EmergencyService", "Phone number: " + phoneNumber);
        Log.d("EmergencyService", "Message length: " + message.length());
        Log.d("EmergencyService", "Message: " + message);
        Log.d("EmergencyService", "Emergency type: " + emergencyType);
        Log.d("EmergencyService", "Location available: " + (location != null));

        try {
            Log.d("EmergencyService", "Attempting to send SMS...");

            //PendingIntents for sent and delivery tracking
            Intent sentIntent = new Intent("SMS_SENT");
            Intent deliveredIntent = new Intent("SMS_DELIVERED");

            PendingIntent sentPI = PendingIntent.getBroadcast(this, 0, sentIntent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            PendingIntent deliveredPI = PendingIntent.getBroadcast(this, 0, deliveredIntent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

            SmsManager smsManager;
            // Handle dual SIM devices
            int defaultSmsSubscriptionId = SmsManager.getDefaultSmsSubscriptionId();
            Log.d("EmergencyService", "Default SMS subscription ID: " + defaultSmsSubscriptionId);

            if (defaultSmsSubscriptionId != -1) {
                smsManager = SmsManager.getSmsManagerForSubscriptionId(defaultSmsSubscriptionId);
                Log.d("EmergencyService", "Using SMS manager for subscription ID: " + defaultSmsSubscriptionId);
            } else {
                smsManager = SmsManager.getDefault();
                Log.d("EmergencyService", "Using default SMS manager");
            }

            //split message if its too long
            if (message.length() > 160) {
                Log.d("EmergencyService", "Message is long, splitting into parts...");
                ArrayList<String> parts = smsManager.divideMessage(message);
                Log.d("EmergencyService", "Message split into " + parts.size() + " parts");

                //ArrayLists of PendingIntents for multipart messages
                ArrayList<PendingIntent> sentPIs = new ArrayList<>();
                ArrayList<PendingIntent> deliveredPIs = new ArrayList<>();
                for (int i = 0; i < parts.size(); i++) {
                    sentPIs.add(sentPI);
                    deliveredPIs.add(deliveredPI);
                }

                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, sentPIs, deliveredPIs);
                Log.d("EmergencyService", "Multipart SMS sent!");
            } else {
                Log.d("EmergencyService", "Sending single SMS...");
                smsManager.sendTextMessage(phoneNumber, null, message, sentPI, deliveredPI);
                Log.d("EmergencyService", "Single SMS sent!");
            }
            //smsManager.sendTextMessage(phoneNumber, null, message, null, null);
            saveAlertToDatabase(emergencyType, location);
            Log.d("EmergencyService", "Alert saved to database");
            // Show notification that SMS was sent
            showSMSSentNotification(location != null);

            // Save the alert to the database
            String alertType = (emergencyType != null) ? emergencyType : "EMERGENCY";
            long timestamp = System.currentTimeMillis();
            Double latitude = (location != null) ? location.getLatitude() : null;
            Double longitude = (location != null) ? location.getLongitude() : null;
            String contactName = contactManager.getContactName();
            String contactPhone = contactManager.getContactPhone();
            boolean locationAvailable = (location != null);

            AlertEntity alert = new AlertEntity(
                    alertType,
                    timestamp,
                    latitude,
                    longitude,
                    contactName,
                    contactPhone,
                    locationAvailable
            );
            alertRepository.insert(alert, success -> {});

        } catch (Exception e) {
            e.printStackTrace();
            showSMSFailedNotification();
        }
    }
    */

    @NonNull
    private String getMessage(Location location, String emergencyType) {
        //String message = emergencyType != null ?
        //        emergencyType + "! This is an automated alert. Please check on me immediately." :
        //        "EMERGENCY! This is an automated alert. Please check on me immediately.";

        //gets custom message from contact manager
        String customMessage = contactManager.getEmergencyMessage();

        //builds complete message
        StringBuilder message = new StringBuilder();

        if (emergencyType != null) {
            message.append("🚨 ").append(emergencyType).append("!\n\n");
        }

        message.append(customMessage);

        if (location != null) {
            double latitude = location.getLatitude();
            double longitude = location.getLongitude();
            message.append("\n\n📍 https://maps.google.com/?q=")
                    .append(latitude)
                    .append(",")
                    .append(longitude);
            //message += "\n\nMy location:\nLat: " + latitude + "\nLong: " + longitude + "\nMap: " + locationUrl;
        } else {
            message.append("\n\n(Location unavalable)");
        }
        return message.toString();
    }

    private void showSMSFailedNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Emergency SMS Failed")
                .setContentText("Failed to send alert to emergency contact")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(3, builder.build());
        }
    }

    private void showSMSSentNotification(boolean withLocation) {
        String contentText = withLocation ?
                "Alert with location sent to emergency contact" :
                "Alert sent to emergency contact (location unavailable)";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Emergency SMS Sent")
                .setContentText(contentText)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(2, builder.build());
        }
    }
}
