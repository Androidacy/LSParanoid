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
import java.io.File
import java.io.Closeable

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

  private val seed = seed.toLong() and 0xffff_ffffL
  private val tempFile = File.createTempFile("lsparanoid-", ".tmp").apply {
    deleteOnExit()
  }
  private var length = 0L
  private val writer = tempFile.writer()

  override fun registerString(string: String): Long {
    var mask = 0L
    var state = RandomHelper.seed(seed)
    state = RandomHelper.next(state)
    mask = mask or (state and 0xffff_0000_0000L)
    state = RandomHelper.next(state)
    mask = mask or ((state and 0xffff_0000_0000L) shl 16)
    val index = length
    val id = seed or ((index shl 32) xor mask)

    state = RandomHelper.next(state)
    writer.append((((state ushr 32) and 0xffffL) xor string.length.toLong()).toInt().toChar())

    for (char in string) {
      state = RandomHelper.next(state)
      writer.append((((state ushr 32) and 0xffffL) xor char.code.toLong()).toInt().toChar())
    }
    length += string.length + 1

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
    val totalLength = tempFile.length()
    if (totalLength == 0L) {
      return
    }
    tempFile.reader().use { reader ->
      val buffer = CharArray(DeobfuscatorHelper.MAX_CHUNK_LENGTH)
      var read: Int
      while (reader.read(buffer).also { read = it } != -1) {
        consumer(String(buffer, 0, read))
      }
    }
  }

  override fun getChunkCount(): Int {
    writer.flush()
    val totalLength = tempFile.length()
    if (totalLength == 0L) {
      return 0
    }
    return ((totalLength + DeobfuscatorHelper.MAX_CHUNK_LENGTH - 1) / DeobfuscatorHelper.MAX_CHUNK_LENGTH).toInt()
  }

  override fun close() {
    writer.close()
    tempFile.delete()
  }
}
