# Nova / Luna Build and Proof Runbook

This document describes the exact steps to reproduce the green build and unit test suite for the Nova/Luna project.

## Prerequisites

- Android SDK (installed at `C:\Users\cricv\AppData\Local\Android\Sdk` or equivalent)
- Flutter SDK (stable channel)
- Java 17+ (configured for Gradle)

## Environment Setup

1.  **Android SDK Pointer**: Ensure `local.properties` exists in the repository root with the correct `sdk.dir` path.
    ```properties
    sdk.dir=C:\\Users\\cricv\\AppData\\Local\\Android\\Sdk
    ```
    *Note: Use double backslashes for Windows paths.*

2.  **Gradle Configuration**: `gradle.properties` should contain necessary JVM arguments and AndroidX settings.

## Build and Test Commands

### 1. Android Unit Tests
Run the following command from the repository root to execute the complete Android unit test suite.
```powershell
./gradlew :app:testDebugUnitTest --no-daemon
```
- **Success Criteria**: `BUILD SUCCESSFUL` with all tests passing.
- **Reproducibility**: Run twice on a clean state to ensure no cache locks or flakiness.

### 2. Flutter Analysis
Run from the `flutter_app` directory to verify code quality and linting.
```powershell
cd flutter_app
flutter clean
flutter analyze
```
- **Success Criteria**: `No issues found!`.

### 3. Flutter Unit Tests
Run from the `flutter_app` directory to execute Flutter-specific tests.
```powershell
cd flutter_app
flutter test
```
- **Success Criteria**: `All tests passed!`.

## Troubleshooting

### Cache Locks
If you encounter "Unable to delete file" or "cache lock" errors:
1.  Stop any running Gradle daemons: `./gradlew --stop`.
2.  Run commands with `--no-daemon`.
3.  Ensure no other IDEs (Android Studio, VS Code) are holding locks on the `build/` or `.dart_tool/` directories.
4.  Run `flutter clean` in the `flutter_app` directory.

### SDK Not Found
If Gradle fails with "SDK location not found", verify `local.properties` has the correct `sdk.dir` and that the `ANDROID_HOME` environment variable is either unset or points to the same location.

## Verification Ledger (Phase 32)

| Command | Result | Notes |
| --- | --- | --- |
| `./gradlew :app:testDebugUnitTest` | PASS | Reproducible twice (clean state) |
| `flutter analyze` | PASS | No issues found |
| `flutter test` | PASS | 4 tests passed |
