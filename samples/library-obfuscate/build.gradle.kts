plugins {
    id("com.android.library")
    id("com.github.Androidacy.LSParanoid")
}

lsparanoid {
    classFilter = { true }
}

android {
    namespace = "org.lsposed.paranoid.samples.library_obfuscate"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
