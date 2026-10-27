package net.z33v.fukbd.core.route

import net.z33v.fukbd.core.profile.Action
import net.z33v.fukbd.core.profile.Product
import net.z33v.fukbd.core.profile.Source
import net.z33v.fukbd.core.route.RouteFixtures.A
import net.z33v.fukbd.core.route.RouteFixtures.device
import net.z33v.fukbd.core.route.RouteFixtures.down
import net.z33v.fukbd.core.route.RouteFixtures.profile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** R4: one profile per event, by session assignment, persisted assignment, specificity, then priority. */
class RoutingTest {
    private val generic = profile("generic")
    private val byName = profile("by-name", input = """{"source": "keyboard", "name": "Keyboard 1"}""")
    private val byVidPid = profile("by-vidpid", input = """{"source": "keyboard", "vendor_id": 1, "product_id": 2}""")
    private val byDescriptor = profile("by-descriptor", input = """{"source": "keyboard", "descriptor": "abc"}""")

    private fun chosen(router: Router, dev: DeviceInfo): String {
        // Put the device in Hebrew so the keymap fires, then read which profile consumed the key.
        val d = router.decide(down(dev.deviceId, A), dev)
        return assertIs<Decision.Consume>(d, d.toString()).profileId
    }

    private fun langHe(vararg profiles: net.z33v.fukbd.core.profile.Profile) =
        profiles.map { it.copy(defaultLanguage = "he") }

    @Test
    fun moreSpecificProfileWins() {
        val dev = device(1, descriptor = "abc", vendorId = 1, productId = 2)
        val all = langHe(generic, byName, byVidPid, byDescriptor)
        assertEquals("by-descriptor", chosen(Router(all, Product.ANDROID_PHYSICAL), dev))
        assertEquals("by-vidpid", chosen(Router(all - all[3], Product.ANDROID_PHYSICAL), dev))
        assertEquals("by-name", chosen(Router(listOf(all[0], all[1]), Product.ANDROID_PHYSICAL), dev))
        assertEquals("generic", chosen(Router(listOf(all[0]), Product.ANDROID_PHYSICAL), dev))
    }

    @Test
    fun priorityBreaksEqualSpecificity() {
        val low = profile("low", priority = -5).copy(defaultLanguage = "he")
        val high = profile("high", priority = 5).copy(defaultLanguage = "he")
        assertEquals("high", chosen(Router(listOf(low, high), Product.ANDROID_PHYSICAL), device(1)))
    }

    @Test
    fun sessionAssignmentBeatsSpecificityAndPersistedAssignment() {
        val all = langHe(generic, byDescriptor)
        val dev = device(1, descriptor = "abc")
        val router = Router(all, Product.ANDROID_PHYSICAL, persistedAssignments = mapOf("abc" to "by-descriptor"))
        assertEquals("by-descriptor", chosen(router, dev))
        router.assign(1, "generic")
        assertEquals("generic", chosen(router, dev))
    }

    @Test
    fun persistedAssignmentBeatsSpecificity() {
        val router = Router(langHe(generic, byDescriptor), Product.ANDROID_PHYSICAL, mapOf("abc" to "generic"))
        assertEquals("generic", chosen(router, device(1, descriptor = "abc")))
    }

    @Test
    fun aPredicateOnAMissingValueDoesNotMatch() {
        val router = Router(langHe(byDescriptor), Product.ANDROID_PHYSICAL)
        assertIs<Decision.PassThrough>(router.decide(down(1, A), device(1, descriptor = null)))
        assertIs<Decision.PassThrough>(router.decide(down(2, A), device(2, descriptor = "")))
    }

    @Test
    fun hostOnlyProfileNeverMatchesButHostRefinesAnInputMatch() {
        val hostOnly = profile("host-only", host = """{"model": "Titan 2"}""").copy(defaultLanguage = "he")
        assertIs<Decision.PassThrough>(Router(listOf(hostOnly), Product.ANDROID_PHYSICAL).decide(down(1, A), device(1)))
        val titan = profile("titan", host = """{"manufacturer": " UNIHERTZ ", "model": "titan 2"}""",
            input = """{"source": "keyboard", "is_external": false}""").copy(defaultLanguage = "he")
        assertEquals("titan", chosen(Router(listOf(titan), Product.ANDROID_PHYSICAL), device(1, isExternal = false)))
        assertIs<Decision.PassThrough>(Router(listOf(titan), Product.ANDROID_PHYSICAL).decide(down(1, A), device(1, isExternal = false, hostModel = "Pixel 9")))
    }

    @Test
    fun genericSourceOnlyProfileMatchesItsSourceOnly() {
        val remote = profile("remote", input = """{"source": "dpad"}""", products = """["android_tv"]""", keymap = null,
            bindings = """[{"id": "ok", "trigger": {"key_code": "KEYCODE_DPAD_CENTER", "phase": "down"}, "action": {"type": "ui.focus.activate"}, "consume": true}]""")
        val router = Router(listOf(remote), Product.ANDROID_TV)
        val d = router.decide(down(5, 23), device(5, sources = setOf(Source.DPAD, Source.KEYBOARD)))
        assertEquals(listOf(Action.FocusActivate), assertIs<Decision.Consume>(d).actions)
        assertIs<Decision.PassThrough>(router.decide(down(6, 23), device(6, sources = setOf(Source.KEYBOARD))))
    }

    @Test
    fun profilesForTheOtherProductAreIgnored() {
        val tvOnly = profile("tv-only", products = """["android_tv"]""").copy(defaultLanguage = "he")
        assertIs<Decision.PassThrough>(Router(listOf(tvOnly), Product.ANDROID_PHYSICAL).decide(down(1, A), device(1)))
    }
}
