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
import org.objectweb.asm.Type
import org.objectweb.asm.commons.GeneratorAdapter
import org.objectweb.asm.commons.Method

class StringConstantsClassPatcher(
    classVisitor: ClassVisitor,
    private val deobfuscationMethod: Method,
    private val stringRegistry: StringRegistry,
    private val analysisResult: AnalysisResult
) : ClassVisitor(Opcodes.ASM9, classVisitor) {

    private lateinit var classType: Type
    private var isClassWithObfuscatedStrings = false

    override fun visit(
        version: Int,
        access: Int,
        name: String,
        signature: String?,
        superName: String?,
        interfaces: Array<String>?
    ) {
        classType = Type.getObjectType(name)
        isClassWithObfuscatedStrings = classType in analysisResult.configurationsByType
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
        if (methodVisitor != null && isClassWithObfuscatedStrings && name == "<clinit>") {
            return stringRegistry.createStringConstantsMethodPatcher(methodVisitor, access, name, descriptor)
        }
        return methodVisitor
    }

    private fun StringRegistry.createStringConstantsMethodPatcher(
        methodVisitor: MethodVisitor,
        access: Int,
        name: String,
        descriptor: String
    ): MethodVisitor {
        return object : GeneratorAdapter(Opcodes.ASM9, methodVisitor, access, name, descriptor) {
            override fun visitFieldInsn(opcode: Int, owner: String, name: String, descriptor: String) {
                if (opcode == Opcodes.PUTSTATIC && Type.getObjectType(owner) == classType) {
                    val configuration = analysisResult.configurationsByType[classType]
                    if (configuration != null) {
                        val string = configuration.constantStringsByFieldName[name]
                        if (string != null) {
                            pop()
                            push(registerString(string))
                            invokeStatic(deobfuscationMethod.owner, deobfuscationMethod)
                        }
                    }
                }
                super.visitFieldInsn(opcode, owner, name, descriptor)
            }
        }
    }
}
