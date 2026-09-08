// SignCoach - AVFoundation cameraopname + preview voor iOS.
// Geschreven zonder Xcode-buildverificatie, zie README_BRIDGE.md.

import AVFoundation
import UIKit

/// Beheert een AVCaptureSession met de front camera, toont een live preview, en geeft elk frame
/// door aan de meegegeven MediaPipeHandTracker. Wordt vanuit Kotlin (IosCameraPreview.kt) getoond
/// via IosPlatformHooks.previewControllerFactory.
final class CameraCaptureViewController: UIViewController, AVCaptureVideoDataOutputSampleBufferDelegate {
    private let tracker: MediaPipeHandTracker
    private let session = AVCaptureSession()
    private let outputQueue = DispatchQueue(label: "signcoach.camera.output")
    private var previewLayer: AVCaptureVideoPreviewLayer?
    private var isFrontFacing = true

    init(tracker: MediaPipeHandTracker) {
        self.tracker = tracker
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("init(coder:) is not supported") }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
        configureSession()
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        previewLayer?.frame = view.bounds
    }

    private func configureSession() {
        session.beginConfiguration()
        session.sessionPreset = .high

        guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: isFrontFacing ? .front : .back),
              let input = try? AVCaptureDeviceInput(device: device),
              session.canAddInput(input) else {
            session.commitConfiguration()
            return
        }
        session.addInput(input)

        let output = AVCaptureVideoDataOutput()
        output.videoSettings = [kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA]
        output.alwaysDiscardsLateVideoFrames = true
        output.setSampleBufferDelegate(self, queue: outputQueue)
        guard session.canAddOutput(output) else {
            session.commitConfiguration()
            return
        }
        session.addOutput(output)
        session.commitConfiguration()

        let layer = AVCaptureVideoPreviewLayer(session: session)
        layer.videoGravity = .resizeAspectFill
        view.layer.addSublayer(layer)
        previewLayer = layer

        DispatchQueue.global(qos: .userInitiated).async { [session] in
            session.startRunning()
        }
    }

    func captureOutput(
        _ output: AVCaptureOutput,
        didOutput sampleBuffer: CMSampleBuffer,
        from connection: AVCaptureConnection
    ) {
        // Front camera -> .upMirrored, zodat MediaPipe's landmarks al in de "natuurlijke" (voor de
        // gebruiker gespiegelde) weergave staan - vergelijkbaar met de bitmap-flip in de
        // Android-implementatie (AndroidHandTrackerBridge.kt / AndroidCameraPlatform.kt).
        let orientation: UIImage.Orientation = isFrontFacing ? .upMirrored : .up
        let timestampMs = Int(CMTimeGetSeconds(CMSampleBufferGetPresentationTimeStamp(sampleBuffer)) * 1000)
        tracker.process(sampleBuffer: sampleBuffer, orientation: orientation, timestampMs: timestampMs)
    }

    deinit {
        session.stopRunning()
    }
}
