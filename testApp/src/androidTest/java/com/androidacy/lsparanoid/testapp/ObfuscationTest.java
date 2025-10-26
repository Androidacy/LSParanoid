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
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                assertNotNull("Activity should not be null", activity);

                // Verify obfuscated TAG field can be accessed
                String tag = MainActivity.TAG;
                assertNotNull("TAG should not be null", tag);
                assertEquals("TAG should be 'MainActivity'", "MainActivity", tag);

                // Verify other obfuscated strings
                assertEquals("MESSAGE_CREATED should match",
                        "Activity created successfully",
                        MainActivity.MESSAGE_CREATED);
                assertEquals("MESSAGE_STARTED should match",
                        "Activity started",
                        MainActivity.MESSAGE_STARTED);
                assertEquals("MESSAGE_RESUMED should match",
                        "Activity resumed",
                        MainActivity.MESSAGE_RESUMED);

                // Test method that uses obfuscated strings
                String testString = activity.getTestString();
                assertNotNull("Test string should not be null", testString);
                assertTrue("Test string should contain TAG",
                        testString.contains("MainActivity"));
            });
        } catch (Exception e) {
            fail("Activity launch failed with obfuscated strings: " + e.getMessage());
        }
    }

    @Test
    public void testObfuscatedUtilityClass() {
        // Test utility class with obfuscated strings
        try {
            // Access static obfuscated strings
            String tag = StringUtils.UTILITY_TAG;
            assertEquals("UTILITY_TAG should match", "StringUtils", tag);

            String msg1 = StringUtils.TEST_MESSAGE_1;
            assertEquals("TEST_MESSAGE_1 should match", "First test message", msg1);

            String msg2 = StringUtils.TEST_MESSAGE_2;
            assertEquals("TEST_MESSAGE_2 should match", "Second test message", msg2);

            String msg3 = StringUtils.TEST_MESSAGE_3;
            assertTrue("TEST_MESSAGE_3 should contain special chars",
                    msg3.contains("!@#$%^&*()"));

            // Test utility methods
            String concatenated = StringUtils.concatenate("Hello", "World");
            assertEquals("Concatenation should work", "Hello World", concatenated);

            String formatted = StringUtils.getFormattedMessage("Test");
            assertTrue("Formatted message should contain name",
                    formatted.contains("Test"));
            assertTrue("Formatted message should be obfuscated",
                    formatted.contains("obfuscated"));

            // Test array of messages
            String[] messages = StringUtils.getAllMessages();
            assertEquals("Should return 3 messages", 3, messages.length);
            assertNotNull("Messages should not be null", messages[0]);
            assertNotNull("Messages should not be null", messages[1]);
            assertNotNull("Messages should not be null", messages[2]);

        } catch (Exception e) {
            fail("Utility class methods failed with obfuscated strings: " + e.getMessage());
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
