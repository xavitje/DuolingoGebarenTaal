package io.github.xavitje.signcoach

import io.github.xavitje.signcoach.data.model.GestureRule
import io.github.xavitje.signcoach.data.model.HandPose
import io.github.xavitje.signcoach.data.model.LandmarkPoint
import io.github.xavitje.signcoach.data.model.RuleType
import io.github.xavitje.signcoach.data.model.SignTemplate

fun pose(
    wristX: Float = 0.3f,
    timestampMs: Long = 0L,
    transform: (Int, LandmarkPoint) -> LandmarkPoint = { _, point -> point },
): HandPose = HandPose(
    landmarks = List(21) { index ->
        val point = LandmarkPoint(
            x = wristX + (index % 5) * 0.025f,
            y = 0.8f - index * 0.018f,
            z = -index * 0.002f,
        )
        transform(index, point)
    },
    timestampMs = timestampMs,
)

fun staticTemplate(id: String = "letter_a", frame: HandPose = pose()): SignTemplate =
    SignTemplate(id, id, listOf(frame), emptyList())

fun dynamicTemplate(): SignTemplate = SignTemplate(
    id = "swipe_right",
    name = "Swipe rechts",
    frames = listOf(pose(0.3f, 0), pose(0.45f, 200), pose(0.6f, 400)),
    rules = listOf(GestureRule(RuleType.MOTION_DIRECTION, targetValue = 0.2f, minDurationMs = 150)),
)
