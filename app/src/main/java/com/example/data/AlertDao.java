package com.example.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Delete;
import androidx.room.OnConflictStrategy;
import androidx.room.Update;

import java.util.List;

@Dao
public interface AlertDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(AlertEntity alert);

    @Update
    void update(AlertEntity alert);

    @Delete
    void delete(AlertEntity alert);

    @Query("DELETE FROM alert_history")
    void deleteAll();

    @Query("SELECT * FROM alert_history ORDER BY timestamp DESC")
    List<AlertEntity> getAllAlerts();

    @Query("SELECT * FROM alert_history WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp DESC")
    List<AlertEntity> getAlertsByDateRange(long startTime, long endTime);

    @Query("SELECT * FROM alert_history WHERE alertType = :type ORDER BY timestamp DESC")
    List<AlertEntity> getAlertsByType(String type);

    @Query("SELECT COUNT(*) FROM alert_history")
    int getAlertCount();

    @Query("SELECT * FROM alert_history WHERE locationAvailable = 1 ORDER BY timestamp DESC")
    List<AlertEntity> getAlertsWithLocation();

    @Query("DELETE FROM alert_history WHERE timestamp < :timestamp")
    void deleteOlderThan(long timestamp);
}