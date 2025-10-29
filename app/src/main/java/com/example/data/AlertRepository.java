package com.example.data;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Objects;

public class AlertRepository {

    private static final String TAG = "AlertRepository";
    private AlertDao alertDao;
    private ExecutorService executorService;
    private Handler mainHandler;
    private DatabaseReference databaseReference;
    private FirebaseAuth firebaseAuth;
    private static AlertRepository instance;
    private String currentUserId;

    private AlertRepository(Application application) {
        AlertDatabase db = AlertDatabase.getDatabase(application);
        alertDao = db.alertDao();
        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        firebaseAuth = FirebaseAuth.getInstance();

        initializeFirebaseReference();
    }

    //singleton getter
    public static synchronized AlertRepository getInstance(Application application) {
        if (instance == null) {
            instance = new AlertRepository(application);
        } else {
            // Check if user changed
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            String newUserId = currentUser != null ? currentUser.getUid() : null;

            if (!Objects.equals(instance.currentUserId, newUserId)) {
                Log.d(TAG, "User changed from " + instance.currentUserId + " to " + newUserId);
                instance.initializeFirebaseReference();
            }
        }
        return instance;
    }

    private void initializeFirebaseReference() {
        Log.d(TAG, "=== INITIALIZING FIREBASE ===");
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();

        if (currentUser != null) {
            currentUserId = currentUser.getUid();
            Log.d(TAG, "Current user ID: " + currentUserId);
            Log.d(TAG, "User email: " + currentUser.getEmail());

            // Verify the ID token
            currentUser.getIdToken(true).addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    String idToken = task.getResult().getToken();
                    Log.d(TAG, "✓ ID Token refreshed successfully");
                    Log.d(TAG, "Token preview: " + (idToken != null ? idToken.substring(0, Math.min(50, idToken.length())) + "..." : "null"));
                } else {
                    Log.e(TAG, "✗ Failed to get ID token", task.getException());
                }
            });

            String databaseUrl = "https://sentinel-7b6b4-default-rtdb.asia-southeast1.firebasedatabase.app";

            try {
                FirebaseDatabase database = FirebaseDatabase.getInstance(databaseUrl);
                databaseReference = database.getReference("users")
                        .child(currentUserId)
                        .child("alerts");

                Log.d(TAG, "✓ Firebase path: users/" + currentUserId + "/alerts");
                Log.d(TAG, "✓ Firebase initialized successfully");
            } catch (Exception e) {
                Log.e(TAG, "✗ Error initializing Firebase Database: " + e.getMessage(), e);
                e.printStackTrace();
            }
        } else {
            currentUserId = null;
            databaseReference = null;
            Log.w(TAG, "⚠ No user logged in, Firebase sync disabled");
        }
    }

    /**
     * Insert alert to both local Room database and Firebase
     */
    public void insert(AlertEntity alert, RepositoryCallback<String> callback) {
        Log.d(TAG, "=== INSERT ALERT START ===");
        Log.d(TAG, "Alert type: " + alert.getAlertType());
        Log.d(TAG, "Timestamp: " + alert.getTimestamp());
        Log.d(TAG, "Contact: " + alert.getContactName() + " - " + alert.getContactPhone());

        executorService.execute(() -> {
            try {
                Log.d(TAG, "Inserting to Room database...");
                // Insert to local database first (on background thread)
                alertDao.insert(alert);
                Log.d(TAG, "✓ Room insert successful");

                // Sync to Firebase (callbacks will run on main thread automatically)
                if (databaseReference != null) {
                    Log.d(TAG, "Firebase reference exists, pushing to Firebase...");
                    DatabaseReference newAlertRef = databaseReference.push();
                    String firebaseKey = newAlertRef.getKey();
                    Log.d(TAG, "Generated Firebase key: " + firebaseKey);

                    alert.setFirebaseKey(firebaseKey);

                    newAlertRef.setValue(alert)
                            .addOnSuccessListener(aVoid -> {
                                Log.d(TAG, "Alert synced to Firebase successfully" + firebaseKey);
                                // Update local database with Firebase key
                                //executorService.execute(() -> {
                                //    try {
                                //        alertDao.update(alert);
                                //    } catch (Exception e) {
                                //        Log.e(TAG, "Failed to update alert with Firebase key", e);
                                //    }
                                //});

                                if (callback != null) {
                                    mainHandler.post(() -> callback.onComplete(firebaseKey));
                                }
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "x Failed to sync alert to Firebase: " + e.getMessage(), e);
                                Log.e(TAG, "Firebase path: " + newAlertRef.toString());
                                Log.e(TAG, "User ID: " + (firebaseAuth.getCurrentUser() != null ? firebaseAuth.getCurrentUser().getUid() : "NULL"));
                                Log.e(TAG, "User authenticated: " + (firebaseAuth.getCurrentUser() != null));

                                if (callback != null) {
                                    mainHandler.post(() -> callback.onComplete(null));
                                }
                            });
                } else {
                    Log.w(TAG, "Firebase reference not initialized, alert saved locally only");
                    if (callback != null) {
                        mainHandler.post(() -> callback.onComplete(null));
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, " x Error inserting alert:" + e.getMessage(), e);
                e.printStackTrace();
                if (callback != null) {
                    mainHandler.post(() -> callback.onComplete(null));
                }
            }
        });
    }

    /**
     * Get all alerts from Firebase (primary) or local database (fallback)
     */
    public void getAllAlerts(RepositoryCallback<List<AlertEntity>> callback) {
        if (databaseReference != null) {
            getAlertsFromFirebase(callback);
        } else {
            getAlertsFromLocal(callback);
        }
    }

    /**
     * Get alerts from Firebase (single fetch, not real-time)
     */
    private void getAlertsFromFirebase(RepositoryCallback<List<AlertEntity>> callback) {
        Log.d(TAG, "=== FETCHING FROM FIREBASE ===");
        Log.d(TAG, "Database reference: " + (databaseReference != null ? databaseReference.toString() : "NULL"));

        Query query = databaseReference.orderByChild("timestamp");

        // Firebase listeners run on main thread by default, which is fine for async operations
        query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                Log.d(TAG, "Firebase onDataChange triggered");
                Log.d(TAG, "DataSnapshot exists: " + dataSnapshot.exists());
                Log.d(TAG, "Children count: " + dataSnapshot.getChildrenCount());

                // Parse data on background thread to avoid blocking main thread
                executorService.execute(() -> {
                    List<AlertEntity> alerts = new ArrayList<>();

                    for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                        try {
                            Log.d(TAG, "Processing snapshot key: " + snapshot.getKey());
                            AlertEntity alert = snapshot.getValue(AlertEntity.class);
                            if (alert != null) {
                                alert.setFirebaseKey(snapshot.getKey());
                                alerts.add(alert);
                                Log.d(TAG, "✓ Parsed alert: " + alert.getAlertType() + " at " + alert.getTimestamp());
                            } else {
                                Log.w(TAG, "⚠ Alert is null for key: " + snapshot.getKey());
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing alert from Firebase" + e.getMessage(), e);
                            Log.e(TAG, "Snapshot data: " + snapshot.toString());
                        }
                    }

                    // Sort by timestamp descending (newest first)
                    Collections.sort(alerts, (a1, a2) ->
                            Long.compare(a2.getTimestamp(), a1.getTimestamp()));

                    Log.d(TAG, "Loaded " + alerts.size() + " alerts from Firebase");

                    // Return results on main thread
                    if (callback != null) {
                        mainHandler.post(() -> callback.onComplete(alerts));
                    }

                    // Sync to local database in background
                    syncToLocalDatabase(alerts);
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "✗ Firebase query cancelled: " + databaseError.getMessage());
                Log.e(TAG, "Error code: " + databaseError.getCode());
                Log.e(TAG, "Error details: " + databaseError.getDetails());
                Log.e(TAG, "Firebase query cancelled", databaseError.toException());
                databaseError.toException().printStackTrace();
                getAlertsFromLocal(callback);
            }
        });
    }

    /**
     * Get alerts from Firebase with real-time updates (use only when needed)
     */
    public void getAlertsWithRealtimeUpdates(RepositoryCallback<List<AlertEntity>> callback) {
        if (databaseReference == null) {
            getAlertsFromLocal(callback);
            return;
        }

        Query query = databaseReference.orderByChild("timestamp");

        query.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                // Process data on background thread
                executorService.execute(() -> {
                    List<AlertEntity> alerts = new ArrayList<>();

                    for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                        try {
                            AlertEntity alert = snapshot.getValue(AlertEntity.class);
                            if (alert != null) {
                                alert.setFirebaseKey(snapshot.getKey());
                                alerts.add(alert);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing alert from Firebase", e);
                        }
                    }

                    Collections.sort(alerts, (a1, a2) ->
                            Long.compare(a2.getTimestamp(), a1.getTimestamp()));

                    Log.d(TAG, "Real-time update: " + alerts.size() + " alerts");

                    if (callback != null) {
                        mainHandler.post(() -> callback.onComplete(alerts));
                    }

                    syncToLocalDatabase(alerts);
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Firebase query cancelled", databaseError.toException());
                getAlertsFromLocal(callback);
            }
        });
    }

    /**
     * Get alerts from local Room database
     */
    private void getAlertsFromLocal(RepositoryCallback<List<AlertEntity>> callback) {
        Log.d(TAG, "=== FETCHING FROM LOCAL DATABASE ===");
        executorService.execute(() -> {
            try {
                List<AlertEntity> alerts = alertDao.getAllAlerts();
                Log.d(TAG, "✓ Retrieved " + alerts.size() + " alerts from Room");

                for (AlertEntity alert : alerts) {
                    Log.d(TAG, "Local alert: " + alert.getAlertType() + " | " + alert.getTimestamp());
                }

                Collections.sort(alerts, (a1, a2) ->
                        Long.compare(a2.getTimestamp(), a1.getTimestamp()));

                if (callback != null) {
                    mainHandler.post(() -> callback.onComplete(alerts));
                }
            } catch (Exception e) {
                Log.e(TAG, "✗ Error loading alerts from local database: " + e.getMessage(), e);
                e.printStackTrace();
                if (callback != null) {
                    mainHandler.post(() -> callback.onComplete(new ArrayList<>()));
                }
            }
        });
    }

    /**
     * Sync Firebase alerts to local database for offline access
     */
    private void syncToLocalDatabase(List<AlertEntity> alerts) {
        Log.d(TAG, "=== SYNCING TO LOCAL DATABASE ===");
        Log.d(TAG, "Alerts to sync: " + alerts.size());

        executorService.execute(() -> {
            try {
                Log.d(TAG, "Deleting all existing local alerts...");
                alertDao.deleteAll();
                Log.d(TAG, "✓ Local database cleared");

                int successCount = 0;
                for (AlertEntity alert : alerts) {
                    try {
                        alertDao.insert(alert);
                        successCount++;
                        Log.d(TAG, "✓ Synced alert " + successCount + "/" + alerts.size() + ": " + alert.getAlertType());
                    } catch (Exception e) {
                        Log.e(TAG, "✗ Error inserting alert to local DB: " + e.getMessage(), e);
                        Log.e(TAG, "Failed alert data: Type=" + alert.getAlertType() + ", Time=" + alert.getTimestamp());
                    }
                }

                Log.d(TAG, "✓ Synced " + successCount + "/" + alerts.size() + " alerts to local database");
            } catch (Exception e) {
                Log.e(TAG, "✗ Error syncing to local database: " + e.getMessage(), e);
                e.printStackTrace();
            }
        });
    }

    /**
     * Delete a single alert from both Firebase and local database
     */
    public void deleteAlert(AlertEntity alert, RepositoryCallback<Boolean> callback) {
        executorService.execute(() -> {
            try {
                // Delete from local database
                alertDao.delete(alert);

                // Delete from Firebase if available
                if (databaseReference != null && alert.getFirebaseKey() != null) {
                    databaseReference.child(alert.getFirebaseKey())
                            .removeValue()
                            .addOnSuccessListener(aVoid -> {
                                Log.d(TAG, "Alert deleted from Firebase");
                                if (callback != null) {
                                    mainHandler.post(() -> callback.onComplete(true));
                                }
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Failed to delete alert from Firebase", e);
                                if (callback != null) {
                                    mainHandler.post(() -> callback.onComplete(false));
                                }
                            });
                } else {
                    if (callback != null) {
                        mainHandler.post(() -> callback.onComplete(true));
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error deleting alert", e);
                if (callback != null) {
                    mainHandler.post(() -> callback.onComplete(false));
                }
            }
        });
    }

    /**
     * Delete all alerts from both Firebase and local database
     */
    public void deleteAllAlerts(RepositoryCallback<Boolean> callback) {
        executorService.execute(() -> {
            try {
                // Delete from local database
                alertDao.deleteAll();

                // Delete from Firebase if available
                if (databaseReference != null) {
                    databaseReference.removeValue()
                            .addOnSuccessListener(aVoid -> {
                                Log.d(TAG, "All alerts deleted from Firebase");
                                if (callback != null) {
                                    mainHandler.post(() -> callback.onComplete(true));
                                }
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Failed to delete all alerts from Firebase", e);
                                if (callback != null) {
                                    mainHandler.post(() -> callback.onComplete(false));
                                }
                            });
                } else {
                    if (callback != null) {
                        mainHandler.post(() -> callback.onComplete(true));
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error deleting all alerts", e);
                if (callback != null) {
                    mainHandler.post(() -> callback.onComplete(false));
                }
            }
        });
    }

    /**
     * Get alerts within a date range
     */
    public void getAlertsByDateRange(long startTime, long endTime,
                                     RepositoryCallback<List<AlertEntity>> callback) {
        if (databaseReference != null) {
            Query query = databaseReference
                    .orderByChild("timestamp")
                    .startAt(startTime)
                    .endAt(endTime);

            query.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    executorService.execute(() -> {
                        List<AlertEntity> alerts = new ArrayList<>();

                        for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                            AlertEntity alert = snapshot.getValue(AlertEntity.class);
                            if (alert != null) {
                                alert.setFirebaseKey(snapshot.getKey());
                                alerts.add(alert);
                            }
                        }

                        Collections.sort(alerts, (a1, a2) ->
                                Long.compare(a2.getTimestamp(), a1.getTimestamp()));

                        if (callback != null) {
                            mainHandler.post(() -> callback.onComplete(alerts));
                        }
                    });
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    Log.e(TAG, "Query cancelled", databaseError.toException());
                    if (callback != null) {
                        mainHandler.post(() -> callback.onComplete(new ArrayList<>()));
                    }
                }
            });
        } else {
            if (callback != null) {
                mainHandler.post(() -> callback.onComplete(new ArrayList<>()));
            }
        }
    }

    /**
     * Get count of alerts
     */
    public void getAlertCount(RepositoryCallback<Integer> callback) {
        getAllAlerts(alerts -> {
            if (callback != null) {
                callback.onComplete(alerts != null ? alerts.size() : 0);
            }
        });
    }

    /**
     * Force sync from Firebase to local database
     */
    public void forceSyncFromFirebase(RepositoryCallback<Boolean> callback) {
        if (databaseReference != null) {
            getAlertsFromFirebase(alerts -> {
                if (callback != null) {
                    callback.onComplete(true);
                }
            });
        } else {
            if (callback != null) {
                callback.onComplete(false);
            }
        }
    }

    /**
     * Callback interface for async operations
     */
    public interface RepositoryCallback<T> {
        void onComplete(T result);
    }

    /**
     * Clean up resources
     */
    public void cleanup() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }

    //reintializes firebase reference
    public void reinitializeFirebase() {
        Log.d(TAG, "=== REINITIALIZING FIREBASE FOR NEW USER ===");
        initializeFirebaseReference();
    }
}