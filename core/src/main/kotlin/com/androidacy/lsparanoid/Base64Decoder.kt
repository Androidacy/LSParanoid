/*
 * Copyright 2020 Michael Rozumyanskiy
 * Copyright 2023 LSPosed
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.androidacy.lsparanoid

/**
 * Minimal Base64 decoder for chunk data.
 */
object Base64Decoder {
    private val DECODE_TABLE = ByteArray(128) { -1 }.apply {
        for (i in 0 until 26) {
            this['A'.code + i] = i.toByte()
            this['a'.code + i] = (i + 26).toByte()
        }
        for (i in 0 until 10) {
            this['0'.code + i] = (i + 52).toByte()
        }
        this['+'.code] = 62
        this['/'.code] = 63
    }

    /**
     * Decode Base64 string to byte array.
     */
    @JvmStatic
    fun decode(input: String?): ByteArray {
        if (input == null || input.isEmpty()) return ByteArray(0)

        val len = input.length
        var padding = 0
        if (len > 0 && input[len - 1] == '=') padding++
        if (len > 1 && input[len - 2] == '=') padding++

        val outLen = ((len * 3) / 4 - padding).coerceAtLeast(0)
        val out = ByteArray(outLen)

        var inIndex = 0
        var outIndex = 0

        while (inIndex < len) {
            val ca = input[inIndex++]
            val cb = if (inIndex < len) input[inIndex++] else 'A'
            val cc = if (inIndex < len) input[inIndex++] else 'A'
            val cd = if (inIndex < len) input[inIndex++] else 'A'

            require(ca.code < 128 && cb.code < 128 && cc.code < 128 && cd.code < 128) {
                "Invalid Base64 character"
            }

            val a = DECODE_TABLE[ca.code].toInt()
            val b = DECODE_TABLE[cb.code].toInt()
            val c = if (cc == '=') 0 else DECODE_TABLE[cc.code].toInt()
            val d = if (cd == '=') 0 else DECODE_TABLE[cd.code].toInt()

            val triple = (a shl 18) or (b shl 12) or (c shl 6) or d

            if (outIndex < outLen) out[outIndex++] = (triple shr 16).toByte()
            if (outIndex < outLen) out[outIndex++] = (triple shr 8).toByte()
            if (outIndex < outLen) out[outIndex++] = triple.toByte()
        }

        return out
    }
}
