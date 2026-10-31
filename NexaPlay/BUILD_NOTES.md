# Build / validation notes

- Android Gradle Plugin: 8.13.2
- Gradle: 8.13
- Kotlin Android plugin: 2.2.21
- Chaquopy: 17.2.0, Python 3.11
- AndroidX Media3: 1.11.1
- `compileSdk`: 36
- `targetSdk`: 35
- `minSdk`: 24
- ABI: arm64-v8a

The workflow is deliberately manual (`workflow_dispatch`) to avoid triggering an APK build on every push.
