package net.z33v.fukbd.core.profile

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * A strict JSON reader for untrusted profile files. Unlike kotlinx.serialization's reader it
 * refuses duplicate object keys (the last value would otherwise silently win and could hide an
 * unknown key), nesting deeper than [maxDepth], and unpaired UTF-16 surrogates in strings.
 * It produces kotlinx [JsonElement]s so the rest of the core uses one JSON model.
 */
internal class StrictJson private constructor(private val text: String, private val maxDepth: Int) {
    class Failure(val path: String, message: String) : Exception(message)

    private var pos = 0

    companion object {
        fun parse(text: String, maxDepth: Int): JsonElement {
            val reader = StrictJson(text, maxDepth)
            reader.skipWhitespace()
            val value = reader.readValue("", 0)
            reader.skipWhitespace()
            if (reader.pos != text.length) reader.fail("", "unexpected text after the JSON value")
            return value
        }
    }

    private fun fail(path: String, message: String): Nothing =
        throw Failure(path, "$message (at character ${pos + 1})")

    private fun skipWhitespace() {
        while (pos < text.length && text[pos] in " \t\r\n") pos++
    }

    private fun readValue(path: String, depth: Int): JsonElement {
        if (depth > maxDepth) fail(path, "nesting deeper than $maxDepth levels")
        if (pos >= text.length) fail(path, "unexpected end of input")
        return when (val c = text[pos]) {
            '{' -> readObject(path, depth)
            '[' -> readArray(path, depth)
            '"' -> JsonPrimitive(readString(path))
            't' -> readLiteral(path, "true", JsonPrimitive(true))
            'f' -> readLiteral(path, "false", JsonPrimitive(false))
            'n' -> readLiteral(path, "null", JsonNull)
            else -> if (c == '-' || c in '0'..'9') readNumber(path) else fail(path, "unexpected character '$c'")
        }
    }

    private fun readLiteral(path: String, word: String, value: JsonElement): JsonElement {
        if (!text.startsWith(word, pos)) fail(path, "invalid literal")
        pos += word.length
        return value
    }

    private fun readObject(path: String, depth: Int): JsonObject {
        pos++ // '{'
        val map = LinkedHashMap<String, JsonElement>()
        skipWhitespace()
        if (pos < text.length && text[pos] == '}') { pos++; return JsonObject(map) }
        while (true) {
            skipWhitespace()
            if (pos >= text.length || text[pos] != '"') fail(path, "expected a property name")
            val key = readString(path)
            val childPath = "$path/${escapePointer(key)}"
            if (key in map) fail(childPath, "duplicate key \"$key\"")
            skipWhitespace()
            if (pos >= text.length || text[pos] != ':') fail(childPath, "expected ':'")
            pos++
            skipWhitespace()
            map[key] = readValue(childPath, depth + 1)
            skipWhitespace()
            if (pos >= text.length) fail(path, "unexpected end of input in object")
            when (text[pos]) {
                ',' -> pos++
                '}' -> { pos++; return JsonObject(map) }
                else -> fail(path, "expected ',' or '}'")
            }
        }
    }

    private fun readArray(path: String, depth: Int): JsonArray {
        pos++ // '['
        val items = ArrayList<JsonElement>()
        skipWhitespace()
        if (pos < text.length && text[pos] == ']') { pos++; return JsonArray(items) }
        while (true) {
            skipWhitespace()
            items += readValue("$path/${items.size}", depth + 1)
            skipWhitespace()
            if (pos >= text.length) fail(path, "unexpected end of input in array")
            when (text[pos]) {
                ',' -> pos++
                ']' -> { pos++; return JsonArray(items) }
                else -> fail(path, "expected ',' or ']'")
            }
        }
    }

    private fun readString(path: String): String {
        pos++ // opening quote
        val sb = StringBuilder()
        while (true) {
            if (pos >= text.length) fail(path, "unterminated string")
            val c = text[pos++]
            when {
                c == '"' -> break
                c == '\\' -> {
                    if (pos >= text.length) fail(path, "unterminated escape")
                    when (val e = text[pos++]) {
                        '"' -> sb.append('"'); '\\' -> sb.append('\\'); '/' -> sb.append('/')
                        'b' -> sb.append('\b'); 'f' -> sb.append('\u000C'); 'n' -> sb.append('\n')
                        'r' -> sb.append('\r'); 't' -> sb.append('\t')
                        'u' -> {
                            if (pos + 4 > text.length) fail(path, "short \\u escape")
                            val hex = text.substring(pos, pos + 4)
                            val code = hex.toIntOrNull(16) ?: fail(path, "invalid \\u escape")
                            sb.append(code.toChar()); pos += 4
                        }
                        else -> fail(path, "invalid escape '\\$e'")
                    }
                }
                c < ' ' -> fail(path, "control character in string")
                else -> sb.append(c)
            }
        }
        val s = sb.toString()
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            if (Character.isHighSurrogate(ch)) {
                if (i + 1 >= s.length || !Character.isLowSurrogate(s[i + 1])) fail(path, "unpaired surrogate in string")
                i += 2
            } else {
                if (Character.isLowSurrogate(ch)) fail(path, "unpaired surrogate in string")
                i++
            }
        }
        return s
    }

    private fun readNumber(path: String): JsonPrimitive {
        val start = pos
        if (text[pos] == '-') pos++
        while (pos < text.length && (text[pos].isDigit() || text[pos] in ".eE+-")) pos++
        val raw = text.substring(start, pos)
        if (!Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?").matches(raw)) fail(path, "invalid number '$raw'")
        return raw.toLongOrNull()?.let { JsonPrimitive(it) } ?: JsonPrimitive(raw.toDouble())
    }
}

/** RFC 6901 JSON Pointer escaping for one path segment. */
internal fun escapePointer(segment: String): String = segment.replace("~", "~0").replace("/", "~1")
