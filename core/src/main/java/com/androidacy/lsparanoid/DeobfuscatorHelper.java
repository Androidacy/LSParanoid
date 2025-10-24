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

package com.androidacy.lsparanoid;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * The type Deobfuscator helper.
 */
public class DeobfuscatorHelper {
  /**
   * The constant MAX_CHUNK_LENGTH.
   */
  public static final int MAX_CHUNK_LENGTH = 0x1fff;

  private DeobfuscatorHelper() {
    // Cannot be instantiated.
  }

  /**
   * Load chunks from resource.
   *
   * @param clazz the class to load resource from
   * @param resourceName the resource name
   * @param totalLength the total length in characters
   * @return the chunks array
   */
  public static String[] loadChunksFromResource(final Class<?> clazz, final String resourceName, final long totalLength) {
    try (final InputStream is = clazz.getResourceAsStream(resourceName);
         final DataInputStream dis = new DataInputStream(is)) {
      final int chunkCount = (int) ((totalLength + MAX_CHUNK_LENGTH - 1) / MAX_CHUNK_LENGTH);
      final String[] chunks = new String[chunkCount];

      long charsRead = 0;
      int chunkIndex = 0;

      while (charsRead < totalLength) {
        final int chunkSize = (int) Math.min(MAX_CHUNK_LENGTH, totalLength - charsRead);
        final char[] buffer = new char[chunkSize];

        for (int i = 0; i < chunkSize; i++) {
          buffer[i] = dis.readChar();
        }

        chunks[chunkIndex++] = new String(buffer);
        charsRead += chunkSize;
      }

      return chunks;
    } catch (final IOException e) {
      throw new RuntimeException("Failed to load obfuscated strings", e);
    }
  }

  /**
   * Load chunks from byte array.
   *
   * @param data the byte array containing encoded characters
   * @param totalLength the total length in characters
   * @return the chunks array
   */
  public static String[] loadChunksFromByteArray(final byte[] data, final long totalLength) {
    try (final DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data))) {
      final int chunkCount = (int) ((totalLength + MAX_CHUNK_LENGTH - 1) / MAX_CHUNK_LENGTH);
      final String[] chunks = new String[chunkCount];

      long charsRead = 0;
      int chunkIndex = 0;

      while (charsRead < totalLength) {
        final int chunkSize = (int) Math.min(MAX_CHUNK_LENGTH, totalLength - charsRead);
        final char[] buffer = new char[chunkSize];

        for (int i = 0; i < chunkSize; i++) {
          buffer[i] = dis.readChar();
        }

        chunks[chunkIndex++] = new String(buffer);
        charsRead += chunkSize;
      }

      return chunks;
    } catch (final IOException e) {
      throw new RuntimeException("Failed to load obfuscated strings", e);
    }
  }

  /**
   * Gets string.
   *
   * @param id     the id
   * @param chunks the chunks
   * @return the string
   */
  public static String getString(final long id, final String[] chunks) {
    long state = RandomHelper.seed(id & 0xffffffffL);
    state = RandomHelper.next(state);
    final long low = (state >>> 32) & 0xffff;
    state = RandomHelper.next(state);
    final long high = (state >>> 16) & 0xffff0000;
    final int index = (int) ((id >>> 32) ^ low ^ high);
    state = getCharAt(index, chunks, state);
    final int length = (int) ((state >>> 32) & 0xffffL);
    final char[] chars = new char[length];

    for (int i = 0; i < length; ++i) {
      state = getCharAt(index + i + 1, chunks, state);
      chars[i] = (char) ((state >>> 32) & 0xffffL);
    }

    return new String(chars);
  }

  private static long getCharAt(final int charIndex, final String[] chunks, final long state) {
    final long nextState = RandomHelper.next(state);
    final int chunkIndex = charIndex / MAX_CHUNK_LENGTH;

    if (chunkIndex < 0 || chunkIndex >= chunks.length) {
      throw new IllegalArgumentException("Chunk index out of bounds: " + chunkIndex);
    }

    final String chunk = chunks[chunkIndex];
    if (chunk == null) {
      throw new IllegalStateException("Chunk is null at index: " + chunkIndex);
    }

    final int indexInChunk = charIndex - (chunkIndex * MAX_CHUNK_LENGTH);
    if (indexInChunk < 0 || indexInChunk >= chunk.length()) {
      throw new IllegalArgumentException("Index in chunk out of bounds: " + indexInChunk + ", chunk length: " + chunk.length());
    }

    return nextState ^ ((long) chunk.charAt(indexInChunk) << 32);
  }
}
