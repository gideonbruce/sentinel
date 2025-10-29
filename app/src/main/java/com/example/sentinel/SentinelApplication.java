package com.example.sentinel;


import android.app.Application;
import com.google.firebase.FirebaseApp;
import com.google.firebase.database.FirebaseDatabase;

public class SentinelApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // Initialize Firebase
        FirebaseApp.initializeApp(this);

        // Set the correct database URL for Asia Southeast 1 region
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        database.useEmulator("10.0.2.2", 9000); // Remove this line if not using emulator

        // IMPORTANT: Set your actual database URL
        // Replace with your actual database URL from Firebase Console
        String databaseUrl = "https://sentinel-7b6b4-default-rtdb.asia-southeast1.firebasedatabase.app";
        FirebaseDatabase.getInstance(databaseUrl).setPersistenceEnabled(true);

        // Enable offline persistence (optional but recommended)
        try {
            FirebaseDatabase.getInstance().setPersistenceEnabled(true);
        } catch (Exception e) {
            // Persistence already enabled
        }
    }
}
