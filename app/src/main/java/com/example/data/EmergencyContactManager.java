package com.example.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import androidx.annotation.NonNull;

public class EmergencyContactManager {
    private static final String TAG = "EmergencyContactManager";
    private static final String PREFS_NAME = "EmergencyContactPrefs";
    private static final String KEY_CONTACT_NAME = "emergency_contact_name";
    private static final String KEY_CONTACT_PHONE = "emergency_contact_phone";

    private final SharedPreferences prefs;
    private final FirebaseAuth firebaseAuth;
    private DatabaseReference databaseReference;

    public EmergencyContactManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        firebaseAuth = FirebaseAuth.getInstance();
        initializeFirebaseReference();
    }

    private void initializeFirebaseReference() {
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();

        if (currentUser != null) {
            String userId = currentUser.getUid();
            String databaseUrl = "https://sentinel-7b6b4-default-rtdb.asia-southeast1.firebasedatabase.app";

            try {
                FirebaseDatabase database = FirebaseDatabase.getInstance(databaseUrl);
                databaseReference = database.getReference("users")
                        .child(userId)
                        .child("emergency_contact");

                Log.d(TAG, "Firebase reference initialized for user: " + userId);
            } catch (Exception e) {
                Log.e(TAG, "Error initializing Firebase: " + e.getMessage(), e);
                databaseReference = null;
            }
        } else {
            databaseReference = null;
            Log.w(TAG, "No user logged in, Firebase sync disabled");
        }
    }

    public void saveEmergencyContact(String name, String phoneNumber) {
        // Save to SharedPreferences first
        prefs.edit()
                .putString(KEY_CONTACT_NAME, name)
                .putString(KEY_CONTACT_PHONE, phoneNumber)
                .apply();

        Log.d(TAG, "Contact saved locally: " + name + " - " + phoneNumber);

        // Sync to Firebase
        if (databaseReference != null) {
            EmergencyContact contact = new EmergencyContact(name, phoneNumber);

            databaseReference.setValue(contact)
                    .addOnSuccessListener(aVoid ->
                            Log.d(TAG, "✓ Contact synced to Firebase"))
                    .addOnFailureListener(e ->
                            Log.e(TAG, "✗ Failed to sync contact to Firebase: " + e.getMessage(), e));
        } else {
            Log.w(TAG, "Firebase not initialized, contact saved locally only");
        }
    }

    public void loadFromFirebase(ContactLoadCallback callback) {
        if (databaseReference == null) {
            Log.w(TAG, "Firebase not initialized, loading from local only");
            if (callback != null) {
                callback.onLoaded(getContactName(), getContactPhone());
            }
            return;
        }

        Log.d(TAG, "Loading contact from Firebase...");

        databaseReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    EmergencyContact contact = snapshot.getValue(EmergencyContact.class);

                    if (contact != null) {
                        // Update local storage with Firebase data
                        prefs.edit()
                                .putString(KEY_CONTACT_NAME, contact.name)
                                .putString(KEY_CONTACT_PHONE, contact.phoneNumber)
                                .apply();

                        Log.d(TAG, "✓ Contact loaded from Firebase: " + contact.name);

                        if (callback != null) {
                            callback.onLoaded(contact.name, contact.phoneNumber);
                        }
                    }
                } else {
                    Log.d(TAG, "No contact found in Firebase, using local data");
                    if (callback != null) {
                        callback.onLoaded(getContactName(), getContactPhone());
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "✗ Failed to load contact from Firebase: " + error.getMessage());
                // Fallback to local data
                if (callback != null) {
                    callback.onLoaded(getContactName(), getContactPhone());
                }
            }
        });
    }

    public String getContactName() {
        return prefs.getString(KEY_CONTACT_NAME, null);
    }

    public String getContactPhone() {
        return prefs.getString(KEY_CONTACT_PHONE, null);
    }

    public boolean hasEmergencyContact() {
        return getContactPhone() != null && !getContactPhone().isEmpty();
    }

    public void clearEmergencyContact() {
        // Clear from SharedPreferences
        prefs.edit()
                .remove(KEY_CONTACT_NAME)
                .remove(KEY_CONTACT_PHONE)
                .apply();

        Log.d(TAG, "Contact cleared locally");

        // Clear from Firebase
        if (databaseReference != null) {
            databaseReference.removeValue()
                    .addOnSuccessListener(aVoid ->
                            Log.d(TAG, "✓ Contact cleared from Firebase"))
                    .addOnFailureListener(e ->
                            Log.e(TAG, "✗ Failed to clear contact from Firebase: " + e.getMessage(), e));
        }
    }

    public void reinitializeFirebase() {
        Log.d(TAG, "Reinitializing Firebase for new user");
        initializeFirebaseReference();
    }

    // Inner class for Firebase data structure
    public static class EmergencyContact {
        public String name;
        public String phoneNumber;
        public long lastUpdated;

        public EmergencyContact() {
            // Required empty constructor for Firebase
        }

        public EmergencyContact(String name, String phoneNumber) {
            this.name = name;
            this.phoneNumber = phoneNumber;
            this.lastUpdated = System.currentTimeMillis();
        }
    }

    // Callback interface for async loading
    public interface ContactLoadCallback {
        void onLoaded(String name, String phoneNumber);
    }
}