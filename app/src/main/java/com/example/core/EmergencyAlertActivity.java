package com.example.core;

import android.app.Activity;
import android.os.Bundle;

import com.example.ui.EmergencyAlertDialog;

public class EmergencyAlertActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EmergencyAlertDialog.show(this, new EmergencyAlertDialog.OnAlertActionListener() {
            @Override
            public void onAlertSent() {
                finish();
            }

            @Override
            public void onAlertCancelled() {
                finish();
            }
        });
    }
}