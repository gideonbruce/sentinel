package com.example.core;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
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
import com.example.ml.FallDetectionService;
import com.example.sentinel.MainActivity;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

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
    private BroadcastReceiver screenReceiver;
    private VolumeButtonGestureDetector volumeGestureDetector;
    private AlertRepository alertRepository;
    private String lastPhoneNumber;
    private String lastMessage;
    private String lastEmergencyType;
    private Location lastLocation;
    private SharedPreferences prefs;
    private BroadcastReceiver settingsChangedReceiver;
    private static final long SENSOR_RESTART_DELAY = 5000;
    private Runnable sensorRestartRunnable;
    private final Handler sensorRestartHandler = new Handler(Looper.getMainLooper());
    private FallDetectionService fallDetectionService;
    private boolean isFallDetectionEnabled = false;
    private VoiceDetector voiceDetector;

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("sentinel_prefs", MODE_PRIVATE);
        Log.d("EmergencyService", "=============== Service onCreate ===============");
        contactManager = new EmergencyContactManager(this);
        alertRepository = AlertRepository.getInstance(getApplication());
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        shakeDetector = new ShakeDetector();
        shakeDetector.setSensitivity(getShakeSensitivityThreshold());
        shakeDetector.setOnShakeListener(count -> {
            if (isShakeDetectionEnabled() && count >= getShakeCountRequired()) {
                showEmergencyAlertDialog(null);
            }
        });
        if (isVolumeButtonsEnabled() && Settings.canDrawOverlays(this)) {
            setupOverlayForVolumeDetection();
        }
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "Sentinel::ShakeDetectionWakeLock");
        wakeLock.acquire(24 * 60 * 60 * 1000L);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        startLocationUpdates();

        boolean voiceEnabled = prefs.getBoolean("voice_detection_enabled", false);
        if (voiceEnabled) {
            initializeVoiceDetector();
        }

        volumeGestureDetector = new VolumeButtonGestureDetector(new VolumeButtonGestureDetector.OnVolumeGestureListener() {
            @Override
            public void onSilentEmergency() {showEmergencyAlertDialog("SILENT EMERGENCY");}
            @Override
            public void onPoliceNeeded() {showEmergencyAlertDialog("POLICE NEEDED");}
            @Override
            public void onMedicalEmergency() {showEmergencyAlertDialog("MEDICAL EMERGENCY");}
            @Override
            public void onPanicAlert() {showEmergencyAlertDialog("PANIC ALERT");}
        });

        initializeFallDetection();

        if (Settings.canDrawOverlays(this)) {
            setupOverlayForVolumeDetection();
        }
        if (isShakeDetectionEnabled()) {
            setupSensorReregistration();
        }
        registerScreenReceiver();

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

                    //sendEmergencySMS(location, emergencyType);
                    saveAlertToDatabase(emergencyType, location);
                }
            }
        };

        IntentFilter smsFilter = new IntentFilter("com.example.sentinel.SEND_EMERGENCY_SMS");
        registerReceiver(smsConfirmReceiver, smsFilter, Context.RECEIVER_NOT_EXPORTED);
        IntentFilter filter = new IntentFilter("com.example.sentinel.VOLUME_BUTTON_EVENT");
        registerReceiver(volumeButtonReceiver, filter, Context.RECEIVER_NOT_EXPORTED);

        settingsChangedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if ("com.example.sentinel.SETTINGS_CHANGED".equals(intent.getAction())) {
                    if (shakeDetector != null) {
                        shakeDetector.setSensitivity(getShakeSensitivityThreshold());
                    }
                    boolean fallDetectionEnabled = prefs.getBoolean("fall_detection_enabled", false);
                    if (fallDetectionEnabled && !isFallDetectionEnabled) {
                        initializeFallDetection();
                    } else if (!fallDetectionEnabled && isFallDetectionEnabled) {
                        if (fallDetectionService != null) {
                            fallDetectionService.stop();
                            fallDetectionService.cleanup();
                            fallDetectionService = null;
                        }
                        isFallDetectionEnabled = false;
                    }
                    Log.d("EmergencyService", "Settings updated - shake sensitivity: " + getShakeSensitivityThreshold() + ", fall detection: " + isFallDetectionEnabled);

                    boolean voiceDetectionEnabled = prefs.getBoolean("voice_detection_enabled", false);
                    if (voiceDetectionEnabled && voiceDetector == null) {
                        initializeVoiceDetector();
                    } else if (!voiceDetectionEnabled && voiceDetector != null) {
                        voiceDetector.stop();
                        voiceDetector = null;
                    }
                }
            }
        };

        IntentFilter settingsFilter = new IntentFilter("com.example.sentinel.SETTINGS_CHANGED");
        registerReceiver(settingsChangedReceiver, settingsFilter, Context.RECEIVER_NOT_EXPORTED);
    }

    private void initializeFallDetection() {
        isFallDetectionEnabled = prefs.getBoolean("fall_detection_enabled", false);
        if (isFallDetectionEnabled) {
            try {
                fallDetectionService = new FallDetectionService(this);
                fallDetectionService.initialize();
                fallDetectionService.setOnFallDetectedCallback(result -> {
                    Log.d("EmergencyService", "Fall detected by ML model   -   confidence: " +
                            (result.getConfidence() * 100) + "%");
                    showEmergencyAlertDialog("FALL DETECTED");
                });
                fallDetectionService.start();
                Log.i("EmergencyService", "Fall detection initialized and started");
            } catch (Exception e) {
                Log.e("EmergencyService", "Failed to initialize fall detection", e);
                isFallDetectionEnabled = false;
            }
        }
    }

    private void initializeVoiceDetector() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w("EmergencyService", "RECORD_AUDIO permission not granted — voice detection skipped");
            return;
        }
        try {
            voiceDetector = new VoiceDetector(this, emergencyType -> {
                Log.i("EmergencyService", "Voice emergency detected: " + emergencyType);
                showEmergencyAlertDialog(emergencyType);
            });
            voiceDetector.start();
            Log.i("EmergencyService", "Voice detector started");
        } catch (Exception e) {
            Log.e("EmergencyService", "Failed to start voice detector: " + e.getMessage());
        }
    }

    private void reregisterSensor() {
        if (sensorManager != null && accelerometer != null && shakeDetector != null && isShakeDetectionEnabled()) {
            sensorManager.unregisterListener(shakeDetector);
            boolean registered = sensorManager.registerListener(shakeDetector, accelerometer,
                    SensorManager.SENSOR_DELAY_GAME);
            Log.d("EmergencyService", "Sensor re-registered due to screen state change: " + registered);
        }
    }

    private void refreshWakeLock() {
        if (wakeLock != null) {
            if (wakeLock.isHeld()) {
                wakeLock.release();
            }
            wakeLock.acquire(24 * 60 * 60 * 1000L);
            Log.d("EmergencyService", "Wake lock refreshed");
        }
    }

    private void setupSensorReregistration() {
        sensorRestartRunnable = new Runnable() {
            @Override
            public void run() {
                // Check if sensor is still registered and re-register if needed
                if (isShakeDetectionEnabled()) {
                    if (sensorManager != null && accelerometer != null && shakeDetector != null) {
                        sensorManager.unregisterListener(shakeDetector);
                        boolean registered = sensorManager.registerListener(shakeDetector, accelerometer,
                                SensorManager.SENSOR_DELAY_GAME);
                        Log.d("EmergencyService", "Periodic sensor re-registration: " + registered);
                    }
                }
                // Schedule next check
                sensorRestartHandler.postDelayed(this, 60000); // Check every minute
            }
        };

        // Start periodic checks after 1 minute
        sensorRestartHandler.postDelayed(sensorRestartRunnable, 60000);
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

    private boolean isShakeDetectionEnabled() {
        return prefs.getBoolean("shake_detection_enabled", true);
    }

    private boolean isVolumeButtonsEnabled() {
        return prefs.getBoolean("volume_buttons_enabled", true);
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

    private float getShakeSensitivityThreshold() {
        int sensitivity = prefs.getInt("shake_sensitivity", 2); // 0-4 scale

        // Convert to acceleration threshold
        // Lower sensitivity = higher threshold (harder to trigger)
        // Higher sensitivity = lower threshold (easier to trigger)
        switch (sensitivity) {
            case 0: return 3.5f; // Very Low - hardest to trigger
            case 1: return 3.0f; // Low
            case 2: return 2.5f; // Medium (default)
            case 3: return 2.0f; // High
            case 4: return 1.5f; // Very High - easiest to trigger
            default: return 2.5f;
        }
    }

    private int getShakeCountRequired() {
        int sensitivity = prefs.getInt("shake_sensitivity", 2); // 0-4 scale

        // Convert to required shake count
        // Higher sensitivity = fewer shakes needed
        switch (sensitivity) {
            case 0: return 4; // Very Low - needs 4 shakes
            case 1: return 4; // Low - needs 4 shakes
            case 2: return 3; // Medium (default) - needs 3 shakes
            case 3: return 2; // High - needs 2 shakes
            case 4: return 2; // Very High - needs 2 shakes
            default: return 3;
        }
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
        if (volumeGestureDetector == null || !isVolumeButtonsEnabled()) {
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
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Sentinel Emergency")
                .setContentText("Emergency detection is active")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
        startForeground(NOTIFICATION_ID, notification);
        if (sensorManager != null && shakeDetector != null) {
            sensorManager.unregisterListener(shakeDetector);
        }
        // Register sensor listener
        if (accelerometer != null && isShakeDetectionEnabled()) {
            sensorManager.registerListener(shakeDetector, accelerometer,
                    SensorManager.SENSOR_DELAY_GAME);
            //Log.d("EmergencyService", "Sensor registration " + (registered ? "successful" : "failed"));
        }
        initializeFallDetection();

        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // Restart the service if task is removed
        Intent restartServiceIntent = new Intent(getApplicationContext(), this.getClass());
        PendingIntent restartPendingIntent = PendingIntent.getService(
                getApplicationContext(), 1, restartServiceIntent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager alarmService = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        alarmService.set(AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + 1000, restartPendingIntent);
        super.onTaskRemoved(rootIntent);
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
                Priority.PRIORITY_HIGH_ACCURACY, 60000) // Update every 60 seconds
                .setMinUpdateIntervalMillis(30000) // minimum 30 seconds
                .setMaxUpdateDelayMillis(120000)  // batch updates for better battery
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

    private void registerScreenReceiver() {
        screenReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction() != null) {
                    if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                        Log.d("EmergencyService", "Screen OFF - ensuring sensor registration");
                        reregisterSensor();
                    } else if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
                        Log.d("EmergencyService", "Screen ON - ensuring sensor registration");
                        reregisterSensor();
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(screenReceiver, filter);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d("EmergencyService", "=== Service onDestroy ===");
        // unregisters settings receiver
        if (settingsChangedReceiver != null) {
            try {
                unregisterReceiver(settingsChangedReceiver);
            } catch (Exception e) {
                Log.e("EmergencyService", "Error unregistering settings receiver", e);
            }
        }
        if (fallDetectionService != null) {
            fallDetectionService.cleanup();
            fallDetectionService = null;
        }
        if (sensorRestartHandler != null && sensorRestartRunnable != null) {
            sensorRestartHandler.removeCallbacks(sensorRestartRunnable);
        }
        if (screenReceiver != null) {
            try {
                unregisterReceiver(screenReceiver);
            } catch (Exception e) {
                Log.e("EmergencyService", "Error unregistering screen receiver", e);
            }
        }
        if (volumeGestureDetector != null) {
            volumeGestureDetector.cleanup();
        }
        if (sensorManager != null && shakeDetector != null) {
            sensorManager.unregisterListener(shakeDetector);
        }
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        if (volumeButtonReceiver != null) {
            unregisterReceiver(volumeButtonReceiver);
        }
        if (smsConfirmReceiver != null) {
            unregisterReceiver(smsConfirmReceiver);
        }
        if (voiceDetector != null) {
            voiceDetector.stop();
            voiceDetector = null;
        }
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
