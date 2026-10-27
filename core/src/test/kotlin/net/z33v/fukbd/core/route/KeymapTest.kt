package net.z33v.fukbd.core.route

import net.z33v.fukbd.core.profile.Action
import net.z33v.fukbd.core.profile.Modifier
import net.z33v.fukbd.core.profile.Product
import net.z33v.fukbd.core.route.RouteFixtures.A
import net.z33v.fukbd.core.route.RouteFixtures.device
import net.z33v.fukbd.core.route.RouteFixtures.down
import net.z33v.fukbd.core.route.RouteFixtures.profile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** R7: keymap tables expand into bindings that leave Ctrl, Alt and Meta chords to the app. */
class KeymapTest {
    private val dev = device(1)

    private fun typed(r: Router, vararg mods: Modifier): Any {
        val d = r.decide(down(1, A, *mods), dev)
        r.decide(RouteFixtures.up(1, A, *mods), dev)
        return if (d is Decision.Consume) d.actions else d::class.simpleName!!
    }

    @Test
    fun expansionComesAfterExplicitBindingsInFileOrder() {
        val p = profile("p", bindings = """[{"id": "explicit_a", "trigger": {"key_code": "KEYCODE_A", "phase": "down"},
            "when": {"language": "he"}, "action": {"type": "text.type", "text": "X"}, "consume": true}]""")
        val ids = KeymapExpander.expand(p).map { it.id }
        assertEquals(listOf("explicit_a", "keymap.he.normal.KEYCODE_A", "keymap.he.normal.KEYCODE_T"), ids)
        val r = Router(listOf(p.copy(defaultLanguage = "he")), Product.ANDROID_PHYSICAL)
        assertEquals(listOf(Action.TypeText("X")), typed(r), "first match wins")
    }

    @Test
    fun chordsReachTheApp() {
        val r = Router(listOf(profile("p").copy(defaultLanguage = "he")), Product.ANDROID_PHYSICAL)
        for (m in listOf(Modifier.CTRL, Modifier.ALT, Modifier.META, Modifier.SYM)) {
            assertEquals("PassThrough", typed(r, m), "$m+A must reach the app (e.g. Ctrl+A selects all)")
        }
        assertEquals(listOf(Action.TypeText("ש")), typed(r))
    }

    @Test
    fun shiftWithoutAShiftLayerTypesTheNormalCharacter() {
        val r = Router(listOf(profile("p").copy(defaultLanguage = "he")), Product.ANDROID_PHYSICAL)
        assertEquals(listOf(Action.TypeText("ש")), typed(r, Modifier.SHIFT))
    }

    @Test
    fun shiftLayerWinsWhenPresent() {
        val p = profile("p", keymap = """{"he": {"normal": {"KEYCODE_A": "ש"}, "shift": {"KEYCODE_A": "₪"}}}""")
        val r = Router(listOf(p.copy(defaultLanguage = "he")), Product.ANDROID_PHYSICAL)
        assertEquals(listOf(Action.TypeText("ש")), typed(r))
        assertEquals(listOf(Action.TypeText("₪")), typed(r, Modifier.SHIFT))
    }

    @Test
    fun explicitBindingsKeepV05ModifierSemantics() {
        // No "modifiers" on an explicit binding means any modifier state, Ctrl included.
        val p = profile("p", keymap = null, bindings = """[{"id": "any", "trigger": {"key_code": "KEYCODE_A", "phase": "down"},
            "action": {"type": "editor.backspace"}, "consume": true}]""")
        val r = Router(listOf(p), Product.ANDROID_PHYSICAL)
        assertEquals(listOf(Action.Backspace), typed(r, Modifier.CTRL))
    }

    @Test
    fun keymapEntriesOnlyFireInTheirLanguage() {
        val r = Router(listOf(profile("p")), Product.ANDROID_PHYSICAL) // default en, keymap he only
        assertIs<String>(typed(r))
    }
}
