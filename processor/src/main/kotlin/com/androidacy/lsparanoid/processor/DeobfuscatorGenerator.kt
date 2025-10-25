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
    writer.generateEnsureChunkLoadedMethod()
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

      // Lazy-initialize chunks array (empty, all nulls) on first call
      getStatic(deobfuscator.type.toAsmType(), "chunks", STRING_ARRAY_TYPE)
      val chunksReady = newLabel()
      ifNonNull(chunksReady)

      // First call - initialize empty array (chunks loaded on-demand by DeobfuscatorHelper)
      push(chunkCount)
      newArray(STRING_TYPE)
      putStatic(deobfuscator.type.toAsmType(), "chunks", STRING_ARRAY_TYPE)

      mark(chunksReady)
      // Call DeobfuscatorHelper.getString with lazy chunk loading
      loadArg(0)  // id parameter
      getStatic(deobfuscator.type.toAsmType(), "chunks", STRING_ARRAY_TYPE)  // chunks array
      push(deobfuscator.type.toAsmType())  // Deobfuscator.class for reflection
      invokeStatic(DEOBFUSCATOR_HELPER_TYPE, METHOD_GET_STRING)
      returnValue()
    }
  }

  private fun ClassVisitor.generateEnsureChunkLoadedMethod() {
    newMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC or Opcodes.ACC_SYNCHRONIZED, METHOD_ENSURE_CHUNK_LOADED) {
      // This method is called via reflection by DeobfuscatorHelper when a chunk is needed
      // It loads the chunk at the given index and returns it
      // Signature: public static synchronized String ensureChunkLoaded(int index)

      loadArg(0)  // chunk index
      invokeStatic(deobfuscator.type.toAsmType(), METHOD_LOAD_CHUNK)
      returnValue()
    }
  }

  companion object {
    private val METHOD_DEFAULT_CONSTRUCTOR = Method("<init>", "()V")
    private val METHOD_LOAD_CHUNK = Method("loadChunk", "(I)Ljava/lang/String;")
    private val METHOD_ENSURE_CHUNK_LOADED = Method("ensureChunkLoaded", "(I)Ljava/lang/String;")
    private val METHOD_LOAD_CHUNKS_FROM_BYTE_ARRAY = Method("loadChunksFromByteArray", "([BJ)[Ljava/lang/String;")
    private val METHOD_GET_STRING = Method("getString", "(J[Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/String;")
    private val METHOD_BASE64_DECODE = Method("decode", "(Ljava/lang/String;)[B")

    private val OBJECT_TYPE = Type.getObjectType("java/lang/Object")
    private val STRING_TYPE = Type.getType(String::class.java)
    private val STRING_ARRAY_TYPE = Type.getType(Array<String>::class.java)
    private val DEOBFUSCATOR_HELPER_TYPE = Type.getObjectType("com/androidacy/lsparanoid/DeobfuscatorHelper")
    private val BASE64_DECODER_TYPE = Type.getObjectType("com/androidacy/lsparanoid/Base64Decoder")
  }
}
