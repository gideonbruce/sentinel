package com.example.core;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.util.Log;

public class SilentSmsReceiver extends BroadcastReceiver {
    private static final String TAG = "SilentSmsReceiver";
    public static final String SOS_PREFIX = "SNTL_SOS:";
    public static final String ACK_PREFIX = "SNTL_ACK:";
    private static MediaPlayer alarmPlayer;
    private static PowerManager.WakeLock wakeLock;

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.e(TAG, "=== onReceive FIRED === action: " + intent.getAction());
        if (!"android.provider.Telephony.SMS_RECEIVED".equals(intent.getAction())) return;
        Bundle bundle = intent.getExtras();
        if (bundle == null) {
            Log.e(TAG, "Bundle is null");
            return;
        }
        Object[] pdus = (Object[]) bundle.get("pdus");
        String format = bundle.getString("format", "3gpp");
        if (pdus == null) {
            Log.e(TAG, "PDUs are null");
            return;
        }
        for (Object pdu : pdus) {
            SmsMessage msg = SmsMessage.createFromPdu((byte[]) pdu, format);
            if (msg == null) continue;
            String body   = msg.getMessageBody();
            String sender = msg.getOriginatingAddress();
            Log.d(TAG, "SMS from: " + sender);
            Log.d(TAG, "Body starts with SOS: " + (body != null && body.startsWith(SOS_PREFIX)));
            if (body == null) continue;
            if (body.startsWith(SOS_PREFIX)) {
                Log.i(TAG, "SNTL_SOS received from " + sender);
                abortBroadcast();
                String payload    = body.substring(SOS_PREFIX.length());
                String senderName = sender; // fallback
                if (payload.contains("\n")) {
                    senderName = payload.substring(0, payload.indexOf("\n")).trim();
                } else if (payload.contains(":")) {
                    String[] parts = payload.split(":", 2);
                    senderName = parts.length > 1 ? parts[1] : sender;
                }
                final Context appContext    = context.getApplicationContext();
                final String finalSenderName = senderName;
                final String finalSender     = sender;
                new Handler(Looper.getMainLooper()).post(() -> {
                    wakeScreen(appContext);
                    setMaxVolume(appContext);
                    playAlarm(appContext);
                    Intent alertIntent = new Intent(appContext, EmergencyAlertActivity.class);
                    alertIntent.putExtra("sender_name", finalSenderName);
                    alertIntent.putExtra("sender_number", finalSender);
                    alertIntent.addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK |
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP
                    );
                    appContext.startActivity(alertIntent);
                });
                sendAck(context, sender);
            } else if (body.startsWith(ACK_PREFIX)) {
                Log.i(TAG, "SNTL_ACK received from " + sender);
                abortBroadcast();
                Intent ackIntent = new Intent("com.example.sentinel.ALERT_ACKNOWLEDGED");
                ackIntent.putExtra("contact_number", sender);
                context.sendBroadcast(ackIntent);

            } else {
                // Not a Sentinel message — do nothing, let it through to default SMS app
                Log.d(TAG, "Non-Sentinel SMS — passing through");
            }
        }
    }

    private void wakeScreen(Context context) {
        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm == null) return;
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
            wakeLock = pm.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK |
                            PowerManager.ACQUIRE_CAUSES_WAKEUP |
                            PowerManager.ON_AFTER_RELEASE,
                    "sentinel:sos_wake"
            );
            wakeLock.acquire(10 * 60 * 1000L);
            Log.d(TAG, "Wake lock acquired");
        } catch (Exception e) {
            Log.e(TAG, "Failed to acquire wake lock: " + e.getMessage());
        }
    }

    private void setMaxVolume(Context context) {
        try {
            AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (audio == null) return;
            audio.setStreamVolume(
                    AudioManager.STREAM_ALARM,
                    audio.getStreamMaxVolume(AudioManager.STREAM_ALARM),
                    0
            );
            Log.d(TAG, "Alarm volume set to max");
        } catch (Exception e) {
            Log.e(TAG, "Failed to set volume: " + e.getMessage());
        }
    }

    private void playAlarm(Context context) {
        try {
            stopAlarm();
            Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (alarmUri == null) {
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            }
            if (alarmUri == null) {
                Log.e(TAG, "No alarm URI found");
                return;
            }
            alarmPlayer = new MediaPlayer();
            alarmPlayer.setDataSource(context, alarmUri);
            alarmPlayer.setAudioAttributes(
                    new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
            );
            alarmPlayer.setLooping(true);
            alarmPlayer.setOnPreparedListener(mp -> {
                mp.start();
                Log.i(TAG, "Alarm started");
            });
            alarmPlayer.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "MediaPlayer error — what: " + what + ", extra: " + extra);
                stopAlarm();
                return true;
            });
            alarmPlayer.prepareAsync();
            new Handler(Looper.getMainLooper())
                    .postDelayed(SilentSmsReceiver::stopAlarm, 60_000);
        } catch (Exception e) {
            Log.e(TAG, "Failed to play alarm: " + e.getMessage());
        }
    }

    public static void stopAlarm() {
        if (alarmPlayer != null) {
            try {
                if (alarmPlayer.isPlaying()) {
                    alarmPlayer.stop();
                }
                alarmPlayer.release();
                Log.i(TAG, "Alarm stopped");
            } catch (Exception e) {
                Log.e(TAG, "Error stopping alarm: " + e.getMessage());
            }
            alarmPlayer = null;
        }
    }

    private void sendAck(Context context, String toNumber) {
        try {
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(
                    toNumber, null,
                    ACK_PREFIX + System.currentTimeMillis(),
                    null, null
            );
            Log.i(TAG, "ACK sent to " + toNumber);
        } catch (Exception e) {
            Log.e(TAG, "Failed to send ACK: " + e.getMessage());
        }
    }
}