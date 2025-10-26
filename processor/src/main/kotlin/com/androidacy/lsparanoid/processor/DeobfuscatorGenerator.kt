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

package com.androidacy.lsparanoid.processor

import com.joom.grip.ClassRegistry
import com.joom.grip.FileRegistry
import com.joom.grip.mirrors.toAsmType
import com.androidacy.lsparanoid.processor.model.Deobfuscator
import com.androidacy.lsparanoid.DeobfuscatorHelper
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Opcodes.ACC_PUBLIC
import org.objectweb.asm.Opcodes.ACC_SUPER
import org.objectweb.asm.Type
import org.objectweb.asm.commons.Method
import org.objectweb.asm.commons.GeneratorAdapter

class DeobfuscatorGenerator(
  private val deobfuscator: Deobfuscator,
  private val stringRegistry: StringRegistry,
  private val classRegistry: ClassRegistry,
  private val fileRegistry: FileRegistry
) {

  // Returns map of class name to class bytes
  fun generateDeobfuscatorClasses(): Map<String, ByteArray> {
    val classes = mutableMapOf<String, ByteArray>()

    // Generate main Deobfuscator class
    classes["${deobfuscator.type.internalName}.class"] = generateMainClass()

    // Generate chunk data classes
    val chunkCount = stringRegistry.getChunkCount()
    val data = stringRegistry.getDataAsByteArray()
    val charsPerChunk = DeobfuscatorHelper.MAX_CHUNK_LENGTH
    val bytesPerChunk = charsPerChunk * 2  // 2 bytes per char

    for (i in 0 until chunkCount) {
      val chunkClassName = "${deobfuscator.type.internalName}\$Chunk$i"
      val startByte = i * bytesPerChunk
      val endByte = minOf((i + 1) * bytesPerChunk, data.size)
      val chunkData = data.copyOfRange(startByte, endByte)
      classes["$chunkClassName.class"] = generateChunkClass(i, chunkData)
    }
    return classes
  }

  private fun generateMainClass(): ByteArray {
    val writer = StandaloneClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES, classRegistry, fileRegistry)
    writer.visit(
      Opcodes.V1_6,
      ACC_PUBLIC or ACC_SUPER,
      deobfuscator.type.internalName,
      null,
      OBJECT_TYPE.internalName,
      null
    )

    writer.generateFields()
    writer.generateLoadChunkMethod()
    writer.generateGetCharAtMethod()
    writer.generateDefaultConstructor()
    writer.generateGetStringMethod()

    writer.visitEnd()
    return writer.toByteArray()
  }

  private fun generateChunkClass(chunkIndex: Int, chunkData: ByteArray): ByteArray {
    val writer = StandaloneClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES, classRegistry, fileRegistry)
    val className = "${deobfuscator.type.internalName}\$Chunk$chunkIndex"

    writer.visit(
      Opcodes.V1_6,
      ACC_PUBLIC or ACC_SUPER,
      className,
      null,
      OBJECT_TYPE.internalName,
      null
    )

    // Add DATA field
    writer.visitField(
      Opcodes.ACC_STATIC or Opcodes.ACC_FINAL,
      "DATA",
      "[B",
      null,
      null
    ).visitEnd()

    // Generate static initializer with Base64-encoded chunk data
    val base64Data = java.util.Base64.getEncoder().encodeToString(chunkData)
    writer.newMethod(Opcodes.ACC_STATIC, Method("<clinit>", "()V")) {
      push(base64Data)
      invokeStatic(BASE64_DECODER_TYPE, METHOD_BASE64_DECODE)
      putStatic(Type.getObjectType(className), "DATA", Type.getType("[B"))
      returnValue()
    }

    // Default constructor
    writer.newMethod(Opcodes.ACC_PUBLIC, METHOD_DEFAULT_CONSTRUCTOR) {
      loadThis()
      invokeConstructor(OBJECT_TYPE, METHOD_DEFAULT_CONSTRUCTOR)
      returnValue()
    }

    writer.visitEnd()
    return writer.toByteArray()
  }

  private fun ClassVisitor.generateFields() {
    // String[] chunks field - lazy loaded on demand
    visitField(
      Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC or Opcodes.ACC_VOLATILE,
      "chunks",
      STRING_ARRAY_TYPE.descriptor,
      null,
      null
    ).visitEnd()
  }

  private fun ClassVisitor.generateLoadChunkMethod() {
    newMethod(Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC, METHOD_LOAD_CHUNK) {
      val chunkCount = stringRegistry.getChunkCount()
      val totalLength = stringRegistry.getTotalLength()
      val charsPerChunk = DeobfuscatorHelper.MAX_CHUNK_LENGTH.toLong()

      val switchLabels = Array(chunkCount) { newLabel() }
      val defaultLabel = newLabel()
      val endLabel = newLabel()

      loadArg(0)  // chunk index
      visitTableSwitchInsn(0, chunkCount - 1, defaultLabel, *switchLabels)

      // Generate case for each chunk
      for (i in 0 until chunkCount) {
        mark(switchLabels[i])
        val chunkClassName = "${deobfuscator.type.internalName}\$Chunk$i"

        // Load DATA field
        getStatic(Type.getObjectType(chunkClassName), "DATA", Type.getType("[B"))

        // Calculate length for this chunk
        val startChar = i * charsPerChunk
        val endChar = minOf((i + 1) * charsPerChunk, totalLength)
        val chunkLength = endChar - startChar

        push(chunkLength)
        invokeStatic(DEOBFUSCATOR_HELPER_TYPE, METHOD_LOAD_CHUNKS_FROM_BYTE_ARRAY)
        push(0)  // Get first (and only) element
        arrayLoad(Type.getType(String::class.java))
        goTo(endLabel)
      }

      mark(defaultLabel)
      throwException(Type.getType(IllegalArgumentException::class.java), "Invalid chunk index")

      mark(endLabel)
      returnValue()
    }
  }

  private fun ClassVisitor.generateGetCharAtMethod() {
    // private static long getCharAt(int charIndex, long state)
    newMethod(Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC, METHOD_GET_CHAR_AT) {
      val maxChunkLength = DeobfuscatorHelper.MAX_CHUNK_LENGTH

      // long nextState = RandomHelper.next(state)
      loadArg(1) // state
      invokeStatic(RANDOM_HELPER_TYPE, METHOD_RANDOM_NEXT)
      val nextState = newLocal(Type.LONG_TYPE)
      storeLocal(nextState)

      // int chunkIndex = charIndex / MAX_CHUNK_LENGTH
      loadArg(0) // charIndex (int)
      push(maxChunkLength) // MAX_CHUNK_LENGTH (int)
      math(GeneratorAdapter.DIV, Type.INT_TYPE) // charIndex / MAX_CHUNK_LENGTH
      val chunkIndex = newLocal(Type.INT_TYPE)
      storeLocal(chunkIndex)

      // String chunk = chunks[chunkIndex]
      getStatic(deobfuscator.type.toAsmType(), "chunks", STRING_ARRAY_TYPE)
      loadLocal(chunkIndex)
      arrayLoad(STRING_TYPE)
      val chunk = newLocal(STRING_TYPE)
      storeLocal(chunk)

      // if (chunk == null) { chunk = loadChunk(chunkIndex); chunks[chunkIndex] = chunk; }
      loadLocal(chunk)
      val chunkLoaded = newLabel()
      ifNonNull(chunkLoaded)

      loadLocal(chunkIndex)
      invokeStatic(deobfuscator.type.toAsmType(), METHOD_LOAD_CHUNK)
      dup()
      storeLocal(chunk)
      getStatic(deobfuscator.type.toAsmType(), "chunks", STRING_ARRAY_TYPE)
      swap()
      loadLocal(chunkIndex)
      swap()
      arrayStore(STRING_TYPE)

      mark(chunkLoaded)

      // int indexInChunk = charIndex - (chunkIndex * MAX_CHUNK_LENGTH)
      loadArg(0) // charIndex
      loadLocal(chunkIndex)
      push(maxChunkLength)
      math(GeneratorAdapter.MUL, Type.INT_TYPE)
      math(GeneratorAdapter.SUB, Type.INT_TYPE)
      val indexInChunk = newLocal(Type.INT_TYPE)
      storeLocal(indexInChunk)

      // return nextState ^ ((long)chunk.charAt(indexInChunk) << 32)
      loadLocal(nextState)
      loadLocal(chunk)
      loadLocal(indexInChunk)
      invokeVirtual(STRING_TYPE, Method("charAt", "(I)C"))
      cast(Type.CHAR_TYPE, Type.LONG_TYPE)
      push(32)
      math(GeneratorAdapter.SHL, Type.LONG_TYPE)
      math(GeneratorAdapter.XOR, Type.LONG_TYPE)
      returnValue()
    }
  }

  private fun ClassVisitor.generateDefaultConstructor() {
    newMethod(Opcodes.ACC_PUBLIC, METHOD_DEFAULT_CONSTRUCTOR) {
      loadThis()
      invokeConstructor(OBJECT_TYPE, METHOD_DEFAULT_CONSTRUCTOR)
      returnValue()
    }
  }

  private fun ClassVisitor.generateGetStringMethod() {
    newMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, deobfuscator.deobfuscationMethod) {
      val chunkCount = stringRegistry.getChunkCount()

      // Handle empty string registry
      if (chunkCount == 0) {
        push("")
        returnValue()
        return@newMethod
      }

      // Lazy-initialize chunks array
      getStatic(deobfuscator.type.toAsmType(), "chunks", STRING_ARRAY_TYPE)
      val chunksReady = newLabel()
      ifNonNull(chunksReady)

      push(chunkCount)
      newArray(STRING_TYPE)
      putStatic(deobfuscator.type.toAsmType(), "chunks", STRING_ARRAY_TYPE)

      mark(chunksReady)

      // long state = RandomHelper.seed(id & 0xFFFFFFFFL)
      loadArg(0) // id
      push(0xFFFFFFFFL)
      math(GeneratorAdapter.AND, Type.LONG_TYPE)
      invokeStatic(RANDOM_HELPER_TYPE, METHOD_RANDOM_SEED)
      val state = newLocal(Type.LONG_TYPE)
      storeLocal(state)

      // state = RandomHelper.next(state)
      loadLocal(state)
      invokeStatic(RANDOM_HELPER_TYPE, METHOD_RANDOM_NEXT)
      storeLocal(state)

      // long low = (state >>> 32) & 0xffff
      loadLocal(state)
      push(32)
      math(GeneratorAdapter.USHR, Type.LONG_TYPE)
      push(0xffffL)
      math(GeneratorAdapter.AND, Type.LONG_TYPE)
      val low = newLocal(Type.LONG_TYPE)
      storeLocal(low)

      // state = RandomHelper.next(state)
      loadLocal(state)
      invokeStatic(RANDOM_HELPER_TYPE, METHOD_RANDOM_NEXT)
      storeLocal(state)

      // long high = (state >>> 16) & 0xffff0000
      loadLocal(state)
      push(16)
      math(GeneratorAdapter.USHR, Type.LONG_TYPE)
      push(0xffff0000L)
      math(GeneratorAdapter.AND, Type.LONG_TYPE)
      val high = newLocal(Type.LONG_TYPE)
      storeLocal(high)

      // int index = (int)((id >>> 32) ^ low ^ high)
      loadArg(0) // id
      push(32)
      math(GeneratorAdapter.USHR, Type.LONG_TYPE)
      loadLocal(low)
      math(GeneratorAdapter.XOR, Type.LONG_TYPE)
      loadLocal(high)
      math(GeneratorAdapter.XOR, Type.LONG_TYPE)
      cast(Type.LONG_TYPE, Type.INT_TYPE)
      val index = newLocal(Type.INT_TYPE)
      storeLocal(index)

      // state = getCharAt(index, state)
      loadLocal(index)
      loadLocal(state)
      invokeStatic(deobfuscator.type.toAsmType(), METHOD_GET_CHAR_AT)
      storeLocal(state)

      // int length = (int)((state >>> 32) & 0xffffL)
      loadLocal(state)
      push(32)
      math(GeneratorAdapter.USHR, Type.LONG_TYPE)
      push(0xffffL)
      math(GeneratorAdapter.AND, Type.LONG_TYPE)
      cast(Type.LONG_TYPE, Type.INT_TYPE)
      val length = newLocal(Type.INT_TYPE)
      storeLocal(length)

      // char[] chars = new char[length]
      loadLocal(length)
      newArray(Type.CHAR_TYPE)
      val chars = newLocal(Type.getType("[C"))
      storeLocal(chars)

      // for (int i = 0; i < length; i++)
      val i = newLocal(Type.INT_TYPE)
      push(0)
      storeLocal(i)
      val loopStart = mark()
      loadLocal(i)
      loadLocal(length)
      val loopEnd = newLabel()
      ifICmp(GeneratorAdapter.GE, loopEnd)

      // state = getCharAt(index + i + 1, state)
      loadLocal(index)
      loadLocal(i)
      math(GeneratorAdapter.ADD, Type.INT_TYPE)
      push(1)
      math(GeneratorAdapter.ADD, Type.INT_TYPE)
      loadLocal(state)
      invokeStatic(deobfuscator.type.toAsmType(), METHOD_GET_CHAR_AT)
      storeLocal(state)

      // chars[i] = (char)((state >>> 32) & 0xffffL)
      loadLocal(chars)
      loadLocal(i)
      loadLocal(state)
      push(32)
      math(GeneratorAdapter.USHR, Type.LONG_TYPE)
      push(0xffffL)
      math(GeneratorAdapter.AND, Type.LONG_TYPE)
      cast(Type.LONG_TYPE, Type.CHAR_TYPE)
      arrayStore(Type.CHAR_TYPE)

      // i++
      iinc(i, 1)
      goTo(loopStart)

      mark(loopEnd)

      // return new String(chars)
      newInstance(STRING_TYPE)
      dup()
      loadLocal(chars)
      invokeConstructor(STRING_TYPE, Method("<init>", "([C)V"))
      returnValue()
    }
  }

  companion object {
    private val METHOD_DEFAULT_CONSTRUCTOR = Method("<init>", "()V")
    private val METHOD_LOAD_CHUNK = Method("loadChunk", "(I)Ljava/lang/String;")
    private val METHOD_GET_CHAR_AT = Method("getCharAt", "(IJ)J")
    private val METHOD_LOAD_CHUNKS_FROM_BYTE_ARRAY = Method("loadChunksFromByteArray", "([BJ)[Ljava/lang/String;")
    private val METHOD_BASE64_DECODE = Method("decode", "(Ljava/lang/String;)[B")
    private val METHOD_RANDOM_SEED = Method("seed", "(J)J")
    private val METHOD_RANDOM_NEXT = Method("next", "(J)J")

    private val OBJECT_TYPE = Type.getObjectType("java/lang/Object")
    private val STRING_TYPE = Type.getType(String::class.java)
    private val STRING_ARRAY_TYPE = Type.getType(Array<String>::class.java)
    private val DEOBFUSCATOR_HELPER_TYPE = Type.getObjectType("com/androidacy/lsparanoid/DeobfuscatorHelper")
    private val RANDOM_HELPER_TYPE = Type.getObjectType("com/androidacy/lsparanoid/RandomHelper")
    private val BASE64_DECODER_TYPE = Type.getObjectType("com/androidacy/lsparanoid/Base64Decoder")
  }
}
