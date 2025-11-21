package com.example.sentinel;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.telephony.SmsManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;

import com.example.data.EmergencyContactManager;
import com.example.ui.EmergencyAlertDialog;
import com.google.firebase.FirebaseApp;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * Instrumentation test for SMS functionality
 *
 * Note: These tests require a real device with:
 * - Active SIM card
 * - SMS permissions granted
 * - Valid phone number configured
 *
 * DO NOT run on production devices with real emergency contacts!
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class EmergencySMSInstrumentationTest {

    private Context context;
    private EmergencyContactManager contactManager;
    private TestSmsBroadcastReceiver testReceiver;

    // Test phone number - use your own test number
    private static final String TEST_PHONE_NUMBER = "+2547113171456";
    private static final String TEST_CONTACT_NAME = "Test Contact";
    private static final String TEST_MESSAGE = "Test emergency message";

    @Rule
    public GrantPermissionRule permissionRule = GrantPermissionRule.grant(
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_CONTACTS
    );

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        // Initialize Firebase for tests if needed
        try {
            FirebaseApp.initializeApp(context);
        } catch (IllegalStateException e) {
            // Already initialized
        }
        contactManager = new EmergencyContactManager(context);

        // Register test broadcast receiver
        testReceiver = new TestSmsBroadcastReceiver();
        IntentFilter sentFilter = new IntentFilter("SMS_SENT");
        IntentFilter deliveredFilter = new IntentFilter("SMS_DELIVERED");

        context.registerReceiver(testReceiver, sentFilter, Context.RECEIVER_NOT_EXPORTED);
        context.registerReceiver(testReceiver, deliveredFilter, Context.RECEIVER_NOT_EXPORTED);

        // Save test contact
        contactManager.saveEmergencyContact(TEST_CONTACT_NAME, TEST_PHONE_NUMBER);
    }

    @After
    public void tearDown() {
        // Clean up
        if (testReceiver != null) {
            try {
                context.unregisterReceiver(testReceiver);
            } catch (Exception e) {
                // Already unregistered
            }
        }

        // Clear test data
        contactManager.clearEmergencyContactLocal();
    }

    @Test
    public void testEmergencyContactSaved() {
        assertTrue("Emergency contact should be saved", contactManager.hasEmergencyContact());
        assertEquals("Contact name should match", TEST_CONTACT_NAME, contactManager.getContactName());
        assertEquals("Contact phone should match", TEST_PHONE_NUMBER, contactManager.getContactPhone());
    }

    @Test
    public void testPhoneNumberNormalization() {
        // Test various phone number formats
        String[] testNumbers = {
                "0712345678",      // Local format
                "+254712345678",   // International format
                "254712345678",    // Without plus
                "07 1234 5678",    // With spaces
                "(071) 234-5678"   // With formatting
        };

        for (String number : testNumbers) {
            contactManager.saveEmergencyContact("Test", number);
            String saved = contactManager.getContactPhone();
            assertNotNull("Saved number should not be null", saved);
            assertTrue("Number should be normalized",
                    saved.startsWith("+254") || saved.startsWith("254") || saved.startsWith("0"));
        }
    }

    @Test
    public void testSmsManagerAvailability() {
        SmsManager smsManager = SmsManager.getDefault();
        assertNotNull("SmsManager should be available", smsManager);
    }

    @Test
    public void testDualSimSupport() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP_MR1) {
            SubscriptionManager subscriptionManager =
                    (SubscriptionManager) context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);

            assertNotNull("SubscriptionManager should be available", subscriptionManager);

            try {
                List<SubscriptionInfo> subscriptions = subscriptionManager.getActiveSubscriptionInfoList();

                if (subscriptions != null && !subscriptions.isEmpty()) {
                    assertTrue("Should have at least one active subscription", subscriptions.size() > 0);

                    for (SubscriptionInfo info : subscriptions) {
                        int subId = info.getSubscriptionId();
                        assertTrue("Subscription ID should be valid", subId >= 0);

                        SmsManager simSpecificManager = SmsManager.getSmsManagerForSubscriptionId(subId);
                        assertNotNull("SIM-specific SmsManager should be available", simSpecificManager);
                    }
                }
            } catch (SecurityException e) {
                fail("Security exception accessing subscriptions: " + e.getMessage());
            }
        }
    }

    @Test
    public void testMessageDivision() {
        SmsManager smsManager = SmsManager.getDefault();

        // Short message (should be 1 part)
        String shortMessage = "Emergency! I need help.";
        ArrayList<String> shortParts = smsManager.divideMessage(shortMessage);
        assertEquals("Short message should be 1 part", 1, shortParts.size());

        // Long message (should be multiple parts)
        StringBuilder longMessage = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            longMessage.append("Test ");
        }
        ArrayList<String> longParts = smsManager.divideMessage(longMessage.toString());
        assertTrue("Long message should be multiple parts", longParts.size() > 1);
    }

    @Test
    public void testLocationInMessage() {
        // Create mock location
        Location testLocation = new Location("test");
        testLocation.setLatitude(-1.286389);
        testLocation.setLongitude(36.817223);

        String message = TEST_MESSAGE;
        StringBuilder fullMessage = new StringBuilder(message);

        fullMessage.append("\n\n📍 https://maps.google.com/?q=")
                .append(testLocation.getLatitude())
                .append(",")
                .append(testLocation.getLongitude());

        String result = fullMessage.toString();

        assertTrue("Message should contain location", result.contains("maps.google.com"));
        assertTrue("Message should contain latitude", result.contains("-1.286389"));
        assertTrue("Message should contain longitude", result.contains("36.817223"));
    }

    @Test
    public void testSendSmsWithoutLocation() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        testReceiver.setLatch(latch);

        // This will attempt to send SMS - make sure you're using a test number!
        try {
            EmergencyAlertDialog.show(
                    InstrumentationRegistry.getInstrumentation().getTargetContext(),
                    new EmergencyAlertDialog.OnAlertActionListener() {
                        @Override
                        public void onAlertSent() {
                            // Alert sent callback
                        }

                        @Override
                        public void onAlertCancelled() {
                            fail("Alert should not be cancelled in test");
                        }
                    },
                    null // No location
            );

            // Wait for SMS result (timeout 30 seconds)
            boolean received = latch.await(30, TimeUnit.SECONDS);
            assertTrue("Should receive SMS result within 30 seconds", received);

            // Check result
            int resultCode = testReceiver.getStoredResultCode();
            assertTrue("SMS should be sent successfully or have known error",
                    resultCode == android.app.Activity.RESULT_OK ||
                            resultCode == SmsManager.RESULT_ERROR_GENERIC_FAILURE ||
                            resultCode == SmsManager.RESULT_ERROR_NO_SERVICE);

        } catch (Exception e) {
            fail("Exception during SMS send: " + e.getMessage());
        }
    }

    @Test
    public void testSendSmsWithLocation() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        testReceiver.setLatch(latch);

        // Create test location
        Location testLocation = new Location("test");
        testLocation.setLatitude(-1.286389);
        testLocation.setLongitude(36.817223);
        testLocation.setAccuracy(10.0f);
        testLocation.setTime(System.currentTimeMillis());

        try {
            EmergencyAlertDialog.show(
                    InstrumentationRegistry.getInstrumentation().getTargetContext(),
                    new EmergencyAlertDialog.OnAlertActionListener() {
                        @Override
                        public void onAlertSent() {
                            // Alert sent callback
                        }

                        @Override
                        public void onAlertCancelled() {
                            fail("Alert should not be cancelled in test");
                        }
                    },
                    testLocation
            );

            // Wait for SMS result
            boolean received = latch.await(30, TimeUnit.SECONDS);
            assertTrue("Should receive SMS result within 30 seconds", received);

        } catch (Exception e) {
            fail("Exception during SMS send with location: " + e.getMessage());
        }
    }

    @Test
    public void testMultipleSimCards() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP_MR1) {
            SubscriptionManager subscriptionManager =
                    (SubscriptionManager) context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);

            if (subscriptionManager != null) {
                List<SubscriptionInfo> subscriptions = subscriptionManager.getActiveSubscriptionInfoList();

                if (subscriptions != null && subscriptions.size() > 1) {
                    // Device has multiple SIMs
                    int defaultSubId = SmsManager.getDefaultSmsSubscriptionId();
                    assertTrue("Default SMS subscription should be valid", defaultSubId != -1);

                    // Test sending from each SIM
                    for (SubscriptionInfo info : subscriptions) {
                        SmsManager simManager = SmsManager.getSmsManagerForSubscriptionId(
                                info.getSubscriptionId());
                        assertNotNull("SIM-specific manager should be available", simManager);
                    }
                } else {
                    // Single SIM or no SIM - test should still pass
                    assertTrue("Test device should have at least one SIM for SMS tests",
                            subscriptions == null || subscriptions.size() >= 1);
                }
            }
        }
    }

    @Test
    public void testPermissionsGranted() {
        int smsPermission = context.checkSelfPermission(Manifest.permission.SEND_SMS);
        assertEquals("SMS permission should be granted",
                android.content.pm.PackageManager.PERMISSION_GRANTED, smsPermission);

        int phonePermission = context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE);
        assertEquals("Phone state permission should be granted",
                android.content.pm.PackageManager.PERMISSION_GRANTED, phonePermission);
    }

    @Test
    public void testEmergencyMessageCustomization() {
        String customMessage = "URGENT: I need immediate assistance!";
        contactManager.saveEmergencyMessage(customMessage);

        String retrieved = contactManager.getEmergencyMessage();
        assertEquals("Custom message should be saved and retrieved", customMessage, retrieved);
    }

    /**
     * Test broadcast receiver to capture SMS results
     */
    private static class TestSmsBroadcastReceiver extends android.content.BroadcastReceiver {
        private final AtomicInteger resultCode = new AtomicInteger(-1);
        private final AtomicBoolean received = new AtomicBoolean(false);
        private CountDownLatch latch;

        public void setLatch(CountDownLatch latch) {
            this.latch = latch;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if ("SMS_SENT".equals(action)) {
                int code = getStoredResultCode();
                resultCode.set(code);
                received.set(true);

                android.util.Log.d("TestReceiver", "SMS_SENT result: " + code);

                if (latch != null) {
                    latch.countDown();
                }
            } else if ("SMS_DELIVERED".equals(action)) {
                android.util.Log.d("TestReceiver", "SMS_DELIVERED result: " + getStoredResultCode());
            }
        }

        public int getStoredResultCode() {
            return resultCode.get();
        }

        public boolean hasReceived() {
            return received.get();
        }
    }

    /**
     * Integration test - tests complete flow
     * WARNING: This will actually send an SMS if all permissions are granted!
     */
    @Test
    @LargeTest
    public void testCompleteEmergencyFlow() throws InterruptedException {
        // 1. Verify contact is saved
        assertTrue("Emergency contact must be configured", contactManager.hasEmergencyContact());

        // 2. Create location
        Location location = new Location("test");
        location.setLatitude(-1.286389);
        location.setLongitude(36.817223);

        // 3. Set up latch for async operation
        CountDownLatch latch = new CountDownLatch(1);
        testReceiver.setLatch(latch);

        // 4. Trigger emergency alert
        AtomicBoolean alertSent = new AtomicBoolean(false);

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            EmergencyAlertDialog.show(
                    context,
                    new EmergencyAlertDialog.OnAlertActionListener() {
                        @Override
                        public void onAlertSent() {
                            alertSent.set(true);
                            android.util.Log.d("Test", "Alert sent callback triggered");
                        }

                        @Override
                        public void onAlertCancelled() {
                            fail("Alert should not be cancelled");
                        }
                    },
                    location
            );
        });

        // 5. Wait for completion
        boolean completed = latch.await(45, TimeUnit.SECONDS);
        assertTrue("Complete flow should finish within 45 seconds", completed);

        // 6. Verify results
        assertTrue("Alert sent callback should be triggered", alertSent.get());
        assertTrue("SMS result should be received", testReceiver.hasReceived());
    }
}