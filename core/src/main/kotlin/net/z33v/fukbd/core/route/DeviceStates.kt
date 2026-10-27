package net.z33v.fukbd.core.route

import net.z33v.fukbd.core.profile.Action
import net.z33v.fukbd.core.profile.Mode

/** A key held on one device, identified by both codes so two keys sharing a keycode stay apart. */
data class HeldKey(val keyCode: Int, val scanCode: Int)

/**
 * What the router decided when a key went down; the key-up and any repeats follow this decision
 * and are never routed again (R6). [upActions] fire on key-up for `phase: up` bindings.
 */
data class HeldDecision(
    val consume: Boolean,
    val repeatActions: List<Action>,
    val upActions: List<Action>,
)

/** Per-device state (R5). Lives for the process; keyed by the runtime device id, never persisted. */
class DeviceState(var profileId: String?, var language: String, var mode: Mode = Mode.NORMAL) {
    val held = linkedMapOf<HeldKey, HeldDecision>()
}

class DeviceStates {
    private val states = hashMapOf<Int, DeviceState>()

    operator fun get(deviceId: Int): DeviceState? = states[deviceId]

    fun getOrCreate(deviceId: Int, create: () -> DeviceState): DeviceState = states.getOrPut(deviceId, create)

    /** Android sends no key-up for a removed device and does not reuse ids within a boot, so dropping is safe (U5). */
    fun onDeviceRemoved(deviceId: Int) { states.remove(deviceId) }

    val deviceIds: Set<Int> get() = states.keys.toSet()
}
