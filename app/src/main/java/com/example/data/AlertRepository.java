package com.example.data;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AlertRepository {

    private static final String TAG = "AlertRepository";
    private final AlertDao alertDao;
    private final ExecutorService executorService;
    private DatabaseReference databaseReference;
    private final FirebaseAuth firebaseAuth;

    public AlertRepository(Application application) {
        AlertDatabase db = AlertDatabase.getDatabase(application);
        alertDao = db.alertDao();
        executorService = Executors.newSingleThreadExecutor();
        firebaseAuth = FirebaseAuth.getInstance();
        //databaseReference = FirebaseDatabase.getInstance().getReference("alerts");

        //user-specific path
        initializeFirebaseReference();
    }
    private void initializeFirebaseReference() {
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser != null) {
            // each user has their own alerts path
            databaseReference = FirebaseDatabase.getInstance()
                    .getReference("users")
                    .child(currentUser.getUid())
                    .child("alerts");
        } else {
            Log.w(TAG, "No user logged in, Firebase sync disabled");
        }
    }

    /**
     * Insert alert to both Room database and firebase
     */
    public void insert(AlertEntity alert, RepositoryCallback<String> callback) {
        executorService.execute(() -> {
            try {
                // inserts to local database first
                alertDao.insert(alert);

                //syncs to db if user is logged in
                if (databaseReference != null) {
                    DatabaseReference newAlertRef = databaseReference.push();
                    String firebaseKey = newAlertRef.getKey();

                    //stores firebase key n alert
                    alert.setFirebaseKey(firebaseKey);

                    newAlertRef.setValue(alert)
                            .addOnSuccessListener(aVoid -> {
                                Log.d(TAG, "Alert synced to cloud successfully");
                                if (callback != null) {
                                    callback.onComplete(firebaseKey);
                                }
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Failed to sync alert", e);
                                if (callback != null) {
                                    callback.onComplete(null);
                                }
                            });
                } else {
                    Log.w(TAG, "Firebase reference not initialized, alert saved locally only");
                    if (callback != null) {
                        callback.onComplete(null);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error inserting alert", e);
                if (callback != null) {
                    callback.onComplete(null);
                }
            }
        });
    }

    public void delete(AlertEntity alert) {
        executorService.execute(() -> alertDao.delete(alert));
    }

    //Get all alerts from Firebase (primary) or local database (fallback)
    public void getAllAlerts(RepositoryCallback<List<AlertEntity>> callback) {
        if (databaseReference != null) {
            getAlertsFromFirebase(callback);
        } else {
            getAlertsFromLocal(callback);
        }
    }

    //Get alerts from Firebase with real-time updates
    private void getAlertsFromFirebase(RepositoryCallback<List<AlertEntity>> callback) {
        Query query = databaseReference.orderByChild("timestamp");

        query.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                List<AlertEntity> alerts = new ArrayList<>();

                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    try {
                        AlertEntity alert = snapshot.getValue(AlertEntity.class);
                        if (alert != null) {
                            // Store Firebase key for later operations
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

                if (callback != null) {
                    callback.onComplete(alerts);
                }

                // Optionally sync to local database for offline access
                syncToLocalDatabase(alerts);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Firebase query cancelled", databaseError.toException());

                // Fallback to local database on error
                getAlertsFromLocal(callback);
            }
        });
    }

    //get alerts from local Room database
    private void getAlertsFromLocal(RepositoryCallback<List<AlertEntity>> callback) {
        executorService.execute(() -> {
            try {
                List<AlertEntity> alerts = alertDao.getAllAlerts();

                // Sort by timestamp descending
                Collections.sort(alerts, (a1, a2) ->
                        Long.compare(a2.getTimestamp(), a1.getTimestamp()));

                Log.d(TAG, "Loaded " + alerts.size() + " alerts from local database");

                if (callback != null) {
                    callback.onComplete(alerts);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading alerts from local database", e);
                if (callback != null) {
                    callback.onComplete(new ArrayList<>());
                }
            }
        });
    }

    //sync Firebase alerts to local database for offline access
    private void syncToLocalDatabase(List<AlertEntity> alerts) {
        executorService.execute(() -> {
            try {
                // clear existing local data
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

    //delete all alerts from both Firebase and local database
    public void deleteAllAlerts(RepositoryCallback<Boolean> callback) {
        executorService.execute(() -> {
            try {
                // delete from local database
                alertDao.deleteAll();

                // delete from Firebase if available
                if (databaseReference != null) {
                    databaseReference.removeValue()
                            .addOnSuccessListener(aVoid -> {
                                Log.d(TAG, "All alerts deleted from Firebase");
                                if (callback != null) {
                                    callback.onComplete(true);
                                }
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Failed to delete all alerts from Firebase", e);
                                if (callback != null) {
                                    callback.onComplete(false);
                                }
                            });
                } else {
                    if (callback != null) {
                        callback.onComplete(true);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error deleting all alerts", e);
                if (callback != null) {
                    callback.onComplete(false);
                }
            }
        });
    }

    //delete a single alert from both Firebase and local database
    public void deleteAlert(AlertEntity alert, RepositoryCallback<Boolean> callback) {
        executorService.execute(() -> {
            try {
                // delete from local database
                alertDao.delete(alert);

                // delete from Firebase if available
                if (databaseReference != null && alert.getFirebaseKey() != null) {
                    databaseReference.child(alert.getFirebaseKey())
                            .removeValue()
                            .addOnSuccessListener(aVoid -> {
                                Log.d(TAG, "Alert deleted from Firebase");
                                if (callback != null) {
                                    callback.onComplete(true);
                                }
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Failed to delete alert from Firebase", e);
                                if (callback != null) {
                                    callback.onComplete(false);
                                }
                            });
                } else {
                    if (callback != null) {
                        callback.onComplete(true);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error deleting alert", e);
                if (callback != null) {
                    callback.onComplete(false);
                }
            }
        });
    }

    //get alerts within a date range
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
                        callback.onComplete(alerts);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    Log.e(TAG, "Query cancelled", databaseError.toException());
                    if (callback != null) {
                        callback.onComplete(new ArrayList<>());
                    }
                }
            });
        } else {
            if (callback != null) {
                callback.onComplete(new ArrayList<>());
            }
        }
    }

    //get count of alerts
    public void getAlertCount(RepositoryCallback<Integer> callback) {
        getAllAlerts(alerts -> {
            if (callback != null) {
                callback.onComplete(alerts != null ? alerts.size() : 0);
            }
        });
    }

    //force sync from firebase to local database
    public void forceSyncFromFirebase(RepositoryCallback<Boolean> callback) {
        if (databaseReference != null) {
            getAlertsFromFirebase(alerts -> {
                syncToLocalDatabase(alerts);
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

    //calllback interface for async operations
    public interface RepositoryCallback<T> {
        void onComplete(T result);
    }

    //cleanup resources
    public void cleanup() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
}
