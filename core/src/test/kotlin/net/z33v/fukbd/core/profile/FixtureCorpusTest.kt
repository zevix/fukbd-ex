package net.z33v.fukbd.core.profile

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/** R3: every valid fixture and example is accepted; every invalid fixture is rejected with a reason. */
class FixtureCorpusTest {
    private fun verdicts(files: List<java.io.File>, expectOk: Boolean): List<String> =
        files.mapNotNull { f ->
            when (val r = ProfileParser.parse(Fixtures.text(f))) {
                is ParseResult.Ok -> if (expectOk) null else "${f.parentFile.name}/${f.name}: accepted, expected rejection"
                is ParseResult.Rejected -> when {
                    expectOk -> "${f.parentFile.name}/${f.name}: rejected: ${r.errors}"
                    r.errors.isEmpty() -> "${f.parentFile.name}/${f.name}: rejected without a reason"
                    else -> { println("rejected ${f.parentFile.name}/${f.name}: ${r.errors.first()}"); null }
                }
            }
        }

    @Test
    fun validFixturesAndExamplesAreAccepted() {
        val files = Fixtures.dir("valid") + Fixtures.exampleFiles
        assertTrue(files.size >= 7, "expected the 4 valid fixtures and 3 examples, found ${files.size}")
        val wrong = verdicts(files, expectOk = true)
        if (wrong.isNotEmpty()) fail(wrong.joinToString("\n"))
    }

    @Test
    fun schemaInvalidFixturesAreRejected() {
        val wrong = verdicts(Fixtures.dir("invalid-schema"), expectOk = false)
        if (wrong.isNotEmpty()) fail(wrong.joinToString("\n"))
    }

    @Test
    fun semanticInvalidFixturesAreRejected() {
        val wrong = verdicts(Fixtures.dir("invalid-semantic"), expectOk = false)
        if (wrong.isNotEmpty()) fail(wrong.joinToString("\n"))
    }
}
