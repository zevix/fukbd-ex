package net.z33v.fukbd.core.route

import net.z33v.fukbd.core.profile.Action
import net.z33v.fukbd.core.profile.Mode
import net.z33v.fukbd.core.profile.Product
import net.z33v.fukbd.core.route.RouteFixtures.A
import net.z33v.fukbd.core.route.RouteFixtures.SYM
import net.z33v.fukbd.core.route.RouteFixtures.T
import net.z33v.fukbd.core.route.RouteFixtures.TOGGLES
import net.z33v.fukbd.core.route.RouteFixtures.device
import net.z33v.fukbd.core.route.RouteFixtures.down
import net.z33v.fukbd.core.route.RouteFixtures.profile
import net.z33v.fukbd.core.route.RouteFixtures.up
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** R6: the key-up of every consumed key-down is consumed; a passed-through down's up passes through. */
class KeyUpPairingTest {
    private val he = profile("kbd", bindings = TOGGLES).copy(defaultLanguage = "he")
    private val dev = device(1)

    @Test
    fun consumedDownMeansConsumedUp() {
        val r = Router(listOf(he), Product.ANDROID_PHYSICAL)
        assertIs<Decision.Consume>(r.decide(down(1, A), dev))
        val upDecision = assertIs<Decision.Consume>(r.decide(up(1, A), dev))
        assertTrue(upDecision.actions.isEmpty(), "the key-up only closes the press")
        assertTrue(r.states[1]!!.held.isEmpty())
    }

    @Test
    fun passedThroughDownMeansPassedThroughUp() {
        val r = Router(listOf(he), Product.ANDROID_PHYSICAL)
        assertIs<Decision.PassThrough>(r.decide(down(1, 32), dev)) // KEYCODE_D: no binding
        assertIs<Decision.PassThrough>(r.decide(up(1, 32), dev))
    }

    @Test
    fun repeatsFollowTheFirstDecision() {
        val r = Router(listOf(he), Product.ANDROID_PHYSICAL)
        r.decide(down(1, A), dev)
        // Keymap bindings allow repeats: holding the key keeps typing.
        assertEquals(listOf(Action.TypeText("ש")), assertIs<Decision.Consume>(r.decide(down(1, A, repeat = 1), dev)).actions)
        assertIs<Decision.Consume>(r.decide(up(1, A), dev))
        // An explicit binding without "repeat" fires once; its repeats are still swallowed.
        r.decide(down(1, RouteFixtures.SPACE, net.z33v.fukbd.core.profile.Modifier.ALT), dev)
        val rep = assertIs<Decision.Consume>(r.decide(down(1, RouteFixtures.SPACE, net.z33v.fukbd.core.profile.Modifier.ALT, repeat = 3), dev))
        assertTrue(rep.actions.isEmpty())
        assertIs<Decision.Consume>(r.decide(up(1, RouteFixtures.SPACE), dev))
    }

    @Test
    fun aRepeatOfAPassedThroughKeyNeverStartsConsuming() {
        val r = Router(listOf(he.copy(defaultLanguage = "en")), Product.ANDROID_PHYSICAL)
        assertIs<Decision.PassThrough>(r.decide(down(1, A), dev)) // English: plain a
        r.states[1]!!.language = "he" // language changes while the key is held
        assertIs<Decision.PassThrough>(r.decide(down(1, A, repeat = 1), dev))
        assertIs<Decision.PassThrough>(r.decide(up(1, A), dev))
    }

    @Test
    fun profileChangeWhileHeldKeepsThePairing() {
        val r = Router(listOf(he), Product.ANDROID_PHYSICAL)
        r.decide(down(1, A), dev)
        r.setProfiles(emptyList()) // e.g. the profile was replaced by an import
        assertIs<Decision.Consume>(r.decide(up(1, A), dev))
    }

    @Test
    fun keysSharingAKeycodeAreTrackedByScanCode() {
        val r = Router(listOf(he), Product.ANDROID_PHYSICAL)
        assertIs<Decision.Consume>(r.decide(down(1, A, scan = 30), dev))
        r.states[1]!!.language = "en"
        assertIs<Decision.PassThrough>(r.decide(down(1, A, scan = 99), dev)) // another physical key, same keycode
        assertIs<Decision.PassThrough>(r.decide(up(1, A, scan = 99), dev))
        assertIs<Decision.Consume>(r.decide(up(1, A, scan = 30), dev))
    }

    @Test
    fun keyUpBindingConsumesItsDownAndActsOnTheUp() {
        val r = Router(listOf(he), Product.ANDROID_PHYSICAL)
        val d = assertIs<Decision.Consume>(r.decide(down(1, SYM), dev))
        assertTrue(d.actions.isEmpty())
        assertEquals(Mode.NORMAL, r.states[1]!!.mode)
        assertEquals(listOf(Action.SymbolsToggle), assertIs<Decision.Consume>(r.decide(up(1, SYM), dev)).actions)
        assertEquals(Mode.SYMBOLS, r.states[1]!!.mode)
    }

    @Test
    fun interleavedKeysPairIndependently() {
        val r = Router(listOf(he), Product.ANDROID_PHYSICAL)
        r.decide(down(1, A), dev); r.decide(down(1, T), dev)
        assertIs<Decision.Consume>(r.decide(up(1, A), dev))
        assertIs<Decision.Consume>(r.decide(up(1, T), dev))
        assertIs<Decision.PassThrough>(r.decide(up(1, T), dev), "a second key-up has nothing to pair with")
    }
}
