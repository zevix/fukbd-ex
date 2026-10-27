package net.z33v.fukbd.android

import android.app.Activity
import android.os.Bundle
import android.os.SystemClock
import android.text.InputType
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.inputmethod.BaseInputConnection
import android.widget.EditText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import net.z33v.fukbd.core.profile.ImportResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * R9: a mapped physical key reaches the editor through FUTO's input logic (composing word),
 * not through a direct commit. U3 on the device: an unmapped key still reaches the app.
 */
@RunWith(AndroidJUnit4::class)
class FutoHookTest {
    class EditorActivity : Activity() {
        lateinit var editor: EditText
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            editor = EditText(this).also { setContentView(it) }
        }
    }

    @get:Rule
    val rule = ActivityTestRule(EditorActivity::class.java)

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val ime get() = "${instrumentation.targetContext.packageName}/org.futo.inputmethod.latin.LatinIME"

    private fun shell(cmd: String): String =
        instrumentation.uiAutomation.executeShellCommand(cmd).let { pfd ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().use { it.readText() }
        }

    private val profile = """{"schema_version": "2.1", "id": "test.r9", "name": "R9 test", "products": ["android_physical", "android_tv"],
        "scope": "ime_active", "languages": ["he"], "default_language": "he", "behavior": {"unmatched_key": "pass_through"},
        "device_match": {"input": {"source": "keyboard"}}, "keymap": {"he": {"normal": {"KEYCODE_A": "ש", "KEYCODE_T": "א"}}}}"""

    @Before
    fun setUp() {
        assertTrue(FukbdRuntime.store.import(profile) is ImportResult.Imported)
        FukbdRuntime.reload()
        shell("ime enable $ime"); shell("ime set $ime")
    }

    @After
    fun tearDown() {
        FukbdRuntime.store.reset("test.r9"); FukbdRuntime.reload()
        shell("ime reset")
    }

    private fun type(code: Int) {
        val now = SystemClock.uptimeMillis()
        for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            instrumentation.sendKeySync(KeyEvent(now, now, action, code, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD))
        }
        instrumentation.waitForIdleSync()
    }

    private fun focus(inputType: Int) = rule.runOnUiThread {
        rule.activity.editor.inputType = inputType
        rule.activity.editor.setText("")
        rule.activity.editor.requestFocus()
    }

    @Test
    fun mappedKeyEntersFutoComposingWord() {
        focus(InputType.TYPE_CLASS_TEXT)
        type(KeyEvent.KEYCODE_A); type(KeyEvent.KEYCODE_T)
        val editable = rule.activity.editor.text
        assertEquals("שא", editable.toString())
        assertTrue("letters must be FUTO's composing word, not a direct commit", BaseInputConnection.getComposingSpanStart(editable) >= 0)
    }

    @Test
    fun unmappedKeyReachesTheApp() {
        focus(InputType.TYPE_CLASS_TEXT)
        type(KeyEvent.KEYCODE_D) // no binding: Android's own key handling types 'd'
        assertEquals("d", rule.activity.editor.text.toString())
    }

    @Test
    fun passwordFieldGetsTheLetterWithoutComposing() {
        focus(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        type(KeyEvent.KEYCODE_A)
        val editable = rule.activity.editor.text
        assertEquals("ש", editable.toString())
        assertEquals("FUTO does not compose (or learn) in password fields", -1, BaseInputConnection.getComposingSpanStart(editable))
    }
}
