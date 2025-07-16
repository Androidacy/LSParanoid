/*
 * Copyright 2021 Michael Rozumyanskiy
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

import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.commons.GeneratorAdapter
import org.objectweb.asm.commons.Method

class StringLiteralsClassPatcher(
    classVisitor: ClassVisitor,
    private val deobfuscationMethod: Method,
    private val stringRegistry: StringRegistry
) : ClassVisitor(Opcodes.ASM9, classVisitor) {

    private var isInterface: Boolean = false

    override fun visit(
        version: Int,
        access: Int,
        name: String,
        signature: String?,
        superName: String?,
        interfaces: Array<String>?
    ) {
        isInterface = (access and Opcodes.ACC_INTERFACE) != 0
        super.visit(version, access, name, signature, superName, interfaces)
    }

    override fun visitMethod(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        exceptions: Array<String>?
    ): MethodVisitor? {
        val methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions)
        if (methodVisitor != null && !isInterface) {
            return stringRegistry.createStringLiteralsMethodPatcher(methodVisitor, access, name, descriptor)
        }
        return methodVisitor
    }

    private fun StringRegistry.createStringLiteralsMethodPatcher(
        methodVisitor: MethodVisitor,
        access: Int,
        name: String,
        descriptor: String
    ): MethodVisitor {
        return object : GeneratorAdapter(Opcodes.ASM9, methodVisitor, access, name, descriptor) {
            override fun visitLdcInsn(value: Any) {
                if (value is String) {
                    push(registerString(value))
                    invokeStatic(deobfuscationMethod.owner, deobfuscationMethod)
                } else {
                    super.visitLdcInsn(value)
                }
            }
        }
    }
}
