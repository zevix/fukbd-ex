package net.z33v.fukbd.core.route

import net.z33v.fukbd.core.profile.Action
import net.z33v.fukbd.core.profile.Modifier
import net.z33v.fukbd.core.profile.Product
import net.z33v.fukbd.core.route.RouteFixtures.A
import net.z33v.fukbd.core.route.RouteFixtures.SHIFT_LEFT
import net.z33v.fukbd.core.route.RouteFixtures.device
import net.z33v.fukbd.core.route.RouteFixtures.down
import net.z33v.fukbd.core.route.RouteFixtures.profile
import net.z33v.fukbd.core.route.RouteFixtures.up
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** U5: a device that disconnects with a key held leaves no stuck modifier and no orphan key-up. */
class DisconnectTest {
    private val he = profile("kbd").copy(defaultLanguage = "he")

    @Test
    fun heldKeysAreForgottenWithTheDevice() {
        val r = Router(listOf(he), Product.ANDROID_PHYSICAL)
        assertIs<Decision.Consume>(r.decide(down(1, A), device(1)))
        r.onDeviceRemoved(1)
        assertNull(r.states[1])
        assertEquals(setOf(), r.states.deviceIds)
    }

    @Test
    fun aHeldShiftOnTheRemovedDeviceDoesNotStickToAnother() {
        val r = Router(listOf(he), Product.ANDROID_PHYSICAL)
        r.decide(down(1, SHIFT_LEFT, Modifier.SHIFT), device(1))
        r.onDeviceRemoved(1)
        // Device 2's events carry their own (empty) modifier state.
        assertEquals(listOf(Action.TypeText("ש")), assertIs<Decision.Consume>(r.decide(down(2, A), device(2))).actions)
        assertIs<Decision.Consume>(r.decide(up(2, A), device(2)))
    }

    @Test
    fun aLateKeyUpForTheRemovedDeviceIsNotSwallowedAgainstAnotherDevice() {
        val r = Router(listOf(he), Product.ANDROID_PHYSICAL)
        r.decide(down(1, A), device(1))
        r.decide(down(2, A), device(2))
        r.onDeviceRemoved(1)
        assertIs<Decision.Consume>(r.decide(up(2, A), device(2)), "device 2's pairing is untouched")
        assertIs<Decision.PassThrough>(r.decide(up(2, A), device(2)))
    }
}
