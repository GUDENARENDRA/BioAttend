# Fix Plugin Resolution Error (AGP 9.3.0 Not Found)

The project is failing to sync because the Android Gradle Plugin (AGP) and related plugins cannot be resolved. This is due to two main reasons:
1. **Missing Repositories**: The `app/settings.gradle.kts` file is effectively empty, causing Gradle to only search the default "Gradle Central Plugin Repository" instead of `google()` where AGP is hosted.
2. **Version Mismatch/Availability**: While `9.3.0` might be intended, `9.4.0` is the currently reported stable version.

## Proposed Changes

### Build Configuration

#### [MODIFY] [settings.gradle.kts](file:///C:/Users/lavan/.gemini/antigravity/scratch/BioAttend/app/settings.gradle.kts)
Restore the `pluginManagement` and `dependencyResolutionManagement` blocks to include `google()` and `mavenCentral()`. This ensures Gradle can find the Android and Kotlin plugins.

#### [MODIFY] [build.gradle.kts](file:///C:/Users/lavan/.gemini/antigravity/scratch/BioAttend/app/build.gradle.kts)
Update plugin versions to `9.4.0` (stable) and ensure they are applied correctly.

#### [MODIFY] [build.gradle.kts](file:///C:/Users/lavan/.gemini/antigravity/scratch/BioAttend/build.gradle.kts)
Sync the versions in the root build file to `9.4.0`.

## Verification Plan

### Automated Tests
- Run Gradle Sync in Android Studio.
- Execute `./gradlew help` to verify plugin resolution.
