package net.z33v.fukbd.core.profile

/**
 * One validated device profile, schema 2.1 (fukbd-ex/schema/device-profile-v2.1.schema.json).
 * Instances only come out of [ProfileParser], so every field has passed the schema and the
 * semantic checks.
 */
data class Profile(
    val id: String,
    val name: String,
    val description: String?,
    val products: Set<Product>,
    val languages: List<String>,
    val defaultLanguage: String,
    val priority: Int,
    val deviceMatch: DeviceMatch,
    /** Explicit bindings, in file order. The router examines them before the expanded keymap. */
    val bindings: List<Binding>,
    /** language → layer → keycode name → text. */
    val keymap: Map<String, Map<Layer, Map<String, String>>>,
)

enum class Product(val json: String) { ANDROID_PHYSICAL("android_physical"), ANDROID_TV("android_tv") }

data class DeviceMatch(val host: HostMatch?, val input: InputMatch)

data class HostMatch(val manufacturer: String?, val model: String?)

data class InputMatch(
    val source: Source,
    val name: String?,
    val descriptor: String?,
    val vendorId: Int?,
    val productId: Int?,
    val isExternal: Boolean?,
)

enum class Source(val json: String) { KEYBOARD("keyboard"), DPAD("dpad") }

data class Binding(
    val id: String,
    val trigger: Trigger,
    val condition: Condition?,
    val action: Action,
    val consume: Boolean,
)

data class Trigger(
    /** Android KeyEvent keycode value, resolved from [keyCodeName]. */
    val keyCode: Int?,
    val keyCodeName: String?,
    val scanCode: Int?,
    val phase: Phase,
    /** Absent in the file means [Repeat.FIRST]: an action fires once per press unless the profile allows repeats. */
    val repeat: Repeat,
    /** Null means the binding matches any modifier state. */
    val modifiers: Modifiers?,
)

enum class Phase { DOWN, UP }

enum class Repeat { FIRST, ALLOW }

data class Modifiers(val all: Set<Modifier>, val none: Set<Modifier>)

enum class Modifier { SHIFT, ALT, CTRL, META, SYM }

/** The binding's optional `when` clause, evaluated against the originating device's state. */
data class Condition(val language: String?, val mode: Mode?)

enum class Mode { NORMAL, SYMBOLS }

enum class Layer(val json: String) { NORMAL("normal"), SHIFT("shift"), SYMBOLS("symbols") }

enum class Direction { LEFT, RIGHT, UP, DOWN }

/** The allowlisted actions. Nothing here can run code, open an intent or reach the network. */
sealed interface Action {
    data class TypeText(val text: String) : Action
    data object Space : Action
    data object Backspace : Action
    data object Enter : Action
    data class MoveCursor(val direction: Direction) : Action
    data object LanguageToggle : Action
    data object SymbolsToggle : Action
    data object VoiceToggle : Action
    data object KeyboardHide : Action
    data class FocusMove(val direction: Direction) : Action
    data object FocusActivate : Action
}

/** One reason a profile was refused. [path] is a JSON Pointer into the file ("" is the whole file). */
data class ProfileError(val path: String, val message: String) {
    override fun toString() = if (path.isEmpty()) message else "$path: $message"
}

sealed interface ParseResult {
    data class Ok(val profile: Profile) : ParseResult
    data class Rejected(val errors: List<ProfileError>) : ParseResult
}
