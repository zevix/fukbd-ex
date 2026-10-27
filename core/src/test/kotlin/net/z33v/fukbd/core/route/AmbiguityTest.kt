package net.z33v.fukbd.core.route

import net.z33v.fukbd.core.profile.Product
import net.z33v.fukbd.core.route.RouteFixtures.A
import net.z33v.fukbd.core.route.RouteFixtures.device
import net.z33v.fukbd.core.route.RouteFixtures.down
import net.z33v.fukbd.core.route.RouteFixtures.profile
import net.z33v.fukbd.core.route.RouteFixtures.up
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** U4: when two profiles tie for one device, pass through and report it; never apply both. */
class AmbiguityTest {
    private val one = profile("one").copy(defaultLanguage = "he")
    private val two = profile("two", keymap = """{"he": {"normal": {"KEYCODE_A": "Z"}}}""").copy(defaultLanguage = "he")

    @Test
    fun equalScoreAndPriorityIsAmbiguous() {
        val r = Router(listOf(one, two), Product.ANDROID_PHYSICAL)
        assertEquals(listOf("one", "two"), assertIs<Decision.Ambiguous>(r.decide(down(1, A), device(1))).profileIds)
        assertIs<Decision.PassThrough>(r.decide(up(1, A), device(1)), "nothing was consumed, so the key-up passes too")
    }

    @Test
    fun aSessionAssignmentResolvesTheTie() {
        val r = Router(listOf(one, two), Product.ANDROID_PHYSICAL)
        r.assign(1, "two")
        assertEquals("two", assertIs<Decision.Consume>(r.decide(down(1, A), device(1))).profileId)
    }

    @Test
    fun identicalDevicesSharingAPersistedAssignmentAreAmbiguousUntilAssigned() {
        val r = Router(listOf(one, two), Product.ANDROID_PHYSICAL, mapOf("Same kbd|1|2" to "one"))
        val a = device(1, name = "Same kbd", vendorId = 1, productId = 2)
        val b = device(2, name = "Same kbd", vendorId = 1, productId = 2)
        assertEquals("one", assertIs<Decision.Consume>(r.decide(down(1, A), a)).profileId)
        r.decide(up(1, A), a)
        r.decide(down(2, 32), b) // the second, identical keyboard appears
        assertIs<Decision.Ambiguous>(r.decide(down(1, 29, scan = 1), a))
        r.assign(1, "one"); r.assign(2, "two")
        assertEquals("one", assertIs<Decision.Consume>(r.decide(down(1, A, scan = 2), a)).profileId)
        assertEquals("two", assertIs<Decision.Consume>(r.decide(down(2, A), b)).profileId)
    }

    @Test
    fun higherPriorityIsNotATie() {
        val r = Router(listOf(one, two.copy(priority = 1)), Product.ANDROID_PHYSICAL)
        assertEquals("two", assertIs<Decision.Consume>(r.decide(down(1, A), device(1))).profileId)
    }
}
