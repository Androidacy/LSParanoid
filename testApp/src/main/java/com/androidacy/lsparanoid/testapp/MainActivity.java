package com.androidacy.lsparanoid.testapp;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import com.androidacy.lsparanoid.Obfuscate;

/**
 * Simple test activity with obfuscated strings to verify ProGuard rules work correctly.
 */
@Obfuscate
public class MainActivity extends Activity {
    // These strings will be obfuscated by LSParanoid
    public static final String TAG = "MainActivity";
    public static final String MESSAGE_CREATED = "Activity created successfully";
    public static final String MESSAGE_STARTED = "Activity started";
    public static final String MESSAGE_RESUMED = "Activity resumed";
    public static final String MESSAGE_PAUSED = "Activity paused";
    public static final String MESSAGE_STOPPED = "Activity stopped";
    public static final String MESSAGE_DESTROYED = "Activity destroyed";

    private static final String PRIVATE_MESSAGE = "This is a private obfuscated string";
    private static final String ERROR_MESSAGE = "An error occurred during activity lifecycle";
    private static final String BUNDLE_KEY = "saved_state_data";

    // Additional test strings
    public static final String LIFECYCLE_INFO = "Activity lifecycle events are being tracked";
    public static final String API_ENDPOINT = "https://api.testapp.com/v2/data";
    public static final String USER_AGENT = "LSParanoid-TestApp/1.0 (Android)";

    private volatile boolean isActivityReady = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            Log.d(TAG, MESSAGE_CREATED);
            Log.v(TAG, PRIVATE_MESSAGE);
            Log.i(TAG, LIFECYCLE_INFO);

            // Test accessing StringUtils obfuscated strings during onCreate
            String utilTag = StringUtils.UTILITY_TAG;
            Log.d(TAG, "Accessed utility tag: " + utilTag);

            // Test various string types
            validateStrings();

            isActivityReady = true;
        } catch (Exception e) {
            Log.e(TAG, ERROR_MESSAGE, e);
            throw new RuntimeException("Failed to create activity with obfuscated strings", e);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, MESSAGE_STARTED);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, MESSAGE_RESUMED);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, MESSAGE_PAUSED);
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, MESSAGE_STOPPED);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, MESSAGE_DESTROYED);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(BUNDLE_KEY, "Test data: " + TAG);
    }

    public String getTestString() {
        return "Test string with obfuscation: " + TAG;
    }

    public boolean isReady() {
        return isActivityReady;
    }

    /**
     * Validates that all obfuscated strings are accessible and correct.
     * Throws RuntimeException if any string is null or incorrect.
     */
    private void validateStrings() {
        if (TAG == null || !TAG.equals("MainActivity")) {
            throw new RuntimeException("TAG string validation failed");
        }
        if (MESSAGE_CREATED == null || MESSAGE_CREATED.isEmpty()) {
            throw new RuntimeException("MESSAGE_CREATED validation failed");
        }
        if (API_ENDPOINT == null || !API_ENDPOINT.startsWith("https://")) {
            throw new RuntimeException("API_ENDPOINT validation failed");
        }
        Log.d(TAG, "All obfuscated strings validated successfully");
    }
}
