plugins {
    id("com.android.application")
    id("com.github.Androidacy.LSParanoid")
}

lsparanoid {
    classFilter = { true }
    includeDependencies = true
    variantFilter = { variant -> variant.name == "release" }
}

android {
    namespace = "org.lsposed.paranoid.samples.application_global_obfuscate"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    defaultConfig {
        applicationId = "org.lsposed.paranoid.samples.application_global_obfuscate"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    compileOnly("androidx.annotation:annotation:1.5.0")
    implementation(project(":library"))
    implementation(project(":library-obfuscate"))
    implementation(project(":library-may-obfuscate"))
}
