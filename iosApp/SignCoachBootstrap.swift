// SignCoach - registreert de native iOS-implementatie bij Kotlin (IosPlatformHooks).
// Geschreven zonder Xcode-buildverificatie, zie README_BRIDGE.md.
//
// AANROEPEN: roep `SignCoachBootstrap.register()` precies één keer aan bij het opstarten van de
// app, vóórdat de Compose UI verschijnt. Voor een Compose Multiplatform-project dat met de
// KMP-wizard is opgezet, is dat typisch in `iOSApp.swift`:
//
//   import SwiftUI
//
//   @main
//   struct IOSApp: App {
//       init() { SignCoachBootstrap.register() }
//       var body: some Scene {
//           WindowGroup { ComposeView() }
//       }
//   }
//
// Dat bestand bestaat nog niet in dit project (er is nog geen Xcode-project, zie IOS_SETUP.md) -
// voeg de `init()`-regel hierboven toe zodra je het aanmaakt.

import AVFoundation
import Shared
import UIKit

/// Zorgt dat nativeTrackerFactory (gebruikt door CoachViewModel om poses te lezen) en
/// previewControllerFactory (gebruikt door de Compose CameraPreview) dezelfde MediaPipeHandTracker-
/// instantie delen. Zonder dit zouden er twee onafhankelijke camera-/trackersessies ontstaan: de
/// preview zou iets tonen, maar de tracker waar CoachViewModel naar luistert zou nooit frames
/// krijgen (en dus nooit poses emitten) - een stille, functionele bug, geen compile-fout.
final class SignCoachTrackerHolder {
    static let shared = SignCoachTrackerHolder()
    private var current: MediaPipeHandTracker?

    func makeOrReuse() -> MediaPipeHandTracker {
        if let existing = current { return existing }
        let created = MediaPipeHandTracker()
        current = created
        return created
    }
}

enum SignCoachBootstrap {
    static func register() {
        IosPlatformHooks.shared.permissionRequester = { onResult in
            switch AVCaptureDevice.authorizationStatus(for: .video) {
            case .authorized:
                onResult(true)
            case .notDetermined:
                AVCaptureDevice.requestAccess(for: .video) { granted in
                    DispatchQueue.main.async { onResult(granted) }
                }
            default:
                onResult(false)
            }
        }

        IosPlatformHooks.shared.nativeTrackerFactory = { SignCoachTrackerHolder.shared.makeOrReuse() }
        IosPlatformHooks.shared.previewControllerFactory = {
            CameraCaptureViewController(tracker: SignCoachTrackerHolder.shared.makeOrReuse())
        }
    }
}
