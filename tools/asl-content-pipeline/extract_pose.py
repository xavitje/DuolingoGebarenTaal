"""
Video -> HandPose-landmarks, exact analoog van de live Android-pijplijn.

Repliceert twee bestanden uit de SignCoach-codebase 1-op-1, zodat een verwerkte PopSign/ASL
Citizen-clip in het `gesture_samples`-formaat terechtkomt alsof hij via de bestaande
Contribute-flow was opgenomen:

  - shared/.../platform/AndroidHandTrackerBridge.kt  (onHandResult: welke hand, welke velden)
  - shared/.../presentation/ContributionViewModel.kt  (averageLandmarks / majorityHandedness)

Belangrijkste ontwerpkeuzes (met reden):

1. RunningMode.VIDEO i.p.v. LIVE_STREAM: dit script verwerkt losse, vooraf opgenomen clips
   i.p.v. een camera-stream. VIDEO-mode van dezelfde MediaPipe HandLandmarker geeft
   deterministische, frame-voor-frame resultaten met een expliciete timestamp - functioneel
   identiek aan LIVE_STREAM voor onze doeleinden (zelfde model, zelfde output-formaat).

2. Zelfde modelbestand: dit script verwacht het EXACTE `hand_landmarker.task`-bestand dat al
   in de repo staat (shared/src/androidMain/assets/hand_landmarker.task), niet een losse
   download. Zo zijn de landmark-coordinaten gegarandeerd hetzelfde model als de live app
   gebruikt - geen risico op drift door een andere modelversie.

3. Geen mirroring: AndroidHandTrackerBridge spiegelt het camerabeeld terug (postScale(-1,1,...))
   zodra de FRONT-camera actief is, zodat MediaPipe het gebaar ziet zoals een toeschouwer het
   zou zien (niet gespiegeld). Video-clips van referentiedatasets (ASL Citizen, PopSign, etc.)
   zijn al normaal (niet-gespiegeld) opgenomen, dus hier gebeurt bewust GEEN extra flip.

4. Face blendshapes: in de huidige app-build ontbreekt het `face_landmarker.task`-asset nog
   (zie de TODO in AndroidHandTrackerBridge), dus `faceLandmarker` is in productie altijd null
   en `faceBlendshapes` altijd leeg. Dit script laat gezichts-tracking daarom bewust weg -
   toevoegen zodra de app dat zelf ook echt gebruikt, anders wijkt de content af van wat de
   live scorer ooit te zien krijgt.

Output per clip: een dict die direct te serialiseren is naar het `NewSampleDto`/
`gesture_samples`-formaat (zie GestureContributionApi.kt), plus een paar losse preview-JPEGs
voor de 'gesture-videos'-bucket (zodat de admin-reviewer iets te zien krijgt, net als bij een
live bijdrage).
"""
from __future__ import annotations

import dataclasses
import io
from pathlib import Path
from typing import Optional

import cv2
import numpy as np
from mediapipe.tasks.python import BaseOptions
from mediapipe.tasks.python.vision import (
    HandLandmarker,
    HandLandmarkerOptions,
    RunningMode,
)
from mediapipe import Image as MPImage
from mediapipe import ImageFormat

LANDMARK_COUNT = 21  # zelfde constante als AndroidHandTrackerBridge.LANDMARK_COUNT
MAX_HANDS = 2  # zelfde als AndroidHandTrackerBridge.MAX_HANDS


@dataclasses.dataclass
class FramePose:
    """Eén frame se resultaat - analoog aan wat onHandResult() per callback ontvangt."""

    landmarks: list[tuple[float, float, float]]  # primaire hand, 21 punten
    handedness: str  # "LEFT" | "RIGHT" | "UNKNOWN"


@dataclasses.dataclass
class ClipResult:
    gesture_id: str
    source_video: str
    landmarks: list[dict]  # 21x {x,y,z}, geaverageerd - zelfde vorm als LandmarkPoint
    handedness: str
    frame_count: int  # aantal frames waarin een hand gedetecteerd is (voor logging/QA)
    preview_frames_jpeg: list[bytes]  # kleine set representatieve JPEGs voor review


def _handedness_label(categories) -> str:
    if not categories:
        return "UNKNOWN"
    label = categories[0].category_name or ""
    label = label.lower()
    if label == "left":
        return "LEFT"
    if label == "right":
        return "RIGHT"
    return "UNKNOWN"


def extract_clip(
    video_path: Path,
    gesture_id: str,
    hand_model_path: Path,
    num_preview_frames: int = 4,
) -> Optional[ClipResult]:
    """Verwerkt één videoclip tot een ClipResult, of None als er in geen enkel frame een hand
    gedetecteerd werd (dan is de clip onbruikbaar als referentie-sample)."""

    options = HandLandmarkerOptions(
        base_options=BaseOptions(model_asset_path=str(hand_model_path)),
        running_mode=RunningMode.VIDEO,
        num_hands=MAX_HANDS,
        min_hand_detection_confidence=0.5,
        min_hand_presence_confidence=0.5,
        min_tracking_confidence=0.5,
    )

    cap = cv2.VideoCapture(str(video_path))
    if not cap.isOpened():
        raise RuntimeError(f"Kan video niet openen: {video_path}")

    fps = cap.get(cv2.CAP_PROP_FPS) or 30.0
    frame_interval_ms = 1000.0 / fps if fps > 0 else 33.0

    frame_poses: list[FramePose] = []
    all_frames_bgr: list[np.ndarray] = []
    frame_idx = 0

    with HandLandmarker.create_from_options(options) as landmarker:
        while True:
            ok, frame_bgr = cap.read()
            if not ok:
                break
            all_frames_bgr.append(frame_bgr)

            frame_rgb = cv2.cvtColor(frame_bgr, cv2.COLOR_BGR2RGB)
            mp_image = MPImage(image_format=ImageFormat.SRGB, data=frame_rgb)
            timestamp_ms = int(frame_idx * frame_interval_ms)

            result = landmarker.detect_for_video(mp_image, timestamp_ms)
            frame_idx += 1

            if not result.hand_landmarks:
                continue
            primary = result.hand_landmarks[0]
            if len(primary) != LANDMARK_COUNT:
                continue
            handedness = _handedness_label(
                result.handedness[0] if result.handedness else None
            )
            landmarks = [(p.x, p.y, p.z) for p in primary]
            if not all(np.isfinite([v for pt in landmarks for v in pt])):
                continue
            frame_poses.append(FramePose(landmarks=landmarks, handedness=handedness))

    cap.release()

    if not frame_poses:
        return None

    # ── averageLandmarks(): index-gewijs gemiddelde over alle frames (zelfde als Kotlin) ──
    averaged = []
    for i in range(LANDMARK_COUNT):
        xs = [fp.landmarks[i][0] for fp in frame_poses]
        ys = [fp.landmarks[i][1] for fp in frame_poses]
        zs = [fp.landmarks[i][2] for fp in frame_poses]
        averaged.append({
            "x": float(np.mean(xs)),
            "y": float(np.mean(ys)),
            "z": float(np.mean(zs)),
        })

    # ── majorityHandedness(): meest voorkomende, UNKNOWN genegeerd (zelfde als Kotlin) ──
    counts: dict[str, int] = {}
    for fp in frame_poses:
        if fp.handedness == "UNKNOWN":
            continue
        counts[fp.handedness] = counts.get(fp.handedness, 0) + 1
    handedness = max(counts, key=counts.get) if counts else "UNKNOWN"

    # ── Preview-frames voor de reviewer: gelijkmatig verdeeld over de clip ──
    preview_jpegs: list[bytes] = []
    if all_frames_bgr:
        step = max(1, len(all_frames_bgr) // num_preview_frames)
        for i in range(0, len(all_frames_bgr), step)[:num_preview_frames]:
            ok, buf = cv2.imencode(".jpg", all_frames_bgr[i], [cv2.IMWRITE_JPEG_QUALITY, 85])
            if ok:
                preview_jpegs.append(buf.tobytes())

    return ClipResult(
        gesture_id=gesture_id,
        source_video=str(video_path),
        landmarks=averaged,
        handedness=handedness,
        frame_count=len(frame_poses),
        preview_frames_jpeg=preview_jpegs,
    )
