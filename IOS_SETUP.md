# iOS opzetten (stappenplan, vereist een Mac met Xcode)

Deze sandbox heeft geen macOS/Xcode, dus alle iOS-code in dit project (`shared/src/iosMain` en
`iosApp/*.swift`) is geschreven maar **niet gecompileerd of getest**. Dit document is het
stappenplan om het op een Mac werkend te krijgen, inclusief de plekken waar het waarschijnlijk
nog niet in één keer goed staat.

## 0. Vereisten

- macOS met Xcode 16.3+ (zoals README.md al aangaf)
- CocoaPods (`sudo gem install cocoapods` als je dat nog niet hebt)
- Een fysieke iPhone met camera wordt aangeraden voor tests (de simulator heeft geen camera)

## 1. Het Xcode-project aanmaken

Er staat nog geen `.xcodeproj`/`.xcworkspace` in `iosApp/` - alleen `Info.plist` en nu de nieuwe
Swift-bestanden. Maak in Xcode een nieuw "App"-project aan in de map `iosApp/` (target-naam
`iosApp`, taal Swift, interface SwiftUI, gebruik het bestaande `Info.plist` in plaats van een
nieuwe te genereren), en voeg de bestaande Swift-bestanden uit `iosApp/` toe aan het target:

- `MediaPipeHandTracker.swift`
- `CameraCaptureViewController.swift`
- `SignCoachBootstrap.swift`

Voeg in `iOSApp.swift` (of het bestand met `@main`) `SignCoachBootstrap.register()` toe in
`init()`, vóórdat de Compose UI verschijnt - zie de instructies bovenaan `SignCoachBootstrap.swift`.

Zet in `Info.plist` een `NSCameraUsageDescription` (camera-toestemmingstekst), anders crasht de
app bij de eerste cameratoegang-aanvraag.

## 2. Het Kotlin-framework koppelen

`composeApp/build.gradle.kts` genereert een `ComposeApp.framework` (zie het `binaries.framework`-
blok, nu met `export(project(":shared"))` zodat `HandPose`, `IosPlatformHooks`, etc. zichtbaar zijn
voor Swift). Volg de standaard KMP-Xcode-integratie: een "Run Script"-buildfase in het `iosApp`-
target die `./gradlew :composeApp:embedAndSignAppleFrameworkForXcode` aanroept (de KMP-wizard
genereert dit normaal automatisch; omdat dit project niet met de wizard is opgezet, moet je dit
handmatig toevoegen). Zie de Kotlin Multiplatform-documentatie over "Connect to platform-specific
APIs voor iOS" voor de exacte scriptregel.

## 3. CocoaPods

Er staat al een `iosApp/Podfile` (met `pod 'MediaPipeTasksVision', '~> 0.10.29'`). Vanaf de map
`iosApp/`:

```bash
pod install
```

Open daarna **`iosApp.xcworkspace`**, niet `.xcodeproj` (CocoaPods vereist het workspace-bestand).

## 4. Modelbestanden toevoegen

Net als bij Android (zie README.md "Lokale assets") moeten de MediaPipe-modelbestanden in de
Xcode-app-bundle terechtkomen (sleep ze in Xcode in het `iosApp`-target, "Copy items if needed"
aangevinkt):

- `hand_landmarker.task` - al aanwezig op Android in `shared/src/androidMain/assets/`, kopieer
  hetzelfde bestand.
- `face_landmarker.task` - **nog niet in dit project aanwezig**, ook niet op Android. Download
  hem van de officiële MediaPipe-modelopslag (vermoedelijke URL, niet live geverifieerd vanuit
  deze sandbox - dubbelcheck tegen
  https://ai.google.dev/edge/mediapipe/solutions/vision/face_landmarker):
  `https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/latest/face_landmarker.task`
  Zet 'm op Android in `shared/src/androidMain/assets/face_landmarker.task` en op iOS in de
  Xcode-bundle (zie hierboven).

## 5. Bouwen en de eerste compile-fouten

Bouw het `iosApp`-target. Gegeven dat dit blind geschreven is, zijn dit de meest waarschijnlijke
eerste probleempunten (zie ook `iosApp/README_BRIDGE.md`):

1. **Delegate-protocolsignatuur klopt niet exact** ("does not conform to protocol
   'HandLandmarkerLiveStreamDelegate'"): open het gegenereerde
   `MediaPipeTasksVision-Swift.h` (Xcode: cmd+klik op `HandLandmarkerLiveStreamDelegate`) en
   pas de methode-signatuur in `MediaPipeHandTracker.swift` aan.
2. **`import Shared` klopt niet**: als je framework een andere naam heeft dan "ComposeApp" (zie
   `baseName` in `composeApp/build.gradle.kts`), vervang `import Shared` door `import ComposeApp`
   (of hoe je het framework ook genoemd hebt) in alle drie de Swift-bestanden.
3. **`UIKitViewController` in `IosCameraPreview.kt`**: als de Compose Multiplatform-versie een
   andere package/parameternaam gebruikt, pas dat bestand aan (zie het commentaar erin).
4. **CocoaPods + statische Kotlin-framework-linking**: als je linkfouten krijgt tussen het
   Kotlin-framework (`isStatic = true`) en de CocoaPods-geïnstalleerde `MediaPipeTasksVision`,
   probeer `isStatic = false` in `composeApp/build.gradle.kts`, of raadpleeg de Kotlin
   Multiplatform-docs over het combineren van CocoaPods-afhankelijkheden met een los
   Xcode-project (niet via de Kotlin cocoapods Gradle-plugin, die is in deze aanpak bewust niet
   gebruikt - zie PLAN.md sectie "waarom geen directe cinterop").

## 6. Terugkoppelen

Meld compile- of runtime-fouten terug (foutmelding + bestand/regel); dat is precies waarom voor
deze aanpak gekozen is - de Swift-code is gebaseerd op Google's eigen voorbeeld-app, dus fouten
zijn waarschijnlijk kleine naamgevings-mismatches die snel te fixen zijn zodra er een echte
compiler-foutmelding is om op te reageren, in plaats van blind opnieuw te gokken.
