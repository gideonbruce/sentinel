package com.example.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {AlertEntity.class}, version = 2, exportSchema = false)
public abstract class AlertDatabase extends RoomDatabase {

    public abstract AlertDao alertDao();

    private static volatile AlertDatabase INSTANCE;

    //migration from version 1 to 2 adding firebase key field
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            //firebaseKey is not stored in Room, so no migration needed
            // pump version number anyway
        }
    };

    public static AlertDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AlertDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            AlertDatabase.class, "alert_database")
                            .addMigrations(MIGRATION_1_2)
                            //Using fallbackToDestructiveMigration()
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
