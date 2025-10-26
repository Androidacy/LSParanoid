package com.androidacy.lsparanoid.testapp;

import com.androidacy.lsparanoid.Obfuscate;

/**
 * Utility class with obfuscated strings for testing.
 */
@Obfuscate
public class StringUtils {
    public static final String UTILITY_TAG = "StringUtils";
    public static final String TEST_MESSAGE_1 = "First test message";
    public static final String TEST_MESSAGE_2 = "Second test message";
    public static final String TEST_MESSAGE_3 = "Third test message with special chars: !@#$%^&*()";

    private StringUtils() {
        // Utility class
    }

    public static String concatenate(String a, String b) {
        return a + " " + b;
    }

    public static String getFormattedMessage(String name) {
        return "Hello, " + name + "! This string is obfuscated.";
    }

    public static String[] getAllMessages() {
        return new String[]{
            TEST_MESSAGE_1,
            TEST_MESSAGE_2,
            TEST_MESSAGE_3
        };
    }
}
