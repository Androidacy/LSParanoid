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
 * Random helper for string obfuscation.
 */
object RandomHelper {
    /**
     * Generate seed from input value.
     */
    @JvmStatic
    fun seed(x: Long): Long {
        val z = (x xor (x ushr 33)) * 7109453100751455733L
        return ((z xor (z ushr 28)) * -3808689974395783757L) ushr 32
    }

    /**
     * Generate next random state.
     */
    @JvmStatic
    fun next(state: Long): Long {
        var s0 = (state and 0xffff).toShort()
        var s1 = ((state ushr 16) and 0xffff).toShort()
        var next = s0

        next = (next + s1).toShort()
        next = rotl(next, 9)
        next = (next + s0).toShort()

        s1 = (s1.toInt() xor s0.toInt()).toShort()
        s0 = rotl(s0, 13)
        s0 = (s0.toInt() xor s1.toInt()).toShort()
        s0 = (s0.toInt() xor (s1.toInt() shl 5)).toShort()
        s1 = rotl(s1, 10)

        var result = next.toLong() and 0xffff
        result = result shl 16
        result = result or (s1.toLong() and 0xffff)
        result = result shl 16
        result = result or (s0.toLong() and 0xffff)
        return result
    }

    @JvmStatic
    private fun rotl(x: Short, k: Int): Short {
        val value = x.toInt() and 0xFFFF
        return ((value shl k) or (value ushr (16 - k))).toShort()
    }
}
