package com.example.sentinel;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class LaunchActivity extends AppCompatActivity {

    private static final int SPLASH_DELAY = 2500; // 2.5 seconds

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launch);

        // Find views
        ImageView logo = findViewById(R.id.iv_logo);
        TextView appName = findViewById(R.id.tv_app_name);
        TextView tagline = findViewById(R.id.tv_tagline);

        // Load and start animations
        try {
            Animation fadeIn = AnimationUtils.loadAnimation(this, android.R.anim.fade_in);
            logo.startAnimation(fadeIn);
            appName.startAnimation(fadeIn);
            tagline.startAnimation(fadeIn);
        } catch (Exception e) {
            // If animation fails, just continue
            e.printStackTrace();
        }

        // Navigate to MainActivity after delay
        new Handler().postDelayed(() -> {
            Intent intent = new Intent(LaunchActivity.this, MainActivity.class);
            startActivity(intent);
            finish(); // Close launch activity
        }, SPLASH_DELAY);
    }
}