package com.example.signcoach.data.repository

import com.example.signcoach.data.model.RuleType
import com.example.signcoach.data.model.SignTemplate
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
            require(template.frames.flatMap { it.landmarks }.all { it.x.isFinite() && it.y.isFinite() && it.z.isFinite() }) { "Template ${template.id} contains non-finite coordinates" }
            require(template.frames.zipWithNext().all { (a, b) -> a.timestampMs <= b.timestampMs }) { "Template ${template.id} timestamps are not ordered" }

            val dynamic = template.rules.any { it.type == RuleType.MOTION_DIRECTION }
            require(!dynamic || template.frames.size >= 3) { "Dynamic template ${template.id} needs at least three frames" }
            template.rules.forEach { rule ->
                require(rule.landmarkIndices.all { it in 0 until LANDMARK_COUNT }) { "Invalid landmark index" }
                require(rule.targetValue?.isFinite() != false && rule.tolerance?.isFinite() != false) { "Rule contains non-finite values" }
                require(rule.tolerance == null || rule.tolerance >= 0f) { "Negative tolerance" }
                require(rule.minDurationMs == null || rule.minDurationMs >= 0L) { "Negative duration" }
            }
        }
    }

    private companion object {
        const val LANDMARK_COUNT = 21
        val REQUIRED_IDS = setOf("letter_a", "letter_b", "letter_l", "swipe_right")
    }
}
