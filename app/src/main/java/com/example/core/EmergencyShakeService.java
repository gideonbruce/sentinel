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
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.telephony.SmsManager;
import android.os.Looper;
import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.location.LocationManager;
import android.view.KeyEvent;

import androidx.core.app.NotificationCompat;
import androidx.core.app.ActivityCompat;

import com.example.data.EmergencyContactManager;
import com.example.sentinel.MainActivity;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

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
    private BroadcastReceiver volumeButtonReceiver;

    private VolumeButtonGestureDetector volumeGestureDetector;

    @Override
    public void onCreate() {
        super.onCreate();

        contactManager = new EmergencyContactManager(this);

        // Initialize shake detection
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        shakeDetector = new ShakeDetector();

        shakeDetector.setOnShakeListener(count -> {
            if (count >= 3) {
                //sendEmergencySMS();
                getLocationAndSendSMS();
            }
        });

        volumeGestureDetector = new VolumeButtonGestureDetector(new VolumeButtonGestureDetector.OnVolumeGestureListener() {
            @Override
            public void onSilentEmergency() {
                getLocationAndSendSMS("SILENT EMERGENCY");
            }

            @Override
            public void onPoliceNeeded() {
                getLocationAndSendSMS("POLICE NEEDED");
            }

            @Override
            public void onMedicalEmergency() {
                getLocationAndSendSMS("MEDICAL EMERGENCY");
            }

            @Override
            public void onPanicAlert() {
                getLocationAndSendSMS("PANIC ALERT");
            }
        });

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

        IntentFilter filter = new IntentFilter("com.example.sentinel.VOLUME_BUTTON_EVENT");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(volumeButtonReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(volumeButtonReceiver, filter);
        }

        // Acquire wake lock to keep CPU running
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "Sentinel::ShakeDetectionWakeLock");
        wakeLock.acquire();

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        startLocationUpdates();
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

    public boolean handleVolumeButtonEvent(int keyCode, boolean isKeyDown) {
        if (volumeGestureDetector == null) {
            return false;
        }

        if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (isKeyDown) {
                return volumeGestureDetector.onVolumeDown();
            } else {
                return volumeGestureDetector.onVolumeUp();
            }
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP && isKeyDown) {
            return volumeGestureDetector.onVolumeUpButton();
        }
        return false;
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        LocationRequest locationRequest = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, 30000) // Update every 30 seconds
                .setMinUpdateIntervalMillis(15000) // Fastest update every 15 seconds
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

        // Also get last known location immediately
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        lastKnownLocation = location;
                    }
                });
    }
    private void getLocationAndSendSMS() {
        getLocationAndSendSMS(null);
    }

    private void getLocationAndSendSMS(String emergencyType) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
            sendEmergencySMS(null, emergencyType);
            return;
        }

        // Try to get fresh location first
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        sendEmergencySMS(location, emergencyType);
                    } else if (lastKnownLocation != null) {
                        sendEmergencySMS(lastKnownLocation, emergencyType);
                    } else {
                        sendEmergencySMS(null, emergencyType);
                    }
                })
                .addOnFailureListener(e -> {
                    // Fall back to last known location or send without location
                    sendEmergencySMS(lastKnownLocation, emergencyType);
                });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (volumeButtonReceiver != null) {
            unregisterReceiver(volumeButtonReceiver);
        }

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
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Emergency Shake Detection",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Monitors shake gestures for emergency alerts");

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void sendEmergencySMS(Location location, String emergencyType) {
        if (!contactManager.hasEmergencyContact()) {
            return;
        }

        String phoneNumber = contactManager.getContactPhone();
        String message = emergencyType != null ? emergencyType + "EMERGENCY! This is an automated alert. Please check on me immediately.";

        if (location != null) {
            double latitude = location.getLatitude();
            double longitude = location.getLongitude();
            String locationUrl = "https://maps.google.com/?q="+ latitude + "," + longitude;
            message += "\n\nMy location:\nLat: " + latitude + "\nLong: " + longitude + "\nMap: " + locationUrl;
        } else {
            message += "\n\n(Location unavalable)";
        }
        try {
            SmsManager smsManager;
            // Handle dual SIM devices
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

            //split message if its too long
            if (message.length() > 160) {
                ArrayList<String> parts = smsManager.divideMessage(message);
                smsManager.sendMultipartTextMessage(phoneNumber, null, smsManager.divideMessage(message), null, null);
            } else {
                smsManager.sendTextMessage(phoneNumber, null, message, null, null);
            }
            //smsManager.sendTextMessage(phoneNumber, null, message, null, null);

            // Show notification that SMS was sent
            showSMSSentNotification(location != null);
        } catch (Exception e) {
            e.printStackTrace();
            showSMSFailedNotification();
        }
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