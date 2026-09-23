package io.github.xavitje.signcoach.data.repository

import io.github.xavitje.signcoach.data.model.RuleType
import io.github.xavitje.signcoach.data.model.SignTemplate
import kotlinx.serialization.json.Json

class AssetTemplateRepository(
    private val source: String,
    private val json: Json = Json { ignoreUnknownKeys = false },
) : TemplateRepository {
    override fun load(): Result<List<SignTemplate>> = runCatching {
        json.decodeFromString<List<SignTemplate>>(source).also(::validate)
    }

    private fun validate(templates: List<SignTemplate>) {
        require(templates.isNotEmpty()) { "No templates found" }
        require(templates.map { it.id }.toSet().size == templates.size) { "Duplicate template id" }
        require(templates.map { it.id }.toSet().containsAll(REQUIRED_IDS)) { "Required templates missing" }

        templates.forEach { template ->
            require(template.id.isNotBlank() && template.name.isNotBlank()) { "Template id/name is blank" }
            require(template.frames.isNotEmpty()) { "Template ${template.id} has no frames" }
            require(template.frames.all { it.landmarks.size == LANDMARK_COUNT }) { "Template ${template.id} must contain 21 landmarks per frame" }
            require(template.frames.flatMap { it.landmarks }.all {
                it.x.isFinite() && it.y.isFinite() && it.z.isFinite() &&
                    it.x in 0f..1f && it.y in 0f..1f
            }) { "Template ${template.id} contains invalid coordinates" }
            require(template.frames.zipWithNext().all { (a, b) -> a.timestampMs <= b.timestampMs }) { "Template ${template.id} timestamps are not ordered" }

            // Tweede hand is optioneel, maar als hij aanwezig is moet hij dezelfde vorm hebben als de eerste.
            require(template.frames.all { frame -> frame.secondaryHandLandmarks?.size?.let { it == LANDMARK_COUNT } ?: true }) {
                "Template ${template.id}: de tweede hand moet, indien aanwezig, 21 landmarks bevatten"
            }
            require(template.frames.flatMap { it.secondaryHandLandmarks.orEmpty() }.all {
                it.x.isFinite() && it.y.isFinite() && it.z.isFinite() &&
                    it.x in 0f..1f && it.y in 0f..1f
            }) { "Template ${template.id} contains invalid coordinates for the secondary hand" }
            require(template.frames.flatMap { it.faceBlendshapes }.all { it.score.isFinite() && it.score in 0f..1f && it.name.isNotBlank() }) {
                "Template ${template.id} contains invalid face blendshape data"
            }

            // referenceVariants zijn extra geldige referentieposes voor hetzelfde (statische)
            // gebaar en moeten aan dezelfde eisen voldoen als frames.
            require(template.referenceVariants.all { it.landmarks.size == LANDMARK_COUNT }) {
                "Template ${template.id}: referenceVariants moeten 21 landmarks per pose bevatten"
            }
            require(template.referenceVariants.flatMap { it.landmarks }.all {
                it.x.isFinite() && it.y.isFinite() && it.z.isFinite() &&
                    it.x in 0f..1f && it.y in 0f..1f
            }) { "Template ${template.id} contains invalid coordinates in referenceVariants" }
            require(template.referenceVariants.all { variant -> variant.secondaryHandLandmarks?.size?.let { it == LANDMARK_COUNT } ?: true }) {
                "Template ${template.id}: de tweede hand van een referenceVariant moet, indien aanwezig, 21 landmarks bevatten"
            }
            require(template.referenceVariants.flatMap { it.secondaryHandLandmarks.orEmpty() }.all {
                it.x.isFinite() && it.y.isFinite() && it.z.isFinite() &&
                    it.x in 0f..1f && it.y in 0f..1f
            }) { "Template ${template.id} contains invalid coordinates for the secondary hand in referenceVariants" }
            require(template.referenceVariants.flatMap { it.faceBlendshapes }.all { it.score.isFinite() && it.score in 0f..1f && it.name.isNotBlank() }) {
                "Template ${template.id} contains invalid face blendshape data in referenceVariants"
            }

            val dynamic = template.rules.any { it.type == RuleType.MOTION_DIRECTION }
            require(!dynamic || template.frames.size >= 3) { "Dynamic template ${template.id} needs at least three frames" }
            template.rules.forEach { rule ->
                require(rule.landmarkIndices.all { it in 0 until LANDMARK_COUNT }) { "Invalid landmark index" }
                require(rule.targetValue?.isFinite() != false && rule.tolerance?.isFinite() != false) { "Rule contains non-finite values" }
                require(rule.tolerance == null || rule.tolerance >= 0f) { "Negative tolerance" }
                require(rule.minDurationMs == null || rule.minDurationMs >= 0L) { "Negative duration" }
                require(rule.type != RuleType.FACE_BLENDSHAPE || !rule.blendshapeName.isNullOrBlank()) {
                    "FACE_BLENDSHAPE rule for template ${template.id} is missing blendshapeName"
                }
            }
        }
    }

    private companion object {
        const val LANDMARK_COUNT = 21
        val REQUIRED_IDS = setOf("letter_a", "letter_b", "letter_l", "swipe_right")
    }
}
