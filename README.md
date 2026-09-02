# KMP Sign Language Coach MVP

Een offline Kotlin Multiplatform-app die handlandmarks lokaal vergelijkt met templates voor **A**, **B**, **L** en **Swipe rechts**.

## Wat werkt

- Shared immutable modellen, reducer, `StateFlow`-presentatielaag en veilige foutstatussen.
- Lokale JSON-templates met schema-validatie.
- Normalisatie rond polslandmark 0 met landmark 9 als schaal.
- Deterministische statische en dynamische scoring.
- Android CameraX-preview en runtime-camerapermissie.
- Android MediaPipe Hand Landmarker in `LIVE_STREAM`, maximaal één hand en `KEEP_ONLY_LATEST` backpressure.
- Geen netwerk tijdens gebruik en geen opslag van camerabeelden.
- Expliciete `UNSUPPORTED`-status op iOS; er worden geen nepresultaten geproduceerd.

## Vereisten

- JDK 17
- Android Studio met Android SDK 35
- Een fysiek Android-apparaat of emulator met camera, Android 8.0 (API 26) of hoger
- Voor iOS-frameworkbuilds: macOS met Xcode 16.3 of een compatibele toolchain

## Bouwen en testen

Op Windows:

```powershell
.\gradlew.bat :shared:allTests
.\gradlew.bat :composeApp:assembleDebug
```

Op macOS/Linux:

```bash
./gradlew :shared:allTests
./gradlew :composeApp:assembleDebug
```

Open de projectmap in Android Studio, laat Gradle synchroniseren en start de configuratie `composeApp` op een apparaat. Kies een gebaar en tik op **Start oefenen** om cameratoegang te vragen.

## Lokale assets

- Templates: `composeApp/src/commonMain/composeResources/files/templates.json`
- MediaPipe-model: `shared/src/androidMain/assets/hand_landmarker.task`
- Modelbron: Google MediaPipe Hand Landmarker full, float16 versie 1
- Model SHA-256: `fbc2a30080c3c557093b5ddfc334698132eb341044ccee322ccf8bcf3607cde1`

De templatecoördinaten zijn MVP-startwaarden. Voor betrouwbare gebarenfeedback moeten ze later met representatieve voorbeeldposes worden gekalibreerd en op verschillende handen, huidtinten, camera's en lichtomstandigheden worden getest.

## iOS-beperking

De shared iOS-adapter retourneert bewust `UNSUPPORTED`, omdat deze MVP geen stabiel gekoppelde MediaPipe iOS-artifact bevat. Het Gradle-framework en de Compose-entrypoint zijn aanwezig; de Xcode-shell en AVFoundation-preview zijn vervolgwerk op macOS.
