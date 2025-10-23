package com.example.sentinel;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.core.EmergencyShakeService;
import com.example.data.EmergencyContactManager;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 100;

    private EditText etContactName;
    private EditText etContactPhone;
    private Button btnSaveContact;
    private Button btnStartService;
    private Button btnStopService;
    private TextView tvStatus;

    private EmergencyContactManager contactManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        contactManager = new EmergencyContactManager(this);

        initViews();
        checkPermissions();
        updateUI();
    }

    private void initViews() {
        etContactName = findViewById(R.id.et_contact_name);
        etContactPhone = findViewById(R.id.et_contact_phone);
        btnSaveContact = findViewById(R.id.btn_save_contact);
        btnStartService = findViewById(R.id.btn_start_service);
        btnStopService = findViewById(R.id.btn_stop_service);
        tvStatus = findViewById(R.id.tv_status);

        btnSaveContact.setOnClickListener(v -> saveContact());
        btnStartService.setOnClickListener(v -> startShakeService());
        btnStopService.setOnClickListener(v -> stopShakeService());
    }

    private void checkPermissions() {
        String[] permissions = {
                Manifest.permission.SEND_SMS,
                Manifest.permission.READ_PHONE_STATE
        };

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
        startService(serviceIntent);
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