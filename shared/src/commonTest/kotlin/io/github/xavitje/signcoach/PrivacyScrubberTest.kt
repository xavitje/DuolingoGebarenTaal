package io.github.xavitje.signcoach

import io.github.xavitje.signcoach.domain.privacy.PrivacyScrubber
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class PrivacyScrubberTest {
    @Test
    fun removesEmailIdsAndTokens() {
        val input = "Fout voor jan@school.nl (user 3f2b8c1e-1234-4abc-9def-0123456789ab) " +
            "Authorization: Bearer eyJhbGciOi.eyJzdWIiOi.abc_DEF-123 url=/rest/v1/x?user_id=eq.42&select=*"
        val out = PrivacyScrubber.scrubNonNull(input)
        assertFalse(out.contains("jan@school.nl"))
        assertFalse(out.contains("3f2b8c1e"))
        assertFalse(out.contains("eyJhbGciOi"))
        assertFalse(out.contains("eq.42"))
    }

    @Test
    fun leavesNormalStackTraceIntact() {
        val trace = "java.lang.IllegalStateException: boom\n\tat io.github.xavitje.signcoach.Foo.bar(Foo.kt:12)"
        assertEquals(trace, PrivacyScrubber.scrubNonNull(trace))
    }

    @Test
    fun nullStaysNull() {
        assertNull(PrivacyScrubber.scrub(null))
    }
}
