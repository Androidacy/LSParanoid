package com.androidacy.lsparanoid.testapp;

import static org.junit.Assert.*;

import android.content.Context;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Instrumented test to verify string obfuscation works correctly in release builds.
 *
 * This test ensures that:
 * 1. Activities with @Obfuscate annotation can be launched
 * 2. Obfuscated strings can be retrieved via reflection (ensureChunkLoaded)
 * 3. The app doesn't crash with NoSuchMethodException in minified builds
 * 4. Chunk loading works correctly for multiple string accesses
 */
@RunWith(AndroidJUnit4.class)
public class ObfuscationTest {

    @Test
    public void testApplicationContext() {
        // Verify the app context is available
        Context context = ApplicationProvider.getApplicationContext();
        assertNotNull("Application context should not be null", context);
        assertEquals("Package name should match",
                "com.androidacy.lsparanoid.testapp",
                context.getPackageName());
    }

    @Test
    public void testObfuscatedActivityLaunches() {
        // Launch the activity with obfuscated strings
        // This will fail with NoSuchMethodException if ProGuard rules are wrong
        // This will also fail if the activity crashes during onCreate
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                assertNotNull("Activity should not be null", activity);
                assertTrue("Activity should be ready (onCreate completed successfully)",
                        activity.isReady());

                // Verify obfuscated TAG field can be accessed
                String tag = MainActivity.TAG;
                assertNotNull("TAG should not be null", tag);
                assertEquals("TAG should be 'MainActivity'", "MainActivity", tag);

                // Verify all lifecycle message strings
                assertEquals("MESSAGE_CREATED should match",
                        "Activity created successfully",
                        MainActivity.MESSAGE_CREATED);
                assertEquals("MESSAGE_STARTED should match",
                        "Activity started",
                        MainActivity.MESSAGE_STARTED);
                assertEquals("MESSAGE_RESUMED should match",
                        "Activity resumed",
                        MainActivity.MESSAGE_RESUMED);
                assertEquals("MESSAGE_PAUSED should match",
                        "Activity paused",
                        MainActivity.MESSAGE_PAUSED);
                assertEquals("MESSAGE_STOPPED should match",
                        "Activity stopped",
                        MainActivity.MESSAGE_STOPPED);
                assertEquals("MESSAGE_DESTROYED should match",
                        "Activity destroyed",
                        MainActivity.MESSAGE_DESTROYED);

                // Verify additional obfuscated strings
                assertEquals("LIFECYCLE_INFO should match",
                        "Activity lifecycle events are being tracked",
                        MainActivity.LIFECYCLE_INFO);
                assertEquals("API_ENDPOINT should match",
                        "https://api.testapp.com/v2/data",
                        MainActivity.API_ENDPOINT);
                assertEquals("USER_AGENT should match",
                        "LSParanoid-TestApp/1.0 (Android)",
                        MainActivity.USER_AGENT);

                // Test method that uses obfuscated strings
                String testString = activity.getTestString();
                assertNotNull("Test string should not be null", testString);
                assertTrue("Test string should contain TAG",
                        testString.contains("MainActivity"));
            });
        } catch (RuntimeException e) {
            // If activity threw RuntimeException during onCreate, test should fail
            fail("Activity crashed during launch with obfuscated strings: " + e.getMessage()
                    + "\nCause: " + (e.getCause() != null ? e.getCause().getMessage() : "none"));
        } catch (Exception e) {
            fail("Activity launch failed with obfuscated strings: " + e.getMessage());
        }
    }

    @Test
    public void testObfuscatedUtilityClass() {
        // Test utility class with obfuscated strings
        try {
            // Access static obfuscated strings - basic messages
            String tag = StringUtils.UTILITY_TAG;
            assertEquals("UTILITY_TAG should match", "StringUtils", tag);

            String msg1 = StringUtils.TEST_MESSAGE_1;
            assertEquals("TEST_MESSAGE_1 should match", "First test message", msg1);

            String msg2 = StringUtils.TEST_MESSAGE_2;
            assertEquals("TEST_MESSAGE_2 should match", "Second test message", msg2);

            String msg3 = StringUtils.TEST_MESSAGE_3;
            assertTrue("TEST_MESSAGE_3 should contain special chars",
                    msg3.contains("!@#$%^&*()"));

            // Test various string types
            String url = StringUtils.URL_STRING;
            assertTrue("URL should be valid", url.startsWith("https://"));
            assertTrue("URL should contain query params", url.contains("?page=1"));

            String json = StringUtils.JSON_STRING;
            assertTrue("JSON should be valid", json.contains("\"name\""));
            assertTrue("JSON should contain value", json.contains("42"));

            String sql = StringUtils.SQL_STRING;
            assertTrue("SQL should be valid", sql.startsWith("SELECT"));
            assertTrue("SQL should contain placeholder", sql.contains("?"));

            String error = StringUtils.ERROR_MESSAGE;
            assertTrue("Error message should contain 'Error'", error.contains("Error"));

            String multiline = StringUtils.MULTILINE_STRING;
            assertTrue("Multiline should contain newlines", multiline.contains("\n"));

            // Test unicode and international strings
            String unicode = StringUtils.UNICODE_STRING;
            assertNotNull("Unicode string should not be null", unicode);
            assertTrue("Unicode string should contain content", unicode.length() > 0);

            String chinese = StringUtils.CHINESE_STRING;
            assertNotNull("Chinese string should not be null", chinese);
            assertTrue("Chinese string should have content", chinese.length() > 0);

            String emoji = StringUtils.EMOJI_STRING;
            assertNotNull("Emoji string should not be null", emoji);
            assertTrue("Emoji string should contain emoji", emoji.contains("Emoji"));

            String arabic = StringUtils.ARABIC_STRING;
            assertNotNull("Arabic string should not be null", arabic);
            assertTrue("Arabic string should have content", arabic.length() > 0);

            // Test long string
            String longStr = StringUtils.LONG_STRING;
            assertTrue("Long string should be long", longStr.length() > 200);
            assertTrue("Long string should contain expected content",
                    longStr.contains("chunk loading"));

            // Test path strings
            String filePath = StringUtils.FILE_PATH;
            assertTrue("File path should start with /", filePath.startsWith("/"));
            assertTrue("File path should contain package", filePath.contains("testapp"));

            String packageName = StringUtils.PACKAGE_NAME;
            assertEquals("Package name should match",
                    "com.androidacy.lsparanoid.testapp", packageName);

            // Test HTML/XML strings
            String html = StringUtils.HTML_STRING;
            assertTrue("HTML should contain tags", html.contains("<html>"));
            assertTrue("HTML should contain content", html.contains("<h1>"));

            String xml = StringUtils.XML_STRING;
            assertTrue("XML should have declaration", xml.contains("<?xml"));
            assertTrue("XML should have root", xml.contains("<root>"));

            // Test regex patterns
            String emailRegex = StringUtils.EMAIL_REGEX;
            assertNotNull("Email regex should not be null", emailRegex);
            assertTrue("Email regex should contain pattern", emailRegex.contains("@"));

            String phoneRegex = StringUtils.PHONE_REGEX;
            assertNotNull("Phone regex should not be null", phoneRegex);

            // Test utility methods
            String concatenated = StringUtils.concatenate("Hello", "World");
            assertEquals("Concatenation should work", "Hello World", concatenated);

            String formatted = StringUtils.getFormattedMessage("Test");
            assertTrue("Formatted message should contain name",
                    formatted.contains("Test"));
            assertTrue("Formatted message should be obfuscated",
                    formatted.contains("obfuscated"));

            String urlWithParam = StringUtils.getUrlWithParam("test123");
            assertTrue("URL with param should contain custom param",
                    urlWithParam.contains("custom=test123"));

            boolean validEmail = StringUtils.validateEmail("test@example.com");
            assertTrue("Valid email should pass validation", validEmail);

            boolean invalidEmail = StringUtils.validateEmail("invalid");
            assertFalse("Invalid email should fail validation", invalidEmail);

            // Test array of messages
            String[] messages = StringUtils.getAllMessages();
            assertEquals("Should return 3 messages", 3, messages.length);
            assertNotNull("Messages should not be null", messages[0]);
            assertNotNull("Messages should not be null", messages[1]);
            assertNotNull("Messages should not be null", messages[2]);

        } catch (Exception e) {
            fail("Utility class methods failed with obfuscated strings: " + e.getMessage()
                    + "\nStack: " + android.util.Log.getStackTraceString(e));
        }
    }

    @Test
    public void testMultipleObfuscatedStringAccess() {
        // Test multiple string accesses to ensure chunk loading works correctly
        // This is important because strings are split into chunks
        try {
            for (int i = 0; i < 100; i++) {
                String tag1 = MainActivity.TAG;
                String tag2 = StringUtils.UTILITY_TAG;
                String msg1 = MainActivity.MESSAGE_CREATED;
                String msg2 = StringUtils.TEST_MESSAGE_1;

                assertNotNull("Iteration " + i + ": TAG should not be null", tag1);
                assertNotNull("Iteration " + i + ": UTILITY_TAG should not be null", tag2);
                assertNotNull("Iteration " + i + ": MESSAGE should not be null", msg1);
                assertNotNull("Iteration " + i + ": TEST_MESSAGE should not be null", msg2);

                // Verify values are consistent
                assertEquals("MainActivity", tag1);
                assertEquals("StringUtils", tag2);
            }
        } catch (Exception e) {
            fail("Multiple string access failed: " + e.getMessage());
        }
    }

    @Test
    public void testConcurrentStringAccess() {
        // Test concurrent access to obfuscated strings (thread safety)
        final int threadCount = 10;
        final int accessesPerThread = 50;
        final Thread[] threads = new Thread[threadCount];
        final boolean[] results = new boolean[threadCount];

        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            threads[i] = new Thread(() -> {
                try {
                    for (int j = 0; j < accessesPerThread; j++) {
                        String tag = MainActivity.TAG;
                        String msg = StringUtils.TEST_MESSAGE_1;
                        assertNotNull(tag);
                        assertNotNull(msg);
                    }
                    results[threadIndex] = true;
                } catch (Exception e) {
                    results[threadIndex] = false;
                }
            });
            threads[i].start();
        }

        // Wait for all threads
        for (Thread thread : threads) {
            try {
                thread.join(5000); // 5 second timeout
            } catch (InterruptedException e) {
                fail("Thread interrupted: " + e.getMessage());
            }
        }

        // Check all threads succeeded
        for (int i = 0; i < threadCount; i++) {
            assertTrue("Thread " + i + " should succeed", results[i]);
        }
    }
}
