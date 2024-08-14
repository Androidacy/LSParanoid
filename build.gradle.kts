import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinBasePlugin


plugins {
    alias(libs.plugins.lsplugin.publish)
    alias(libs.plugins.kotlin) apply false
}


allprojects {
    group = "com.github.Androidacy.LSParanoid"
    version = "0.6.10"

    plugins.withType(JavaPlugin::class.java) {
        extensions.configure(JavaPluginExtension::class.java) {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
    }

    plugins.withType(KotlinBasePlugin::class.java) {
        extensions.configure(KotlinJvmProjectExtension::class.java) {
            jvmToolchain(17)
        }
    }
}
