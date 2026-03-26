package com.example.core;

import android.app.Activity;
import android.os.Bundle;
import com.example.ui.EmergencyAlertDialog;

public class EmergencyAlertActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String senderName = getIntent().getStringExtra("sender_name");
        String senderNumber = getIntent().getStringExtra("sender_number");
        if (senderName == null) senderName = "Your contact";
        if (senderNumber == null) senderNumber = "";
        final String finalNumber = senderNumber;
        //android.location.Location location = getIntent().getParcelableExtra("LOCATION");

        EmergencyAlertDialog.showIncomingSOS(
                this,
                senderName,
                finalNumber,
                new EmergencyAlertDialog.OnAlertActionListener() {
                    @Override
                    public void onAlertSent() {
                        finish();
                    }

                    @Override
                    public void onAlertCancelled() {
                        SilentSmsReceiver.stopAlarm();
                        finish();
                    }
                }
            );
        }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        SilentSmsReceiver.stopAlarm();
    }
}