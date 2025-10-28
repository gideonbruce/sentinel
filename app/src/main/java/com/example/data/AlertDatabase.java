package com.example.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {AlertEntity.class}, version = 1, exportSchema = false)
public abstract class AlertDatabase extends RoomDatabase {

    public abstract AlertDao alertDao();

    private static volatile AlertDatabase INSTANCE;

    public static AlertDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AlertDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            AlertDatabase.class, "alert_database")
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
