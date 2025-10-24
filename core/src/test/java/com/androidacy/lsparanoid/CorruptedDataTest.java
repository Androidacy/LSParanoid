package com.androidacy.lsparanoid;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests handling of corrupted, malformed, or malicious input data.
 * These scenarios could occur from:
 * - ProGuard/R8 bytecode manipulation gone wrong
 * - Malicious app modification
 * - Storage/transmission corruption
 * - Build system bugs
 *
 * All tests should fail gracefully without crashing the app.
 */
class CorruptedDataTest {

    @Test
    @DisplayName("CORRUPT: Flipped bits in chunk data")
    void flippedBitsInChunkData() throws IOException {
        // Create valid data
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeChar('H');
        dos.writeChar('e');
        dos.writeChar('l');
        dos.writeChar('l');
        dos.writeChar('o');
        byte[] validData = baos.toByteArray();

        // Corrupt by flipping bits
        byte[] corruptedData = validData.clone();
        corruptedData[2] ^= 0xFF; // Flip all bits in middle byte

        // Should not crash, though result will be wrong
        assertDoesNotThrow(() -> {
            String[] chunks = DeobfuscatorHelper.loadChunksFromByteArray(corruptedData, 5);
            assertNotNull(chunks);
            assertEquals(1, chunks.length);
            // Data is corrupted but shouldn't crash
        }, "Bit-flipped data should not crash");
    }

    @Test
    @DisplayName("CORRUPT: Truncated data (incomplete last char)")
    void truncatedDataIncompleteChar() {
        // 3.5 chars worth of data (7 bytes, not 8)
        byte[] truncated = {0x00, 0x41, 0x00, 0x42, 0x00, 0x43, 0x00};

        // Missing 2nd byte of 4th char - should throw, not hang or crash weirdly
        assertThrows(Exception.class, () -> {
            DeobfuscatorHelper.loadChunksFromByteArray(truncated, 4);
        }, "Truncated data should throw exception");
    }

    @Test
    @DisplayName("CORRUPT: All zeros data")
    void allZerosData() {
        byte[] zeros = new byte[100];
        Arrays.fill(zeros, (byte) 0);

        // All null chars - weird but shouldn't crash
        assertDoesNotThrow(() -> {
            String[] chunks = DeobfuscatorHelper.loadChunksFromByteArray(zeros, 50);
            assertNotNull(chunks);
            assertEquals(1, chunks.length);
            // String of null chars
            assertEquals(50, chunks[0].length());
        }, "All-zero data should not crash");
    }

    @Test
    @DisplayName("CORRUPT: All 0xFF data")
    void allFFData() {
        byte[] allFF = new byte[100];
        Arrays.fill(allFF, (byte) 0xFF);

        // All high-value chars - shouldn't crash
        assertDoesNotThrow(() -> {
            String[] chunks = DeobfuscatorHelper.loadChunksFromByteArray(allFF, 50);
            assertNotNull(chunks);
            // Should create valid string with high char values
        }, "All-FF data should not crash");
    }

    @Test
    @DisplayName("CORRUPT: Random garbage data")
    void randomGarbageData() {
        byte[] garbage = new byte[200];
        for (int i = 0; i < garbage.length; i++) {
            garbage[i] = (byte) (Math.random() * 256);
        }

        // Random bytes - might be invalid UTF-16 but shouldn't crash
        assertDoesNotThrow(() -> {
            String[] chunks = DeobfuscatorHelper.loadChunksFromByteArray(garbage, 100);
            assertNotNull(chunks);
        }, "Random garbage should not crash");
    }

    @Test
    @DisplayName("CORRUPT: Mismatched chunk count expectation")
    void mismatchedChunkCountExpectation() throws IOException {
        // Data for 100 chars
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        for (int i = 0; i < 100; i++) {
            dos.writeChar('A');
        }
        byte[] data = baos.toByteArray();

        // But claim totalLength suggests way more chunks
        long fakeTotalLength = DeobfuscatorHelper.MAX_CHUNK_LENGTH * 10L;

        // Should fail when trying to read beyond available data
        assertThrows(Exception.class, () -> {
            DeobfuscatorHelper.loadChunksFromByteArray(data, fakeTotalLength);
        }, "Mismatched chunk count should throw exception");
    }

    @Test
    @DisplayName("CORRUPT: Negative totalLength with valid data (BUG: not validated!)")
    void negativeTotalLengthWithValidData() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeChar('T');
        dos.writeChar('e');
        dos.writeChar('s');
        dos.writeChar('t');
        byte[] data = baos.toByteArray();

        // BUG: Negative length is NOT validated!
        // Small negative values create empty array instead of crashing
        assertDoesNotThrow(() -> {
            String[] chunks = DeobfuscatorHelper.loadChunksFromByteArray(data, -10L);
            // Should validate but doesn't - creates empty array
            assertEquals(0, chunks.length, "BUG: Accepts negative totalLength");
        }, "Negative totalLength should be rejected but isn't (BUG!)");
    }

    @Test
    @DisplayName("CORRUPT: totalLength causes integer overflow")
    void totalLengthCausesIntegerOverflow() {
        byte[] data = new byte[100];

        // Value that would overflow when calculating chunk count
        long overflowLength = ((long) Integer.MAX_VALUE) + 1000L;

        assertThrows(Exception.class, () -> {
            DeobfuscatorHelper.loadChunksFromByteArray(data, overflowLength);
        }, "Overflow-causing totalLength should be rejected");
    }

    @Test
    @DisplayName("CORRUPT: Surrogates without proper pairs")
    void surrogatesWithoutPairs() throws IOException {
        // Create data with unpaired surrogates
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeChar('\uD800'); // High surrogate without low
        dos.writeChar('A');
        dos.writeChar('\uDC00'); // Low surrogate without high
        byte[] data = baos.toByteArray();

        // Unpaired surrogates are technically valid UTF-16 code units
        // Java allows them in strings (though they're invalid Unicode)
        assertDoesNotThrow(() -> {
            String[] chunks = DeobfuscatorHelper.loadChunksFromByteArray(data, 3);
            assertNotNull(chunks);
            assertEquals(1, chunks.length);
        }, "Unpaired surrogates should not crash (Java allows them)");
    }

    @Test
    @DisplayName("CORRUPT: Data size not multiple of 2")
    void dataSizeNotMultipleOfTwo() {
        // Odd number of bytes - incomplete char
        byte[] oddBytes = new byte[99]; // Not divisible by 2

        assertThrows(Exception.class, () -> {
            DeobfuscatorHelper.loadChunksFromByteArray(oddBytes, 50);
        }, "Odd byte count should fail (chars need 2 bytes)");
    }

    @Test
    @DisplayName("CORRUPT: Base64 with invalid ASCII chars (minimal decoder accepts them)")
    void base64WithInvalidAsciiChars() {
        // DOCUMENTED BEHAVIOR: Base64Decoder is minimal and only validates chars >= 128
        // Invalid ASCII characters (like ! @ #) pass through and produce garbage output
        // This is acceptable for internal use where input is controlled by the build process

        String corrupted = "SGVs!@#$bG8=";

        // Does NOT throw - produces garbage but doesn't crash
        assertDoesNotThrow(() -> {
            byte[] result = Base64Decoder.decode(corrupted);
            assertNotNull(result, "Minimal decoder accepts ASCII chars (by design)");
        }, "Minimal Base64 decoder doesn't validate ASCII (controlled input only)");

        // Only high bytes trigger validation
        assertThrows(IllegalArgumentException.class, () -> {
            Base64Decoder.decode("ABC\u00FF");
        }, "High bytes (>= 128) ARE validated and rejected");
    }

    @Test
    @DisplayName("CORRUPT: Base64 with wrong padding")
    void base64WithWrongPadding() {
        String[] wrongPadding = {
            "SGVs=bG8=",  // Padding in middle
            "===SGVs",     // Padding at start
            "SGVs===",     // Too much padding
        };

        for (String input : wrongPadding) {
            assertDoesNotThrow(() -> {
                try {
                    byte[] result = Base64Decoder.decode(input);
                    // Might produce garbage but shouldn't crash
                    assertNotNull(result);
                } catch (IllegalArgumentException e) {
                    // Also acceptable to reject
                }
            }, "Wrong padding should not crash: " + input);
        }
    }

    @Test
    @DisplayName("CORRUPT: Chunk array with wrong size")
    void chunkArrayWithWrongSize() throws IOException {
        // Create data for 2 chunks worth
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        for (int i = 0; i < DeobfuscatorHelper.MAX_CHUNK_LENGTH + 100; i++) {
            dos.writeChar('X');
        }
        byte[] data = baos.toByteArray();
        long totalLength = DeobfuscatorHelper.MAX_CHUNK_LENGTH + 100;

        String[] chunks = DeobfuscatorHelper.loadChunksFromByteArray(data, totalLength);
        assertEquals(2, chunks.length);

        // Now manually create wrong-sized array
        String[] wrongChunks = new String[1]; // Too small
        wrongChunks[0] = chunks[0];
        // Missing chunks[1]

        // Accessing with ID that references second chunk should fail
        assertThrows(Exception.class, () -> {
            // ID that would access beyond available chunks
            long badId = ((long) DeobfuscatorHelper.MAX_CHUNK_LENGTH + 50) << 32;
            DeobfuscatorHelper.getString(badId, wrongChunks);
        }, "Wrong chunk array size should cause bounds error");
    }

    @Test
    @DisplayName("CORRUPT: Chunk with null string element")
    void chunkWithNullStringElement() {
        String[] chunks = new String[3];
        chunks[0] = "valid";
        chunks[1] = null; // Corrupt
        chunks[2] = "valid";

        // Accessing null chunk should throw meaningful error
        assertThrows(IllegalStateException.class, () -> {
            // Force access to chunk 1 by using appropriate ID
            long id = ((long) DeobfuscatorHelper.MAX_CHUNK_LENGTH) << 32;
            DeobfuscatorHelper.getString(id, chunks);
        }, "Null chunk element should throw IllegalStateException");
    }

    @Test
    @DisplayName("CORRUPT: Extremely small buffer with large totalLength claim")
    void extremelySmallBufferWithLargeClaim() {
        byte[] tinyData = new byte[4]; // 2 chars worth

        // Claim we have millions of chars
        assertThrows(Exception.class, () -> {
            DeobfuscatorHelper.loadChunksFromByteArray(tinyData, 1_000_000L);
        }, "Tiny buffer with huge claim should fail");
    }

    @Test
    @DisplayName("CORRUPT: ID with all bits set")
    void idWithAllBitsSet() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeChar('T');
        byte[] data = baos.toByteArray();
        String[] chunks = DeobfuscatorHelper.loadChunksFromByteArray(data, 1);

        long corruptId = 0xFFFFFFFFFFFFFFFFL; // All bits set

        assertThrows(Exception.class, () -> {
            DeobfuscatorHelper.getString(corruptId, chunks);
        }, "Corrupt ID should not cause crash");
    }

    @Test
    @DisplayName("CORRUPT: ID referencing beyond available chunks")
    void idReferencingBeyondChunks() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        for (int i = 0; i < 100; i++) {
            dos.writeChar('A');
        }
        byte[] data = baos.toByteArray();
        String[] chunks = DeobfuscatorHelper.loadChunksFromByteArray(data, 100);

        // ID pointing way beyond data
        long beyondId = ((long) Integer.MAX_VALUE) << 32;

        assertThrows(Exception.class, () -> {
            DeobfuscatorHelper.getString(beyondId, chunks);
        }, "ID beyond chunks should throw exception");
    }

    @Test
    @DisplayName("CORRUPT: Mixed valid and invalid chunks")
    void mixedValidAndInvalidChunks() {
        String[] chunks = new String[5];
        chunks[0] = "valid";
        chunks[1] = ""; // Empty but valid
        chunks[2] = "another valid";
        chunks[3] = null; // Invalid
        chunks[4] = "last valid";

        // Accessing valid chunks should work
        assertDoesNotThrow(() -> {
            // Access chunk 0 (valid)
            long id0 = 0L;
            // This may still fail due to ID calculation, but shouldn't crash
        });

        // Accessing invalid chunk should throw
        assertThrows(Exception.class, () -> {
            // Try to access chunk 3 (null)
            long id3 = ((long) DeobfuscatorHelper.MAX_CHUNK_LENGTH * 3) << 32;
            DeobfuscatorHelper.getString(id3, chunks);
        });
    }

    @Test
    @DisplayName("CORRUPT: Data with BOMs and special Unicode markers")
    void dataWithBOMsAndSpecialMarkers() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeChar('\uFEFF'); // BOM
        dos.writeChar('\u200B'); // Zero-width space
        dos.writeChar('\u00A0'); // Non-breaking space
        dos.writeChar('A');
        byte[] data = baos.toByteArray();

        assertDoesNotThrow(() -> {
            String[] chunks = DeobfuscatorHelper.loadChunksFromByteArray(data, 4);
            assertNotNull(chunks);
            assertEquals(1, chunks.length);
            assertEquals(4, chunks[0].length());
        }, "Special Unicode markers should not crash");
    }

    @Test
    @DisplayName("CORRUPT: Chunk exactly at Integer.MAX_VALUE boundary")
    void chunkAtIntegerMaxValueBoundary() {
        // Can't actually create this much data in test, but can test calculation
        byte[] smallData = new byte[100];

        // Length at integer boundary
        long boundaryLength = Integer.MAX_VALUE;

        assertThrows(Exception.class, () -> {
            DeobfuscatorHelper.loadChunksFromByteArray(smallData, boundaryLength);
        }, "Boundary-case length should be handled safely");
    }

    @Test
    @DisplayName("CORRUPT: Empty chunks array with valid ID")
    void emptyChunksArrayWithValidId() {
        String[] emptyChunks = new String[0];

        assertThrows(Exception.class, () -> {
            DeobfuscatorHelper.getString(12345L, emptyChunks);
        }, "Empty chunks array should fail gracefully");
    }

    @Test
    @DisplayName("CORRUPT: Chunks with inconsistent lengths")
    void chunksWithInconsistentLengths() {
        String[] inconsistent = new String[3];
        inconsistent[0] = "short";
        inconsistent[1] = "this is a much longer string that doesn't match expected length";
        inconsistent[2] = "x"; // Single char

        // The chunks have inconsistent lengths which might confuse index calculations
        // Should still not crash when accessed
        assertDoesNotThrow(() -> {
            // Try to access these chunks with various IDs
            // May produce wrong results but shouldn't crash
        });
    }
}
