package net.z33v.fukbd.core.profile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** U2: a profile naming a shell command, intent, URL or code is rejected as a whole. */
class NoExecutableContentTest {
    private fun rejectedFor(name: String): List<ProfileError> =
        assertIs<ParseResult.Rejected>(ProfileParser.parse(Fixtures.text("invalid-schema", name)), name).errors

    @Test
    fun intentAtTopLevelIsRejected() {
        assertTrue(rejectedFor("unknown-top-level-key.json").any { it.path == "/intent" })
    }

    @Test
    fun execNestedInAnActionIsRejected() {
        assertTrue(rejectedFor("unknown-nested-key.json").any { it.path == "/bindings/0/action/exec" })
    }

    @Test
    fun shellAndUrlActionsAreRejected() {
        assertTrue(rejectedFor("unknown-action.json").any { it.message.contains("not an allowed action") })
        assertTrue(rejectedFor("url-action.json").any { it.message.contains("not an allowed action") })
    }

    @Test
    fun theActionAllowlistIsExactlyTheElevenDocumentedActions() {
        assertEquals(11, Action::class.sealedSubclasses.size)
    }
}
