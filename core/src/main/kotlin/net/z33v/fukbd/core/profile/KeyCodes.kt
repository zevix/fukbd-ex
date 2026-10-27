package net.z33v.fukbd.core.profile

/**
 * Android `KeyEvent.KEYCODE_*` names and values for API 35, read from the generated resource
 * `keycodes-api35.txt` (never typed by hand). `KEYCODE_UNKNOWN` is excluded: a binding on it
 * would be meaningless.
 */
object KeyCodes {
    private val byName: Map<String, Int> by lazy {
        val stream = KeyCodes::class.java.getResourceAsStream("/net/z33v/fukbd/core/keycodes-api35.txt")
            ?: error("keycodes-api35.txt missing from the core resources")
        stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.filter { it.isNotBlank() && !it.startsWith("#") }
                .map { it.split('=', limit = 2) }
                .associate { (name, value) -> name to value.toInt() }
        } - "KEYCODE_UNKNOWN"
    }

    private val byValue: Map<Int, String> by lazy { byName.entries.associate { (k, v) -> v to k } }

    fun valueOf(name: String): Int? = byName[name]

    fun nameOf(value: Int): String? = byValue[value]

    val size: Int get() = byName.size
}
