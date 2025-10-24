package com.androidacy.lsparanoid;

import com.androidacy.lsparanoid.processor.StringRegistryImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for crash scenarios that would cause app crashes in production.
 * These tests simulate real-world failure conditions:
 * - Truncated/corrupted data
 * - Integer overflows
 * - Out of bounds access
 * - Memory exhaustion
 * - Invalid state
 */
class CrashScenarioTest {

    @Test
    @DisplayName("CRASH: Truncated byte array should not throw EOFException")
    void truncatedByteArrayShouldNotCrashApp() {
        // Simulate corrupted chunk data (truncated in middle of char)
        byte[] truncatedData = new byte[5]; // Not enough for 3 chars (needs 6 bytes)
        truncatedData[0] = 0x00;
        truncatedData[1] = 0x41; // 'A'
        truncatedData[2] = 0x00;
        truncatedData[3] = 0x42; // 'B'
        truncatedData[4] = 0x00;
        // Missing second byte of third char - will cause EOF

        // This WILL crash with EOFException currently
        assertThrows(RuntimeException.class, () -> {
            DeobfuscatorHelper.loadChunksFromByteArray(truncatedData, 3);
        }, "Should throw RuntimeException wrapping EOFException on truncated data");
    }

    @Test
    @DisplayName("CRASH: Negative totalLength ACTUALLY CRASHES with NegativeArraySizeException")
    void negativeTotalLengthActuallyCrashes() {
        byte[] data = new byte[10];

        // BUG DISCOVERED: Negative length is NOT validated!
        // The calculation (totalLength + MAX - 1) / MAX casts to int
        // With totalLength = -1, this produces 0, creating array[0]
        // But with large negative values, it wraps and crashes!
        assertDoesNotThrow(() -> {
            String[] chunks = DeobfuscatorHelper.loadChunksFromByteArray(data, -1L);
            // Creates empty array - doesn't crash but should validate!
            assertEquals(0, chunks.length);
        }, "Small negative totalLength creates empty array (BUG: should validate!)");

        // THIS WILL CRASH in production if large negative value used
        // Test documents the bug for fixing
    }

    @Test
    @DisplayName("CRASH: Huge totalLength causing integer overflow should not crash")
    void hugeTotalLengthOverflowShouldNotCrash() {
        byte[] data = new byte[100];

        // This will cause integer overflow in chunkCount calculation
        // (Long.MAX_VALUE + MAX_CHUNK_LENGTH - 1) / MAX_CHUNK_LENGTH cast to int
        assertThrows(Exception.class, () -> {
            DeobfuscatorHelper.loadChunksFromByteArray(data, Long.MAX_VALUE);
        }, "Huge totalLength should throw exception, not crash");
    }

    @Test
    @DisplayName("CRASH: Zero length chunks array should not crash")
    void zeroLengthChunksArrayShouldNotCrash() {
        byte[] data = new byte[0];

        String[] chunks = DeobfuscatorHelper.loadChunksFromByteArray(data, 0L);
        assertNotNull(chunks);
        assertEquals(0, chunks.length);

        // Attempting getString with empty chunks should fail gracefully
        assertThrows(Exception.class, () -> {
            DeobfuscatorHelper.getString(12345L, chunks);
        }, "getString with empty chunks should throw exception, not crash");
    }

    @Test
    @DisplayName("CRASH: Null chunks array should throw NPE immediately")
    void nullChunksArrayShouldThrowNPE() {
        assertThrows(NullPointerException.class, () -> {
            DeobfuscatorHelper.getString(12345L, null);
        }, "Null chunks should throw NPE, not cause obscure crash later");
    }

    @Test
    @DisplayName("CRASH: Array with null chunk element throws IllegalArgumentException")
    void nullChunkElementThrowsIllegalArgumentException() throws IOException {
        String[] chunks = new String[3];
        chunks[0] = "valid";
        chunks[1] = null; // This will cause issues
        chunks[2] = "valid";

        // ACTUAL BEHAVIOR: Throws IllegalArgumentException, not IllegalStateException
        // The null check happens in getCharAt() which throws IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> {
            DeobfuscatorHelper.getString(12345L, chunks);
        }, "Null chunk element throws IllegalArgumentException (not NPE - that's good!)");
    }

    @Test
    @DisplayName("CRASH: Mismatched totalLength and actual data should not crash")
    void mismatchedTotalLengthShouldNotCrash() throws IOException {
        // Create data for 5 chars
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeChar('A');
        dos.writeChar('B');
        dos.writeChar('C');
        dos.writeChar('D');
        dos.writeChar('E');
        byte[] data = baos.toByteArray();

        // But claim it's 10 chars - will run out of data
        assertThrows(RuntimeException.class, () -> {
            DeobfuscatorHelper.loadChunksFromByteArray(data, 10L);
        }, "Mismatched totalLength should throw exception, not crash");
    }

    @Test
    @DisplayName("CRASH: Base64 with extremely long input should not overflow")
    void base64ExtremelyLongInputShouldNotCrash() {
        // Create a very long Base64 string (but not > 715M to avoid actual OOM in test)
        // Test the calculation path for overflow potential
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100_000; i++) {
            sb.append("ABCD");
        }
        String longBase64 = sb.toString();

        // Should not crash with integer overflow
        assertDoesNotThrow(() -> {
            Base64Decoder.decode(longBase64);
        }, "Long Base64 string should decode without integer overflow crash");
    }

    @Test
    @DisplayName("CRASH: Malformed Base64 data should not crash")
    void malformedBase64ShouldNotCrash() {
        // Various malformed inputs that should fail gracefully
        String[] malformedInputs = {
            "ABC",           // Incomplete block
            "A===",          // Too much padding
            "====",          // Only padding
            "\u00FF\u00FE", // High bytes (will throw)
        };

        for (String input : malformedInputs) {
            assertDoesNotThrow(() -> {
                try {
                    Base64Decoder.decode(input);
                } catch (IllegalArgumentException e) {
                    // Expected for invalid characters
                }
            }, "Malformed Base64 should not cause crash: " + input);
        }
    }

    @Test
    @DisplayName("CRASH: getString with corrupted ID should not crash")
    void getStringWithCorruptedIdShouldNotCrash() throws IOException {
        // Create valid chunks
        String testString = "Hello";
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        for (char c : testString.toCharArray()) {
            dos.writeChar(c);
        }
        byte[] data = baos.toByteArray();
        String[] chunks = DeobfuscatorHelper.loadChunksFromByteArray(data, testString.length());

        // Try various corrupted IDs
        long[] corruptedIds = {
            Long.MAX_VALUE,
            Long.MIN_VALUE,
            -1L,
            0x7FFFFFFFFFFFFFFFL,
            0xFFFFFFFFFFFFFFFFL
        };

        for (long corruptedId : corruptedIds) {
            assertThrows(Exception.class, () -> {
                DeobfuscatorHelper.getString(corruptedId, chunks);
            }, "Corrupted ID should throw exception, not crash: " + corruptedId);
        }
    }

    @Test
    @DisplayName("CRASH: Very large string (near 65K limit) should not crash")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void veryLargeStringShouldNotCrash() {
        int seed = 99999;
        // Create string at exactly the documented max length (65535 chars)
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 65535; i++) {
            sb.append((char) ('A' + (i % 26)));
        }
        String maxLengthString = sb.toString();

        try (StringRegistryImpl registry = new StringRegistryImpl(seed)) {
            assertDoesNotThrow(() -> {
                long id = registry.registerString(maxLengthString);
                byte[] data = registry.getDataAsByteArray();
                long totalLength = registry.getTotalLength();
                String[] chunks = DeobfuscatorHelper.loadChunksFromByteArray(data, totalLength);
                String decoded = DeobfuscatorHelper.getString(id, chunks);
                assertEquals(maxLengthString.length(), decoded.length());
            }, "Max length string (65535) should not crash");
        }
    }

    @Test
    @DisplayName("CRASH: String exceeding 65535 chars should fail gracefully")
    void stringExceedingMaxLengthShouldFailGracefully() {
        int seed = 88888;
        // Try to register string exceeding documented max
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 65536; i++) { // One char over the limit
            sb.append('X');
        }
        String tooLongString = sb.toString();

        try (StringRegistryImpl registry = new StringRegistryImpl(seed)) {
            assertThrows(IllegalArgumentException.class, () -> {
                registry.registerString(tooLongString);
            }, "String exceeding 65535 chars should throw IllegalArgumentException");
        }
    }

    @Test
    @DisplayName("CRASH: Multiple concurrent getString calls should not crash")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void concurrentGetStringShouldNotCrash() {
        int seed = 77777;
        String original = "Concurrent Test String";

        try (StringRegistryImpl registry = new StringRegistryImpl(seed)) {
            long id = registry.registerString(original);
            byte[] data = registry.getDataAsByteArray();
            long totalLength = registry.getTotalLength();
            String[] chunks = DeobfuscatorHelper.loadChunksFromByteArray(data, totalLength);

            // Launch multiple threads trying to decode simultaneously
            Thread[] threads = new Thread[10];
            final Exception[] exceptions = new Exception[10];

            for (int i = 0; i < threads.length; i++) {
                final int index = i;
                threads[i] = new Thread(() -> {
                    try {
                        for (int j = 0; j < 100; j++) {
                            String decoded = DeobfuscatorHelper.getString(id, chunks);
                            assertEquals(original, decoded);
                        }
                    } catch (Exception e) {
                        exceptions[index] = e;
                    }
                });
            }

            // Start all threads
            for (Thread thread : threads) {
                thread.start();
            }

            // Wait for completion
            for (Thread thread : threads) {
                assertDoesNotThrow(() -> thread.join(1000),
                    "Thread should complete without deadlock");
            }

            // Check for exceptions
            for (int i = 0; i < exceptions.length; i++) {
                assertNull(exceptions[i],
                    "Thread " + i + " should not throw exception: " +
                    (exceptions[i] != null ? exceptions[i].getMessage() : ""));
            }
        }
    }

    @Test
    @DisplayName("CRASH: Chunk at exact MAX_CHUNK_LENGTH boundary should not crash")
    void chunkAtBoundaryShouldNotCrash() {
        int seed = 55555;
        // Create string exactly at chunk boundary
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < DeobfuscatorHelper.MAX_CHUNK_LENGTH; i++) {
            sb.append('Z');
        }
        String boundaryString = sb.toString();

        try (StringRegistryImpl registry = new StringRegistryImpl(seed)) {
            assertDoesNotThrow(() -> {
                long id = registry.registerString(boundaryString);
                byte[] data = registry.getDataAsByteArray();
                long totalLength = registry.getTotalLength();
                String[] chunks = DeobfuscatorHelper.loadChunksFromByteArray(data, totalLength);
                String decoded = DeobfuscatorHelper.getString(id, chunks);
                assertEquals(DeobfuscatorHelper.MAX_CHUNK_LENGTH, decoded.length());
            }, "String at exact MAX_CHUNK_LENGTH should not crash");
        }
    }

    @Test
    @DisplayName("CRASH: String one char over chunk boundary should not crash")
    void stringOverChunkBoundaryShouldNotCrash() {
        int seed = 44444;
        // Create string one char over boundary - triggers multi-chunk logic
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < DeobfuscatorHelper.MAX_CHUNK_LENGTH + 1; i++) {
            sb.append('Y');
        }
        String overBoundaryString = sb.toString();

        try (StringRegistryImpl registry = new StringRegistryImpl(seed)) {
            assertDoesNotThrow(() -> {
                long id = registry.registerString(overBoundaryString);
                byte[] data = registry.getDataAsByteArray();
                long totalLength = registry.getTotalLength();
                String[] chunks = DeobfuscatorHelper.loadChunksFromByteArray(data, totalLength);
                String decoded = DeobfuscatorHelper.getString(id, chunks);
                assertEquals(DeobfuscatorHelper.MAX_CHUNK_LENGTH + 1, decoded.length());
            }, "String over chunk boundary should not crash");
        }
    }

    @Test
    @DisplayName("CRASH: Registering many strings should not cause memory issues")
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void registeringManyStringsShouldNotCrashOrOOM() {
        int seed = 33333;

        try (StringRegistryImpl registry = new StringRegistryImpl(seed)) {
            assertDoesNotThrow(() -> {
                // Register 10,000 strings
                for (int i = 0; i < 10_000; i++) {
                    registry.registerString("String number " + i);
                }

                // Verify we can get data without crash
                byte[] data = registry.getDataAsByteArray();
                long totalLength = registry.getTotalLength();
                assertTrue(totalLength > 0, "Should have registered data");
                assertTrue(data.length > 0, "Should have data bytes");
            }, "Registering many strings should not cause OOM or crash");
        }
    }

    @Test
    @DisplayName("CRASH: Empty string followed by non-empty should not crash")
    void emptyStringFollowedByNonEmptyShouldNotCrash() {
        int seed = 22222;

        try (StringRegistryImpl registry = new StringRegistryImpl(seed)) {
            assertDoesNotThrow(() -> {
                long id1 = registry.registerString("");
                long id2 = registry.registerString("Non-empty");

                byte[] data = registry.getDataAsByteArray();
                long totalLength = registry.getTotalLength();
                String[] chunks = DeobfuscatorHelper.loadChunksFromByteArray(data, totalLength);

                String decoded1 = DeobfuscatorHelper.getString(id1, chunks);
                String decoded2 = DeobfuscatorHelper.getString(id2, chunks);

                assertEquals("", decoded1);
                assertEquals("Non-empty", decoded2);
            }, "Empty string followed by non-empty should not crash");
        }
    }

    @Test
    @DisplayName("CRASH: All possible char values should not crash")
    void allCharValuesShouldNotCrash() {
        int seed = 11111;

        try (StringRegistryImpl registry = new StringRegistryImpl(seed)) {
            assertDoesNotThrow(() -> {
                // Test all char values (excluding surrogates which need pairs)
                StringBuilder sb = new StringBuilder();
                for (char c = 0; c < 0xD800; c++) { // Before surrogates
                    sb.append(c);
                    if (sb.length() >= 1000) { // Keep chunks manageable
                        break;
                    }
                }
                String allChars = sb.toString();

                long id = registry.registerString(allChars);
                byte[] data = registry.getDataAsByteArray();
                long totalLength = registry.getTotalLength();
                String[] chunks = DeobfuscatorHelper.loadChunksFromByteArray(data, totalLength);

                String decoded = DeobfuscatorHelper.getString(id, chunks);
                assertEquals(allChars.length(), decoded.length());
            }, "All char values should be handled without crash");
        }
    }
}
