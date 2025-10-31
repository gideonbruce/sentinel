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
    private static final String KEY_EMERGENCY_MESSAGE = "emergency_message";
    private static final String DEFAULT_MESSAGE = "🚨 EMERGENCY! I need help! Please check on me immediately.";

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
        syncContactToFirebase(name, phoneNumber);
    }

    private void syncContactToFirebase(String name, String phoneNumber) {
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

    /**
     * Loads contact from Firebase and syncs with local storage.
     * If Firebase has data, it updates local storage.
     * If Firebase is empty but local storage has data, it syncs local data to Firebase.
     * This ensures data is never lost.
     */
    public void loadFromFirebase(ContactLoadCallback callback) {
        // First, get local data
        String localName = getContactName();
        String localPhone = getContactPhone();
        boolean hasLocalData = localPhone != null && !localPhone.isEmpty();

        if (databaseReference == null) {
            Log.w(TAG, "Firebase not initialized, using local data only");
            if (callback != null) {
                callback.onLoaded(localName, localPhone);
            }
            return;
        }

        Log.d(TAG, "Loading contact from Firebase...");

        databaseReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    EmergencyContact contact = snapshot.getValue(EmergencyContact.class);

                    if (contact != null && contact.phoneNumber != null && !contact.phoneNumber.isEmpty()) {
                        // Firebase has data - update local storage
                        prefs.edit()
                                .putString(KEY_CONTACT_NAME, contact.name)
                                .putString(KEY_CONTACT_PHONE, contact.phoneNumber)
                                .apply();

                        Log.d(TAG, "✓ Contact loaded from Firebase and saved locally: " + contact.name);

                        if (callback != null) {
                            callback.onLoaded(contact.name, contact.phoneNumber);
                        }
                    } else {
                        // Firebase data is invalid - use local data
                        Log.d(TAG, "Firebase data invalid, using local data");
                        handleLocalData(localName, localPhone, hasLocalData, callback);
                    }
                } else {
                    // No data in Firebase - use local data and optionally sync to Firebase
                    Log.d(TAG, "No contact found in Firebase");
                    handleLocalData(localName, localPhone, hasLocalData, callback);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "✗ Failed to load contact from Firebase: " + error.getMessage());
                // Fallback to local data
                if (callback != null) {
                    callback.onLoaded(localName, localPhone);
                }
            }
        });
    }

    private void handleLocalData(String localName, String localPhone, boolean hasLocalData, ContactLoadCallback callback) {
        if (hasLocalData) {
            // We have local data but Firebase is empty - sync local data to Firebase
            Log.d(TAG, "Syncing local data to Firebase: " + localName);
            syncContactToFirebase(localName, localPhone);
        }

        if (callback != null) {
            callback.onLoaded(localName, localPhone);
        }
    }

    public String getContactName() {
        return prefs.getString(KEY_CONTACT_NAME, null);
    }

    public String getContactPhone() {
        return prefs.getString(KEY_CONTACT_PHONE, null);
    }

    public boolean hasEmergencyContact() {
        String phone = getContactPhone();
        return phone != null && !phone.isEmpty();
    }

    /**
     * Clears emergency contact from both local storage and Firebase.
     * Use this when user wants to delete their contact permanently.
     */
    public void clearEmergencyContact() {
        clearEmergencyContactLocal();
        clearEmergencyContactFromFirebase();
    }

    /**
     * Clears emergency contact from local storage only.
     * Use this when signing out - keeps Firebase data intact for next login.
     */
    public void clearEmergencyContactLocal() {
        // Clear from SharedPreferences only
        prefs.edit()
                .remove(KEY_CONTACT_NAME)
                .remove(KEY_CONTACT_PHONE)
                .remove(KEY_EMERGENCY_MESSAGE)
                .apply();

        Log.d(TAG, "Contact cleared locally (Firebase data preserved)");
    }

    /**
     * Clears emergency contact from Firebase only.
     * Use with caution - this permanently deletes user data from the cloud.
     */
    public void clearEmergencyContactFromFirebase() {
        if (databaseReference != null) {
            DatabaseReference userRef = databaseReference.getParent();
            if (userRef != null) {
                userRef.removeValue()
                        .addOnSuccessListener(aVoid ->
                                Log.d(TAG, "✓ All user data cleared from Firebase"))
                        .addOnFailureListener(e ->
                                Log.e(TAG, "✗ Failed to clear data from Firebase: " + e.getMessage(), e));
            }
        } else {
            Log.w(TAG, "Firebase not initialized, cannot clear from cloud");
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

    public void saveEmergencyMessage(String message) {
        // Save to SharedPreferences first
        prefs.edit()
                .putString(KEY_EMERGENCY_MESSAGE, message)
                .apply();

        Log.d(TAG, "Emergency message saved locally");

        // Sync to Firebase
        syncMessageToFirebase(message);
    }

    private void syncMessageToFirebase(String message) {
        if (databaseReference != null) {
            DatabaseReference userRef = databaseReference.getParent();
            if (userRef != null) {
                userRef.child("emergency_message").setValue(message)
                        .addOnSuccessListener(aVoid ->
                                Log.d(TAG, "✓ Emergency message synced to Firebase"))
                        .addOnFailureListener(e ->
                                Log.e(TAG, "✗ Failed to sync message to Firebase: " + e.getMessage(), e));
            }
        } else {
            Log.w(TAG, "Firebase not initialized, message saved locally only");
        }
    }

    public String getEmergencyMessage() {
        return prefs.getString(KEY_EMERGENCY_MESSAGE, DEFAULT_MESSAGE);
    }

    public void loadEmergencyMessageFromFirebase(MessageLoadCallback callback) {
        // First, get local data
        String localMessage = getEmergencyMessage();

        if (databaseReference == null) {
            Log.w(TAG, "Firebase not initialized, using local message only");
            if (callback != null) {
                callback.onLoaded(localMessage);
            }
            return;
        }

        Log.d(TAG, "Loading emergency message from Firebase...");

        DatabaseReference userRef = databaseReference.getParent();
        if (userRef != null) {
            userRef.child("emergency_message").addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        String message = snapshot.getValue(String.class);

                        if (message != null && !message.isEmpty()) {
                            // Firebase has data - update local storage
                            prefs.edit()
                                    .putString(KEY_EMERGENCY_MESSAGE, message)
                                    .apply();

                            Log.d(TAG, "✓ Emergency message loaded from Firebase");

                            if (callback != null) {
                                callback.onLoaded(message);
                            }
                        } else {
                            // Firebase data is invalid - use local
                            handleLocalMessage(localMessage, callback);
                        }
                    } else {
                        // No data in Firebase - use local and sync
                        Log.d(TAG, "No message found in Firebase");
                        handleLocalMessage(localMessage, callback);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.e(TAG, "✗ Failed to load message from Firebase: " + error.getMessage());
                    // Fallback to local data
                    if (callback != null) {
                        callback.onLoaded(localMessage);
                    }
                }
            });
        }
    }

    private void handleLocalMessage(String localMessage, MessageLoadCallback callback) {
        // If we have a non-default local message and Firebase is empty, sync it
        if (!localMessage.equals(DEFAULT_MESSAGE)) {
            Log.d(TAG, "Syncing local message to Firebase");
            syncMessageToFirebase(localMessage);
        }

        if (callback != null) {
            callback.onLoaded(localMessage);
        }
    }

    public void resetEmergencyMessage() {
        saveEmergencyMessage(DEFAULT_MESSAGE);
    }

    // Callback interface
    public interface MessageLoadCallback {
        void onLoaded(String message);
    }
}