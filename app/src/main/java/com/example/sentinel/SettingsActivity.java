package com.example.sentinel;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import com.example.data.EmergencyContactManager;
import com.google.android.material.textfield.TextInputEditText;
import java.util.Objects;

public class SettingsActivity extends AppCompatActivity {
    private EmergencyContactManager contactManager;
    private SharedPreferences prefs;
    private ActivityResultLauncher<Intent> contactPickerLauncher;
    private TextView tvContactNameDisplay;
    private TextView tvContactPhoneDisplay;
    private LinearLayout contactDisplay;
    private LinearLayout contactForm;
    private TextInputEditText etContactName;
    private TextInputEditText etContactPhone;
    private TextInputEditText etEmergencyMessage;
    private TextView tvCharCount;
    private Button btnResetMessage;
    private Switch switchShakeDetection;
    private Switch switchVolumeButtons;
    private Switch switchVibration;
    private Switch switchSound;
    private Switch switchLocationSharing;
    private Switch switchAIMessages;
    private Switch switchAutoSend;
    private Switch switchVoiceDetection;
    private SeekBar seekShakeSensitivity;
    private TextView tvSensitivityValue;
    private TextView tvCountdownValue;
    private SeekBar seekCountdown;
    private SwitchCompat switchFallDetection;
    private LinearLayout contact2Display;
    private LinearLayout contact2Form;
    private TextView tvContact2NameDisplay;
    private TextView tvContact2PhoneDisplay;
    private TextInputEditText etContact2Name;
    private TextInputEditText etContact2Phone;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Settings");
        }
        contactManager = new EmergencyContactManager(this);
        prefs = getSharedPreferences("sentinel_prefs", MODE_PRIVATE);
        contactPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        handleContactSelection(result.getData());
                    }
                });

        initViews();
        loadSettings();
    }

    private void initViews() {
        contactDisplay = findViewById(R.id.contact_display);
        contactForm = findViewById(R.id.contact_form);
        tvContactNameDisplay = findViewById(R.id.tv_contact_name_display);
        tvContactPhoneDisplay = findViewById(R.id.tv_contact_phone_display);
        etContactName = findViewById(R.id.et_contact_name);
        etContactPhone = findViewById(R.id.et_contact_phone);
        contact2Display  = findViewById(R.id.contact2_display);
        contact2Form     = findViewById(R.id.contact2_form);
        tvContact2NameDisplay = findViewById(R.id.tv_contact2_name_display);
        tvContact2PhoneDisplay = findViewById(R.id.tv_contact2_phone_display);
        etContact2Name   = findViewById(R.id.et_contact2_name);
        etContact2Phone  = findViewById(R.id.et_contact2_phone);
        etEmergencyMessage = findViewById(R.id.et_emergency_message);
        tvCharCount = findViewById(R.id.tv_char_count);
        btnResetMessage = findViewById(R.id.btn_reset_message);
        btnResetMessage.setOnClickListener(v -> resetEmergencyMessage());
        setupMessageListener();
        Button btnPickContact = findViewById(R.id.btn_pick_contact);
        Button btnSaveContact = findViewById(R.id.btn_save_contact);
        ImageButton btnEditContact = findViewById(R.id.btn_edit_contact);
        Button btnChangeContact = findViewById(R.id.btn_change_contact);
        btnPickContact.setOnClickListener(v -> pickContact());
        btnSaveContact.setOnClickListener(v -> saveContact());
        btnEditContact.setOnClickListener(v -> editContact());
        if (btnChangeContact != null) {btnChangeContact.setOnClickListener(v -> editContact());}
        switchShakeDetection = findViewById(R.id.switch_shake_detection);
        switchVolumeButtons = findViewById(R.id.switch_volume_buttons);
        switchVibration = findViewById(R.id.switch_vibration);
        switchSound = findViewById(R.id.switch_sound);
        switchLocationSharing = findViewById(R.id.switch_location_sharing);
        seekShakeSensitivity = findViewById(R.id.seek_shake_sensitivity);
        tvSensitivityValue = findViewById(R.id.tv_sensitivity_value);
        seekCountdown = findViewById(R.id.seek_countdown);
        tvCountdownValue = findViewById(R.id.tv_countdown_value);
        switchAIMessages = findViewById(R.id.switch_ai_messages);
        switchFallDetection = findViewById(R.id.switch_fall_detection);
        switchVoiceDetection = findViewById(R.id.switch_voice_detection);
        switchAutoSend = findViewById(R.id.switch_auto_send);
        Button btnSaveContact2   = findViewById(R.id.btn_save_contact2);
        Button btnRemoveContact2 = findViewById(R.id.btn_remove_contact2);
        ImageButton btnEditContact2 = findViewById(R.id.btn_edit_contact2);
        btnSaveContact2.setOnClickListener(v -> saveSecondaryContact());
        btnRemoveContact2.setOnClickListener(v -> removeSecondaryContact());
        btnEditContact2.setOnClickListener(v -> {
            contact2Display.setVisibility(View.GONE);
            contact2Form.setVisibility(View.VISIBLE);
        });

        setupListeners();
    }

    private void setupMessageListener() {
        etEmergencyMessage.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                int length = s.length();
                tvCharCount.setText(length + "/160");
                // Change color based on SMS length
                if (length > 160) {
                    tvCharCount.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                } else if (length > 140) {
                    tvCharCount.setTextColor(getResources().getColor(android.R.color.holo_orange_dark));
                } else {
                    tvCharCount.setTextColor(getResources().getColor(android.R.color.darker_gray));
                }
            }
            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });
    }

    private void setupFallDetectionToggle() {
        SwitchCompat fallDetectionSwitch = findViewById(R.id.switch_fall_detection);
        SharedPreferences prefs = getSharedPreferences("sentinel_prefs", MODE_PRIVATE);
        boolean isEnabled = prefs.getBoolean("fall_detection_enabled", false);
        fallDetectionSwitch.setChecked(isEnabled);
        fallDetectionSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("fall_detection_enabled", isChecked).apply();
            //notifying service of settings change
            Intent intent = new Intent("com.example.sentinel.SETTINGS_CHANGED");
            sendBroadcast(intent);
            //Toast.makeText(this, isChecked ? "Fall detection enabled" : "Fall detection disabled", Toast.LENGTH_SHORT).show();
        });
    }

    private void setupListeners() {
        switchShakeDetection.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("shake_detection_enabled", isChecked).apply();
        });
        switchVolumeButtons.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("volume_buttons_enabled", isChecked).apply();
        });
        switchVibration.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("vibration_enabled", isChecked).apply();
        });
        switchSound.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("sound_enabled", isChecked).apply();
        });
        switchLocationSharing.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("location_sharing_enabled", isChecked).apply();
        });
        switchAIMessages.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("use_ai_messages", isChecked).apply();
        });
        switchAutoSend.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("auto_send_enabled", isChecked).apply();
            Toast.makeText(this, isChecked ? "Auto send enabled" : "Auto-send disabled", Toast.LENGTH_SHORT).show();
        });
        switchFallDetection.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("fall_detection_enabled", isChecked).apply();
            Intent intent = new Intent("com.example.sentinel.SETTINGS_CHANGED");
            sendBroadcast(intent);
            Toast.makeText(this, isChecked ? "Fall detection on" : "Fall detection off", Toast.LENGTH_SHORT).show();
        });
        switchVoiceDetection.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("voice_detection_enabled", isChecked).apply();
            Intent intent = new Intent("com.example.sentinel.SETTINGS_CHANGED");
            sendBroadcast(intent);
            Toast.makeText(this, isChecked ? "Voice detection enabled" : "Voice detection disabled", Toast.LENGTH_SHORT).show();
        });
        seekShakeSensitivity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                String[] levels = {"Very Low", "Low", "Medium", "High", "Very High"};
                tvSensitivityValue.setText(levels[progress]);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                prefs.edit().putInt("shake_sensitivity", seekBar.getProgress()).apply();
                //broadcasting settings change to service
                Intent intent = new Intent("com.example.sentinel.SETTINGS_CHANGED");
                sendBroadcast(intent);
                Toast.makeText(SettingsActivity.this, "Sensitivity updated", Toast.LENGTH_SHORT).show();
            }
        });
        seekCountdown.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int seconds = progress + 3; // 3-10 seconds
                tvCountdownValue.setText(seconds + " seconds");
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int seconds = seekBar.getProgress() + 3;
                prefs.edit().putInt("countdown_seconds", seconds).apply();
            }
        });
    }

    private void loadSettings() {
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
        switchShakeDetection.setChecked(prefs.getBoolean("shake_detection_enabled", true));
        switchVolumeButtons.setChecked(prefs.getBoolean("volume_buttons_enabled", true));
        switchVibration.setChecked(prefs.getBoolean("vibration_enabled", true));
        switchSound.setChecked(prefs.getBoolean("sound_enabled", true));
        switchLocationSharing.setChecked(prefs.getBoolean("location_sharing_enabled", true));
        switchFallDetection.setChecked(prefs.getBoolean("fall_detection_enabled", false));
        switchVoiceDetection.setChecked(prefs.getBoolean("voice_detection_enabled", true));
        switchAutoSend.setChecked(prefs.getBoolean("auto_send_enabled", false));
        int sensitivity = prefs.getInt("shake_sensitivity", 2);
        seekShakeSensitivity.setProgress(sensitivity);
        String[] levels = {"Very Low", "Low", "Medium", "High", "Very High"};
        tvSensitivityValue.setText(levels[sensitivity]);
        int countdown = prefs.getInt("countdown_seconds", 5);
        seekCountdown.setProgress(countdown - 3);
        tvCountdownValue.setText(countdown + " seconds");
        String localMessage = contactManager.getEmergencyMessage();
        etEmergencyMessage.setText(localMessage);
        tvCharCount.setText(localMessage.length() + "/160");
        contactManager.loadEmergencyMessageFromFirebase(message -> {
            if (message != null && !message.equals(localMessage)) {
                runOnUiThread(() -> {
                    etEmergencyMessage.setText(message);
                    tvCharCount.setText(message.length() + "/160");
                });
            }
        });
        boolean useAI = prefs.getBoolean("use_ai_messages", false);
        switchAIMessages.setChecked(useAI);
        loadSecondaryContact();
        contactManager.loadSecondaryContactFromFirebase((name, phone) -> {
            runOnUiThread(this::loadSecondaryContact);
        });
    }

    private void pickContact() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Please grant contacts permission", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent contactPickerIntent = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
        contactPickerLauncher.launch(contactPickerIntent);
    }

    private void resetEmergencyMessage() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Reset Message")
                .setMessage("Reset to default emergency message?")
                .setPositiveButton("Reset", (dialog, which) -> {
                    contactManager.resetEmergencyMessage();
                    etEmergencyMessage.setText(contactManager.getEmergencyMessage());
                    Toast.makeText(this, "Message reset to default", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void handleContactSelection(Intent data) {
        Uri contactUri = data.getData();
        if (contactUri == null) return;
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
        }
    }

    private void saveSecondaryContact() {
        String name  = Objects.requireNonNull(etContact2Name.getText()).toString().trim();
        String phone = Objects.requireNonNull(etContact2Phone.getText()).toString().trim();
        if (phone.isEmpty()) {
            Toast.makeText(this, "Phone number required", Toast.LENGTH_SHORT).show();
            return;
        }
        contactManager.saveSecondaryContact(name, phone);
        Toast.makeText(this, "Secondary contact saved", Toast.LENGTH_SHORT).show();
        loadSecondaryContact();
    }

    private void loadSecondaryContact() {
        if (contactManager.hasSecondaryContact()) {
            contact2Display.setVisibility(View.VISIBLE);
            contact2Form.setVisibility(View.GONE);
            String name  = contactManager.getSecondaryContactName();
            String phone = contactManager.getSecondaryContactPhone();
            tvContact2NameDisplay.setText(name != null ? name : "Secondary Contact");
            tvContact2PhoneDisplay.setText(phone);
            etContact2Name.setText(name);
            etContact2Phone.setText(phone);
        } else {
            contact2Display.setVisibility(View.GONE);
            contact2Form.setVisibility(View.VISIBLE);
        }
    }

    private void removeSecondaryContact() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Remove contact")
                .setMessage("Remove secondary emergency contact?")
                .setPositiveButton("Remove", (d, w) -> {
                    contactManager.removeSecondaryContact();
                    contact2Display.setVisibility(View.GONE);
                    contact2Form.setVisibility(View.VISIBLE);
                    etContact2Name.setText("");
                    etContact2Phone.setText("");
                    Toast.makeText(this, "Secondary contact removed", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
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
        loadSettings();
    }

    private void editContact() {
        contactDisplay.setVisibility(View.GONE);
        contactForm.setVisibility(View.VISIBLE);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onPause() {
        super.onPause();
        //saves emergency message when leaving the screen
        String message = Objects.requireNonNull(etEmergencyMessage.getText()).toString().trim();
        if (!message.isEmpty()) {
            contactManager.saveEmergencyMessage(message);
        }
    }
}