# iOS-brug: SignCoach + MediaPipe Tasks Vision

Dit mapje bevat de Swift-kant van de hand-/gezichtstracking. De Kotlin-kant (in
`shared/src/iosMain`) roept deze code NIET rechtstreeks aan - dat kan niet, de afhankelijkheid
loopt andersom (iosApp linkt tegen `ComposeApp.framework`, gegenereerd uit `shared` + `composeApp`).
In plaats daarvan registreert deze Swift-code zichzelf bij het opstarten van de app via
`IosPlatformHooks` (een Kotlin `object`, zichtbaar vanuit Swift als `IosPlatformHooks.shared`,
zolang `shared` correct geëxporteerd is - zie `composeApp/build.gradle.kts`).

## Bestanden

- `MediaPipeHandTracker.swift` - implementeert `IosNativeHandTracker` (Kotlin-interface) bovenop
  `HandLandmarker` + `FaceLandmarker` uit `MediaPipeTasksVision`, in LIVE_STREAM-modus. Gebaseerd
  op Google's officiële iOS-voorbeeldcode (mediapipe-samples repo, hand_landmarker/ios en
  face_landmarker/ios).
- `CameraCaptureViewController.swift` - `UIViewController` met een `AVCaptureSession` +
  `AVCaptureVideoPreviewLayer`, die elk camera-frame doorgeeft aan `MediaPipeHandTracker`.
- `SignCoachBootstrap.swift` - registreert bovenstaande bij `IosPlatformHooks`. Moet één keer
  aangeroepen worden bij app-start (zie onderaan dat bestand voor waar).

## Bekende risicopunten (kon niet gecompileerd worden - geen Xcode/macOS in deze omgeving)

1. Het exacte `HandLandmarkerLiveStreamDelegate`/`FaceLandmarkerLiveStreamDelegate`
   protocolmethode-signatuur is gereconstrueerd op basis van Google's Swift-voorbeeldcode, niet
   1-op-1 uit de headerbron gehaald. Als Xcode klaagt over "does not conform to protocol", check
   de exacte signatuur in het gegenereerde `MediaPipeTasksVision-Swift.h`.
2. De Kotlin-enum-waarden (`Handedness`, `TrackerStatus`) worden bewust NIET rechtstreeks vanuit
   Swift aangeroepen (bv. `Handedness.left`), omdat de exacte Swift-naamgeving van Kotlin/Native
   enum-cases kan verschillen. In plaats daarvan gebruikt deze code de helperfuncties in
   `IosBridgeHelpersKt` (zie `shared/src/iosMain/kotlin/.../IosBridgeHelpers.kt`).
3. `shared`/`composeApp` moeten eerst gebouwd zijn (`./gradlew :composeApp:embedAndSignAppleFrameworkForXcode`
   of via de Xcode build-fase die de KMP-wizard normaal genereert) voordat Xcode deze Swift-bestanden
   kan compileren - anders bestaan `HandPose`, `IosPlatformHooks`, etc. nog niet als Swift-symbolen.
4. Er bestaat op dit moment nog geen `.xcodeproj`/`.xcworkspace` in `iosApp/` in deze repo (alleen
   `Info.plist`). Zie IOS_SETUP.md in de root voor het aanmaken daarvan.
