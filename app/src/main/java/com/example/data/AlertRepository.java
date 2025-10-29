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

public class AlertRepository {

    private static final String TAG = "AlertRepository";
    private AlertDao alertDao;
    private ExecutorService executorService;
    private Handler mainHandler;
    private DatabaseReference databaseReference;
    private FirebaseAuth firebaseAuth;

    public AlertRepository(Application application) {
        AlertDatabase db = AlertDatabase.getDatabase(application);
        alertDao = db.alertDao();
        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        firebaseAuth = FirebaseAuth.getInstance();

        initializeFirebaseReference();
    }

    private void initializeFirebaseReference() {
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser != null) {
            String databaseUrl = "https://sentinel-7b6b4-default-rtdb.asia-southeast1.firebasedatabase.app";

            try {
                FirebaseDatabase database = FirebaseDatabase.getInstance(databaseUrl);
                databaseReference = database.getReference("users")
                        .child(currentUser.getUid())
                        .child("alerts");

                Log.d(TAG, "Firebase initialized with region: asia-southeast1");
            } catch (Exception e) {
                Log.e(TAG, "Error initializing Firebase Database", e);
            }
        } else {
            Log.w(TAG, "No user logged in, Firebase sync disabled");
        }
    }

    /**
     * Insert alert to both local Room database and Firebase
     */
    public void insert(AlertEntity alert, RepositoryCallback<String> callback) {
        executorService.execute(() -> {
            try {
                // Insert to local database first (on background thread)
                alertDao.insert(alert);

                // Sync to Firebase (callbacks will run on main thread automatically)
                if (databaseReference != null) {
                    DatabaseReference newAlertRef = databaseReference.push();
                    String firebaseKey = newAlertRef.getKey();

                    alert.setFirebaseKey(firebaseKey);

                    newAlertRef.setValue(alert)
                            .addOnSuccessListener(aVoid -> {
                                Log.d(TAG, "Alert synced to Firebase successfully");
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
                                Log.e(TAG, "Failed to sync alert to Firebase", e);
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
                Log.e(TAG, "Error inserting alert", e);
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
        Query query = databaseReference.orderByChild("timestamp");

        // Firebase listeners run on main thread by default, which is fine for async operations
        query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                // Parse data on background thread to avoid blocking main thread
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
                Log.e(TAG, "Firebase query cancelled", databaseError.toException());
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
        executorService.execute(() -> {
            try {
                List<AlertEntity> alerts = alertDao.getAllAlerts();

                Collections.sort(alerts, (a1, a2) ->
                        Long.compare(a2.getTimestamp(), a1.getTimestamp()));

                Log.d(TAG, "Loaded " + alerts.size() + " alerts from local database");

                if (callback != null) {
                    mainHandler.post(() -> callback.onComplete(alerts));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading alerts from local database", e);
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
        executorService.execute(() -> {
            try {
                // Clear existing local data
                alertDao.deleteAll();

                // Insert all alerts from Firebase
                for (AlertEntity alert : alerts) {
                    alertDao.insert(alert);
                }

                Log.d(TAG, "Synced " + alerts.size() + " alerts to local database");
            } catch (Exception e) {
                Log.e(TAG, "Error syncing to local database", e);
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
}