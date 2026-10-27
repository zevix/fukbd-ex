package net.z33v.fukbd.core.route

import net.z33v.fukbd.core.profile.Modifier
import net.z33v.fukbd.core.profile.Source

/**
 * One key event, copied out of Android's KeyEvent by the adapter as plain values. Lock states
 * (Caps, Num, Function) are deliberately not represented: they never take part in matching.
 */
data class KeyInput(
    val deviceId: Int,
    val keyCode: Int,
    val scanCode: Int,
    val down: Boolean,
    val repeatCount: Int,
    val modifiers: Set<Modifier>,
)

/**
 * What Android exposes about the originating InputDevice, plus the host. Any value may be
 * missing or empty on some firmware; a profile predicate on a missing value does not match.
 * [deviceId] is the runtime id: used for routing only, never persisted.
 */
data class DeviceInfo(
    val deviceId: Int,
    val name: String?,
    val descriptor: String?,
    val vendorId: Int?,
    val productId: Int?,
    val isExternal: Boolean?,
    val sources: Set<Source>,
    val hostManufacturer: String?,
    val hostModel: String?,
) {
    /** The persisted-assignment key: the descriptor, or name+vendor+product when there is none. */
    val assignmentKey: String?
        get() = descriptor?.takeIf { it.isNotBlank() }
            ?: name?.takeIf { it.isNotBlank() }?.let { "$it|${vendorId ?: ""}|${productId ?: ""}" }
}
