package net.z33v.fukbd.android

import android.app.Activity
import android.content.Intent
import android.hardware.input.InputManager
import android.os.Build
import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * "fukbd key diagnostics" (R8): shows every key event this screen receives with the metadata of
 * the device that sent it, and exports the session as JSON through the system file picker (no
 * permissions, no network). Profile authors use it to measure real hardware; nothing is guessed.
 */
class DiagnosticsActivity : Activity() {
    private val events = mutableListOf<JsonObject>()
    private lateinit var log: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()
        log = TextView(this).apply {
            setTextIsSelectable(false)
            text = "Press keys on any keyboard or remote. Each event is listed with its device.\n\n" + devicesText()
        }
        val export = Button(this).apply {
            text = "Export JSON"
            isFocusable = false // keys must reach this screen, not the button
            setOnClickListener { startExport() }
        }
        setContentView(ScrollView(this).apply {
            addView(LinearLayout(this@DiagnosticsActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(pad, pad, pad, pad)
                addView(export)
                addView(log)
            })
        })
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Back still leaves the screen; everything else is recorded and swallowed.
        if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
            record(event); finish(); return true
        }
        record(event)
        return true
    }

    fun record(e: KeyEvent): JsonObject {
        val o = eventJson(e)
        events += o
        log.append("\n${o["action"]} ${o["key_name"]} scan=${o["scan_code"]} meta=${o["meta_state"]} repeat=${o["repeat_count"]} device=${o["device"]}")
        return o
    }

    /** The whole session as one JSON document. */
    fun sessionJson(): JsonObject = buildJsonObject {
        put("tool", "fukbd key diagnostics")
        put("host", buildJsonObject {
            put("manufacturer", Build.MANUFACTURER); put("model", Build.MODEL); put("sdk_int", Build.VERSION.SDK_INT)
        })
        put("devices", JsonArray(InputDevice.getDeviceIds().map { deviceJson(it) }))
        put("events", JsonArray(events.toList()))
    }

    private fun startExport() {
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "fukbd-diagnostics-${System.currentTimeMillis()}.json")
        }, REQUEST_EXPORT)
    }

    @Deprecated("Activity result API kept simple: no AndroidX dependency on this screen")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (requestCode != REQUEST_EXPORT || resultCode != RESULT_OK || uri == null) return
        contentResolver.openOutputStream(uri)?.use { it.write(sessionJson().toString().toByteArray(Charsets.UTF_8)) }
        log.append("\n\nExported ${events.size} events.")
    }

    private fun devicesText(): String = InputDevice.getDeviceIds().joinToString("\n") { id ->
        val d = InputDevice.getDevice(id) ?: return@joinToString "#$id (gone)"
        "#$id ${d.name} vendor=${d.vendorId} product=${d.productId} descriptor=${d.descriptor}"
    }

    companion object {
        private const val REQUEST_EXPORT = 1

        fun deviceJson(deviceId: Int): JsonObject {
            val info = FukbdRuntime.deviceInfo(deviceId)
            val d = InputDevice.getDevice(deviceId)
            return buildJsonObject {
                put("device_id", deviceId)
                put("name", info.name)
                put("descriptor", info.descriptor)
                put("vendor_id", info.vendorId)
                put("product_id", info.productId)
                put("is_external", info.isExternal)
                put("sources", JsonArray(info.sources.map { JsonPrimitive(it.json) }))
                put("sources_raw", d?.sources)
                put("keyboard_type", d?.keyboardType)
            }
        }

        fun eventJson(e: KeyEvent): JsonObject = buildJsonObject {
            put("action", when (e.action) { KeyEvent.ACTION_DOWN -> "down"; KeyEvent.ACTION_UP -> "up"; else -> "other" })
            put("key_code", e.keyCode)
            put("key_name", KeyEvent.keyCodeToString(e.keyCode))
            put("scan_code", e.scanCode)
            put("meta_state", e.metaState)
            put("modifiers", JsonArray(FukbdRuntime.modifiers(e.metaState).map { JsonPrimitive(it.name) }))
            put("repeat_count", e.repeatCount)
            put("source", e.source)
            put("event_time_ms", e.eventTime)
            put("device", deviceJson(e.deviceId))
        }
    }
}
