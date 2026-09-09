# Fix Warnings and Errors

This plan addresses the Gradle sync warnings and the code lint warnings found in `MainActivity.kt` and `EnrollmentManagerActivity.kt`.

## User Review Required

> [!NOTE]
> I am upgrading the Gradle version to 8.10.2 to resolve the "New Minor Gradle Version Available" warning. This might trigger a project re-sync.

## Proposed Changes

### Gradle Configuration

#### [MODIFY] [gradle-wrapper.properties](file:///C:/Users/lavan/.gemini/antigravity/scratch/BioAttend/gradle/wrapper/gradle-wrapper.properties)
- Upgrade `distributionUrl` to Gradle 8.10.2.

### UI Components

#### [MODIFY] [EnrollmentManagerActivity.kt](file:///C:/Users/lavan/.gemini/antigravity/scratch/BioAttend/app/src/main/java/com/mpc/bioattend/ui/EnrollmentManagerActivity.kt)
- Remove unused `response` variable at line 165.
- Fix string concatenation in `setText` by using string templates or resources.
- Replace `Color.parseColor` with `toColorInt()` extension.
- Move lambda argument out of parentheses.
- Add clarifying parentheses to complex boolean expressions.

#### [MODIFY] [MainActivity.kt](file:///C:/Users/lavan/.gemini/antigravity/scratch/BioAttend/app/src/main/java/com/mpc/bioattend/ui/MainActivity.kt)
- Remove unused import `android.widget.Toast`.
- Fix hardcoded string concatenation in `tvCurrentDay.text`.
- Replace `Color.parseColor` with `toColorInt()`.
- Remove unused exception parameter `e`.
- Add clarifying parentheses to boolean expressions.

## Verification Plan

### Automated Tests
- Run `./gradlew lint` to verify that the lint warnings are resolved.
- Run `./gradlew assembleDebug` to ensure the build still passes with the new Gradle version.

### Manual Verification
- Perform a Gradle Sync in Android Studio and verify the "New Minor Gradle Version Available" warning is gone.
- Inspect the code in `MainActivity.kt` and `EnrollmentManagerActivity.kt` to ensure no yellow highlights remain for the addressed issues.
