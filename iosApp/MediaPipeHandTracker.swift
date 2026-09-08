// SignCoach - iOS hand- en gezichtstracking op basis van MediaPipe Tasks Vision.
//
// Gebaseerd op Google's officiële iOS-voorbeeldcode:
// https://github.com/google-ai-edge/mediapipe-samples/tree/main/examples/hand_landmarker/ios
// https://github.com/google-ai-edge/mediapipe-samples/tree/main/examples/face_landmarker/ios
//
// Geschreven zonder Xcode-buildverificatie (deze sandbox heeft geen macOS/Xcode). Zie
// README_BRIDGE.md voor bekende risicopunten, met name de exacte delegate-protocolsignatuur.

import Foundation
import MediaPipeTasksVision
import Shared // Naam van het gegenereerde Kotlin-framework; pas aan als je project een andere
              // framework-naam gebruikt dan de default "ComposeApp" (zie composeApp/build.gradle.kts).

/// Implementeert de Kotlin-interface `IosNativeHandTracker` (uit shared/src/iosMain) bovenop
/// twee losse MediaPipe LIVE_STREAM-detectoren. Dezelfde combinatie-aanpak als de Android-kant
/// (AndroidHandTrackerBridge.kt): het gezichtsresultaat wordt "meegelift" op elke handdetectie.
final class MediaPipeHandTracker: NSObject, IosNativeHandTracker {
    private var handLandmarker: HandLandmarker?
    private var faceLandmarker: FaceLandmarker?
    private var onPose: ((HandPose) -> Void)?
    private var onStatus: ((TrackerStatus) -> Void)?
    private var latestFaceBlendshapes: [FaceBlendshape] = []
    private let processingQueue = DispatchQueue(label: "signcoach.mediapipe.processing")

    private static let handModelName = "hand_landmarker" // hand_landmarker.task, zie IOS_SETUP.md
    private static let faceModelName = "face_landmarker" // face_landmarker.task, zie IOS_SETUP.md
    private static let maxHands = 2

    func start(onPose: @escaping (HandPose) -> Void, onStatus: @escaping (TrackerStatus) -> Void) {
        self.onPose = onPose
        self.onStatus = onStatus
        onStatus(IosBridgeHelpersKt.trackerStatusStarting())

        processingQueue.async { [weak self] in
            guard let self = self else { return }
            do {
                self.handLandmarker = try Self.makeHandLandmarker(delegate: self)
            } catch {
                DispatchQueue.main.async { onStatus(IosBridgeHelpersKt.trackerStatusError()) }
                return
            }
            // Gezicht/mondbeeld is een aanvullend, niet-blokkerend signaal (net als op Android):
            // als het laden faalt, blijft handtracking gewoon werken.
            self.faceLandmarker = try? Self.makeFaceLandmarker(delegate: self)
            DispatchQueue.main.async { onStatus(IosBridgeHelpersKt.trackerStatusRunning()) }
        }
    }

    func stop() {
        processingQueue.async { [weak self] in
            self?.handLandmarker = nil
            self?.faceLandmarker = nil
        }
        latestFaceBlendshapes = []
        onStatus?(IosBridgeHelpersKt.trackerStatusStopped())
        onPose = nil
        onStatus = nil
    }

    /// Wordt per cameraframe aangeroepen door CameraCaptureViewController.
    func process(sampleBuffer: CMSampleBuffer, orientation: UIImage.Orientation, timestampMs: Int) {
        guard let handLandmarker = handLandmarker,
              let image = try? MPImage(sampleBuffer: sampleBuffer, orientation: orientation) else {
            return
        }
        try? handLandmarker.detectAsync(image: image, timestampInMilliseconds: timestampMs)
        try? faceLandmarker?.detectAsync(image: image, timestampInMilliseconds: timestampMs)
    }

    private static func makeHandLandmarker(delegate: HandLandmarkerLiveStreamDelegate) throws -> HandLandmarker {
        guard let modelPath = Bundle.main.path(forResource: handModelName, ofType: "task") else {
            throw NSError(domain: "SignCoach", code: 1, userInfo: [NSLocalizedDescriptionKey: "hand_landmarker.task ontbreekt in de app bundle"])
        }
        let options = HandLandmarkerOptions()
        options.baseOptions.modelAssetPath = modelPath
        options.runningMode = .liveStream
        options.numHands = maxHands
        options.minHandDetectionConfidence = 0.5
        options.minHandPresenceConfidence = 0.5
        options.minTrackingConfidence = 0.5
        options.handLandmarkerLiveStreamDelegate = delegate
        return try HandLandmarker(options: options)
    }

    private static func makeFaceLandmarker(delegate: FaceLandmarkerLiveStreamDelegate) throws -> FaceLandmarker {
        guard let modelPath = Bundle.main.path(forResource: faceModelName, ofType: "task") else {
            throw NSError(domain: "SignCoach", code: 2, userInfo: [NSLocalizedDescriptionKey: "face_landmarker.task ontbreekt in de app bundle"])
        }
        let options = FaceLandmarkerOptions()
        options.baseOptions.modelAssetPath = modelPath
        options.runningMode = .liveStream
        options.numFaces = 1
        options.outputFaceBlendshapes = true
        options.minFaceDetectionConfidence = 0.5
        options.minFacePresenceConfidence = 0.5
        options.minTrackingConfidence = 0.5
        options.faceLandmarkerLiveStreamDelegate = delegate
        return try FaceLandmarker(options: options)
    }

    private func handedness(from categories: [ClassificationResult]?, handIndex: Int) -> Handedness {
        guard let label = categories?[safe: handIndex]?.categories.first?.categoryName else {
            return IosBridgeHelpersKt.handedness(fromLabel: nil)
        }
        return IosBridgeHelpersKt.handedness(fromLabel: label)
    }

    private func toLandmarkPoints(_ landmarks: [NormalizedLandmark]) -> [LandmarkPoint] {
        landmarks.map { LandmarkPoint(x: $0.x, y: $0.y, z: $0.z) }
    }
}

extension MediaPipeHandTracker: HandLandmarkerLiveStreamDelegate {
    // LET OP: exacte selector/parameter-namen gereconstrueerd, zie README_BRIDGE.md punt 1.
    func handLandmarker(
        _ handLandmarker: HandLandmarker,
        didFinishDetection result: HandLandmarkerResult?,
        timestampInMilliseconds: Int,
        error: Error?
    ) {
        guard let result = result, !result.landmarks.isEmpty else { return }
        let primary = result.landmarks[0]
        guard primary.count == 21 else { return }
        let secondaryRaw = result.landmarks[safe: 1]
        let secondary = (secondaryRaw?.count == 21) ? secondaryRaw : nil

        let pose = HandPose(
            landmarks: toLandmarkPoints(primary),
            timestampMs: Int64(timestampInMilliseconds),
            handedness: handedness(from: result.handedness, handIndex: 0),
            secondaryHandLandmarks: secondary.map { toLandmarkPoints($0) },
            secondaryHandedness: secondary != nil ? handedness(from: result.handedness, handIndex: 1) : IosBridgeHelpersKt.handedness(fromLabel: nil),
            faceBlendshapes: latestFaceBlendshapes
        )
        onPose?(pose)
    }
}

extension MediaPipeHandTracker: FaceLandmarkerLiveStreamDelegate {
    // LET OP: exacte selector/parameter-namen gereconstrueerd, zie README_BRIDGE.md punt 1.
    func faceLandmarker(
        _ faceLandmarker: FaceLandmarker,
        didFinishDetection result: FaceLandmarkerResult?,
        timestampInMilliseconds: Int,
        error: Error?
    ) {
        guard let categories = result?.faceBlendshapes.first?.categories else {
            latestFaceBlendshapes = []
            return
        }
        latestFaceBlendshapes = categories.map { FaceBlendshape(name: $0.categoryName ?? "", score: $0.score) }
            .filter { !$0.name.isEmpty }
    }
}

private extension Array {
    subscript(safe index: Int) -> Element? {
        indices.contains(index) ? self[index] : nil
    }
}
