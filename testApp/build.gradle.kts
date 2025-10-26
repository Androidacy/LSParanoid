plugins {
    id("com.android.application") version "8.13.0"
    id("com.androidacy.lsparanoid") version "0.10.2"
}

lsparanoid {
    // Enable obfuscation for both debug and release to allow testing
    variantFilter = { variant -> true }
}

android {
    namespace = "com.androidacy.lsparanoid.testapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.androidacy.lsparanoid.testapp"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // No minification for debug - tests run faster
            isMinifyEnabled = false
        }
        release {
            // Enable minification for release to test ProGuard rules
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        managedDevices {
            localDevices {
                create("pixel6api34") {
                    device = "Pixel 6"
                    apiLevel = 34
                    systemImageSource = "aosp-atd"
                    require64Bit = true
                }
            }
        }
    }
}

dependencies {
    implementation("com.androidacy.lsparanoid:core:0.10.2")

    // Kotlin stdlib needed for annotations used by lsparanoid
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib:2.2.20")

    // Testing dependencies
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation("androidx.test:core:1.5.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
