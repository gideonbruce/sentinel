package com.example.sentinel;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.view.MenuItem;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.view.KeyEvent;
import android.telephony.SmsManager;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.drawerlayout.widget.DrawerLayout;
import com.bumptech.glide.Glide;
import com.example.core.EmergencyShakeService;
import com.example.data.EmergencyContactManager;
import com.example.ui.EmergencyAlertDialog;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.FirebaseDatabase;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 100;
    private BroadcastReceiver smsSentReceiver;
    private BroadcastReceiver smsDeliveredReceiver;
    private TextInputEditText etContactName;
    private TextInputEditText etContactPhone;
    private TextView tvStatus;
    private View statusIndicator;
    private LinearLayout contactDisplay;
    private LinearLayout contactForm;
    private TextView tvContactNameDisplay;
    private TextView tvContactPhoneDisplay;
    private DrawerLayout drawerLayout;
    private ActionBarDrawerToggle toggle;
    private TextView tvUserName;
    private TextView tvUserEmail;
    private ImageView ivUserProfile;
    private FirebaseAuth mAuth;
    private EmergencyContactManager contactManager;
    private ActivityResultLauncher<Intent> contactPickerLauncher;
    private boolean isServiceRunning = false;
    private android.location.Location currentEmergencyLocation;
    private static final int BACKGROUND_LOCATION_PERMISSION_CODE = 101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        FirebaseApp.initializeApp(this);

        //offline persistence
        try {
            FirebaseDatabase.getInstance().setPersistenceEnabled(true);
        } catch (Exception e) {
            //persistence already enabled
        }
        //FirebaseDatabase database = FirebaseDatabase.getInstance();

        String databaseUrl = "https://sentinel-7b6b4-default-rtdb.asia-southeast1.firebasedatabase.app";
        FirebaseDatabase database = FirebaseDatabase.getInstance(databaseUrl);
        contactManager = new EmergencyContactManager(this);
        registerSmsReceivers();
        contactPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        handleContactSelection(result.getData());
                    }
                });
        initViews();
        loadUserProfile();
        checkPermissions();
        //updateUI();
        handleEmergencyDialogIntent(getIntent());
        autoStartService();
    }

    private void initViews() {
        etContactName = findViewById(R.id.et_contact_name);
        etContactPhone = findViewById(R.id.et_contact_phone);
        Button btnPickContact = findViewById(R.id.btn_pick_contact);
        Button btnSaveContact = findViewById(R.id.btn_save_contact);
        //Button btnStartService = findViewById(R.id.btn_start_service);
        tvStatus = findViewById(R.id.tv_status);
        statusIndicator = findViewById(R.id.status_indicator);
        contactDisplay = findViewById(R.id.contact_display);
        contactForm = findViewById(R.id.contact_form);
        tvContactNameDisplay = findViewById(R.id.tv_contact_name_display);
        tvContactPhoneDisplay = findViewById(R.id.tv_contact_phone_display);
        ImageButton btnEditContact = findViewById(R.id.btn_edit_contact);
        btnPickContact.setOnClickListener(v -> pickContact());
        btnSaveContact.setOnClickListener(v -> saveContact());
        //btnStartService.setOnClickListener(v -> startShakeService());
        btnEditContact.setOnClickListener(v -> editContact());
        drawerLayout = findViewById(R.id.drawer_layout);
        NavigationView navigationView = findViewById(R.id.navigation_view);
        mAuth = FirebaseAuth.getInstance();
        View headerView = navigationView.getHeaderView(0);
        tvUserName = headerView.findViewById(R.id.tv_user_name);
        tvUserEmail = headerView.findViewById(R.id.tv_user_email);
        ivUserProfile = headerView.findViewById(R.id.iv_user_profile);
        toggle = new ActionBarDrawerToggle(this, drawerLayout, R.string.drawer_open, R.string.drawer_close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        navigationView.setNavigationItemSelectedListener(item -> {
            handleNavigationItemSelected(item);
            drawerLayout.closeDrawers();
            return true;
        });
    }

    private boolean isShakeDetectionEnabled() {
        SharedPreferences prefs = getSharedPreferences("sentinel_prefs", MODE_PRIVATE);
        return prefs.getBoolean("shake_detection_enabled", true);
    }
    private boolean isVolumeButtonsEnabled() {
        SharedPreferences prefs = getSharedPreferences("sentinel_prefs", MODE_PRIVATE);
        return prefs.getBoolean("volume_buttons_enabled", true);
    }

    @SuppressLint("SetTextI18n")
    private void loadUserProfile() {
        com.google.firebase.auth.FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            String displayName = currentUser.getDisplayName();
            if (displayName != null && !displayName.isEmpty()) {
                tvUserName.setText(displayName);
            } else {
                tvUserName.setText("User");
            }
            String email = currentUser.getEmail();
            if (email != null && !email.isEmpty()) {
                tvUserEmail.setText(email);
            } else {
                tvUserEmail.setText("No email");
            }
            Uri photoUrl = currentUser.getPhotoUrl();
            if (photoUrl != null) {
                // loading google profile picture using Glide
                Glide.with(this)
                        .load(photoUrl)
                        .placeholder(R.drawable.ic_user_placeholder)
                        .error(R.drawable.ic_user_placeholder)
                        .circleCrop()
                        .into(ivUserProfile);
            } else {
                ivUserProfile.setImageResource(R.drawable.ic_user_placeholder);
            }
            // Reinitialize Firebase reference for emergency contact when user changes
            contactManager.reinitializeFirebase();
            // load local contact immediately
            updateUI();

            //then sync firebase in background
            contactManager.loadFromFirebase((name, phone) -> {
                runOnUiThread(() -> {
                    updateUI(); //update ui again if firebase has different data
                    if (name != null && phone != null) {
                        Log.d("MainActivity", "Contact loaded: " + name);
                    }
                });
            });
            contactManager.loadEmergencyMessageFromFirebase(message -> {
                Log.d("MainActivity", "Emergency message loaded: " + message);
            });
        } else {
            // No user logged in, redirect to login
            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        }
    }

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            //
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {
            EmergencyShakeService serviceInstance = null;
            boolean isBound = false;
        }
    };

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (isServiceRunning && isVolumeButtonsEnabled() && (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
                                 keyCode == KeyEvent.KEYCODE_VOLUME_UP)) {
            // sending broadcast to service
            Intent intent = new Intent("com.example.sentinel.VOLUME_BUTTON_EVENT");
            intent.putExtra("keyCode", keyCode);
            intent.putExtra("isKeyDown", true);
            sendBroadcast(intent);
            return true;  // consume event
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public  boolean onKeyUp(int keyCode, KeyEvent event) {
        if (isServiceRunning && isVolumeButtonsEnabled() &&(keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP)) {
            //send broadcast to service
            Intent intent = new Intent("com.example.sentinel.VOLUME_BUTTON_EVENT");
            intent.putExtra("keyCode", keyCode);
            intent.putExtra("isKeyDown", false);
            sendBroadcast(intent);
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (toggle.onOptionsItemSelected(item)) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void handleNavigationItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.nav_home) {
        } else if (id == R.id.nav_settings) {
            //TODO: open settings activity
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
        } else if (id == R.id.nav_history) {
            //TODO: open history activity
            Intent intent = new Intent(this, AlertHistoryActivity.class);
            startActivity(intent);
        } else if (id == R.id.nav_help) {
            //TODO: open help activity
            Intent intent = new Intent(this, HelpActivity.class);
            startActivity(intent);
        } else if (id == R.id.nav_about) {
            //TODO: open  about dialog
            Intent intent = new Intent(this, AboutActivity.class);
            startActivity(intent);
        } else if (id == R.id.nav_sign_out) {
            signOut();
        }
    }

    private void checkPermissions() {
        String[] permissions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions = new String[]{
                    Manifest.permission.SEND_SMS,
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.POST_NOTIFICATIONS,
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.READ_SMS
                    //Manifest.permission.ACCESS_BACKGROUND_LOCATION
            };
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions = new String[]{
                    Manifest.permission.SEND_SMS,
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.READ_SMS
                    //Manifest.permission.ACCESS_BACKGROUND_LOCATION
            };
        } else {
            permissions = new String[]{
                    Manifest.permission.SEND_SMS,
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.READ_SMS
            };
        }
        boolean allGranted = true;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }
        if (!allGranted) {
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            checkBackgroundLocationPermission();
        }
    }

    private void requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            //shows explanation dialog
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Overlay Permission Needed")
                    .setMessage("To detect volume button gestures for emergency alerts, this app needs permission to display over other apps. This allows volume button detection to work even when the app is in the background.")
                    .setPositiveButton("Grant Permission", (dialog, which) -> {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + getPackageName()));
                        startActivityForResult(intent, 1234);
                    })
                    .setNegativeButton("Skip", (dialog, which) -> {
                        Toast.makeText(this,
                                "Volume button gestures will not work without overlay permission",
                                Toast.LENGTH_LONG).show();
                    })
                    .show();
        }
    }

    private void checkLocationSettings() {
        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
                .build();
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest);
        Task<LocationSettingsResponse> task = LocationServices.getSettingsClient(this)
                .checkLocationSettings(builder.build());
        task.addOnSuccessListener(this, locationSettingsResponse -> {
            // Location settings are satisfied, start service
        });
        task.addOnFailureListener(this, e -> {
            if (e instanceof ResolvableApiException) {
                try {
                    // Showing dialog to enable location
                    ResolvableApiException resolvable = (ResolvableApiException) e;
                    resolvable.startResolutionForResult(this, 1001);
                } catch (Exception sendEx) {
                    Toast.makeText(this, "Please enable location services",
                            Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1234) {
            if (Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Overlay permission granted - Volume gestures enabled",
                        Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Overlay permission denied - Volume gestures won't work",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private void pickContact() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Please grant contacts permission", Toast.LENGTH_SHORT).show();
            checkPermissions();
            return;
        }
        Intent contactPickerIntent = new Intent(Intent.ACTION_PICK,
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
        contactPickerLauncher.launch(contactPickerIntent);
    }

    private void handleContactSelection(Intent data) {
        Uri contactUri = data.getData();
        if (contactUri == null) {
            return;
        }
        String[] projection = {
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
        };
        try (Cursor cursor = getContentResolver().query(
                contactUri, projection, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                int numberIndex = cursor.getColumnIndex(
                        ContactsContract.CommonDataKinds.Phone.NUMBER);
                if (nameIndex >= 0 && numberIndex >= 0) {
                    String name = cursor.getString(nameIndex);
                    String phoneNumber = cursor.getString(numberIndex);
                    phoneNumber = phoneNumber.replaceAll("[\\s()-]", "");
                    etContactName.setText(name);
                    etContactPhone.setText(phoneNumber);
                    Toast.makeText(this, "Contact selected: " + name,
                            Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error reading contact: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    private void saveContact() {
        String name = Objects.requireNonNull(etContactName.getText()).toString().trim();
        String phone = Objects.requireNonNull(etContactPhone.getText()).toString().trim();
        if (phone.isEmpty()) {
            Toast.makeText(this, "Phone number is required", Toast.LENGTH_SHORT).show();
            return;
        }
        contactManager.saveEmergencyContact(name, phone);
        Toast.makeText(this, "Emergency contact saved", Toast.LENGTH_SHORT).show();
        updateUI();
        autoStartService();
    }

    private void editContact() {
        contactDisplay.setVisibility(View.GONE);
        contactForm.setVisibility(View.VISIBLE);
    }

    private void startShakeService() {
        if (!contactManager.hasEmergencyContact()) {
            Toast.makeText(this, "Please set emergency contact first",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        // checking if atleast one method is enabled
        boolean isFallDetectionEnabled = getSharedPreferences("sentinel_prefs", MODE_PRIVATE).getBoolean("fall_detection_enabled", false);
        if (!isShakeDetectionEnabled() && !isVolumeButtonsEnabled() && !isFallDetectionEnabled) {
            Toast.makeText(this, "Please enable atleast one gesture method in settings", Toast.LENGTH_LONG).show();
            //opening settings
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            return;
        }
        //checking location permission first
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Location permission required for emergency alerts", Toast.LENGTH_SHORT).show();
            checkPermissions();
            return;
        }

        checkLocationSettings();

        //overlay only for volume gestures
        if (isVolumeButtonsEnabled()) {
            requestOverlayPermission();
        }
        //requestOverlayPermission(); //for overlay buttons

        Intent serviceIntent = new Intent(this, EmergencyShakeService.class);
        startForegroundService(serviceIntent);
        isServiceRunning = true;

        // showing active gestures
        StringBuilder methods = new StringBuilder();
        int count = 0;
        if (isShakeDetectionEnabled()) {
            methods.append("Shake");
            count++;
        }
        if (isVolumeButtonsEnabled()) {
            if (count > 0) methods.append(" & ");
            methods.append("Volume gestures");
            count++;
        }
        if (isFallDetectionEnabled) {
            if (count > 0) methods.append(" & ");
            methods.append("Fall detection");
            count++;
        }
        methods.append(" started");
        Toast.makeText(this, methods, Toast.LENGTH_SHORT).show();
        updateUI();
    }

    private void stopShakeService() {
        Intent serviceIntent = new Intent(this, EmergencyShakeService.class);
        stopService(serviceIntent);
        isServiceRunning = false;
        Toast.makeText(this, "Shake detection stopped", Toast.LENGTH_SHORT).show();
        updateUI();
    }

    private void updateUI() {
        if (contactManager.hasEmergencyContact()) {
            String name = contactManager.getContactName();
            String phone = contactManager.getContactPhone();
            contactDisplay.setVisibility(View.VISIBLE);
            contactForm.setVisibility(View.GONE);
            tvContactNameDisplay.setText(name != null && !name.isEmpty() ? name : "Emergency Contact");
            tvContactPhoneDisplay.setText(phone);
            etContactName.setText(name);
            etContactPhone.setText(phone);
        } else {
            contactDisplay.setVisibility(View.GONE);
            contactForm.setVisibility(View.VISIBLE);
        }
        updateStatusIndicator();
    }

    private void updateStatusIndicator() {
        GradientDrawable drawable = (GradientDrawable) statusIndicator.getBackground();
        if (isServiceRunning && contactManager.hasEmergencyContact()) {
            drawable.setColor(Color.parseColor("#4CAF50"));
            tvStatus.setText(R.string.running);
        } else if (contactManager.hasEmergencyContact()) {
            drawable.setColor(Color.parseColor("#FF9800"));
            tvStatus.setText(R.string.ready);
        } else {
            drawable.setColor(Color.parseColor("#BDBDBD"));
            tvStatus.setText(R.string.set_emergency);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                Toast.makeText(this, "All permissions granted", Toast.LENGTH_SHORT).show();
                sendBroadcast(new Intent("com.example.sentinel.SETTINGS_CHANGED"));
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    checkBackgroundLocationPermission();
                }
            } else {
                Toast.makeText(this, "Some permissions were denied. App may not work properly",
                        Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == BACKGROUND_LOCATION_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Background location permission granted",
                        Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this,
                        "Background location denied. Emergency alerts may not include location when app is closed",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private void checkBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Background Location Permission")
                        .setMessage("For emergency alerts to work properly when the app is in the background, " +
                                "we need access to your location all the time.\n\n" +
                                "Please select 'Allow all the time' in the next screen.")
                        .setPositiveButton("Continue", (dialog, which) -> {
                            ActivityCompat.requestPermissions(this,
                                    new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                                    BACKGROUND_LOCATION_PERMISSION_CODE);
                        })
                        .setNegativeButton("Skip", (dialog, which) -> {
                            Toast.makeText(this,
                                    "Location may not work in background without this permission",
                                    Toast.LENGTH_LONG).show();
                        })
                        .show();
            }
        }
    }

    @Override
    protected  void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleEmergencyDialogIntent(intent);
    }

    private void handleEmergencyDialogIntent(Intent intent) {
        if (intent != null && intent.getBooleanExtra("SHOW_EMERGENCY_DIALOG", false)) {
            String emergencyType = intent.getStringExtra("EMERGENCY_TYPE");
            SharedPreferences prefs = getSharedPreferences("sentinel_prefs", MODE_PRIVATE);
            boolean useAI = prefs.getBoolean("use_ai_messages", false);
            currentEmergencyLocation = intent.getParcelableExtra("LOCATION");
            EmergencyAlertDialog.show(this, new EmergencyAlertDialog.OnAlertActionListener() {
                @Override
                public void onAlertSent() {
                    sendEmergencyAlertToService(emergencyType, currentEmergencyLocation);
                }
                @Override
                public void onAlertCancelled() {
                    Toast.makeText(MainActivity.this, "Emergency alert cancelled",
                            Toast.LENGTH_SHORT).show();
                }
            }, currentEmergencyLocation, emergencyType, useAI);
        }
    }

    private void registerSmsReceivers() {
        smsSentReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int resultCode = getResultCode();
                Log.d("MainActivity", "SMS Sent result code: " + resultCode);
                Log.d("MainActivity", "Result code: " + resultCode);
                switch (resultCode) {
                    case android.app.Activity.RESULT_OK:
                        Log.d("MainActivity", "SMS sent successfully!");
                        Toast.makeText(context, "Emergency alert sent!", Toast.LENGTH_SHORT).show();
                        break;
                    case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                        Log.e("MainActivity", "SMS generic failure");
                        Toast.makeText(context, "Generic error",
                                Toast.LENGTH_LONG).show();
                        break;
                    case SmsManager.RESULT_ERROR_NO_SERVICE:
                        Log.e("MainActivity", "SMS failed - No service");
                        Toast.makeText(context, "Failed - No cellular service",
                                Toast.LENGTH_LONG).show();
                        break;
                    case SmsManager.RESULT_ERROR_RADIO_OFF:
                        Log.e("MainActivity", "SMS failed - Radio off");
                        Toast.makeText(context, "Failed - Airplane mode?",
                                Toast.LENGTH_LONG).show();
                        break;
                }
            }
        };
        smsDeliveredReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int resultCode = getResultCode();
                Log.d("MainActivity", "SMS Delivery result code: " + resultCode);
                if (resultCode == android.app.Activity.RESULT_OK) {
                    Log.d("MainActivity", "SMS delivered successfully!");
                }
            }
        };
        IntentFilter sentFilter = new IntentFilter("SMS_SENT");
        IntentFilter deliveredFilter = new IntentFilter("SMS_DELIVERED");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(smsSentReceiver, sentFilter, Context.RECEIVER_NOT_EXPORTED);
            registerReceiver(smsDeliveredReceiver, deliveredFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(smsSentReceiver, sentFilter, Context.RECEIVER_NOT_EXPORTED);
            registerReceiver(smsDeliveredReceiver, deliveredFilter, Context.RECEIVER_NOT_EXPORTED);
        }
        Log.d("MainActivity", "SMS broadcast receivers registered");
    }

    private void sendEmergencyAlertToService(String emergencyType, android.location.Location location) {
        Log.d("MainActivity", "Alert confirmed by user, SMS already sent by dialog");
        // SMS is already sent by EmergencyAlertDialog.sendEmergencyAlert()
        // Send broadcast to service to actually send the SMS
        //Intent intent = new Intent("com.example.sentinel.SEND_EMERGENCY_SMS");
        //intent.putExtra("EMERGENCY_TYPE", emergencyType);
        //intent.putExtra("LOCATION", location);
        //sendBroadcast(intent);
        saveAlertToDatabase(emergencyType, location);
    }

    private void saveAlertToDatabase(String emergencyType, android.location.Location location) {
        com.example.data.AlertRepository alertRepository = com.example.data.AlertRepository.getInstance(getApplication());
        String alertType = (emergencyType != null) ? emergencyType : "EMERGENCY";
        long timestamp = System.currentTimeMillis();
        Double latitude = (location != null) ? location.getLatitude() : null;
        Double longitude = (location != null) ? location.getLongitude() : null;
        String contactName = contactManager.getContactName();
        String contactPhone = contactManager.getContactPhone();
        boolean locationAvailable = (location != null);
        com.example.data.AlertEntity alert = new com.example.data.AlertEntity(
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
                Log.d("MainActivity", "Alert saved to database with key: " + firebaseKey);
                Toast.makeText(MainActivity.this, "Alert logged", Toast.LENGTH_SHORT).show();
            } else {
                Log.e("MainActivity", "Failed to save alert to database");
            }
        });
    }

    private void autoStartService() {
        if (!contactManager.hasEmergencyContact()) return;
        Intent serviceIntent = new Intent(this, EmergencyShakeService.class);
        startForegroundService(serviceIntent);
        isServiceRunning = true;
        updateUI();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EmergencyAlertDialog.cleanup();
        if (smsSentReceiver != null) {
            try {
                unregisterReceiver(smsSentReceiver);
            } catch (Exception e) {
                Log.e("MainActivity", "Error unregistering smsSentReceiver", e);
            }
        }
        if (smsDeliveredReceiver != null) {
            try {
                unregisterReceiver(smsDeliveredReceiver);
            } catch (Exception e) {
                Log.e("MainActivity", "Error unregistering smsDeliveredReceiver", e);
            }
        }
    }

    private void signOut() {
        contactManager.clearEmergencyContactLocal();
        FirebaseAuth.getInstance().signOut();
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        GoogleSignInClient googleSignInClient = GoogleSignIn.getClient(this, gso);
        googleSignInClient.signOut().addOnCompleteListener(this, task -> {
            //navigate back to login
            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }
}
