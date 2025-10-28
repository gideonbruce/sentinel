package com.example.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Delete;

import java.util.List;

@Dao
public interface AlertDao {
    @Insert
    long insert(AlertEntity alert);

    @Query("SELECT * FROM alert_history ORDER BY timestamp DESC")
    List<AlertEntity> getAllAlerts();

    @Query("SELECT * FROM alert_history WHERE id = :alertId")
    AlertEntity getAlertById(int alertId);

    @Query("SELECT * FROM alert_history ORDER BY timestamp DESC LIMIT :limit")
    List<AlertEntity> getRecentAlerts(int limit);

    @Delete
    void delete(AlertEntity alert);

    @Query("DELETE FROM alert_history")
    void deleteAll();

    @Query("SELECT COUNT(*) FROM alert_history")
    int getAlertCount();
}