package net.z33v.fukbd.core.profile

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** U1: an invalid import is rejected with a message and the previous profile stays in effect. */
class ProfileStoreTest {
    private val dir: File = Files.createTempDirectory("fukbd-store").toFile()
    private val base = Fixtures.text("valid", "base.json")
    private val bundled = listOf(BundledProfile("base.json", base))

    @AfterTest
    fun cleanUp() { dir.deleteRecursively() }

    private fun store(b: List<BundledProfile> = emptyList()) = ProfileStore(dir, b)

    @Test
    fun validImportIsStoredUnderItsId() {
        val r = assertIs<ImportResult.Imported>(store().import(base))
        assertEquals("fixture.base", r.profile.id)
        assertEquals(listOf("fixture.base.json"), dir.list()!!.toList(), "no temp files are left behind")
        assertEquals(listOf("fixture.base"), store().load().profiles.map { it.id })
    }

    @Test
    fun invalidImportKeepsThePreviousFileByteForByte() {
        store().import(base)
        val before = File(dir, "fixture.base.json").readBytes()
        val broken = base.replace("\"Fixture base\"", "\"Renamed\"").replace("\"ime_active\"", "\"global\"")
        val r = assertIs<ImportResult.Rejected>(store().import(broken))
        assertTrue(r.errors.any { it.path == "/scope" }, r.errors.toString())
        assertTrue(before.contentEquals(File(dir, "fixture.base.json").readBytes()))
        assertEquals("Fixture base", store().load().profiles.single().name)
        assertEquals(listOf("fixture.base.json"), dir.list()!!.toList())
    }

    @Test
    fun everyInvalidFixtureIsRejectedWithoutWritingAnything() {
        for (f in Fixtures.dir("invalid-schema") + Fixtures.dir("invalid-semantic")) {
            val r = store().import(Fixtures.text(f))
            assertIs<ImportResult.Rejected>(r, f.name)
            assertTrue(r.errors.isNotEmpty(), "${f.name} needs a message")
        }
        assertEquals(0, dir.list()?.size ?: 0)
    }

    @Test
    fun importOverridesBundledAndResetRestoresIt() {
        val s = store(bundled)
        val changed = base.replace("\"Fixture base\"", "\"My version\"")
        assertTrue(assertIs<ImportResult.Imported>(s.import(changed)).overridesBundled)
        assertEquals("My version", s.load().profiles.single().name)
        assertTrue(s.reset("fixture.base"))
        assertEquals("Fixture base", s.load().profiles.single().name)
        assertFalse(s.reset("fixture.base"), "nothing left to reset")
    }

    @Test
    fun corruptOrMisnamedFilesAreReportedAndSkipped() {
        store().import(base)
        File(dir, "broken.json").writeText("{ not json")
        File(dir, "other.json").writeText(base) // id fixture.base, wrong file name
        val report = store().load()
        assertEquals(listOf("fixture.base"), report.profiles.map { it.id })
        assertEquals(setOf("broken.json", "other.json"), report.problems.keys)
    }

    @Test
    fun resetCannotEscapeTheProfileDirectory() {
        val outside = File(dir.parentFile, "fukbd-outside-${System.nanoTime()}.json").apply { writeText("x") }
        try {
            assertFalse(store().reset("../${outside.nameWithoutExtension}"))
            assertTrue(outside.exists())
        } finally { outside.delete() }
    }
}
