import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.jvm.tasks.Jar
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*

plugins {
    idea
    alias(libs.plugins.kotlin)
    `java-gradle-plugin`
    id("com.vanniktech.maven.publish")
}

group = "com.androidacy.lsparanoid"
version = rootProject.version

gradlePlugin {
    plugins {
        create("lsparanoid") {
            id = "com.androidacy.lsparanoid"
            implementationClass = "com.androidacy.lsparanoid.plugin.LSParanoidPlugin"
            displayName = "LSParanoid"
            description = "String obfuscator for Android applications"
        }
    }
}

dependencies {
    implementation(projects.core)
    implementation(projects.processor)
    implementation(libs.kotlin.api)
    implementation(libs.agp.api)
}

abstract class GenerateBuildClass : DefaultTask() {
    @get:Input
    abstract val version: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val buildClassFile = outputDir.file("main/java/com/androidacy/lsparanoid/plugin/Build.java").get().asFile
        buildClassFile.parentFile.mkdirs()
        buildClassFile.writeText(
            """
            package com.androidacy.lsparanoid.plugin;
            /**
             * The type Build.
             */
            public class Build {
               /**
                * The constant VERSION.
                */
               public static final String VERSION = "${version.get()}";
            }""".trimIndent()
        )
    }
}

val generatedDir = File(projectDir, "generated")
val generatedJavaSourcesDir = File(generatedDir, "main/java")

val genTask = tasks.register<GenerateBuildClass>("generateBuildClass") {
    version.set(project.version.toString())
    outputDir.set(generatedDir)
}

sourceSets {
    main {
        java {
            srcDir(generatedJavaSourcesDir)
        }
    }
}

tasks.withType(KotlinCompile::class.java) {
    dependsOn(genTask)
}

tasks.withType(Jar::class.java) {
    dependsOn(genTask)
}

idea {
    module {
        generatedSourceDirs.add(generatedJavaSourcesDir)
    }
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    if (project.hasProperty("signingInMemoryKey")) {
        signAllPublications()
    }

    coordinates("com.androidacy.lsparanoid", project.name, version.toString())

    pom {
        name = "LSParanoid - ${project.name}"
        description = "String obfuscator for Android applications"
        url = "https://github.com/Androidacy/LSParanoid"

        licenses {
            license {
                name = "Apache License 2.0"
                url = "https://github.com/Androidacy/LSParanoid/blob/master/LICENSE.txt"
            }
        }

        developers {
            developer {
                name = "Androidacy"
                url = "https://github.com/Androidacy"
            }
        }

        scm {
            connection = "scm:git:https://github.com/Androidacy/LSParanoid.git"
            url = "https://github.com/Androidacy/LSParanoid"
        }
    }
}
