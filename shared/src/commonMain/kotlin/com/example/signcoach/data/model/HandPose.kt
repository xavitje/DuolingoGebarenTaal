package com.example.signcoach.data.model

import kotlinx.serialization.Serializable

/**
 * Welke hand een detectie betreft. MediaPipe rapporteert dit per gedetecteerde hand
 * ("Left"/"Right", vanuit het perspectief van het model op het al dan niet gespiegelde beeld).
 */
@Serializable
enum class Handedness {
    LEFT,
    RIGHT,
    UNKNOWN,
}

/**
 * Eén blendshape-score (gezichtsuitdrukking-coëfficiënt) zoals MediaPipe Face Landmarker
 * die teruggeeft, bv. naam "mouthPucker" met score 0.0..1.0.
 * Zie: https://ai.google.dev/edge/mediapipe/solutions/vision/face_landmarker
 */
@Serializable
data class FaceBlendshape(
    val name: String,
    val score: Float,
)

/**
 * Eén frame van tracking-data. Blijft backwards compatible met de oorspronkelijke MVP
 * (landmarks + timestampMs voor één hand): de nieuwe velden hebben allemaal een default,
 * dus bestaande call-sites (Android single-hand emit, tests, templates.json) blijven werken.
 *
 * - [secondaryHandLandmarks]: landmarks van een tweede hand, indien gedetecteerd (veel NGT-gebaren
 *   zijn tweehandig).
 * - [faceBlendshapes]: non-manual signaal, met name het mondbeeld is voor NGT linguïstisch relevant
 *   (zie PLAN.md sectie 2 voor de onderbouwing waarom mond > overige gezichtsregio's).
 */
@Serializable
data class HandPose(
    val landmarks: List<LandmarkPoint>,
    val timestampMs: Long,
    val handedness: Handedness = Handedness.UNKNOWN,
    val secondaryHandLandmarks: List<LandmarkPoint>? = null,
    val secondaryHandedness: Handedness = Handedness.UNKNOWN,
    val faceBlendshapes: List<FaceBlendshape> = emptyList(),
) {
    fun blendshapeScore(name: String): Float = faceBlendshapes.firstOrNull { it.name == name }?.score ?: 0f
}
