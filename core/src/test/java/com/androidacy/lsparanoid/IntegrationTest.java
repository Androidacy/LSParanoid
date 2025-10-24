package com.androidacy.lsparanoid;

import com.androidacy.lsparanoid.processor.StringRegistryImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the full encode/decode pipeline.
 * Tests the round-trip: StringRegistry (encode) -> DeobfuscatorHelper (decode).
 */
class IntegrationTest {

    @Test
    @DisplayName("Round-trip: simple ASCII string")
    void roundTripSimpleAsciiString() {
        int seed = 12345;
        String original = "Hello, World!";

        try (StringRegistryImpl registry = new StringRegistryImpl(seed)) {
            long id = registry.registerString(original);

            byte[] data = registry.getDataAsByteArray();
            long totalLength = registry.getTotalLength();
            String[] chunks = DeobfuscatorHelper.loadChunksFromByteArray(data, totalLength);

            String decoded = DeobfuscatorHelper.getString(id, chunks);
            assertEquals(original, decoded, "Round-trip should preserve string");
        }
    }

    @Test
    @DisplayName("Round-trip: empty string")
    void roundTripEmptyString() {
        int seed = 12345;
        String original = "";

        try (StringRegistryImpl registry = new StringRegistryImpl(seed)) {
            long id = registry.registerString(original);

            byte[] data = registry.getDataAsByteArray();
            long totalLength = registry.getTotalLength();
            String[] chunks = DeobfuscatorHelper.loadChunksFromByteArray(data, totalLength);

            String decoded = DeobfuscatorHelper.getString(id, chunks);
            assertEquals(original, decoded, "Round-trip should handle empty string");
        }
    }

    @Test
    @DisplayName("Round-trip: Unicode and emoji")
    void roundTripUnicodeAndEmoji() {
        int seed = 54321;
        String original = "Hello 世界 🌍 Привет 🎉";

        try (StringRegistryImpl registry = new StringRegistryImpl(seed)) {
            long id = registry.registerString(original);

            byte[] data = registry.getDataAsByteArray();
            long totalLength = registry.getTotalLength();
            String[] chunks = DeobfuscatorHelper.loadChunksFromByteArray(data, totalLength);

            String decoded = DeobfuscatorHelper.getString(id, chunks);
            assertEquals(original, decoded, "Round-trip should preserve Unicode/emoji");
        }
    }

    @Test
    @DisplayName("Round-trip: special characters")
    void roundTripSpecialCharacters() {
        int seed = 99999;
        String original = "!@#$%^&*()_+-=[]{}|;':\",./<>?`~\n\r\t";

        try (StringRegistryImpl registry = new StringRegistryImpl(seed)) {
            long id = registry.registerString(original);

            byte[] data = registry.getDataAsByteArray();
            long totalLength = registry.getTotalLength();
            String[] chunks = DeobfuscatorHelper.loadChunksFromByteArray(data, totalLength);

            String decoded = DeobfuscatorHelper.getString(id, chunks);
            assertEquals(original, decoded, "Round-trip should preserve special characters");
        }
    }

    @Test
    @DisplayName("Round-trip: multiple strings")
    void roundTripMultipleStrings() {
        int seed = 11111;
        String[] originals = {
            "First string",
            "Second string",
            "Third string",
            "Fourth string"
        };

        try (StringRegistryImpl registry = new StringRegistryImpl(seed)) {
            long[] ids = new long[originals.length];
            for (int i = 0; i < originals.length; i++) {
                ids[i] = registry.registerString(originals[i]);
            }

            byte[] data = registry.getDataAsByteArray();
            long totalLength = registry.getTotalLength();
            String[] chunks = DeobfuscatorHelper.loadChunksFromByteArray(data, totalLength);

            for (int i = 0; i < originals.length; i++) {
                String decoded = DeobfuscatorHelper.getString(ids[i], chunks);
                assertEquals(originals[i], decoded,
                    "Should decode string " + i + " correctly");
            }
        }
    }

    @Test
    @DisplayName("Round-trip: long string requiring single chunk")
    void roundTripLongStringSingleChunk() {
        int seed = 22222;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5000; i++) {
            sb.append((char) ('A' + (i % 26)));
        }
        String original = sb.toString();

        try (StringRegistryImpl registry = new StringRegistryImpl(seed)) {
            long id = registry.registerString(original);

            byte[] data = registry.getDataAsByteArray();
            long totalLength = registry.getTotalLength();
            String[] chunks = DeobfuscatorHelper.loadChunksFromByteArray(data, totalLength);

            assertEquals(1, chunks.length, "Should fit in single chunk");

            String decoded = DeobfuscatorHelper.getString(id, chunks);
            assertEquals(original, decoded, "Round-trip should preserve long string");
        }
    }

    @Test
    @DisplayName("Round-trip: string requiring multiple chunks")
    void roundTripMultipleChunks() {
        int seed = 33333;
        StringBuilder sb = new StringBuilder();
        // Create string larger than MAX_CHUNK_LENGTH
        for (int i = 0; i < DeobfuscatorHelper.MAX_CHUNK_LENGTH + 1000; i++) {
            sb.append((char) ('0' + (i % 10)));
        }
        String original = sb.toString();

        try (StringRegistryImpl registry = new StringRegistryImpl(seed)) {
            long id = registry.registerString(original);

            byte[] data = registry.getDataAsByteArray();
            long totalLength = registry.getTotalLength();
            String[] chunks = DeobfuscatorHelper.loadChunksFromByteArray(data, totalLength);

            assertTrue(chunks.length >= 2, "Should require multiple chunks");

            String decoded = DeobfuscatorHelper.getString(id, chunks);
            assertEquals(original, decoded,
                "Round-trip should preserve string spanning multiple chunks");
        }
    }

    @Test
    @DisplayName("Round-trip: very long string (near max length)")
    void roundTripVeryLongString() {
        int seed = 44444;
        // Create a string of 50,000 characters
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 50000; i++) {
            sb.append((char) ('a' + (i % 26)));
        }
        String original = sb.toString();

        try (StringRegistryImpl registry = new StringRegistryImpl(seed)) {
            long id = registry.registerString(original);

            byte[] data = registry.getDataAsByteArray();
            long totalLength = registry.getTotalLength();
            String[] chunks = DeobfuscatorHelper.loadChunksFromByteArray(data, totalLength);

            int expectedChunks = (int) Math.ceil(50001.0 / DeobfuscatorHelper.MAX_CHUNK_LENGTH);
            assertEquals(expectedChunks, chunks.length,
                "Should split into expected number of chunks");

            String decoded = DeobfuscatorHelper.getString(id, chunks);
            assertEquals(original, decoded, "Round-trip should preserve very long string");
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 42, 12345, 99999, -1, -12345, Integer.MAX_VALUE, Integer.MIN_VALUE})
    @DisplayName("Same seed produces deterministic output")
    void sameSeedProducesDeterministicOutput(int seed) {
        String original = "Test string for determinism";

        // First encoding
        long id1;
        byte[] data1;
        try (StringRegistryImpl registry = new StringRegistryImpl(seed)) {
            id1 = registry.registerString(original);
            data1 = registry.getDataAsByteArray();
        }

        // Second encoding with same seed
        long id2;
        byte[] data2;
        try (StringRegistryImpl registry = new StringRegistryImpl(seed)) {
            id2 = registry.registerString(original);
            data2 = registry.getDataAsByteArray();
        }

        assertEquals(id1, id2, "Same seed should produce same ID");
        assertArrayEquals(data1, data2, "Same seed should produce same encoded data");
    }

    @Test
    @DisplayName("Different seeds produce different output")
    void differentSeedsProduceDifferentOutput() {
        String original = "Test string";

        long id1;
        byte[] data1;
        try (StringRegistryImpl registry = new StringRegistryImpl(111)) {
            id1 = registry.registerString(original);
            data1 = registry.getDataAsByteArray();
        }

        long id2;
        byte[] data2;
        try (StringRegistryImpl registry = new StringRegistryImpl(222)) {
            id2 = registry.registerString(original);
            data2 = registry.getDataAsByteArray();
        }

        // IDs contain the seed, so they will differ
        assertNotEquals(id1, id2, "Different seeds should produce different IDs");

        // The encoded data should also differ
        assertFalse(java.util.Arrays.equals(data1, data2),
            "Different seeds should produce different encoded data");
    }

    @Test
    @DisplayName("Round-trip: boundary at MAX_CHUNK_LENGTH")
    void roundTripBoundaryAtMaxChunkLength() {
        int seed = 55555;
        // Create string exactly at chunk boundary
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < DeobfuscatorHelper.MAX_CHUNK_LENGTH; i++) {
            sb.append('X');
        }
        String original = sb.toString();

        try (StringRegistryImpl registry = new StringRegistryImpl(seed)) {
            long id = registry.registerString(original);

            byte[] data = registry.getDataAsByteArray();
            long totalLength = registry.getTotalLength();
            String[] chunks = DeobfuscatorHelper.loadChunksFromByteArray(data, totalLength);

            String decoded = DeobfuscatorHelper.getString(id, chunks);
            assertEquals(original, decoded,
                "Round-trip should handle string at chunk boundary");
        }
    }

    @Test
    @DisplayName("Round-trip: strings with all Unicode planes")
    void roundTripAllUnicodePlanes() {
        int seed = 66666;
        // Test various Unicode planes
        String original = "ASCII: abc, "
                + "Latin: áéíóú, "
                + "Cyrillic: абв, "
                + "CJK: 中文日本, "
                + "Arabic: العربية, "
                + "Emoji: 😀🎉🌍, "
                + "Math: ∑∫∞";

        try (StringRegistryImpl registry = new StringRegistryImpl(seed)) {
            long id = registry.registerString(original);

            byte[] data = registry.getDataAsByteArray();
            long totalLength = registry.getTotalLength();
            String[] chunks = DeobfuscatorHelper.loadChunksFromByteArray(data, totalLength);

            String decoded = DeobfuscatorHelper.getString(id, chunks);
            assertEquals(original, decoded,
                "Round-trip should preserve all Unicode planes");
        }
    }

    @Test
    @DisplayName("Chunk count calculation is correct")
    void chunkCountCalculationIsCorrect() {
        int seed = 77777;
        int[] testSizes = {
            1,
            100,
            DeobfuscatorHelper.MAX_CHUNK_LENGTH - 1,
            DeobfuscatorHelper.MAX_CHUNK_LENGTH,
            DeobfuscatorHelper.MAX_CHUNK_LENGTH + 1,
            DeobfuscatorHelper.MAX_CHUNK_LENGTH * 2,
            DeobfuscatorHelper.MAX_CHUNK_LENGTH * 3 + 100
        };

        for (int size : testSizes) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < size; i++) {
                sb.append('A');
            }
            String original = sb.toString();

            try (StringRegistryImpl registry = new StringRegistryImpl(seed)) {
                registry.registerString(original);

                int expectedChunks = (int) Math.ceil((size + 1.0) / DeobfuscatorHelper.MAX_CHUNK_LENGTH);
                assertEquals(expectedChunks, registry.getChunkCount(),
                    "Chunk count should be correct for size " + size);
            }
        }
    }
}
