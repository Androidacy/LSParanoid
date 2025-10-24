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
    writer.generateEnsureChunksLoadedMethod()
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
    // WeakReference<String>[] chunks field
    visitField(
      Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC or Opcodes.ACC_VOLATILE,
      "chunks",
      WEAK_REF_ARRAY_TYPE.descriptor,
      null,
      null
    ).visitEnd()

    // int loadedUpTo field
    visitField(
      Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC,
      "loadedUpTo",
      "I",
      null,
      0
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

  private fun ClassVisitor.generateEnsureChunksLoadedMethod() {
    newMethod(Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC or Opcodes.ACC_SYNCHRONIZED, METHOD_ENSURE_CHUNKS_LOADED) {
      val chunkCount = stringRegistry.getChunkCount()
      val batchSize = 20

      // Initialize chunks array if null
      getStatic(deobfuscator.type.toAsmType(), "chunks", WEAK_REF_ARRAY_TYPE)
      val skipInit = newLabel()
      ifNonNull(skipInit)

      push(chunkCount)
      newArray(WEAK_REF_TYPE)
      putStatic(deobfuscator.type.toAsmType(), "chunks", WEAK_REF_ARRAY_TYPE)

      mark(skipInit)

      // Load argument (minIndex)
      loadArg(0)
      val indexLocal = newLocal(Type.INT_TYPE)
      storeLocal(indexLocal)

      // while (loadedUpTo <= minIndex)
      val loopStart = newLabel()
      val loopEnd = newLabel()

      mark(loopStart)
      getStatic(deobfuscator.type.toAsmType(), "loadedUpTo", Type.INT_TYPE)
      loadLocal(indexLocal)
      ifCmp(Type.INT_TYPE, GeneratorAdapter.GT, loopEnd)

      // int end = Math.min(loadedUpTo + batchSize, chunkCount)
      getStatic(deobfuscator.type.toAsmType(), "loadedUpTo", Type.INT_TYPE)
      push(batchSize)
      math(Opcodes.IADD, Type.INT_TYPE)
      push(chunkCount)
      invokeStatic(Type.getType(Math::class.java), Method("min", "(II)I"))
      val endLocal = newLocal(Type.INT_TYPE)
      storeLocal(endLocal)

      // for (int i = loadedUpTo; i < end; i++)
      getStatic(deobfuscator.type.toAsmType(), "loadedUpTo", Type.INT_TYPE)
      val iLocal = newLocal(Type.INT_TYPE)
      storeLocal(iLocal)

      val forStart = newLabel()
      val forEnd = newLabel()

      mark(forStart)
      loadLocal(iLocal)
      loadLocal(endLocal)
      ifCmp(Type.INT_TYPE, GeneratorAdapter.GE, forEnd)

      // Check if chunks[i] is null or cleared
      getStatic(deobfuscator.type.toAsmType(), "chunks", WEAK_REF_ARRAY_TYPE)
      loadLocal(iLocal)
      arrayLoad(WEAK_REF_TYPE)
      dup()
      val refNotNull = newLabel()
      ifNonNull(refNotNull)

      // Ref is null, need to load
      pop()  // Remove dup
      val needLoad = newLabel()
      goTo(needLoad)

      mark(refNotNull)
      // Check if WeakReference.get() returns null
      invokeVirtual(WEAK_REF_TYPE, Method("get", "()Ljava/lang/Object;"))
      val dontNeedLoad = newLabel()
      ifNonNull(dontNeedLoad)

      mark(needLoad)
      // Load chunk
      getStatic(deobfuscator.type.toAsmType(), "chunks", WEAK_REF_ARRAY_TYPE)
      loadLocal(iLocal)

      // Create WeakReference with loaded chunk
      newInstance(WEAK_REF_TYPE)
      dup()
      loadLocal(iLocal)
      invokeStatic(deobfuscator.type.toAsmType(), METHOD_LOAD_CHUNK)
      invokeConstructor(WEAK_REF_TYPE, Method("<init>", "(Ljava/lang/Object;)V"))

      arrayStore(WEAK_REF_TYPE)

      mark(dontNeedLoad)

      // i++
      iinc(iLocal, 1)
      goTo(forStart)

      mark(forEnd)

      // loadedUpTo = end
      loadLocal(endLocal)
      putStatic(deobfuscator.type.toAsmType(), "loadedUpTo", Type.INT_TYPE)

      goTo(loopStart)

      mark(loopEnd)
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

      // Ensure chunks loaded
      push(chunkCount - 1)
      invokeStatic(deobfuscator.type.toAsmType(), METHOD_ENSURE_CHUNKS_LOADED)

      // Unwrap WeakReferences to String[]
      push(chunkCount)
      newArray(Type.getType(String::class.java))
      val chunksLocal = newLocal(Type.getType("[Ljava/lang/String;"))
      storeLocal(chunksLocal)

      for (i in 0 until chunkCount) {
        loadLocal(chunksLocal)
        push(i)

        // Get WeakReference from chunks[i]
        getStatic(deobfuscator.type.toAsmType(), "chunks", WEAK_REF_ARRAY_TYPE)
        push(i)
        arrayLoad(WEAK_REF_TYPE)

        // Check if WeakReference object itself is null
        dup()
        val weakRefNotNull = newLabel()
        ifNonNull(weakRefNotNull)

        // WeakReference is null, reload chunk
        pop()
        push(i)
        invokeStatic(deobfuscator.type.toAsmType(), METHOD_LOAD_CHUNK)
        val afterLoad = newLabel()
        goTo(afterLoad)

        mark(weakRefNotNull)
        // WeakReference exists, call get()
        invokeVirtual(WEAK_REF_TYPE, Method("get", "()Ljava/lang/Object;"))

        // Check if referent is null (GC'd)
        dup()
        val referentNotNull = newLabel()
        ifNonNull(referentNotNull)

        // Referent was GC'd, reload chunk
        pop()
        push(i)
        invokeStatic(deobfuscator.type.toAsmType(), METHOD_LOAD_CHUNK)
        goTo(afterLoad)

        mark(referentNotNull)
        // Have valid referent from WeakRef
        mark(afterLoad)
        // Stack: chunksLocal, i, String
        checkCast(Type.getType(String::class.java))
        arrayStore(Type.getType(String::class.java))
      }

      // Call DeobfuscatorHelper.getString
      loadArg(0)
      loadLocal(chunksLocal)
      invokeStatic(DEOBFUSCATOR_HELPER_TYPE, METHOD_GET_STRING)
      returnValue()
    }
  }

  companion object {
    private val METHOD_DEFAULT_CONSTRUCTOR = Method("<init>", "()V")
    private val METHOD_LOAD_CHUNK = Method("loadChunk", "(I)Ljava/lang/String;")
    private val METHOD_ENSURE_CHUNKS_LOADED = Method("ensureChunksLoaded", "(I)V")
    private val METHOD_LOAD_CHUNKS_FROM_BYTE_ARRAY = Method("loadChunksFromByteArray", "([BJ)[Ljava/lang/String;")
    private val METHOD_GET_STRING = Method("getString", "(J[Ljava/lang/String;)Ljava/lang/String;")
    private val METHOD_BASE64_DECODE = Method("decode", "(Ljava/lang/String;)[B")

    private val OBJECT_TYPE = Type.getObjectType("java/lang/Object")
    private val DEOBFUSCATOR_HELPER_TYPE = Type.getObjectType("com/androidacy/lsparanoid/DeobfuscatorHelper")
    private val BASE64_DECODER_TYPE = Type.getObjectType("com/androidacy/lsparanoid/Base64Decoder")
    private val WEAK_REF_TYPE = Type.getObjectType("java/lang/ref/WeakReference")
    private val WEAK_REF_ARRAY_TYPE = Type.getType("[Ljava/lang/ref/WeakReference;")
  }
}
