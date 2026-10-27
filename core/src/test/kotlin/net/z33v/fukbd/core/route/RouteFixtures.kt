package net.z33v.fukbd.core.route

import net.z33v.fukbd.core.profile.Modifier
import net.z33v.fukbd.core.profile.ParseResult
import net.z33v.fukbd.core.profile.Profile
import net.z33v.fukbd.core.profile.ProfileParser
import net.z33v.fukbd.core.profile.Source

/** Builds profiles through ProfileParser, so router tests only ever see valid profiles. */
object RouteFixtures {
    const val A = 29; const val T = 48; const val SPACE = 62; const val SYM = 63; const val SHIFT_LEFT = 59

    fun profile(
        id: String,
        input: String = """{"source": "keyboard"}""",
        host: String? = null,
        priority: Int = 0,
        products: String = """["android_physical"]""",
        languages: String = """["en", "he"]""",
        bindings: String? = null,
        keymap: String? = """{"he": {"normal": {"KEYCODE_A": "ש", "KEYCODE_T": "א"}}}""",
    ): Profile {
        val parts = mutableListOf(
            """"schema_version": "2.1"""", """"id": "$id"""", """"name": "$id"""",
            """"products": $products""", """"scope": "ime_active"""", """"languages": $languages""",
            """"default_language": "en"""", """"behavior": {"unmatched_key": "pass_through"}""",
            """"priority": $priority""",
            """"device_match": {${host?.let { "\"host\": $it, " } ?: ""}"input": $input}""",
        )
        bindings?.let { parts += """"bindings": $it""" }
        keymap?.let { parts += """"keymap": $it""" }
        return when (val r = ProfileParser.parse("{" + parts.joinToString(",\n") + "}")) {
            is ParseResult.Ok -> r.profile
            is ParseResult.Rejected -> error("test profile $id rejected: ${r.errors}")
        }
    }

    /** Alt+Space toggles the language; Sym (on key-up) toggles symbols. */
    const val TOGGLES = """[
        {"id": "lang", "trigger": {"key_code": "KEYCODE_SPACE", "phase": "down", "modifiers": {"all": ["ALT"]}},
         "action": {"type": "language.toggle"}, "consume": true},
        {"id": "sym", "trigger": {"key_code": "KEYCODE_SYM", "phase": "up"}, "action": {"type": "symbols.toggle"}, "consume": true}
    ]"""

    fun device(
        id: Int,
        name: String? = "Keyboard $id",
        descriptor: String? = null,
        vendorId: Int? = null,
        productId: Int? = null,
        isExternal: Boolean? = null,
        sources: Set<Source> = setOf(Source.KEYBOARD),
        hostManufacturer: String? = "Unihertz",
        hostModel: String? = "Titan 2",
    ) = DeviceInfo(id, name, descriptor, vendorId, productId, isExternal, sources, hostManufacturer, hostModel)

    fun down(dev: Int, key: Int, vararg mods: Modifier, scan: Int = 0, repeat: Int = 0) =
        KeyInput(dev, key, scan, down = true, repeatCount = repeat, modifiers = mods.toSet())

    fun up(dev: Int, key: Int, vararg mods: Modifier, scan: Int = 0) =
        KeyInput(dev, key, scan, down = false, repeatCount = 0, modifiers = mods.toSet())
}
