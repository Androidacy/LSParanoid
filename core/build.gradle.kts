plugins {
    `java-library`
    kotlin("jvm")
    id("com.vanniktech.maven.publish")
}

dependencies {
    implementation(kotlin("stdlib"))
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(projects.processor)
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<Copy>("copyConsumerRules") {
    from("consumer-rules.pro")
    into("build/resources/main/META-INF/proguard")
    rename { "lsparanoid-core.pro" }
}

tasks.named("processResources") {
    dependsOn("copyConsumerRules")
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
