package net.z33v.fukbd.core.route

import net.z33v.fukbd.core.profile.Action
import net.z33v.fukbd.core.profile.Binding
import net.z33v.fukbd.core.profile.Condition
import net.z33v.fukbd.core.profile.KeyCodes
import net.z33v.fukbd.core.profile.Layer
import net.z33v.fukbd.core.profile.Mode
import net.z33v.fukbd.core.profile.Modifier
import net.z33v.fukbd.core.profile.Modifiers
import net.z33v.fukbd.core.profile.Phase
import net.z33v.fukbd.core.profile.Profile
import net.z33v.fukbd.core.profile.Repeat
import net.z33v.fukbd.core.profile.Trigger

/**
 * Turns a profile's compact `keymap` into ordinary bindings, appended after the explicit ones so
 * the router sees one form only (R7). Expanded bindings never fire with Ctrl, Alt, Meta or Sym
 * held, so shortcuts reach the app; without a `shift` layer, Shift+key types the `normal` text
 * (Hebrew has no letter case, design.md A-2).
 */
object KeymapExpander {
    private val CHORDS = setOf(Modifier.CTRL, Modifier.ALT, Modifier.META, Modifier.SYM)

    fun expand(profile: Profile): List<Binding> = profile.bindings + expandKeymap(profile)

    fun expandKeymap(profile: Profile): List<Binding> = profile.keymap.flatMap { (language, layers) ->
        val hasShift = Layer.SHIFT in layers
        layers.flatMap { (layer, table) ->
            val (modifiers, mode) = when (layer) {
                Layer.NORMAL -> Modifiers(emptySet(), if (hasShift) CHORDS + Modifier.SHIFT else CHORDS) to Mode.NORMAL
                Layer.SHIFT -> Modifiers(setOf(Modifier.SHIFT), CHORDS) to Mode.NORMAL
                Layer.SYMBOLS -> Modifiers(emptySet(), CHORDS - Modifier.SYM) to Mode.SYMBOLS
            }
            table.map { (keyName, text) ->
                Binding(
                    id = "keymap.$language.${layer.json}.$keyName",
                    trigger = Trigger(
                        keyCode = KeyCodes.valueOf(keyName), keyCodeName = keyName, scanCode = null,
                        phase = Phase.DOWN, repeat = Repeat.ALLOW, modifiers = modifiers,
                    ),
                    condition = Condition(language, mode),
                    action = Action.TypeText(text),
                    consume = true,
                )
            }
        }
    }
}
