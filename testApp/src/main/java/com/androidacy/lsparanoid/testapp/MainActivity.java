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
    private static final String PRIVATE_MESSAGE = "This is a private obfuscated string";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, MESSAGE_CREATED);
        Log.v(TAG, PRIVATE_MESSAGE);
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

    public String getTestString() {
        return "Test string with obfuscation: " + TAG;
    }
}
