package com.example.sentinel;


import android.app.Application;
import com.google.firebase.FirebaseApp;
import com.google.firebase.database.FirebaseDatabase;

public class SentinelApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        FirebaseApp.initializeApp(this);

        FirebaseDatabase database = FirebaseDatabase.getInstance();
        database.useEmulator("10.0.2.2", 9000);

        String databaseUrl = "https://sentinel-7b6b4-default-rtdb.asia-southeast1.firebasedatabase.app";
        FirebaseDatabase.getInstance(databaseUrl).setPersistenceEnabled(true);

        try {
            FirebaseDatabase.getInstance().setPersistenceEnabled(true);
        } catch (Exception e) {
            // Persistence already enabled
        }
    }
}
