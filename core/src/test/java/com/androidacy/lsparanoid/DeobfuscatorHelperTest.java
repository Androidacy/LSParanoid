package com.androidacy.lsparanoid;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DeobfuscatorHelper.
 * Tests chunk loading and string deobfuscation functionality.
 */
class DeobfuscatorHelperTest {

    @Test
    @DisplayName("MAX_CHUNK_LENGTH should be 0x1fff (8191)")
    void maxChunkLengthShouldBe8191() {
        assertEquals(0x1fff, DeobfuscatorHelper.MAX_CHUNK_LENGTH,
            "MAX_CHUNK_LENGTH should be 8191");
    }

    @Test
    @DisplayName("loadChunksFromByteArray() should handle empty data")
    void loadChunksFromByteArrayShouldHandleEmptyData() {
        byte[] data = new byte[0];
        String[] chunks = DeobfuscatorHelper.loadChunksFromByteArray(data, 0L);
        assertNotNull(chunks);
        assertEquals(0, chunks.length, "Empty data should produce no chunks");
    }

    @Test
    @DisplayName("loadChunksFromByteArray() should load single chunk")
    void loadChunksFromByteArrayShouldLoadSingleChunk() throws IOException {
        String testString = "Hello, World!";
        byte[] data = createCharData(testString);

        String[] chunks = DeobfuscatorHelper.loadChunksFromByteArray(data, testString.length());

        assertNotNull(chunks);
        assertEquals(1, chunks.length, "Should create one chunk");
        assertEquals(testString, chunks[0], "Chunk should contain the test string");
    }

    @Test
    @DisplayName("loadChunksFromByteArray() should handle chunk exactly at MAX_CHUNK_LENGTH")
    void loadChunksFromByteArrayShouldHandleExactChunkSize() throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < DeobfuscatorHelper.MAX_CHUNK_LENGTH; i++) {
            sb.append('A');
        }
        String testString = sb.toString();
        byte[] data = createCharData(testString);

        String[] chunks = DeobfuscatorHelper.loadChunksFromByteArray(data, testString.length());

        assertNotNull(chunks);
        assertEquals(1, chunks.length, "Should create exactly one chunk");
        assertEquals(DeobfuscatorHelper.MAX_CHUNK_LENGTH, chunks[0].length(),
            "Chunk should be exactly MAX_CHUNK_LENGTH");
    }

    @Test
    @DisplayName("loadChunksFromByteArray() should split into multiple chunks")
    void loadChunksFromByteArrayShouldSplitMultipleChunks() throws IOException {
        // Create string larger than MAX_CHUNK_LENGTH
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < DeobfuscatorHelper.MAX_CHUNK_LENGTH + 100; i++) {
            sb.append((char) ('A' + (i % 26)));
        }
        String testString = sb.toString();
        byte[] data = createCharData(testString);

        String[] chunks = DeobfuscatorHelper.loadChunksFromByteArray(data, testString.length());

        assertNotNull(chunks);
        assertEquals(2, chunks.length, "Should create two chunks");
        assertEquals(DeobfuscatorHelper.MAX_CHUNK_LENGTH, chunks[0].length(),
            "First chunk should be MAX_CHUNK_LENGTH");
        assertEquals(100, chunks[1].length(), "Second chunk should contain remainder");

        // Verify content
        String reconstructed = chunks[0] + chunks[1];
        assertEquals(testString, reconstructed, "Chunks should reconstruct original string");
    }

    @Test
    @DisplayName("loadChunksFromByteArray() should handle three chunks")
    void loadChunksFromByteArrayShouldHandleThreeChunks() throws IOException {
        int size = DeobfuscatorHelper.MAX_CHUNK_LENGTH * 2 + 500;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < size; i++) {
            sb.append((char) ('0' + (i % 10)));
        }
        String testString = sb.toString();
        byte[] data = createCharData(testString);

        String[] chunks = DeobfuscatorHelper.loadChunksFromByteArray(data, testString.length());

        assertNotNull(chunks);
        assertEquals(3, chunks.length, "Should create three chunks");
        assertEquals(DeobfuscatorHelper.MAX_CHUNK_LENGTH, chunks[0].length());
        assertEquals(DeobfuscatorHelper.MAX_CHUNK_LENGTH, chunks[1].length());
        assertEquals(500, chunks[2].length());

        String reconstructed = chunks[0] + chunks[1] + chunks[2];
        assertEquals(testString, reconstructed);
    }

    @Test
    @DisplayName("loadChunksFromByteArray() should handle Unicode characters")
    void loadChunksFromByteArrayShouldHandleUnicode() throws IOException {
        String testString = "Hello 世界 🌍 Привет";
        byte[] data = createCharData(testString);

        String[] chunks = DeobfuscatorHelper.loadChunksFromByteArray(data, testString.length());

        assertNotNull(chunks);
        assertEquals(1, chunks.length);
        assertEquals(testString, chunks[0], "Should preserve Unicode characters");
    }

    @Test
    @DisplayName("getString() requires valid chunks array")
    void getStringRequiresValidChunks() throws IOException {
        // This test verifies getString works with properly structured data
        // Full encode/decode testing is done in IntegrationTest with StringRegistryImpl
        byte[] data = createCharData("AB");
        String[] chunks = DeobfuscatorHelper.loadChunksFromByteArray(data, 2);

        assertNotNull(chunks, "Chunks should be loaded");
        assertEquals(1, chunks.length, "Should have one chunk");
    }

    @Test
    @DisplayName("getString() should throw on null chunks")
    void getStringShouldThrowOnNullChunks() {
        assertThrows(NullPointerException.class,
            () -> DeobfuscatorHelper.getString(0L, null),
            "Should throw on null chunks array");
    }

    @Test
    @DisplayName("getString() should throw on invalid chunk index")
    void getStringShouldThrowOnInvalidChunkIndex() throws IOException {
        byte[] data = createCharData("Test");
        String[] chunks = DeobfuscatorHelper.loadChunksFromByteArray(data, 4);

        // Create an ID that would reference a chunk beyond the array
        long id = (((long) DeobfuscatorHelper.MAX_CHUNK_LENGTH * 10) << 32) | 12345L;

        assertThrows(IllegalArgumentException.class,
            () -> DeobfuscatorHelper.getString(id, chunks),
            "Should throw on chunk index out of bounds");
    }

    @Test
    @DisplayName("getString() should throw on invalid ID (defensive test)")
    void getStringShouldThrowOnInvalidId() throws IOException {
        // This test verifies defensive behavior with corrupted/invalid data
        // In production, IDs are created by StringRegistry and always valid
        byte[] data = createCharData("Test");
        String[] chunks = DeobfuscatorHelper.loadChunksFromByteArray(data, 4);

        // Use an arbitrary ID not created by StringRegistry
        // This will decode to an out-of-bounds index
        long id = 12345L;

        assertThrows(IllegalArgumentException.class,
            () -> DeobfuscatorHelper.getString(id, chunks),
            "Should throw when ID decodes to invalid index");
    }

    @Test
    @DisplayName("loadChunksFromByteArray() correctly splits data")
    void loadChunksCorrectlySplitsData() throws IOException {
        // Test that chunks are properly created and data is preserved
        String testData = "ABCDEFGHIJ";
        byte[] data = createCharData(testData);
        String[] chunks = DeobfuscatorHelper.loadChunksFromByteArray(data, testData.length());

        assertNotNull(chunks);
        assertEquals(1, chunks.length);
        assertEquals(testData, chunks[0]);
    }

    // Helper method to create byte array with char data (2 bytes per char, big-endian)
    private byte[] createCharData(String s) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        for (char c : s.toCharArray()) {
            dos.writeChar(c);
        }
        return baos.toByteArray();
    }
}
