package com.example.sentinel;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.core.EmergencyShakeService;
import com.example.data.EmergencyContactManager;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 100;

    private EditText etContactName;
    private EditText etContactPhone;
    private Button btnPickContact;
    private Button btnSaveContact;
    private Button btnStartService;
    private Button btnStopService;
    private TextView tvStatus;

    private EmergencyContactManager contactManager;
    private ActivityResultLauncher<Intent> contactPickerLauncher;

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
        btnPickContact = findViewById(R.id.btn_pick_contact);
        btnSaveContact = findViewById(R.id.btn_save_contact);
        btnStartService = findViewById(R.id.btn_start_service);
        btnStopService = findViewById(R.id.btn_stop_service);
        tvStatus = findViewById(R.id.tv_status);

        btnPickContact.setOnClickListener(v -> pickContact());
        btnSaveContact.setOnClickListener(v -> saveContact());
        btnStartService.setOnClickListener(v -> startShakeService());
        btnStopService.setOnClickListener(v -> stopShakeService());
    }

    private void checkPermissions() {
        String[] permissions;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions = new String[]{
                    Manifest.permission.SEND_SMS,
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.POST_NOTIFICATIONS,
                    Manifest.permission.READ_CONTACTS
            };
        } else {
            permissions = new String[]{
                    Manifest.permission.SEND_SMS,
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.READ_CONTACTS
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
        // Check if READ_CONTACTS permission is granted
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

                    // Clean phone number (remove spaces, dashes, etc.)
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
        String name = etContactName.getText().toString().trim();
        String phone = etContactPhone.getText().toString().trim();

        if (phone.isEmpty()) {
            Toast.makeText(this, "Phone number is required", Toast.LENGTH_SHORT).show();
            return;
        }

        contactManager.saveEmergencyContact(name, phone);
        Toast.makeText(this, "Emergency contact saved", Toast.LENGTH_SHORT).show();
        updateUI();
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
        Toast.makeText(this, "Shake detection started", Toast.LENGTH_SHORT).show();
        updateUI();
    }

    private void stopShakeService() {
        Intent serviceIntent = new Intent(this, EmergencyShakeService.class);
        stopService(serviceIntent);
        Toast.makeText(this, "Shake detection stopped", Toast.LENGTH_SHORT).show();
        updateUI();
    }

    private void updateUI() {
        if (contactManager.hasEmergencyContact()) {
            String name = contactManager.getContactName();
            String phone = contactManager.getContactPhone();
            tvStatus.setText("Emergency Contact: " +
                    (name != null && !name.isEmpty() ? name + " - " : "") + phone);
            etContactName.setText(name);
            etContactPhone.setText(phone);
        } else {
            tvStatus.setText("No emergency contact set");
        }
    }
}