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

import com.joom.grip.Grip
import com.joom.grip.GripFactory
import com.joom.grip.io.IoFactory
import com.joom.grip.mirrors.getObjectTypeByInternalName
import com.androidacy.lsparanoid.processor.commons.closeQuietly
import com.androidacy.lsparanoid.processor.commons.createFile
import com.androidacy.lsparanoid.processor.logging.getLogger
import com.androidacy.lsparanoid.processor.model.Deobfuscator
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.commons.Method
import java.nio.file.Path
import java.util.jar.JarOutputStream

class ParanoidProcessor(
    private val seed: Int,
    classpath: Set<Path>,
    private val inputs: List<Path>,
    private val output: JarOutputStream,
    private val asmApi: Int = Opcodes.ASM9,
    private val projectName: String,
    private val classFilter: ((className: String) -> Boolean)?
) {

    private val logger = getLogger()

    private val sortedInputs = inputs.distinct().sorted()
    private val grip: Grip = GripFactory.newInstance(asmApi).create(classpath + sortedInputs)

    fun process() {
        dumpConfiguration()
        StringRegistryImpl(seed).use { stringRegistry ->
            val analysisResult = Analyzer(grip, classFilter).analyze(sortedInputs)
            analysisResult.dump()

            val deobfuscator = createDeobfuscator()
            logger.info("Prepare to generate {}", deobfuscator)

            val sources = sortedInputs.asSequence().map { input ->
                IoFactory.createFileSource(input)
            }

            try {
                Patcher(
                    deobfuscator,
                    stringRegistry,
                    analysisResult,
                    grip.classRegistry,
                    grip.fileRegistry,
                    asmApi
                ).copyAndPatchClasses(sources, output)
                val resourceName = composeResourceName()
                val deobfuscatorBytes =
                    DeobfuscatorGenerator(
                        deobfuscator,
                        stringRegistry,
                        grip.classRegistry,
                        grip.fileRegistry,
                        resourceName
                    ).generateDeobfuscator()
                output.createFile("${deobfuscator.type.internalName}.class", deobfuscatorBytes)
                output.putNextEntry(java.util.jar.JarEntry(resourceName))
                stringRegistry.copyDataTo(output)
                output.closeEntry()
            } finally {
                sources.forEach { source ->
                    source.closeQuietly()
                }
            }
        }
    }

    private fun dumpConfiguration() {
        logger.info("Starting ParanoidProcessor:")
        logger.info("  inputs        = {}", inputs)
        logger.info("  output        = {}", output)
    }

    private fun AnalysisResult.dump() {
        if (configurationsByType.isEmpty()) {
            logger.info("No classes to obfuscate")
        } else {
            logger.info("Classes to obfuscate:")
            configurationsByType.forEach {
                val (type, configuration) = it
                logger.info("  {}:", type.internalName)
                configuration.constantStringsByFieldName.forEach { (field, string) ->
                    logger.info("    {} = \"{}\"", field, string)
                }
            }
        }
    }

    private fun createDeobfuscator(): Deobfuscator {
        val deobfuscatorInternalName =
            "com/androidacy/lsparanoid/Deobfuscator${composeDeobfuscatorNameSuffix()}"
        val deobfuscatorType = getObjectTypeByInternalName(deobfuscatorInternalName)
        val deobfuscationMethod =
            Method("getString", Type.getType(String::class.java), arrayOf(Type.LONG_TYPE))
        return Deobfuscator(deobfuscatorType, deobfuscationMethod)
    }

    private fun composeDeobfuscatorNameSuffix(): String {
        val normalizedProjectName =
            projectName.filter { it.isLetterOrDigit() || it == '_' || it == '$' }
        return if (normalizedProjectName.isEmpty() || normalizedProjectName.startsWith('$')) {
            normalizedProjectName
        } else {
            "$$normalizedProjectName"
        }
    }

    private fun composeResourceName(): String {
        val hash = (projectName.hashCode().toLong() and 0xffffffffL xor seed.toLong()).toString(16)
        return "assets/$hash.bin"
    }
}
