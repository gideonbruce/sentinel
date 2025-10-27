package com.example.gestures;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android:os.PowerManager;
import android.os.PowerManager;
import android.telephony.SmsManager;
import android.view.KeyEvent;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.example.core.ShakeDetector;
import com.example.data.EmergencyContactManager;
import com.example.sentinel.MainActivity;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import java.util.ArrayList;

public class GestureDetectionService extends Service {
    private static final String CHANNEL_ID = "GestureDetectionChannel";
    private static final int NOTIFICATION_ID = 100;

    // Static instance for MainActivity to access
    private static GestureDetectionService instance;

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private ShakeDetector shakeDetector;
    private VolumeButtonGestureDetector volumeGestureDetector;
    private EmergencyContactManager contactManager;
    private PowerManager.WakeLock wakeLock;
    private FusedLocationProviderClient fusedLocationClient;
    private Location lastKnownLocation;
    private LocationCallback locationCallback;

    public static GestureDetectionService getInstance() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        instance = this;

        contactManager = new EmergencyContactManager(this);

        // Initialize location client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        startLocationUpdates();

        // Initialize shake detection
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        shakeDetector = new ShakeDetector();

        shakeDetector.setOnShakeListener(count -> {
            if (count >= 3) {
                triggerEmergencyAlert("shake_alert", "General Emergency");
            }
        });

        // Initialize volume gesture detection
        volumeGestureDetector = new VolumeButtonGestureDetector(new VolumeButtonGestureDetector.OnVolumeGestureListener() {
            @Override
            public void onSilentEmergency() {
                triggerEmergencyAlert("silent_alert", "Silent Emergency");
            }

            @Override
            public void onPoliceNeeded() {
                triggerEmergencyAlert("police_alert", "Police Alert");
            }

            @Override
            public void onMedicalEmergency() {
                triggerEmergencyAlert("medical_alert", "Medical Emergency");
            }

            @Override
            public void onPanicAlert() {
                triggerEmergencyAlert("panic_alert", "Panic Alert");
            }
        });

        // Acquire wake lock
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "Sentinel::GestureDetectionWakeLock");
        wakeLock.acquire();
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        LocationRequest locationRequest = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, 30000)
                .setMinUpdateIntervalMillis(15000)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult != null) {
                    lastKnownLocation = locationResult.getLastLocation();
                }
            }
        };

        fusedLocationClient.requestLocationUpdates(locationRequest,
                locationCallback, Looper.getMainLooper());

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        lastKnownLocation = location;
                    }
                });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Sentinel Protection Active")
                .setContentText("Shake & Volume gesture detection running")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        startForeground(NOTIFICATION_ID, notification);

        // Register sensor listener
        if (accelerometer != null) {
            sensorManager.registerListener(shakeDetector, accelerometer,
                    SensorManager.SENSOR_DELAY_UI);
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // Unregister sensor listener
        if (sensorManager != null && shakeDetector != null) {
            sensorManager.unregisterListener(shakeDetector);
        }

        // Stop location updates
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }

        // Cleanup volume gesture detector
        if (volumeGestureDetector != null) {
            volumeGestureDetector.cleanup();
        }

        // Release wake lock
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // Public method to handle volume button events from MainActivity
    public void handleVolumeKey(int keyCode, int action) {
        if (volumeGestureDetector != null) {
            if (action == 0) { // KeyEvent.ACTION_DOWN
                volumeGestureDetector.onKeyDown(keyCode, null);
            } else if (action == 1) { // KeyEvent.ACTION_UP
                volumeGestureDetector.onKeyUp(keyCode, null);
            }
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Gesture Detection",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Monitors gestures for emergency alerts");

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void triggerEmergencyAlert(String alertType, String alertName) {
        if (!contactManager.hasEmergencyContact()) {
            showNotification("No Emergency Contact",
                    "Please set up an emergency contact first", 2);
            return;
        }

        // Get location and send SMS
        getLocationAndSendSMS(alertType, alertName);
    }

    private void getLocationAndSendSMS(String alertType, String alertName) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
            sendEmergencySMS(null, alertType, alertName);
            return;
        }

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        sendEmergencySMS(location, alertType, alertName);
                    } else if (lastKnownLocation != null) {
                        sendEmergencySMS(lastKnownLocation, alertType, alertName);
                    } else {
                        sendEmergencySMS(null, alertType, alertName);
                    }
                })
                .addOnFailureListener(e -> {
                    sendEmergencySMS(lastKnownLocation, alertType, alertName);
                });
    }

    private void sendEmergencySMS(Location location, String alertType, String alertName) {
        String phoneNumber = contactManager.getContactPhone();
        String contactName = contactManager.getContactName();

        String message = "🚨 EMERGENCY: " + alertName + " 🚨\n\n" +
                "This is an automated alert from Sentinel.\n" +
                "Please check on me immediately!";

        if (location != null) {
            double latitude = location.getLatitude();
            double longitude = location.getLongitude();
            String locationUrl = "https://maps.google.com/?q=" + latitude + "," + longitude;
            message += "\n\nLocation:\n" + locationUrl;
        } else {
            message += "\n\n(Location unavailable)";
        }

        sendSMS(phoneNumber, message, location != null);

        String notificationText = "Alert sent to " +
                (contactName != null && !contactName.isEmpty() ? contactName : phoneNumber);
        showNotification(alertName + " Alert Sent", notificationText, 3);
    }

    private void sendSMS(String phoneNumber, String message, boolean hasLocation) {
        try {
            SmsManager smsManager;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                int defaultSmsSubscriptionId = SmsManager.getDefaultSmsSubscriptionId();
                if (defaultSmsSubscriptionId != -1) {
                    smsManager = SmsManager.getSmsManagerForSubscriptionId(defaultSmsSubscriptionId);
                } else {
                    smsManager = SmsManager.getDefault();
                }
            } else {
                smsManager = SmsManager.getDefault();
            }

            if (message.length() > 160) {
                ArrayList<String> parts = smsManager.divideMessage(message);
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null);
            } else {
                smsManager.sendTextMessage(phoneNumber, null, message, null, null);
            }
        } catch (Exception e) {
            e.printStackTrace();
            showNotification("SMS Failed",
                    "Failed to send emergency alert: " + e.getMessage(), 4);
        }
    }

    private void showNotification(String title, String content, int notificationId) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(notificationId, builder.build());
        }
    }
}