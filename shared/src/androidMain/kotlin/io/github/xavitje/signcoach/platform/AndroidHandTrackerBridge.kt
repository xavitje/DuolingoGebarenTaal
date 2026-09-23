package io.github.xavitje.signcoach.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import io.github.xavitje.signcoach.data.model.FaceBlendshape
import io.github.xavitje.signcoach.data.model.HandPose
import io.github.xavitje.signcoach.data.model.Handedness
import io.github.xavitje.signcoach.data.model.LandmarkPoint
import io.github.xavitje.signcoach.data.model.TrackerStatus
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.components.containers.Category
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * Combineert twee losse MediaPipe LIVE_STREAM-detectoren (Hand Landmarker + Face Landmarker) tot
 * één [HandPose]-stream met tot 2 handen en mondgerichte non-manual signalen (blendshapes).
 *
 * De twee detectoren draaien onafhankelijk en async op hetzelfde camera-frame; de laatst bekende
 * gezichts-blendshapes worden "meegelift" op elke hand-detectie (ze komen typisch binnen een paar
 * ms van elkaar bij dezelfde framerate, dus dat is voor coaching-doeleinden nauwkeurig genoeg -
 * dit is bewust geen frame-exacte synchronisatie).
 */
class AndroidHandTrackerBridge internal constructor(
    context: Context,
) : HandTrackerBridge {
    private val applicationContext = context.applicationContext
    private val mutableStatus = MutableStateFlow(TrackerStatus.STOPPED)
    private val poses = MutableSharedFlow<HandPose>(extraBufferCapacity = 1)
    private var handLandmarker: HandLandmarker? = null
    private var faceLandmarker: FaceLandmarker? = null
    @Volatile private var active = false
    @Volatile private var latestFaceBlendshapes: List<FaceBlendshape> = emptyList()
    @Volatile private var frameSink: ((Bitmap) -> Unit)? = null

    override val status: StateFlow<TrackerStatus> = mutableStatus

    /** Alleen voor community-bijdragen ([PlatformContributionCapture]): ontvangt, zolang gezet,
     * het gecorrigeerde (gedraaide/gespiegelde) camerabeeld van elk verwerkt frame — hetzelfde
     * beeld dat MediaPipe ook analyseert. Roept de sink synchroon aan op de cameraExecutor-thread,
     * dus de sink moet snel zijn (bv. direct JPEG-comprimeren, niet naar UI posten). */
    fun setFrameSink(sink: ((Bitmap) -> Unit)?) {
        frameSink = sink
    }

    override fun start(): Flow<HandPose> = flow {
        startTrackers()
        emitAll(poses.asSharedFlow())
    }

    @Synchronized
    private fun startTrackers() {
        if (active && handLandmarker != null) {
            Log.d("SignCoach", "HandTracker already active")
            return
        }
        Log.d("SignCoach", "Starting HandTracker MediaPipe model...")
        mutableStatus.value = TrackerStatus.STARTING
        try {
            val handOptions = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(
                    BaseOptions.builder()
                        .setModelAssetPath(HAND_MODEL_ASSET)
                        .build(),
                )
                .setMinHandDetectionConfidence(0.5f)
                .setMinHandPresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setNumHands(MAX_HANDS)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setResultListener(::onHandResult)
                .setErrorListener(::onHandError)
                .build()
            handLandmarker = HandLandmarker.createFromOptions(applicationContext, handOptions)
            Log.d("SignCoach", "HandLandmarker created successfully")

            val faceModelExists = runCatching {
                applicationContext.assets.open(FACE_MODEL_ASSET).use { }
                true
            }.getOrDefault(false)
            if (faceModelExists) runCatching {
                val faceOptions = FaceLandmarker.FaceLandmarkerOptions.builder()
                    .setBaseOptions(
                        BaseOptions.builder()
                            .setModelAssetPath(FACE_MODEL_ASSET)
                            .build(),
                    )
                    .setNumFaces(1)
                    .setOutputFaceBlendshapes(true)
                    .setMinFaceDetectionConfidence(0.5f)
                    .setMinFacePresenceConfidence(0.5f)
                    .setMinTrackingConfidence(0.5f)
                    .setRunningMode(RunningMode.LIVE_STREAM)
                    .setResultListener(::onFaceResult)
                    .setErrorListener(::onFaceError)
                    .build()
                faceLandmarker = FaceLandmarker.createFromOptions(applicationContext, faceOptions)
            }.onFailure {
                faceLandmarker = null
                latestFaceBlendshapes = emptyList()
            }

            active = true
            mutableStatus.value = TrackerStatus.RUNNING
            Log.d("SignCoach", "HandTracker status set to RUNNING")
        } catch (e: Throwable) {
            Log.e("SignCoach", "Error initializing HandLandmarker", e)
            active = false
            handLandmarker = null
            faceLandmarker = null
            mutableStatus.value = TrackerStatus.ERROR
        }
    }

    internal fun analyze(imageProxy: ImageProxy, isFrontFacing: Boolean) {
        if (!active) {
            imageProxy.close()
            return
        }

        val timestamp = SystemClock.uptimeMillis()
        val rotation = imageProxy.imageInfo.rotationDegrees.toFloat()
        val width = imageProxy.width
        val height = imageProxy.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            bitmap.copyPixelsFromBuffer(imageProxy.planes[0].buffer)
        } catch (_: Throwable) {
            return
        } finally {
            imageProxy.close()
        }

        val matrix = Matrix().apply {
            postRotate(rotation)
            if (isFrontFacing) postScale(-1f, 1f, width.toFloat(), height.toFloat())
        }
        val oriented = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true)
        frameSink?.let { sink -> runCatching { sink(oriented) } }
        val mpImage = BitmapImageBuilder(oriented).build()
        
        synchronized(this) {
            val detector = handLandmarker
            if (!active || detector == null) {
                return
            }
            try {
                detector.detectAsync(mpImage, timestamp)
            } catch (_: Throwable) {
                mutableStatus.value = TrackerStatus.ERROR
            }
            try {
                faceLandmarker?.detectAsync(mpImage, timestamp)
            } catch (_: Throwable) {
                // Non-fataal: mondbeeld/gezicht is een aanvullend signaal, handtracking gaat door.
            }
        }
    }

    private fun onHandResult(result: HandLandmarkerResult, @Suppress("UNUSED_PARAMETER") input: MPImage) {
        val handLandmarkLists = result.landmarks()
        if (handLandmarkLists.isEmpty()) return
        val handednessLists = runCatching { result.handednesses() }.getOrNull()

        val primaryLandmarks = handLandmarkLists.getOrNull(0) ?: return
        if (primaryLandmarks.size != LANDMARK_COUNT) return
        val secondaryLandmarksRaw = handLandmarkLists.getOrNull(1)
        val secondaryLandmarks = secondaryLandmarksRaw?.takeIf { it.size == LANDMARK_COUNT }

        val pose = HandPose(
            landmarks = primaryLandmarks.map { point -> LandmarkPoint(point.x(), point.y(), point.z()) },
            timestampMs = result.timestampMs(),
            handedness = handednessAt(handednessLists, 0),
            secondaryHandLandmarks = secondaryLandmarks?.map { point -> LandmarkPoint(point.x(), point.y(), point.z()) },
            secondaryHandedness = if (secondaryLandmarks != null) handednessAt(handednessLists, 1) else Handedness.UNKNOWN,
            faceBlendshapes = latestFaceBlendshapes,
        )
        val validPrimary = pose.landmarks.all { it.x.isFinite() && it.y.isFinite() && it.z.isFinite() }
        val validSecondary = pose.secondaryHandLandmarks?.all { it.x.isFinite() && it.y.isFinite() && it.z.isFinite() } ?: true
        if (validPrimary && validSecondary) {
            poses.tryEmit(pose)
        }
    }

    private fun handednessAt(handednessLists: List<List<Category>>?, index: Int): Handedness {
        val label = handednessLists?.getOrNull(index)?.firstOrNull()?.categoryName() ?: return Handedness.UNKNOWN
        return when (label.lowercase()) {
            "left" -> Handedness.LEFT
            "right" -> Handedness.RIGHT
            else -> Handedness.UNKNOWN
        }
    }

    // LET OP (grootste onzekerheid in dit bestand, kon niet gecompileerd worden zonder Android SDK/netwerk):
    // result.faceBlendshapes() wordt hier aangenomen als java.util.Optional<List<List<Category>>>,
    // conform de MediaPipe-conventie voor optionele outputs. Geeft de build hier een type-mismatch,
    // vervang dan .orElse(null) door een directe cast/aanroep op basis van de echte signatuur in
    // com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult (zie de .aar/javadoc).
    private fun onFaceResult(result: FaceLandmarkerResult, @Suppress("UNUSED_PARAMETER") input: MPImage) {
        val blendshapesPerFace = runCatching { result.faceBlendshapes() }.getOrNull()
        val categories = blendshapesPerFace?.orElse(null)?.firstOrNull() ?: run {
            latestFaceBlendshapes = emptyList()
            return
        }
        latestFaceBlendshapes = categories.map { category ->
            FaceBlendshape(name = category.categoryName() ?: "", score = category.score())
        }.filter { it.name.isNotBlank() }
    }

    private fun onHandError(@Suppress("UNUSED_PARAMETER") error: RuntimeException) {
        mutableStatus.value = TrackerStatus.ERROR
    }

    private fun onFaceError(@Suppress("UNUSED_PARAMETER") error: RuntimeException) {
        // Non-fataal: laat handtracking doorlopen zonder mondbeeld-signaal.
        latestFaceBlendshapes = emptyList()
    }

    // @Synchronized kan niet op een suspend fun staan (Kotlin-beperking) - de mutaties die
    // gelijktijdig met startTrackers() kunnen lopen zitten daarom in een eigen synchronized-blok.
    override suspend fun stop() {
        synchronized(this) {
            active = false
            handLandmarker?.close()
            handLandmarker = null
            faceLandmarker?.close()
            faceLandmarker = null
        }
        latestFaceBlendshapes = emptyList()
        mutableStatus.value = TrackerStatus.STOPPED
    }

    private companion object {
        const val HAND_MODEL_ASSET = "hand_landmarker.task"
        // Nog toe te voegen aan shared/src/androidMain/assets/, zie README "Lokale assets".
        const val FACE_MODEL_ASSET = "face_landmarker.task"
        const val LANDMARK_COUNT = 21
        const val MAX_HANDS = 2
    }
}
