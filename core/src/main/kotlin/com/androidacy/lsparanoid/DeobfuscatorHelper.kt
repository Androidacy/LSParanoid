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

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.InputStream

/**
 * Helper for deobfuscating strings.
 */
object DeobfuscatorHelper {
    /**
     * Maximum chunk length.
     */
    const val MAX_CHUNK_LENGTH = 0x1fff

    /**
     * Load chunks from resource.
     *
     * @param clazz the class to load resource from
     * @param resourceName the resource name
     * @param totalLength the total length in characters
     * @return the chunks array
     */
    @JvmStatic
    fun loadChunksFromResource(clazz: Class<*>, resourceName: String, totalLength: Long): Array<String> {
        return try {
            clazz.getResourceAsStream(resourceName).use { inputStream ->
                requireNotNull(inputStream) { "Resource not found: $resourceName" }
                DataInputStream(inputStream).use { dis ->
                    loadChunksFromStream(dis, totalLength)
                }
            }
        } catch (e: Exception) {
            throw RuntimeException("Failed to load obfuscated strings", e)
        }
    }

    /**
     * Load chunks from byte array.
     *
     * @param data the byte array containing encoded characters
     * @param totalLength the total length in characters
     * @return the chunks array
     */
    @JvmStatic
    fun loadChunksFromByteArray(data: ByteArray, totalLength: Long): Array<String> {
        return try {
            DataInputStream(ByteArrayInputStream(data)).use { dis ->
                loadChunksFromStream(dis, totalLength)
            }
        } catch (e: Exception) {
            throw RuntimeException("Failed to load obfuscated strings", e)
        }
    }

    private fun loadChunksFromStream(dis: DataInputStream, totalLength: Long): Array<String> {
        val chunkCount = ((totalLength + MAX_CHUNK_LENGTH - 1) / MAX_CHUNK_LENGTH).toInt()
        val chunks = Array(chunkCount) { "" }

        var charsRead = 0L
        var chunkIndex = 0

        while (charsRead < totalLength) {
            val chunkSize = minOf(MAX_CHUNK_LENGTH.toLong(), totalLength - charsRead).toInt()
            val buffer = CharArray(chunkSize) { dis.readChar() }
            chunks[chunkIndex++] = String(buffer)
            charsRead += chunkSize
        }

        return chunks
    }

    /**
     * Get deobfuscated string.
     *
     * @param id the obfuscated string ID
     * @param chunks the chunks array
     * @return the deobfuscated string
     */
    @JvmStatic
    fun getString(id: Long, chunks: Array<String?>): String {
        var state = RandomHelper.seed(id and 0xffffffffL)
        state = RandomHelper.next(state)
        val low = (state ushr 32) and 0xffff
        state = RandomHelper.next(state)
        val high = (state ushr 16) and 0xffff0000
        val index = ((id ushr 32) xor low xor high).toInt()

        state = getCharAt(index, chunks, state)
        val length = ((state ushr 32) and 0xffffL).toInt()
        val chars = CharArray(length) { i ->
            state = getCharAt(index + i + 1, chunks, state)
            ((state ushr 32) and 0xffffL).toInt().toChar()
        }

        return String(chars)
    }

    @JvmStatic
    private fun getCharAt(charIndex: Int, chunks: Array<String?>, state: Long): Long {
        val nextState = RandomHelper.next(state)
        val chunkIndex = charIndex / MAX_CHUNK_LENGTH

        if (chunkIndex < 0 || chunkIndex >= chunks.size) {
            throw IllegalArgumentException("Chunk index out of bounds: $chunkIndex")
        }

        val chunk = chunks[chunkIndex]
            ?: throw IllegalStateException("Chunk is null at index: $chunkIndex")

        val indexInChunk = charIndex - (chunkIndex * MAX_CHUNK_LENGTH)

        if (indexInChunk < 0 || indexInChunk >= chunk.length) {
            throw IllegalArgumentException("Index in chunk out of bounds: $indexInChunk, chunk length: ${chunk.length}")
        }

        return nextState xor (chunk[indexInChunk].code.toLong() shl 32)
    }
}
