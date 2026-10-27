package net.z33v.fukbd.core.route

import net.z33v.fukbd.core.profile.Action
import net.z33v.fukbd.core.profile.Binding
import net.z33v.fukbd.core.profile.Mode
import net.z33v.fukbd.core.profile.Phase
import net.z33v.fukbd.core.profile.Product
import net.z33v.fukbd.core.profile.Profile
import net.z33v.fukbd.core.profile.Repeat

sealed interface Decision {
    /** Android delivers the event to the app. Non-consuming bindings may still carry [actions]. */
    data class PassThrough(val reason: String, val actions: List<Action> = emptyList()) : Decision
    /** The event is swallowed; [actions] (possibly none) are carried out instead. */
    data class Consume(val actions: List<Action>, val profileId: String) : Decision
    /** Several profiles tie for this device: nothing is applied and the event passes through (U4). */
    data class Ambiguous(val profileIds: List<String>) : Decision
}

/**
 * Chooses at most one profile per device and decides each key event (R4–R7, U3–U5).
 *
 * Profile choice, per key-down: session assignment, then persisted assignment (only when its key
 * is unique among the devices seen), then the specificity score, then `priority`; a remaining tie
 * is [Decision.Ambiguous]. Bindings are examined in order, explicit ones first, and the first match
 * wins. A key-up or a repeat is never routed: it follows what its key-down decided.
 */
class Router(
    profiles: List<Profile>,
    private val product: Product,
    persistedAssignments: Map<String, String> = emptyMap(),
) {
    private var profiles: Map<String, Profile> = emptyMap()
    private var expanded: Map<String, List<Binding>> = emptyMap()
    private var persisted: Map<String, String> = persistedAssignments
    private val session = hashMapOf<Int, String>()
    private val seen = hashMapOf<Int, DeviceInfo>()
    val states = DeviceStates()

    init { setProfiles(profiles) }

    /** Replaces the profile set. Held keys keep their recorded decisions (design failure scenario 3). */
    fun setProfiles(newProfiles: List<Profile>) {
        profiles = newProfiles.filter { product in it.products }.associateBy { it.id }
        expanded = profiles.mapValues { (_, p) -> KeymapExpander.expand(p) }
    }

    fun setPersistedAssignments(assignments: Map<String, String>) { persisted = assignments }

    /** A manual choice for this connected device, until it disconnects. */
    fun assign(deviceId: Int, profileId: String) { session[deviceId] = profileId }

    fun onDeviceRemoved(deviceId: Int) {
        states.onDeviceRemoved(deviceId)
        session.remove(deviceId)
        seen.remove(deviceId)
    }

    fun decide(ev: KeyInput, dev: DeviceInfo): Decision {
        seen[dev.deviceId] = dev
        val key = HeldKey(ev.keyCode, ev.scanCode)
        val existing = states[dev.deviceId]

        if (!ev.down) {
            val held = existing?.held?.remove(key) ?: return Decision.PassThrough("key-up of a key we did not take")
            applyState(held.upActions, existing)
            return if (held.consume) Decision.Consume(held.upActions, existing.profileId ?: "")
            else Decision.PassThrough("key-up of a passed-through key", held.upActions)
        }
        if (ev.repeatCount > 0) {
            val held = existing?.held?.get(key) ?: return Decision.PassThrough("repeat of a key we did not take")
            applyState(held.repeatActions, existing)
            return if (held.consume) Decision.Consume(held.repeatActions, existing.profileId ?: "")
            else Decision.PassThrough("repeat of a passed-through key", held.repeatActions)
        }

        val profile = when (val choice = choose(dev)) {
            is Choice.None -> return Decision.PassThrough(choice.reason)
            is Choice.Tie -> return Decision.Ambiguous(choice.ids)
            is Choice.One -> choice.profile
        }
        val state = states.getOrCreate(dev.deviceId) { DeviceState(profile.id, profile.defaultLanguage) }
        if (state.profileId != profile.id) {
            state.profileId = profile.id
            if (state.language !in profile.languages) state.language = profile.defaultLanguage
            state.mode = Mode.NORMAL
        }
        val binding = expanded.getValue(profile.id).firstOrNull { matches(it, ev, state) }
            ?: return Decision.PassThrough("no binding in ${profile.id}")

        val actions = listOf(binding.action)
        val repeatActions = if (binding.trigger.phase == Phase.DOWN && binding.trigger.repeat == Repeat.ALLOW) actions else emptyList()
        val (now, onUp) = if (binding.trigger.phase == Phase.DOWN) actions to emptyList() else emptyList<Action>() to actions
        state.held[key] = HeldDecision(binding.consume, repeatActions, onUp)
        if (binding.trigger.phase == Phase.DOWN) apply(now, state, profile)
        return if (binding.consume) Decision.Consume(now, profile.id) else Decision.PassThrough("binding ${binding.id} does not consume", now)
    }

    /** Applies the actions whose effect is per-device state (language, symbols); the adapter carries out the rest. */
    private fun apply(actions: List<Action>, state: DeviceState, profile: Profile) {
        for (a in actions) when (a) {
            Action.LanguageToggle -> {
                val i = profile.languages.indexOf(state.language)
                state.language = profile.languages[(i + 1) % profile.languages.size]
            }
            Action.SymbolsToggle -> state.mode = if (state.mode == Mode.NORMAL) Mode.SYMBOLS else Mode.NORMAL
            else -> Unit
        }
    }

    private fun applyState(actions: List<Action>, state: DeviceState) {
        if (actions.isEmpty()) return
        val profile = profiles[state.profileId] ?: return
        apply(actions, state, profile)
    }

    private fun matches(b: Binding, ev: KeyInput, state: DeviceState): Boolean {
        val t = b.trigger
        if (t.keyCode != null && t.keyCode != ev.keyCode) return false
        if (t.scanCode != null && t.scanCode != ev.scanCode) return false
        t.modifiers?.let { m ->
            if (!ev.modifiers.containsAll(m.all)) return false
            if (ev.modifiers.any { it in m.none }) return false
        }
        b.condition?.let { c ->
            if (c.language != null && c.language != state.language) return false
            if (c.mode != null && c.mode != state.mode) return false
        }
        return true
    }

    private sealed interface Choice {
        data class None(val reason: String) : Choice
        data class One(val profile: Profile) : Choice
        data class Tie(val ids: List<String>) : Choice
    }

    private fun choose(dev: DeviceInfo): Choice {
        session[dev.deviceId]?.let { id -> profiles[id]?.let { return Choice.One(it) } }
        dev.assignmentKey?.let { k ->
            persisted[k]?.let { id ->
                val shared = seen.values.count { it.assignmentKey == k } > 1
                if (shared) return Choice.Tie(listOf(id))
                profiles[id]?.let { return Choice.One(it) }
            }
        }
        val scored = profiles.values.mapNotNull { p -> Specificity.score(p, dev)?.let { p to it } }
        if (scored.isEmpty()) return Choice.None("no profile matches this device")
        val best = scored.maxOf { it.second }
        val top = scored.filter { it.second == best }
        val topPriority = top.maxOf { it.first.priority }
        val winners = top.filter { it.first.priority == topPriority }.map { it.first }
        return if (winners.size == 1) Choice.One(winners.single()) else Choice.Tie(winners.map { it.id }.sorted())
    }
}

/** design.md *Specificity*: points per matched predicate; null when the profile does not match. */
object Specificity {
    fun score(p: Profile, dev: DeviceInfo): Int? {
        val input = p.deviceMatch.input
        if (input.source !in dev.sources) return null
        var score = 0
        var inputPredicates = 0
        fun pred(wanted: Any?, actual: Any?, points: Int): Boolean {
            if (wanted == null) return true
            inputPredicates++
            if (actual == null || actual == "" || wanted != actual) return false
            score += points
            return true
        }
        if (!pred(input.descriptor, dev.descriptor, 16)) return null
        if (!pred(input.vendorId, dev.vendorId, 4)) return null
        if (!pred(input.productId, dev.productId, 4)) return null
        if (!pred(input.name, dev.name, 4)) return null
        if (!pred(input.isExternal, dev.isExternal, 1)) return null
        p.deviceMatch.host?.let { h ->
            // A profile that identifies only the host cannot tell connected devices apart.
            if (inputPredicates == 0) return null
            if (h.model != null) { if (norm(h.model) != norm(dev.hostModel)) return null else score += 2 }
            if (h.manufacturer != null) { if (norm(h.manufacturer) != norm(dev.hostManufacturer)) return null else score += 1 }
        }
        return score
    }

    private fun norm(s: String?) = s?.trim()?.lowercase()
}
