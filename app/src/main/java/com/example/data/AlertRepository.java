package com.example.data;

import android.app.Application;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AlertRepository {

    private AlertDao alertDao;
    private ExecutorService executorService;

    public AlertRepository(Application application) {
        AlertDatabase db = AlertDatabase.getDatabase(application);
        alertDao = db.alertDao();
        executorService = Executors.newSingleThreadExecutor();
    }

    public void insert(AlertEntity alert) {
        executorService.execute(() -> alertDao.insert(alert));
    }

    public void delete(AlertEntity alert) {
        executorService.execute(() -> alertDao.delete(alert));
    }

    public void getAllAlerts(RepositoryCallback<List<AlertEntity>> callback) {
        executorService.execute(() -> {
            List<AlertEntity> result = alertDao.getAllAlerts();
            if (callback != null) {
                callback.onComplete(result);
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
