package com.example.sentinel;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.view.MenuItem;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.drawerlayout.widget.DrawerLayout;

import com.example.core.EmergencyShakeService;
import com.example.data.EmergencyContactManager;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;

import java.util.Objects;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 100;

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

    private EmergencyContactManager contactManager;
    private ActivityResultLauncher<Intent> contactPickerLauncher;
    private boolean isServiceRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        contactManager = new EmergencyContactManager(this);

        // Register contact picker launcher
        contactPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        handleContactSelection(result.getData());
                    }
                });

        initViews();
        checkPermissions();
        updateUI();
    }

    private void initViews() {
        etContactName = findViewById(R.id.et_contact_name);
        etContactPhone = findViewById(R.id.et_contact_phone);
        Button btnPickContact = findViewById(R.id.btn_pick_contact);
        Button btnSaveContact = findViewById(R.id.btn_save_contact);
        Button btnStartService = findViewById(R.id.btn_start_service);
        Button btnStopService = findViewById(R.id.btn_stop_service);
        tvStatus = findViewById(R.id.tv_status);
        statusIndicator = findViewById(R.id.status_indicator);
        contactDisplay = findViewById(R.id.contact_display);
        contactForm = findViewById(R.id.contact_form);
        tvContactNameDisplay = findViewById(R.id.tv_contact_name_display);
        tvContactPhoneDisplay = findViewById(R.id.tv_contact_phone_display);
        ImageButton btnEditContact = findViewById(R.id.btn_edit_contact);

        btnPickContact.setOnClickListener(v -> pickContact());
        btnSaveContact.setOnClickListener(v -> saveContact());
        btnStartService.setOnClickListener(v -> startShakeService());
        btnStopService.setOnClickListener(v -> stopShakeService());
        btnEditContact.setOnClickListener(v -> editContact());

        //initializing drawer
        drawerLayout = findViewById(R.id.drawer_layout);
        NavigationView navigationView = findViewById(R.id.navigation_view);
        //setup action bar toggle
        toggle = new ActionBarDrawerToggle(
                this, drawerLayout, R.string.drawer_open, R.string.drawer_close
        );
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        //handle nav item clicks
        navigationView.setNavigationItemSelectedListener(item -> {
            handleNavigationItemSelected(item);
            drawerLayout.closeDrawers();
            return true;
        });
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
            //already on home
            Toast.makeText(this, "Home", Toast.LENGTH_SHORT).show();
        } else if (id == R.id.nav_settings) {
            Toast.makeText(this, "Settings", Toast.LENGTH_SHORT).show();
            //TODO: open settings activity
        } else if (id == R.id.nav_history) {
            Toast.makeText(this, "Alert History", Toast.LENGTH_SHORT).show();
            //TODO: open history activity
        } else if (id == R.id.nav_help) {
            Toast.makeText(this, "Help", Toast.LENGTH_SHORT).show();
            //TODO: open help activity
        } else if (id == R.id.nav_about) {
            Toast.makeText(this, "About", Toast.LENGTH_SHORT).show();
            //TODO: open  about dialog
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
                    Manifest.permission.ACCESS_COARSE_LOCATION
            };
        } else {
            permissions = new String[]{
                    Manifest.permission.SEND_SMS,
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
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

        Intent serviceIntent = new Intent(this, EmergencyShakeService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        isServiceRunning = true;
        Toast.makeText(this, "Shake detection started", Toast.LENGTH_SHORT).show();
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

            // Show contact display, hide form
            contactDisplay.setVisibility(View.VISIBLE);
            contactForm.setVisibility(View.GONE);

            tvContactNameDisplay.setText(name != null && !name.isEmpty() ? name : "Emergency Contact");
            tvContactPhoneDisplay.setText(phone);

            // Populate form fields (hidden)
            etContactName.setText(name);
            etContactPhone.setText(phone);
        } else {
            // Show form, hide display
            contactDisplay.setVisibility(View.GONE);
            contactForm.setVisibility(View.VISIBLE);
        }

        // Update status
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

    private void signOut() {
        //FIREBASE signout
        FirebaseAuth.getInstance().signOut();

        //google signout
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