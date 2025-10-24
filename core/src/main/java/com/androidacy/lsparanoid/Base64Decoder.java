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
        if (input == null || input.isEmpty()) {
            return new byte[0];
        }

        final int len = input.length();
        int padding = 0;
        if (len > 0 && input.charAt(len - 1) == '=') padding++;
        if (len > 1 && input.charAt(len - 2) == '=') padding++;

        int outLen = (len * 3) / 4 - padding;
        if (outLen < 0) outLen = 0;
        final byte[] out = new byte[outLen];

        int inIndex = 0;
        int outIndex = 0;

        while (inIndex < len) {
            final char ca = input.charAt(inIndex++);
            final char cb = inIndex < len ? input.charAt(inIndex++) : 'A';
            final char cc = inIndex < len ? input.charAt(inIndex++) : 'A';
            final char cd = inIndex < len ? input.charAt(inIndex++) : 'A';

            if (ca >= 128 || cb >= 128 || cc >= 128 || cd >= 128) {
                throw new IllegalArgumentException("Invalid Base64 character");
            }

            final int a = DECODE_TABLE[ca];
            final int b = DECODE_TABLE[cb];
            final int c = cc == '=' ? 0 : DECODE_TABLE[cc];
            final int d = cd == '=' ? 0 : DECODE_TABLE[cd];

            final int triple = (a << 18) | (b << 12) | (c << 6) | d;

            if (outIndex < outLen) out[outIndex++] = (byte) (triple >> 16);
            if (outIndex < outLen) out[outIndex++] = (byte) (triple >> 8);
            if (outIndex < outLen) out[outIndex++] = (byte) triple;
        }

        return out;
    }
}
