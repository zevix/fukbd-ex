package net.z33v.fukbd.core.profile

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ProfileParserTest {
    private val base = Fixtures.text("valid", "base.json")

    private fun ok(text: String): Profile = assertIs<ParseResult.Ok>(ProfileParser.parse(text), text.take(200)).profile

    private fun rejected(text: String): List<ProfileError> =
        assertIs<ParseResult.Rejected>(ProfileParser.parse(text), "expected rejection").errors

    @Test
    fun convertedTitanExampleKeepsAllRules() {
        val p = ok(File(Fixtures.examples, "titan-illustrative.json").readText())
        assertEquals(6, p.bindings.size)
        assertEquals(34, p.keymap.getValue("he").getValue(Layer.NORMAL).size)
        assertEquals("ש", p.keymap.getValue("he").getValue(Layer.NORMAL)["KEYCODE_A"])
        assertEquals(setOf(Product.ANDROID_PHYSICAL), p.products)
    }

    @Test
    fun tvExampleResolvesKeycodesAndActions() {
        val p = ok(File(Fixtures.examples, "tv-remote.json").readText())
        assertEquals(9, p.bindings.size)
        val left = p.bindings.first { it.id == "remote_left" }
        assertEquals(21, left.trigger.keyCode) // KEYCODE_DPAD_LEFT
        assertEquals(Action.FocusMove(Direction.LEFT), left.action)
        assertEquals(Action.VoiceToggle, p.bindings.first { it.id == "remote_optional_record" }.action)
    }

    @Test
    fun everyErrorIsReportedNotJustTheFirst() {
        val text = base.replace("\"scope\": \"ime_active\"", "\"scope\": \"global\"")
            .replace("\"id\": \"fixture.base\"", "\"id\": \"Bad Id\"")
            .replace("\"KEYCODE_SPACE\"", "\"KEYCODE_NOPE\"")
        val errors = rejected(text)
        assertTrue(errors.any { it.path == "/scope" }, errors.toString())
        assertTrue(errors.any { it.path == "/id" }, errors.toString())
        assertTrue(errors.any { it.path == "/bindings/0/trigger/key_code" }, errors.toString())
    }

    @Test
    fun repeatDefaultsToFirst() {
        assertEquals(Repeat.FIRST, ok(base).bindings.single().trigger.repeat)
    }

    @Test
    fun keycodeUnknownIsNotABindableKey() {
        val errors = rejected(base.replace("\"KEYCODE_SPACE\"", "\"KEYCODE_UNKNOWN\""))
        assertTrue(errors.single().message.contains("not an Android keycode"), errors.toString())
    }

    @Test
    fun textLengthCountsCodePointsNotUtf16Units() {
        ok(base.replace("\"ש\"", "\"" + "😀".repeat(8) + "\""))
        rejected(base.replace("\"ש\"", "\"" + "😀".repeat(9) + "\""))
    }

    @Test
    fun whenLanguageMustBeAProfileLanguage() {
        val text = base.replace("\"consume\": true", "\"when\": {\"language\": \"fr\"},\n      \"consume\": true")
        assertEquals("/bindings/0/when/language", rejected(text).single().path)
    }

    @Test
    fun duplicateKeyIsReportedWithItsPath() {
        val errors = rejected(Fixtures.text("invalid-semantic", "duplicate-json-key.json"))
        assertEquals("/name", errors.single().path)
    }

    @Test
    fun leadingByteOrderMarkIsStripped() {
        assertEquals("fixture.base", ok(Fixtures.text("valid", "bom-prefixed.json")).id)
    }

    @Test
    fun keycodeTableIsTheGeneratedApi35One() {
        assertEquals(318, KeyCodes.size) // 0..318 minus KEYCODE_UNKNOWN
        assertEquals(63, KeyCodes.valueOf("KEYCODE_SYM"))
        assertEquals("KEYCODE_PROG_BLUE", KeyCodes.nameOf(186))
    }
}
