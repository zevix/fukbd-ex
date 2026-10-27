package net.z33v.fukbd.core.route

import net.z33v.fukbd.core.profile.Action
import net.z33v.fukbd.core.profile.Mode
import net.z33v.fukbd.core.profile.Modifier
import net.z33v.fukbd.core.profile.Product
import net.z33v.fukbd.core.route.RouteFixtures.A
import net.z33v.fukbd.core.route.RouteFixtures.SPACE
import net.z33v.fukbd.core.route.RouteFixtures.SYM
import net.z33v.fukbd.core.route.RouteFixtures.TOGGLES
import net.z33v.fukbd.core.route.RouteFixtures.device
import net.z33v.fukbd.core.route.RouteFixtures.down
import net.z33v.fukbd.core.route.RouteFixtures.profile
import net.z33v.fukbd.core.route.RouteFixtures.up
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** R5: language, modifier and symbol state per device, reset when the device disconnects. */
class DeviceStateTest {
    private val p = profile("kbd", bindings = TOGGLES,
        keymap = """{"he": {"normal": {"KEYCODE_A": "ש"}, "symbols": {"KEYCODE_A": "!"}}, "en": {"symbols": {"KEYCODE_A": "@"}}}""")
    private val one = device(1)
    private val two = device(2)

    private fun typeA(r: Router, dev: DeviceInfo): Decision {
        val d = r.decide(down(dev.deviceId, A), dev); r.decide(up(dev.deviceId, A), dev); return d
    }

    private fun toggleLanguage(r: Router, dev: DeviceInfo) {
        r.decide(down(dev.deviceId, SPACE, Modifier.ALT), dev); r.decide(up(dev.deviceId, SPACE, Modifier.ALT), dev)
    }

    @Test
    fun eachDeviceKeepsItsOwnLanguage() {
        val r = Router(listOf(p), Product.ANDROID_PHYSICAL)
        toggleLanguage(r, one) // device 1 → he
        assertEquals(listOf(Action.TypeText("ש")), assertIs<Decision.Consume>(typeA(r, one)).actions)
        assertIs<Decision.PassThrough>(typeA(r, two)) // device 2 still en: plain 'a' goes to the app
        assertEquals("he", r.states[1]!!.language)
        assertEquals("en", r.states[2]!!.language)
    }

    @Test
    fun eachDeviceKeepsItsOwnSymbolMode() {
        val r = Router(listOf(p), Product.ANDROID_PHYSICAL)
        r.decide(down(1, SYM), one); r.decide(up(1, SYM), one)
        assertEquals(Mode.SYMBOLS, r.states[1]!!.mode)
        assertEquals(listOf(Action.TypeText("@")), assertIs<Decision.Consume>(typeA(r, one)).actions)
        assertIs<Decision.PassThrough>(typeA(r, two))
        assertEquals(Mode.NORMAL, r.states[2]!!.mode)
    }

    @Test
    fun modifiersComeFromEachEventNotFromAnotherDevice() {
        val r = Router(listOf(p), Product.ANDROID_PHYSICAL)
        toggleLanguage(r, one); toggleLanguage(r, two)
        // Shift held on device 1 does not change what device 2 types: modifiers are per event.
        r.decide(down(1, 59, Modifier.SHIFT), one)
        assertEquals(listOf(Action.TypeText("ש")), assertIs<Decision.Consume>(typeA(r, two)).actions)
    }

    @Test
    fun disconnectResetsTheDeviceState() {
        val r = Router(listOf(p), Product.ANDROID_PHYSICAL)
        toggleLanguage(r, one)
        r.decide(down(1, SYM), one); r.decide(up(1, SYM), one)
        r.onDeviceRemoved(1)
        assertNull(r.states[1])
        // The same keyboard reconnecting gets a new runtime id and starts from the profile defaults.
        val again = device(7, name = one.name)
        assertIs<Decision.PassThrough>(typeA(r, again))
        assertEquals("en", r.states[7]!!.language)
        assertEquals(Mode.NORMAL, r.states[7]!!.mode)
    }
}
