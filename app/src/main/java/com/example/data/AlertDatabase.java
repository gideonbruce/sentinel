package com.example.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {AlertEntity.class}, version = 5, exportSchema = false)
public abstract class AlertDatabase extends RoomDatabase {

    public abstract AlertDao alertDao();

    private static volatile AlertDatabase INSTANCE;

    // Migration from version 1 to 2 - adding firebaseKey field
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE alert_history ADD COLUMN firebaseKey TEXT");
        }
    };

    public static AlertDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AlertDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AlertDatabase.class, "alert_database")
                            // fallbackToDestructiveMigration will handle all migration failures
                            // by recreating the database from scratch
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    // For testing
    public static void closeDatabase() {
        if (INSTANCE != null) {
            INSTANCE.close();
            INSTANCE = null;
        }
    }
}