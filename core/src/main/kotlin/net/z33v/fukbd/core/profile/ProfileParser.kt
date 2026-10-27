package net.z33v.fukbd.core.profile

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull
import net.z33v.fukbd.core.SCHEMA_VERSION

/**
 * Reads one untrusted profile file. The structural rules mirror
 * `schema/device-profile-v2.1.schema.json` (the JVM tests check that the two agree on every
 * fixture); the semantic rules are the ones the schema cannot express (fukbd-docs M0 design.md,
 * *Refuses*). Any error rejects the whole profile, and every error found is reported.
 */
object ProfileParser {
    const val MAX_BYTES = 64 * 1024
    const val MAX_DEPTH = 16
    const val MAX_BINDINGS = 512

    private val ID = Regex("^[a-z0-9]+(?:[._-][a-z0-9]+)*$")
    private val BINDING_ID = Regex("^[a-z][a-z0-9_\\-]*$")
    private val LANGUAGE = Regex("^[a-z]{2,3}(-[A-Z]{2})?$")
    private val KEYCODE = Regex("^KEYCODE_[A-Z0-9_]+$")

    /** Bidirectional embedding, override and isolate controls: they can make typed text read differently from what is stored. */
    private val BIDI_CONTROLS = ('‪'..'‮').toSet() + ('⁦'..'⁩').toSet()

    fun parse(json: String): ParseResult {
        val text = json.removePrefix("﻿")
        if (text.toByteArray(Charsets.UTF_8).size > MAX_BYTES) {
            return ParseResult.Rejected(listOf(ProfileError("", "file is larger than $MAX_BYTES bytes")))
        }
        val root = try {
            StrictJson.parse(text, MAX_DEPTH)
        } catch (e: StrictJson.Failure) {
            return ParseResult.Rejected(listOf(ProfileError(e.path, e.message ?: "invalid JSON")))
        }
        val reader = Reader()
        val profile = reader.profile(root)
        return if (reader.errors.isEmpty() && profile != null) ParseResult.Ok(profile)
        else ParseResult.Rejected(reader.errors.toList())
    }

    private class Reader {
        val errors = mutableListOf<ProfileError>()

        fun err(path: String, message: String) { errors += ProfileError(path, message) }

        /** Checks an object's keys against [allowed] and [required]; returns null (after reporting) if it is not an object. */
        fun obj(e: JsonElement?, path: String, allowed: Set<String>, required: Set<String> = emptySet()): JsonObject? {
            if (e !is JsonObject) { err(path, "must be an object"); return null }
            for (k in e.keys) if (k !in allowed) err("$path/${escapePointer(k)}", "unknown property \"$k\"")
            for (k in required) if (k !in e) err(path, "missing required property \"$k\"")
            return e
        }

        fun str(o: JsonObject, key: String, path: String, minLen: Int = 0, maxLen: Int = Int.MAX_VALUE, pattern: Regex? = null): String? {
            val e = o[key] ?: return null
            val p = "$path/$key"
            if (e !is JsonPrimitive || !e.isString) { err(p, "must be a string"); return null }
            val s = e.content
            val len = s.codePointCount(0, s.length)
            if (len < minLen) err(p, "must be at least $minLen characters")
            if (len > maxLen) err(p, "must be at most $maxLen characters")
            if (pattern != null && !pattern.matches(s)) err(p, "\"$s\" does not match ${pattern.pattern}")
            return s
        }

        fun int(o: JsonObject, key: String, path: String, min: Long, max: Long = Long.MAX_VALUE): Int? {
            val e = o[key] ?: return null
            val p = "$path/$key"
            val v = (e as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull
            if (v == null) { err(p, "must be an integer"); return null }
            if (v < min || v > max) { err(p, "must be between $min and $max"); return null }
            return v.toInt()
        }

        fun bool(o: JsonObject, key: String, path: String): Boolean? {
            val e = o[key] ?: return null
            val v = (e as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull
            if (v == null) err("$path/$key", "must be true or false")
            return v
        }

        fun <T> enumOf(o: JsonObject, key: String, path: String, values: Map<String, T>): T? {
            val s = str(o, key, path) ?: return null
            return values[s] ?: run { err("$path/$key", "\"$s\" is not one of ${values.keys.joinToString()}"); null }
        }

        fun stringArray(o: JsonObject, key: String, path: String, minItems: Int, maxItems: Int, item: (String, String) -> Unit): List<String>? {
            val e = o[key] ?: return null
            val p = "$path/$key"
            if (e !is JsonArray) { err(p, "must be an array"); return null }
            if (e.size < minItems) err(p, "must have at least $minItems items")
            if (e.size > maxItems) err(p, "must have at most $maxItems items")
            val out = mutableListOf<String>()
            e.forEachIndexed { i, v ->
                if (v !is JsonPrimitive || !v.isString) err("$p/$i", "must be a string")
                else { item(v.content, "$p/$i"); out += v.content }
            }
            if (out.toSet().size != out.size) err(p, "items must be unique")
            return out
        }

        fun profile(root: JsonElement): Profile? {
            val top = setOf(
                "schema_version", "id", "name", "description", "products", "scope", "languages",
                "default_language", "behavior", "priority", "device_match", "bindings", "keymap",
            )
            val required = setOf(
                "schema_version", "id", "name", "products", "scope", "languages",
                "default_language", "behavior", "device_match",
            )
            val o = obj(root, "", top, required) ?: return null
            if ("bindings" !in o && "keymap" !in o) err("", "needs \"bindings\", \"keymap\" or both")

            str(o, "schema_version", "")?.let { if (it != SCHEMA_VERSION) err("/schema_version", "must be \"$SCHEMA_VERSION\", found \"$it\"") }
            val id = str(o, "id", "", minLen = 1, maxLen = 100, pattern = ID)
            val name = str(o, "name", "", minLen = 1, maxLen = 120)
            val description = str(o, "description", "", maxLen = 500)
            val products = stringArray(o, "products", "", 1, 2) { s, p ->
                if (Product.entries.none { it.json == s }) err(p, "\"$s\" is not a known product")
            }?.mapNotNull { s -> Product.entries.firstOrNull { it.json == s } }?.toSet()
            str(o, "scope", "")?.let { if (it != "ime_active") err("/scope", "must be \"ime_active\": profiles cannot ask for system-wide key interception") }
            val languages = stringArray(o, "languages", "", 1, 8) { s, p ->
                if (!LANGUAGE.matches(s)) err(p, "\"$s\" is not a language code like \"en\" or \"pt-BR\"")
            }
            val defaultLanguage = str(o, "default_language", "", pattern = LANGUAGE)
            obj(o["behavior"], "/behavior", setOf("unmatched_key"), setOf("unmatched_key"))?.let { b ->
                str(b, "unmatched_key", "/behavior")?.let { if (it != "pass_through") err("/behavior/unmatched_key", "must be \"pass_through\"") }
            }
            val priority = int(o, "priority", "", -100, 100) ?: 0
            val deviceMatch = deviceMatch(o["device_match"])
            val bindings = bindings(o["bindings"])
            val keymap = keymap(o["keymap"])

            // Semantic checks the schema cannot express.
            val langs = languages?.toSet() ?: emptySet()
            if (defaultLanguage != null && languages != null && defaultLanguage !in langs) {
                err("/default_language", "\"$defaultLanguage\" is not in languages")
            }
            if (languages != null) {
                keymap?.keys?.filter { it !in langs }?.forEach { err("/keymap/$it", "language \"$it\" is not in languages") }
                bindings?.forEachIndexed { i, b ->
                    b.condition?.language?.let { if (it !in langs) err("/bindings/$i/when/language", "language \"$it\" is not in languages") }
                }
            }
            bindings?.groupBy { it.id }?.filter { it.value.size > 1 }?.keys?.forEach { err("/bindings", "duplicate binding id \"$it\"") }

            if (errors.isNotEmpty() || id == null || name == null || products == null || languages == null ||
                defaultLanguage == null || deviceMatch == null) return null
            return Profile(
                id = id, name = name, description = description, products = products, languages = languages,
                defaultLanguage = defaultLanguage, priority = priority, deviceMatch = deviceMatch,
                bindings = bindings ?: emptyList(), keymap = keymap ?: emptyMap(),
            )
        }

        fun deviceMatch(e: JsonElement?): DeviceMatch? {
            val o = obj(e, "/device_match", setOf("host", "input"), setOf("input")) ?: return null
            val host = o["host"]?.let { h ->
                obj(h, "/device_match/host", setOf("manufacturer", "model"))?.let { ho ->
                    if (ho.isEmpty()) err("/device_match/host", "must name a manufacturer, a model or both")
                    HostMatch(
                        str(ho, "manufacturer", "/device_match/host", minLen = 1),
                        str(ho, "model", "/device_match/host", minLen = 1),
                    )
                }
            }
            val p = "/device_match/input"
            val i = obj(o["input"], p, setOf("source", "name", "descriptor", "vendor_id", "product_id", "is_external"), setOf("source"))
                ?: return null
            val source = enumOf(i, "source", p, Source.entries.associateBy { it.json })
            val input = InputMatch(
                source = source ?: return null,
                name = str(i, "name", p, minLen = 1),
                descriptor = str(i, "descriptor", p, minLen = 1),
                vendorId = int(i, "vendor_id", p, 0),
                productId = int(i, "product_id", p, 0),
                isExternal = bool(i, "is_external", p),
            )
            return DeviceMatch(host, input)
        }

        fun bindings(e: JsonElement?): List<Binding>? {
            if (e == null) return null
            if (e !is JsonArray) { err("/bindings", "must be an array"); return null }
            if (e.size > MAX_BINDINGS) err("/bindings", "must have at most $MAX_BINDINGS bindings")
            return e.mapIndexedNotNull { i, b -> binding(b, "/bindings/$i") }
        }

        fun binding(e: JsonElement, path: String): Binding? {
            val o = obj(e, path, setOf("id", "trigger", "when", "action", "consume"), setOf("id", "trigger", "action", "consume"))
                ?: return null
            val id = str(o, "id", path, pattern = BINDING_ID)
            val trigger = trigger(o["trigger"], "$path/trigger")
            val condition = o["when"]?.let { w ->
                obj(w, "$path/when", setOf("language", "mode"))?.let { wo ->
                    Condition(
                        str(wo, "language", "$path/when", pattern = LANGUAGE),
                        enumOf(wo, "mode", "$path/when", mapOf("normal" to Mode.NORMAL, "symbols" to Mode.SYMBOLS)),
                    )
                }
            }
            val action = action(o["action"], "$path/action")
            val consume = bool(o, "consume", path)
            if (id == null || trigger == null || action == null || consume == null) return null
            return Binding(id, trigger, condition, action, consume)
        }

        fun trigger(e: JsonElement?, path: String): Trigger? {
            val o = obj(e, path, setOf("key_code", "scan_code", "phase", "repeat", "modifiers"), setOf("phase")) ?: return null
            if ("key_code" !in o && "scan_code" !in o) err(path, "needs \"key_code\", \"scan_code\" or both")
            val keyName = str(o, "key_code", path, pattern = KEYCODE)
            val keyCode = keyName?.let { n ->
                if (!KEYCODE.matches(n)) null
                else KeyCodes.valueOf(n) ?: run { err("$path/key_code", "\"$n\" is not an Android keycode (API 35)"); null }
            }
            val scanCode = int(o, "scan_code", path, 1)
            val phase = enumOf(o, "phase", path, mapOf("down" to Phase.DOWN, "up" to Phase.UP))
            val repeat = enumOf(o, "repeat", path, mapOf("first" to Repeat.FIRST, "allow" to Repeat.ALLOW))
            val modifiers = o["modifiers"]?.let { m ->
                obj(m, "$path/modifiers", setOf("all", "none"))?.let { mo ->
                    fun set(key: String) = stringArray(mo, key, "$path/modifiers", 0, 5) { s, p ->
                        if (Modifier.entries.none { it.name == s }) err(p, "\"$s\" is not one of SHIFT, ALT, CTRL, META, SYM")
                    }?.mapNotNull { s -> Modifier.entries.firstOrNull { it.name == s } }?.toSet() ?: emptySet()
                    val all = set("all")
                    val none = set("none")
                    (all intersect none).forEach { err("$path/modifiers", "$it is both required and forbidden") }
                    Modifiers(all, none)
                }
            }
            if (phase == null) return null
            return Trigger(keyCode, keyName, scanCode, phase, repeat ?: Repeat.FIRST, modifiers)
        }

        fun action(e: JsonElement?, path: String): Action? {
            if (e !is JsonObject) { err(path, "must be an object"); return null }
            val type = (e["type"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            if (type == null) { err(path, "missing action \"type\""); return null }
            fun only(vararg keys: String) = obj(e, path, setOf("type", *keys), setOf("type", *keys))
            fun direction(o: JsonObject, allowed: Set<String>): Direction? =
                str(o, "direction", path)?.let { d ->
                    if (d !in allowed) { err("$path/direction", "\"$d\" is not one of ${allowed.joinToString()}"); null }
                    else Direction.valueOf(d.uppercase())
                }
            return when (type) {
                "text.type" -> only("text")?.let { o ->
                    str(o, "text", path, minLen = 1, maxLen = 8)?.let { t -> checkTypedText(t, "$path/text"); Action.TypeText(t) }
                }
                "editor.space" -> only()?.let { Action.Space }
                "editor.backspace" -> only()?.let { Action.Backspace }
                "editor.enter" -> only()?.let { Action.Enter }
                "editor.move_cursor" -> only("direction")?.let { direction(it, setOf("left", "right"))?.let(Action::MoveCursor) }
                "language.toggle" -> only()?.let { Action.LanguageToggle }
                "symbols.toggle" -> only()?.let { Action.SymbolsToggle }
                "voice.toggle" -> only()?.let { Action.VoiceToggle }
                "keyboard.hide" -> only()?.let { Action.KeyboardHide }
                "ui.focus.move" -> only("direction")?.let { direction(it, setOf("left", "right", "up", "down"))?.let(Action::FocusMove) }
                "ui.focus.activate" -> only()?.let { Action.FocusActivate }
                else -> { err("$path/type", "\"$type\" is not an allowed action"); null }
            }
        }

        fun keymap(e: JsonElement?): Map<String, Map<Layer, Map<String, String>>>? {
            if (e == null) return null
            if (e !is JsonObject) { err("/keymap", "must be an object"); return null }
            if (e.size > 8) err("/keymap", "must have at most 8 languages")
            val out = linkedMapOf<String, Map<Layer, Map<String, String>>>()
            for ((lang, layersElement) in e) {
                val lp = "/keymap/${escapePointer(lang)}"
                if (!LANGUAGE.matches(lang)) err(lp, "\"$lang\" is not a language code")
                val layers = obj(layersElement, lp, Layer.entries.map { it.json }.toSet()) ?: continue
                if (layers.isEmpty()) err(lp, "must have at least one layer")
                val parsed = linkedMapOf<Layer, Map<String, String>>()
                for (layer in Layer.entries) {
                    val table = layers[layer.json] ?: continue
                    val tp = "$lp/${layer.json}"
                    if (table !is JsonObject) { err(tp, "must be an object"); continue }
                    if (table.size > 128) err(tp, "must have at most 128 keys")
                    val entries = linkedMapOf<String, String>()
                    for ((key, value) in table) {
                        val kp = "$tp/${escapePointer(key)}"
                        if (!KEYCODE.matches(key)) { err(kp, "\"$key\" does not match ${KEYCODE.pattern}"); continue }
                        if (KeyCodes.valueOf(key) == null) err(kp, "\"$key\" is not an Android keycode (API 35)")
                        if (value !is JsonPrimitive || !value.isString) { err(kp, "must be a string"); continue }
                        val t = value.content
                        val len = t.codePointCount(0, t.length)
                        if (len < 1 || len > 8) err(kp, "must be 1 to 8 characters")
                        checkTypedText(t, kp)
                        entries[key] = t
                    }
                    parsed[layer] = entries
                }
                out[lang] = parsed
            }
            return out
        }

        fun checkTypedText(text: String, path: String) {
            if (text.any { it in BIDI_CONTROLS }) err(path, "contains a bidirectional control character")
        }
    }
}
