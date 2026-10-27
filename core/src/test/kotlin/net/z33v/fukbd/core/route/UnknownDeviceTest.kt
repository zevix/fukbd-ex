package net.z33v.fukbd.core.route

import net.z33v.fukbd.core.profile.Product
import net.z33v.fukbd.core.profile.Source
import net.z33v.fukbd.core.route.RouteFixtures.A
import net.z33v.fukbd.core.route.RouteFixtures.device
import net.z33v.fukbd.core.route.RouteFixtures.down
import net.z33v.fukbd.core.route.RouteFixtures.profile
import net.z33v.fukbd.core.route.RouteFixtures.up
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** U3: a device that matches no profile has every event passed through unchanged. */
class UnknownDeviceTest {
    @Test
    fun noProfilesAtAll() {
        val r = Router(emptyList(), Product.ANDROID_PHYSICAL)
        val d = assertIs<Decision.PassThrough>(r.decide(down(1, A), device(1)))
        assertTrue(d.actions.isEmpty())
        assertIs<Decision.PassThrough>(r.decide(up(1, A), device(1)))
        assertNull(r.states[1], "no state is created for an unmatched device")
    }

    @Test
    fun profilesForOtherDevicesOnly() {
        val named = profile("named", input = """{"source": "keyboard", "name": "Titan keyboard"}""").copy(defaultLanguage = "he")
        val r = Router(listOf(named), Product.ANDROID_PHYSICAL)
        for (key in listOf(A, 62, 67, 66)) { // a, space, backspace, enter
            assertIs<Decision.PassThrough>(r.decide(down(9, key), device(9, name = "USB keyboard")))
            assertIs<Decision.PassThrough>(r.decide(up(9, key), device(9, name = "USB keyboard")))
        }
    }

    @Test
    fun deviceOfAnotherSource() {
        val r = Router(listOf(profile("kbd").copy(defaultLanguage = "he")), Product.ANDROID_PHYSICAL)
        assertIs<Decision.PassThrough>(r.decide(down(3, A), device(3, sources = setOf(Source.DPAD))))
    }
}
