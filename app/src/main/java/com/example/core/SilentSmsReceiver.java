package com.example.core;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.util.Log;

public class SilentSmsReceiver extends BroadcastReceiver {
    private static final String TAG = "SilentSmsReceiver";
    public static final String SOS_PREFIX = "SNTL_SOS:";
    public static final String ACK_PREFIX = "SNTL_ACK:";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!"android.provider.Telephony.SMS_RECEIVED".equals(intent.getAction())) return;
        Bundle bundle = intent.getExtras();
        if (bundle == null) return;
        Object[] pdus = (Object[]) bundle.get("pdus");
        String format = bundle.getString("format");
        if (pdus == null) return;

        for (Object pdu : pdus) {
            SmsMessage msg = SmsMessage.createFromPdu((byte[]) pdu, format);
            if (msg == null) continue;
            String body   = msg.getMessageBody();
            String sender = msg.getOriginatingAddress();
            if (body == null) continue;

            if (body.startsWith(SOS_PREFIX)) {
                Log.i(TAG, "SNTL_SOS received from " + sender);
                //prevents it from showing in the default SMS app
                abortBroadcast();
                // Parse: "SNTL_SOS:<lat>,<lng>:<senderName>"
                String payload     = body.substring(SOS_PREFIX.length());
                String[] parts     = payload.split(":", 2);
                String location    = parts.length > 0 ? parts[0] : "Unknown";
                String senderName  = parts.length > 1 ? parts[1] : sender;

                wakeScreen(context);

                //overrides volume to max
                setMaxVolume(context);
                //plays alarm ringtone
                playAlarm(context);
                //launches full-screen emergency UI
                Intent alertIntent = new Intent(context, EmergencyAlertActivity.class);
                alertIntent.putExtra("sender_name", senderName);
                alertIntent.putExtra("sender_number", sender);
                alertIntent.putExtra("location", location);
                alertIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                context.startActivity(alertIntent);
                //auto-send ACK back to sender
                sendAck(context, sender);
            }
            if (body.startsWith(ACK_PREFIX)) {
                Log.i(TAG, "SNTL_ACK received from " + sender);

                //intercept
                abortBroadcast();         //dont show as normal SMS
                //notify the service that contact acknowledged the alert
                Intent ackIntent = new Intent("com.example.sentinel.ALERT_ACKNOWLEDGED");
                ackIntent.putExtra("contact_number", sender);
                context.sendBroadcast(ackIntent);
            }
        }
    }

    private void wakeScreen(Context context) {
        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm == null) return;
            PowerManager.WakeLock wl = pm.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK |
                            PowerManager.ACQUIRE_CAUSES_WAKEUP |
                            PowerManager.ON_AFTER_RELEASE,
                    "sentinel:sos_wake"
            );
            wl.acquire(10 * 60 * 1000L); // hold for 10 minutes max
            Log.d(TAG, "Screen wake lock acquired");
        } catch (Exception e) {
            Log.e(TAG, "Failed to acquire wake lock: " + e.getMessage());
        }
    }

    private void setMaxVolume(Context context) {
        try {
            AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (audio == null) return;
            // STREAM_ALARM ignores silent/vibrate mode — perfect for emergencies
            audio.setStreamVolume(
                    AudioManager.STREAM_ALARM,
                    audio.getStreamMaxVolume(AudioManager.STREAM_ALARM),
                    0
            );
            Log.d(TAG, "Alarm volume set to maximum");
        } catch (Exception e) {
            Log.e(TAG, "Failed to set volume: " + e.getMessage());
        }
    }

    private void playAlarm(Context context) {
        try {
            Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (alarmUri == null) {
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            }
            MediaPlayer player = new MediaPlayer();
            player.setDataSource(context, alarmUri);
            player.setAudioStreamType(AudioManager.STREAM_ALARM);
            player.setLooping(true);
            player.prepare();
            player.start();

            // Stop after 60 seconds if not dismissed
            new android.os.Handler(android.os.Looper.getMainLooper())
                    .postDelayed(player::stop, 60_000);
            Log.d(TAG, "Alarm sound started");
        } catch (Exception e) {
            Log.e(TAG, "Failed to play alarm: " + e.getMessage());
        }
    }

    private void sendAck(Context context, String toNumber) {
        try {
            SmsManager smsManager = SmsManager.getDefault();
            String ackBody = ACK_PREFIX + System.currentTimeMillis();
            smsManager.sendTextMessage(toNumber, null, ackBody, null, null);
            Log.i(TAG, "ACK sent to " + toNumber);
        } catch (Exception e) {
            Log.e(TAG, "Failed to send ACK: " + e.getMessage());
        }
    }
}