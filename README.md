# KMP Sign Language Coach MVP

Een offline Kotlin Multiplatform-app die handlandmarks lokaal vergelijkt met templates voor **A**, **B**, **L** en **Swipe rechts**.

## Wat werkt

- Shared immutable modellen, reducer, `StateFlow`-presentatielaag en veilige foutstatussen.
- Lokale JSON-templates met schema-validatie.
- Normalisatie rond polslandmark 0 met landmark 9 als schaal.
- Deterministische statische en dynamische scoring.
- Android CameraX-preview en runtime-camerapermissie.
- Android MediaPipe Hand Landmarker in `LIVE_STREAM`, tot 2 handen (`setNumHands(2)`) en `KEEP_ONLY_LATEST` backpressure.
- Android MediaPipe Face Landmarker in `LIVE_STREAM` met blendshapes aan, voor non-manual/mondbeeld-feedback (zie PLAN.md sectie 2 voor de onderbouwing).
- Geen netwerk tijdens gebruik en geen opslag van camerabeelden.
- **iOS: code geschreven, nog niet gebouwd/getest.** De `UNSUPPORTED`-stub is vervangen door een echte MediaPipe-koppeling (Hand + Face Landmarker), maar deze sandbox had geen Xcode/macOS om het te compileren. Zie [IOS_SETUP.md](./IOS_SETUP.md) voor het stappenplan en bekende risicopunten. Tot die stappen doorlopen zijn, valt de app op iOS terug op `UNSUPPORTED` (geen nepresultaten).

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
- Hand-model: `shared/src/androidMain/assets/hand_landmarker.task`
- Modelbron: Google MediaPipe Hand Landmarker full, float16 versie 1
- Model SHA-256: `fbc2a30080c3c557093b5ddfc334698132eb341044ccee322ccf8bcf3607cde1`
- Gezichtsmodel: `face_landmarker.task` - **nog niet in dit project aanwezig**, moet je zelf downloaden en toevoegen (zie [IOS_SETUP.md](./IOS_SETUP.md) stap 4 voor de vermoedelijke downloadlocatie; hoort ook op Android in `shared/src/androidMain/assets/` te staan).

De templatecoördinaten zijn MVP-startwaarden. Voor betrouwbare gebarenfeedback moeten ze later met representatieve voorbeeldposes worden gekalibreerd en op verschillende handen, huidtinten, camera's en lichtomstandigheden worden getest. Zie PLAN.md voor de aanpak (o.a. de NGT-dataset in Global Signbank als bron voor trainingsdata).

## iOS

Zie [IOS_SETUP.md](./IOS_SETUP.md) voor het volledige stappenplan (Xcode-project aanmaken, CocoaPods, modelbestanden, bekende risicopunten). De Kotlin- en Swift-code is geschreven maar niet gecompileerd/getest, omdat hiervoor geen Xcode/macOS-omgeving beschikbaar was tijdens het schrijven ervan.
