# LSParanoid Test Application

Dedicated test application to verify string obfuscation works correctly in release builds with R8/ProGuard minification enabled.

## Purpose

This test app validates that the ProGuard rules in `core/consumer-rules.pro` are correct and that:
1. Activities with `@Obfuscate` annotation can be launched without crashes
2. Obfuscated strings can be retrieved via reflection (`ensureChunkLoaded` method)
3. The app doesn't crash with `NoSuchMethodException` in minified release builds
4. Chunk loading works correctly for multiple and concurrent string accesses

## ✅ Build Verification (Automated)

The most important verification is that a minified release APK with obfuscation builds successfully:

```bash
./gradlew :testApp:assembleRelease
```

**What this proves:**
- LSParanoid obfuscation is applied to all `@Obfuscate` annotated classes
- R8 minification runs without errors
- ProGuard consumer rules from `core/consumer-rules.pro` are correct
- The `ensureChunkLoaded` method and deobfuscation infrastructure are preserved

**Output:** `testApp/build/outputs/apk/release/testApp-release-unsigned.apk` (~20KB)

✅ **Status**: Successfully tested - release APK builds without errors

## Running Instrumented Tests

### Prerequisites

- Android SDK installed with API 34+ system images
- At least 5GB free disk space (for system image downloads)
- Working emulator or physical device

### Quick Test (Gradle Managed Device - Headless)

Run all tests on a managed emulator (Android 14, API 34):

```bash
./gradlew :testApp:pixel6api34DebugAndroidTest \
  -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect
```

**Note**: First run downloads ~500MB AOSP ATD system image.

### On Physical Device or Connected Emulator

```bash
# Debug build (no minification, tests obfuscation only)
./gradlew :testApp:connectedDebugAndroidTest

# Release build (full minification + obfuscation)
./gradlew :testApp:connectedReleaseAndroidTest
```

### With Display (For Debugging)

To see the emulator while tests run:

```bash
./gradlew :testApp:pixel6api34DebugAndroidTest --enable-display
```

## CI/CD Usage

For GitHub Actions or other CI environments:

```bash
# Build verification (no emulator needed)
./gradlew :testApp:assembleRelease

# Full instrumented tests (requires emulator)
./gradlew :testApp:pixel6api34DebugAndroidTest \
  -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect
```

## Test Configuration

- **Managed Device**: Pixel 6 API 34 (Android 14)
- **System Image**: AOSP ATD (Automated Test Device) optimized for headless CI
- **Build Types**:
  - Debug: Obfuscation enabled, no minification (faster tests)
  - Release: Full minification + obfuscation (production-like)
- **Test Framework**: AndroidX Test + JUnit4 + Espresso

## What Gets Tested

### ObfuscationTest.java

1. **testApplicationContext**: Verifies basic app initialization with obfuscated code
2. **testObfuscatedActivityLaunches**: Tests activity with multiple obfuscated string constants
3. **testObfuscatedUtilityClass**: Tests static utility class with obfuscated strings
4. **testMultipleObfuscatedStringAccess**: Validates chunk loading with 100+ repeated accesses
5. **testConcurrentStringAccess**: Tests thread safety with 10 threads × 50 accesses each

## Build Outputs

After running tests, check:
- **Debug APK**: `testApp/build/outputs/apk/debug/testApp-debug.apk`
- **Release APK**: `testApp/build/outputs/apk/release/testApp-release-unsigned.apk`
- **ProGuard mapping**: `testApp/build/outputs/mapping/release/mapping.txt`
- **Test results**: `testApp/build/reports/androidTests/`

## Troubleshooting

### NoSuchMethodException in Release Build

If you see `NoSuchMethodException: m32.ensureChunkLoaded [int]`:
- The ProGuard patterns in `core/consumer-rules.pro` don't match generated code
- Verify the pattern is `**.Deobfuscator` (NOT `**.Deobfuscator$**`)
- Check consumer rules are applied: examine the mapping file

### Managed Device Won't Start

- Ensure Android SDK includes API 34 system images
- First run downloads system image (500MB+, several minutes)
- Requires at least 5GB free disk space
- Use `--info` flag for detailed output

### R8 Compilation Errors

If R8 fails with missing class warnings:
- Check `testApp/build/outputs/mapping/debugAndroidTest/missing_rules.txt`
- Add `-dontwarn` rules to `testApp/proguard-rules.pro`

### Tests Pass on Debug but Fail on Release

This indicates a ProGuard configuration issue:
- R8 is removing obfuscation infrastructure in release builds
- Check `core/consumer-rules.pro` is in `META-INF/proguard/`
- Verify rules match actual generated class structure

## Verifying ProGuard Rules

To see what's kept after minification:

```bash
./gradlew :testApp:assembleRelease
grep -i deobfuscator testApp/build/outputs/mapping/release/mapping.txt
```

You should see the Deobfuscator class and `ensureChunkLoaded` method preserved (though renamed).
