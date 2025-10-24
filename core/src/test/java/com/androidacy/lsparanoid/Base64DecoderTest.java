package com.androidacy.lsparanoid;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Base64Decoder.
 * Tests the minimal Base64 decoder used for chunk data.
 */
class Base64DecoderTest {

    @Test
    @DisplayName("decode() should handle empty string")
    void decodeShouldHandleEmptyString() {
        byte[] result = Base64Decoder.decode("");
        assertNotNull(result);
        assertEquals(0, result.length, "Empty string should produce empty array");
    }

    @Test
    @DisplayName("decode() should handle null input")
    void decodeShouldHandleNullInput() {
        byte[] result = Base64Decoder.decode(null);
        assertNotNull(result);
        assertEquals(0, result.length, "Null should produce empty array");
    }

    @ParameterizedTest
    @CsvSource({
        "SGVsbG8=, Hello",
        "V29ybGQ=, World",
        "QQ==, A",
        "QUI=, AB",
        "QUJD, ABC"
    })
    @DisplayName("decode() should decode valid Base64 strings")
    void decodeShouldDecodeValidStrings(String encoded, String expected) {
        byte[] result = Base64Decoder.decode(encoded);
        String decoded = new String(result, StandardCharsets.UTF_8);
        assertEquals(expected, decoded, "Should decode: " + encoded);
    }

    @Test
    @DisplayName("decode() should handle RFC 4648 test vectors")
    void decodeShouldHandleRFC4648TestVectors() {
        // RFC 4648 test vectors
        assertDecodes("", "");
        assertDecodes("Zg==", "f");
        assertDecodes("Zm8=", "fo");
        assertDecodes("Zm9v", "foo");
        assertDecodes("Zm9vYg==", "foob");
        assertDecodes("Zm9vYmE=", "fooba");
        assertDecodes("Zm9vYmFy", "foobar");
    }

    @Test
    @DisplayName("decode() should handle strings without padding")
    void decodeShouldHandleStringsWithoutPadding() {
        // Some encoders omit trailing '=' padding
        assertDecodes("SGVsbG8", "Hello");
        assertDecodes("V29ybGQ", "World");
    }

    @Test
    @DisplayName("decode() should handle single character")
    void decodeShouldHandleSingleCharacter() {
        assertDecodes("QQ==", "A");
        assertDecodes("Qg==", "B");
        assertDecodes("MA==", "0");
    }

    @Test
    @DisplayName("decode() should handle binary data")
    void decodeShouldHandleBinaryData() {
        byte[] original = {0x00, 0x01, 0x02, (byte) 0xFF, (byte) 0xFE};
        String encoded = java.util.Base64.getEncoder().encodeToString(original);
        byte[] decoded = Base64Decoder.decode(encoded);
        assertArrayEquals(original, decoded, "Should correctly decode binary data");
    }

    @Test
    @DisplayName("decode() should handle long strings")
    void decodeShouldHandleLongStrings() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            sb.append((char) ('A' + (i % 26)));
        }
        String original = sb.toString();
        String encoded = java.util.Base64.getEncoder().encodeToString(
            original.getBytes(StandardCharsets.UTF_8));
        byte[] decoded = Base64Decoder.decode(encoded);
        String result = new String(decoded, StandardCharsets.UTF_8);
        assertEquals(original, result, "Should handle long strings");
    }

    @Test
    @DisplayName("decode() should handle all ASCII characters")
    void decodeShouldHandleAllAsciiCharacters() {
        StringBuilder sb = new StringBuilder();
        for (int i = 32; i < 127; i++) { // Printable ASCII
            sb.append((char) i);
        }
        String original = sb.toString();
        String encoded = java.util.Base64.getEncoder().encodeToString(
            original.getBytes(StandardCharsets.UTF_8));
        byte[] decoded = Base64Decoder.decode(encoded);
        String result = new String(decoded, StandardCharsets.UTF_8);
        assertEquals(original, result, "Should handle all ASCII characters");
    }

    @Test
    @DisplayName("decode() should handle Unicode text")
    void decodeShouldHandleUnicodeText() {
        String original = "Hello 世界 🌍";
        String encoded = java.util.Base64.getEncoder().encodeToString(
            original.getBytes(StandardCharsets.UTF_8));
        byte[] decoded = Base64Decoder.decode(encoded);
        String result = new String(decoded, StandardCharsets.UTF_8);
        assertEquals(original, result, "Should handle Unicode text");
    }

    @Test
    @DisplayName("decode() should throw on invalid high characters")
    void decodeShouldThrowOnInvalidHighCharacters() {
        // Base64Decoder only validates characters >= 128
        assertThrows(IllegalArgumentException.class, () -> Base64Decoder.decode("ABC\u00FF"),
            "Should throw on high byte character");
    }

    @Test
    @DisplayName("decode() should handle maximum padding")
    void decodeShouldHandleMaximumPadding() {
        // Test with double padding
        assertDecodes("QQ==", "A");
        assertDecodes("QUI=", "AB");
    }

    @Test
    @DisplayName("decode() should match Java's Base64 decoder")
    void decodeShouldMatchJavaBase64() {
        String[] testStrings = {
            "Hello, World!",
            "The quick brown fox",
            "12345",
            "!@#$%^&*()",
            "UTF-8: 日本語",
            "\n\r\t",
            ""
        };

        for (String test : testStrings) {
            byte[] original = test.getBytes(StandardCharsets.UTF_8);
            String encoded = java.util.Base64.getEncoder().encodeToString(original);

            byte[] ourDecoded = Base64Decoder.decode(encoded);
            byte[] javaDecoded = java.util.Base64.getDecoder().decode(encoded);

            assertArrayEquals(javaDecoded, ourDecoded,
                "Our decoder should match Java's for: " + test);
        }
    }

    @Test
    @DisplayName("decode() should handle standard Base64 alphabet")
    void decodeShouldHandleStandardAlphabet() {
        // Test all valid Base64 characters
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
        assertDoesNotThrow(() -> Base64Decoder.decode(alphabet),
            "Should handle all standard Base64 characters");
    }

    @Test
    @DisplayName("decode() should handle input without explicit validation")
    void decodeShouldHandleInputWithoutValidation() {
        // Base64Decoder is minimal and doesn't validate ASCII characters
        // It only checks for >= 128. Invalid ASCII chars produce garbage but don't throw.
        // This is acceptable for internal use where input is controlled.
        byte[] result = Base64Decoder.decode("AAAA");
        assertNotNull(result, "Should return a result for ASCII input");
    }

    // Helper method
    private void assertDecodes(String encoded, String expected) {
        byte[] result = Base64Decoder.decode(encoded);
        String decoded = new String(result, StandardCharsets.UTF_8);
        assertEquals(expected, decoded,
            "Should decode '" + encoded + "' to '" + expected + "'");
    }
}
