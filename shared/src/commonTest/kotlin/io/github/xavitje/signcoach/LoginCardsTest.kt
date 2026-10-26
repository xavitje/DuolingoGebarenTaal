package io.github.xavitje.signcoach

import io.github.xavitje.signcoach.domain.print.LoginCard
import io.github.xavitje.signcoach.domain.print.LoginCards
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LoginCardsTest {
    @Test
    fun everyStudentGetsACardWithCodeAndPin() {
        val html = LoginCards.html("Groep 7b", "K7B4QX", listOf(LoginCard("Sara", "4821"), LoginCard("Noah", null)))
        assertEquals(2, Regex("class=\"card\"").findAll(html).count())
        assertTrue(html.contains("4821"))
        assertTrue(html.contains("K7B4QX"))
        assertTrue(html.contains("Noah"))
    }

    @Test
    fun namesAreEscaped() {
        val html = LoginCards.html("<b>klas</b>", "ABC123", listOf(LoginCard("<script>x</script>", "1111")))
        assertFalse(html.contains("<script>x"))
        assertTrue(html.contains("&lt;script&gt;"))
    }
}
