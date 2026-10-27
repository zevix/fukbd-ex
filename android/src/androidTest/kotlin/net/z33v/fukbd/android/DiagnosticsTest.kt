package net.z33v.fukbd.android

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** R8: the diagnostics screen records each key event with its device metadata and exports it as JSON. */
@RunWith(AndroidJUnit4::class)
class DiagnosticsTest {
    @get:Rule
    val rule = ActivityTestRule(DiagnosticsActivity::class.java)

    private fun key(action: Int, code: Int, meta: Int = 0) {
        val now = SystemClock.uptimeMillis()
        InstrumentationRegistry.getInstrumentation().sendKeySync(
            KeyEvent(now, now, action, code, 0, meta, KeyCharacterMap.VIRTUAL_KEYBOARD, 30, 0, InputDevice.SOURCE_KEYBOARD),
        )
    }

    @Test
    fun recordsEventsWithDeviceMetadata() {
        key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A, KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON)
        key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_A)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        var session: JsonObject? = null
        rule.runOnUiThread { session = rule.activity.sessionJson() }
        val events = session!!["events"]!!.jsonArray.map { it.jsonObject }
        val down = events.first { it["action"]!!.jsonPrimitive.content == "down" }
        assertEquals("KEYCODE_A", down["key_name"]!!.jsonPrimitive.content)
        assertEquals("30", down["scan_code"]!!.jsonPrimitive.content)
        assertTrue(down["modifiers"]!!.jsonArray.any { it.jsonPrimitive.content == "SHIFT" })
        val device = down["device"]!!.jsonObject
        for (field in listOf("device_id", "name", "descriptor", "vendor_id", "product_id", "is_external", "sources")) {
            assertTrue("device field $field", field in device)
        }
        assertTrue(events.any { it["action"]!!.jsonPrimitive.content == "up" })
        assertTrue(session!!["devices"]!!.jsonArray.isNotEmpty())
    }
}
