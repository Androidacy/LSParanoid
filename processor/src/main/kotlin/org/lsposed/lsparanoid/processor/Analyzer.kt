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

import com.joom.grip.Grip
import com.joom.grip.annotatedWith
import com.joom.grip.classes
import com.joom.grip.fields
import com.joom.grip.from
import com.joom.grip.mirrors.ClassMirror
import com.joom.grip.mirrors.FieldMirror
import com.joom.grip.mirrors.Type
import com.joom.grip.mirrors.getObjectType
import com.joom.grip.mirrors.isFinal
import com.joom.grip.mirrors.isStatic
import org.lsposed.lsparanoid.processor.logging.getLogger
import java.nio.file.Path

class Analyzer(
    private val grip: Grip,
    private val classFilter: ((className: String) -> Boolean)?
) {

    private val logger = getLogger()

    fun analyze(paths: List<Path>): AnalysisResult {
        val configuration = buildObfuscationConfiguration(paths)
        val obfuscatedTypes = findObfuscatedTypes(paths)
        return AnalysisResult(configuration, obfuscatedTypes)
    }

    private fun buildObfuscationConfiguration(paths: List<Path>): Map<Type.Object, ClassConfiguration> {
        val query = grip.select(classes)
            .from(paths)
            .where(annotatedWith(Types.OBFUSCATE_TYPE))

        return query.execute().classes.associate { classMirror ->
            logger.info("Class to obfuscate: {}", classMirror.type.className)
            val configuration = buildClassConfiguration(classMirror)
            classMirror.type to configuration
        }
    }

    private fun buildClassConfiguration(classMirror: ClassMirror): ClassConfiguration {
        return ClassConfiguration(findConstantStringFields(classMirror))
    }

    private fun findConstantStringFields(classMirror: ClassMirror): Map<String, String> {
        return classMirror.fields.asSequence()
            .filter { it.isStatic && it.isFinal }
            .filter { it.type == STRING_TYPE }
            .mapNotNull { field ->
                val value = field.value
                if (value is String) {
                    logger.info("    Found constant string: {}.{} = \"{}\"", classMirror.type.className, field.name, value)
                    field.name to value
                } else {
                    null
                }
            }
            .toMap()
    }

    private fun findObfuscatedTypes(paths: List<Path>): Set<Type.Object> {
        val query = grip.select(classes)
            .from(paths)
            .where(classFilter?.let { filter -> { _, classMirror -> filter(classMirror.type.className) } })

        return query.execute().classes.map { it.type }.toSet()
    }

    private fun FieldMirror.toField() = Field(owner.type, name, type)

    private companion object {
        private val STRING_TYPE = getObjectType<String>()
    }
}
