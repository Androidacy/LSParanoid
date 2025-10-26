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

    // Test various string types
    public static final String URL_STRING = "https://api.example.com/v1/users?page=1&limit=10";
    public static final String JSON_STRING = "{\"name\":\"test\",\"value\":42,\"enabled\":true}";
    public static final String SQL_STRING = "SELECT * FROM users WHERE id = ? AND status = 'active'";
    public static final String ERROR_MESSAGE = "Error: Failed to connect to database. Please check your connection.";
    public static final String MULTILINE_STRING = "This is a multi-line string.\nIt contains multiple lines.\nAnd should be properly obfuscated.";

    // Unicode and international characters
    public static final String UNICODE_STRING = "Unicode test: \u2764\ufe0f \ud83d\ude80 \ud83c\udf89";
    public static final String CHINESE_STRING = "\u4f60\u597d\u4e16\u754c"; // "Hello World" in Chinese
    public static final String EMOJI_STRING = "Emoji test: \ud83d\ude00 \ud83d\ude02 \ud83d\ude0d \ud83d\udc4d";
    public static final String ARABIC_STRING = "\u0645\u0631\u062d\u0628\u0627 \u0628\u0643"; // "Welcome" in Arabic

    // Long strings
    public static final String LONG_STRING = "This is a very long string that should test the chunk loading mechanism of LSParanoid. " +
            "It contains multiple sentences and should be split across multiple chunks. " +
            "The obfuscation processor should handle this correctly and the runtime should be able to " +
            "reassemble the string properly from its obfuscated chunks. " +
            "This is important for real-world applications that may have lengthy strings.";

    // Path and file strings
    public static final String FILE_PATH = "/data/data/com.androidacy.lsparanoid.testapp/files/config.json";
    public static final String PACKAGE_NAME = "com.androidacy.lsparanoid.testapp";

    // HTML/XML strings
    public static final String HTML_STRING = "<html><body><h1>Test</h1><p>Content</p></body></html>";
    public static final String XML_STRING = "<?xml version=\"1.0\"?><root><item id=\"1\">Value</item></root>";

    // Regex patterns
    public static final String EMAIL_REGEX = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$";
    public static final String PHONE_REGEX = "^\\+?[1-9]\\d{1,14}$";

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

    public static String getUrlWithParam(String param) {
        return URL_STRING + "&custom=" + param;
    }

    public static boolean validateEmail(String email) {
        return email != null && email.matches(EMAIL_REGEX);
    }
}
