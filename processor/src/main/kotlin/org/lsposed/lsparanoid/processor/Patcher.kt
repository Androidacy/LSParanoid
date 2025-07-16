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

import com.joom.grip.ClassRegistry
import com.joom.grip.FileRegistry
import com.joom.grip.io.FileSource
import com.joom.grip.mirrors.Type
import org.lsposed.lsparanoid.processor.commons.closeQuietly
import org.lsposed.lsparanoid.processor.logging.getLogger
import org.lsposed.lsparanoid.processor.model.Deobfuscator
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import java.util.jar.JarOutputStream

class Patcher(
    private val deobfuscator: Deobfuscator,
    private val stringRegistry: StringRegistry,
    private val analysisResult: AnalysisResult,
    private val classRegistry: ClassRegistry,
    private val fileRegistry: FileRegistry,
    private val asmApi: Int
) {

    private val logger = getLogger()

    fun copyAndPatchClasses(sources: List<FileSource>, sink: JarOutputStream) {
        for (source in sources) {
            try {
                copyAndPatchClasses(source, sink)
            } finally {
                source.closeQuietly()
            }
        }
    }

    private fun copyAndPatchClasses(source: FileSource, sink: JarOutputStream) {
        source.listFiles { path, type ->
            when (type) {
                FileSource.EntryType.CLASS -> {
                    logger.debug("Patching {}...", path)
                    val classReader = ClassReader(source.readFile(path))
                    val classWriter = StandaloneClassWriter(ClassWriter.COMPUTE_MAXS, classRegistry, fileRegistry)
                    val classVisitor = createClassVisitor(classWriter)
                    classReader.accept(classVisitor, ClassReader.EXPAND_FRAMES)
                    sink.putNextEntry(java.util.jar.JarEntry(path))
                    sink.write(classWriter.toByteArray())
                }
                FileSource.EntryType.FILE -> {
                    logger.debug("Copying {}...", path)
                    sink.putNextEntry(java.util.jar.JarEntry(path))
                    sink.write(source.readFile(path))
                }
                FileSource.EntryType.DIRECTORY -> {
                    logger.debug("Copying {}...", path)
                    sink.putNextEntry(java.util.jar.JarEntry(path))
                }
            }
        }
    }

    private fun createClassVisitor(classVisitor: ClassVisitor): ClassVisitor {
        val stringLiteralsPatcher = StringLiteralsClassPatcher(classVisitor, deobfuscator.deobfuscationMethod, stringRegistry)
        val stringConstantsPatcher =
            StringConstantsClassPatcher(stringLiteralsPatcher, deobfuscator.deobfuscationMethod, stringRegistry, analysisResult)
        return RemoveObfuscateClassPatcher(stringConstantsPatcher, analysisResult.obfuscatedTypes)
    }

    private fun isObfuscated(type: Type.Object): Boolean {
        return type in analysisResult.obfuscatedTypes
    }
}
