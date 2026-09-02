# KMP Sign Language Coach MVP

Offline Kotlin Multiplatform basis for Android and iOS. The shared layer contains immutable models, JSON validation, landmark normalization, deterministic static/dynamic scoring, and StateFlow MVI flow. Camera/MediaPipe adapters are intentionally platform-specific; iOS must report `UNSUPPORTED` when the native artifact is unavailable.

Build with `./gradlew :shared:allTests` and `./gradlew :composeApp:assembleDebug`.
