package com.androidacy.lsparanoid;

/**
 * Minimal Base64 decoder for chunk data.
 */
public final class Base64Decoder {
    private Base64Decoder() {}

    private static final byte[] DECODE_TABLE = new byte[128];

    static {
        for (int i = 0; i < 128; i++) {
            DECODE_TABLE[i] = -1;
        }
        for (int i = 0; i < 26; i++) {
            DECODE_TABLE['A' + i] = (byte) i;
            DECODE_TABLE['a' + i] = (byte) (i + 26);
        }
        for (int i = 0; i < 10; i++) {
            DECODE_TABLE['0' + i] = (byte) (i + 52);
        }
        DECODE_TABLE['+'] = 62;
        DECODE_TABLE['/'] = 63;
    }

    /**
     * Decode Base64 string to byte array.
     */
    public static byte[] decode(final String input) {
        final int len = input.length();
        int padding = 0;
        if (len > 0 && input.charAt(len - 1) == '=') padding++;
        if (len > 1 && input.charAt(len - 2) == '=') padding++;

        final int outLen = (len * 3) / 4 - padding;
        final byte[] out = new byte[outLen];

        int inIndex = 0;
        int outIndex = 0;

        while (inIndex < len) {
            final int a = DECODE_TABLE[input.charAt(inIndex++)];
            final int b = DECODE_TABLE[input.charAt(inIndex++)];
            final int c = inIndex < len ? DECODE_TABLE[input.charAt(inIndex++)] : 0;
            final int d = inIndex < len ? DECODE_TABLE[input.charAt(inIndex++)] : 0;

            final int triple = (a << 18) | (b << 12) | (c << 6) | d;

            if (outIndex < outLen) out[outIndex++] = (byte) (triple >> 16);
            if (outIndex < outLen) out[outIndex++] = (byte) (triple >> 8);
            if (outIndex < outLen) out[outIndex++] = (byte) triple;
        }

        return out;
    }
}
