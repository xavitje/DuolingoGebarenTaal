package io.github.xavitje.signcoach

import io.github.xavitje.signcoach.data.model.HandPose
import io.github.xavitje.signcoach.domain.normalize.LandmarkNormalizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LandmarkNormalizerTest {
    @Test
    fun wristBecomesOriginAndMiddleMcpDefinesScale() {
        val normalized = assertNotNull(LandmarkNormalizer.normalize(pose()))
        assertEquals(0f, normalized.landmarks[0].x, 0.0001f)
        assertEquals(0f, normalized.landmarks[0].y, 0.0001f)
        val middle = normalized.landmarks[9]
        assertEquals(1f, kotlin.math.sqrt(middle.x * middle.x + middle.y * middle.y), 0.0001f)
    }

    @Test
    fun mirroringIsAppliedBeforeCentering() {
        val normal = assertNotNull(LandmarkNormalizer.normalize(pose()))
        val mirrored = assertNotNull(LandmarkNormalizer.normalize(pose(), mirrored = true))
        assertEquals(-normal.landmarks[5].x, mirrored.landmarks[5].x, 0.0001f)
        assertEquals(normal.landmarks[5].y, mirrored.landmarks[5].y, 0.0001f)
    }

    @Test
    fun incompleteAndNonFinitePosesAreRejected() {
        assertNull(LandmarkNormalizer.normalize(HandPose(pose().landmarks.take(20), 0)))
        val invalid = pose(transform = { index, point -> if (index == 4) point.copy(x = Float.NaN) else point })
        assertNull(LandmarkNormalizer.normalize(invalid))
        assertTrue(pose().landmarks.size == 21)
    }
}
