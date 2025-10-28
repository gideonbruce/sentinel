package com.example.data;

import android.app.Application;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AlertRepository {

    private AlertDao alertDao;
    private ExecutorService executorService;
    private DatabaseReference databaseReference;

    public AlertRepository(Application application) {
        AlertDatabase db = AlertDatabase.getDatabase(application);
        alertDao = db.alertDao();
        executorService = Executors.newSingleThreadExecutor();
        databaseReference = FirebaseDatabase.getInstance().getReference("alerts");
    }

    public void insert(AlertEntity alert) {
        executorService.execute(() -> {
            alertDao.insert(alert);
            databaseReference.push().setValue(alert);
        });
    }

    public void delete(AlertEntity alert) {
        executorService.execute(() -> alertDao.delete(alert));
    }

    public void getAllAlerts(RepositoryCallback<List<AlertEntity>> callback) {
        getAlertsFromFirebase(callback);
    }

    private void getAlertsFromFirebase(RepositoryCallback<List<AlertEntity>> callback) {
        databaseReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                List<AlertEntity> alerts = new ArrayList<>();
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    AlertEntity alert = snapshot.getValue(AlertEntity.class);
                    alerts.add(alert);
                }
                if (callback != null) {
                    callback.onComplete(alerts);
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                // Handle error
                if (callback != null) {
                    // Optionally, you can create a custom exception or error handling mechanism
                    // For now, we'll return null to indicate failure
                    callback.onComplete(null);
                }
            }
        });
    }

    public void deleteAllAlerts(RepositoryCallback<Void> callback) {
        executorService.execute(() -> {
            alertDao.deleteAll();
            if (callback != null) {
                callback.onComplete(null);
            }
        });
    }

    public void deleteAlert(AlertEntity alert, RepositoryCallback<Void> callback) {
        executorService.execute(() -> {
            alertDao.delete(alert);
            if (callback != null) {
                callback.onComplete(null);
            }
        });
    }

    public interface RepositoryCallback<T> {
        void onComplete(T result);
    }
}
