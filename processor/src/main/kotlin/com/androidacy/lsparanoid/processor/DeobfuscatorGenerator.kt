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
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Opcodes.ACC_PUBLIC
import org.objectweb.asm.Opcodes.ACC_SUPER
import org.objectweb.asm.Type
import org.objectweb.asm.commons.Method

class DeobfuscatorGenerator(
  private val deobfuscator: Deobfuscator,
  private val stringRegistry: StringRegistry,
  private val classRegistry: ClassRegistry,
  private val fileRegistry: FileRegistry,
  private val resourceName: String
) {

  fun generateDeobfuscator(): ByteArray {
    val writer = StandaloneClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES, classRegistry, fileRegistry)
    writer.visit(
      Opcodes.V1_6,
      ACC_PUBLIC or ACC_SUPER,
      deobfuscator.type.internalName,
      null,
      OBJECT_TYPE.internalName,
      null
    )

    writer.generateChunksField()
    writer.generateLoadChunksMethod()
    writer.generateDefaultConstructor()
    writer.generateGetStringMethod()

    writer.visitEnd()
    return writer.toByteArray()
  }

  private fun ClassVisitor.generateChunksField() {
    visitField(
      Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC or Opcodes.ACC_VOLATILE,
      CHUNKS_FIELD_NAME,
      CHUNKS_FIELD_TYPE.descriptor,
      null,
      null
    ).visitEnd()
  }

  private fun ClassVisitor.generateLoadChunksMethod() {
    newMethod(Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC, METHOD_LOAD_CHUNKS) {
      val totalLength = stringRegistry.getTotalLength()

      push(Type.getObjectType(deobfuscator.type.internalName))
      push("/$resourceName")
      push(totalLength)
      invokeStatic(DEOBFUSCATOR_HELPER_TYPE, METHOD_LOAD_CHUNKS_FROM_RESOURCE)
      returnValue()
    }
  }

  private fun ClassVisitor.generateDefaultConstructor() {
    newMethod(Opcodes.ACC_PUBLIC, METHOD_DEFAULT_CONSTRUCTOR) {
      loadThis()
      invokeConstructor(TYPE_OBJECT, METHOD_DEFAULT_CONSTRUCTOR)
    }
  }

  private fun ClassVisitor.generateGetStringMethod() {
    newMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, deobfuscator.deobfuscationMethod) {
      getStatic(deobfuscator.type.toAsmType(), CHUNKS_FIELD_NAME, CHUNKS_FIELD_TYPE)
      val afterSync = newLabel()
      ifNonNull(afterSync)

      val deobfuscatorClass = Type.getObjectType(deobfuscator.type.internalName)
      push(deobfuscatorClass)
      dup()
      monitorEnter()
      val syncLocal = newLocal(Type.getType(Class::class.java))
      storeLocal(syncLocal)

      getStatic(deobfuscator.type.toAsmType(), CHUNKS_FIELD_NAME, CHUNKS_FIELD_TYPE)
      val skipLoad = newLabel()
      ifNonNull(skipLoad)

      invokeStatic(deobfuscator.type.toAsmType(), METHOD_LOAD_CHUNKS)
      putStatic(deobfuscator.type.toAsmType(), CHUNKS_FIELD_NAME, CHUNKS_FIELD_TYPE)

      mark(skipLoad)
      loadLocal(syncLocal)
      monitorExit()

      mark(afterSync)
      loadArg(0)
      getStatic(deobfuscator.type.toAsmType(), CHUNKS_FIELD_NAME, CHUNKS_FIELD_TYPE)
      invokeStatic(DEOBFUSCATOR_HELPER_TYPE, METHOD_GET_STRING)
    }
  }

  companion object {
    private val METHOD_DEFAULT_CONSTRUCTOR = Method("<init>", "()V")
    private val METHOD_LOAD_CHUNKS = Method("loadChunks", "()[Ljava/lang/String;")
    private val METHOD_LOAD_CHUNKS_FROM_RESOURCE = Method("loadChunksFromResource", "(Ljava/lang/Class;Ljava/lang/String;J)[Ljava/lang/String;")
    private val METHOD_GET_STRING = Method("getString", "(J[Ljava/lang/String;)Ljava/lang/String;")

    private val TYPE_OBJECT = Type.getObjectType("java/lang/Object")
    private val DEOBFUSCATOR_HELPER_TYPE = Type.getObjectType("com/androidacy/lsparanoid/DeobfuscatorHelper")

    private const val CHUNKS_FIELD_NAME = "chunks"
    private val CHUNKS_FIELD_TYPE = Type.getType("[Ljava/lang/String;")
    private val CHUNKS_ELEMENT_TYPE = CHUNKS_FIELD_TYPE.elementType
  }
}
