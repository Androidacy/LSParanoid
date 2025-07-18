/*
 * Copyright 2021 Michael Rozumyanskiy
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

package org.lsposed.lsparanoid.processor

import org.lsposed.lsparanoid.DeobfuscatorHelper
import org.lsposed.lsparanoid.RandomHelper
import java.io.Closeable
import java.io.File
import java.nio.charset.StandardCharsets

interface StringRegistry : Closeable {
  fun registerString(string: String): Long

  @Deprecated("Use streamChunks for better memory efficiency", ReplaceWith("streamChunks(consumer)"))
  fun getAllChunks(): List<String>

  fun streamChunks(consumer: (String) -> Unit)
  fun getChunkCount(): Int
}

class StringRegistryImpl(
  seed: Int
) : StringRegistry {
  // Memory fix: Replaced in-memory StringBuilder with a temporary file to reduce memory consumption.
  private val tempFile = File.createTempFile("lsparanoid", ".dat").apply {
    deleteOnExit()
  }
  private val writer = tempFile.writer(StandardCharsets.UTF_16BE)
  private val length = java.util.concurrent.atomic.AtomicInteger(0)

  private val seed = seed.toLong() and 0xffff_ffffL

  override fun registerString(string: String): Long {
    var mask = 0L
    var state = RandomHelper.seed(seed)
    state = RandomHelper.next(state)
    mask = mask or (state and 0xffff_0000_0000L)
    state = RandomHelper.next(state)
    mask = mask or ((state and 0xffff_0000_0000L) shl 16)
    val index = length
    val id = seed or ((index.toLong() shl 32) xor mask)

    state = RandomHelper.next(state)
    val lengthChar = (((state ushr 32) and 0xffffL) xor string.length.toLong()).toInt().toChar()
    writer.write(lengthChar.code)
    length++

    for (char in string) {
      state = RandomHelper.next(state)
      val contentChar = (((state ushr 32) and 0xffffL) xor char.code.toLong()).toInt().toChar()
      writer.write(contentChar.code)
      length++
    }

    return id
  }

  @Deprecated("Use streamChunks for better memory efficiency", ReplaceWith("streamChunks(consumer)"))
  override fun getAllChunks(): List<String> {
    // This implementation remains for compatibility but should not be used for large datasets.
    val chunks = mutableListOf<String>()
    streamChunks { chunks.add(it) }
    return chunks
  }

  override fun streamChunks(consumer: (String) -> Unit) {
    writer.flush()
    val totalLength = tempFile.length().toInt() / 2 // UTF-16BE uses 2 bytes per char
    if (totalLength == 0) {
      return
    }
    tempFile.reader(StandardCharsets.UTF_16BE).buffered().use { reader ->
      val buffer = CharArray(DeobfuscatorHelper.MAX_CHUNK_LENGTH)
      var charsRead: Int
      while (reader.read(buffer).also { charsRead = it } != -1) {
        consumer(String(buffer, 0, charsRead))
      }
    }
  }

  override fun getChunkCount(): Int {
    writer.flush()
    val totalLength = length
    if (totalLength == 0) {
      return 0
    }
    return ((totalLength + DeobfuscatorHelper.MAX_CHUNK_LENGTH - 1) / DeobfuscatorHelper.MAX_CHUNK_LENGTH).toInt()
  }

  override fun close() {
    writer.close()
    tempFile.delete()
  }
}
