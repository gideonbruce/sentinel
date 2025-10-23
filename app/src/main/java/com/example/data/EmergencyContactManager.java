package com.example.data;

import android.content.Context;
import android.content.SharedPreferences;

public class EmergencyContactManager {
    private static final String PREFS_NAME = "EmergencyContactPrefs";
    private static final String KEY_CONTACT_NAME = "emergency_contact_name";
    private static final String KEY_CONTACT_PHONE = "emergency_contact_phone";

    private SharedPreferences prefs;

    public EmergencyContactManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void saveEmergencyContact(String name, String phoneNumber) {
        prefs.edit()
                .putString(KEY_CONTACT_NAME, name)
                .putString(KEY_CONTACT_PHONE, phoneNumber)
                .apply();
    }

    public String getContactName() {
        return prefs.getString(KEY_CONTACT_NAME, null);
    }

    public String getContactPhone() {
        return prefs.getString(KEY_CONTACT_PHONE, null);
    }

    public boolean hasEmergencyContact() {
        return getContactPhone() != null && !getContactPhone().isEmpty();
    }

    public void clearEmergencyContact() {
        prefs.edit()
                .remove(KEY_CONTACT_NAME)
                .remove(KEY_CONTACT_PHONE)
                .apply();
    }
}
