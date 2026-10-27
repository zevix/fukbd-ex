package net.z33v.fukbd.android

import android.content.Context
import android.hardware.input.InputManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import net.z33v.fukbd.core.profile.BundledProfile
import net.z33v.fukbd.core.profile.Modifier
import net.z33v.fukbd.core.profile.Product
import net.z33v.fukbd.core.profile.ProfileError
import net.z33v.fukbd.core.profile.ProfileStore
import net.z33v.fukbd.core.profile.Source
import net.z33v.fukbd.core.route.DeviceInfo
import net.z33v.fukbd.core.route.KeyInput
import net.z33v.fukbd.core.route.Router
import org.futo.inputmethod.event.HardwareKeyHooks
import org.futo.inputmethod.latin.BuildConfig
import java.io.File

/**
 * Process-wide fukbd state: the profile store, the router and the input-device listener.
 * Created once by [FukbdInit] before any IME component exists (design.md A-3), so the very first
 * hardware decoder FUTO creates is ours.
 */
object FukbdRuntime {
    private const val TAG = "fukbd"
    const val ASSET_DIR = "fukbd/profiles"

    lateinit var store: ProfileStore
        private set
    lateinit var router: Router
        private set
    @Volatile var loadProblems: Map<String, List<ProfileError>> = emptyMap()
        private set

    val product: Product
        get() = if (BuildConfig.FUKBD_PRODUCT == Product.ANDROID_TV.json) Product.ANDROID_TV else Product.ANDROID_PHYSICAL

    @Synchronized
    fun init(context: Context) {
        if (::router.isInitialized) return
        val app = context.applicationContext
        val bundled = (app.assets.list(ASSET_DIR) ?: emptyArray()).filter { it.endsWith(".json") }.sorted().map { name ->
            BundledProfile(name, app.assets.open("$ASSET_DIR/$name").bufferedReader(Charsets.UTF_8).use { it.readText() })
        }
        store = ProfileStore(File(app.filesDir, "fukbd/profiles"), bundled)
        val report = store.load() // synchronous: profiles are in place before the factory is installed
        loadProblems = report.problems
        report.problems.forEach { (file, errors) -> Log.w(TAG, "profile $file not used: $errors") }
        router = Router(report.profiles, product)

        val inputManager = app.getSystemService(Context.INPUT_SERVICE) as InputManager
        inputManager.registerInputDeviceListener(object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) {}
            override fun onInputDeviceChanged(deviceId: Int) {}
            override fun onInputDeviceRemoved(deviceId: Int) = synchronized(this@FukbdRuntime) { router.onDeviceRemoved(deviceId) }
        }, Handler(Looper.getMainLooper()))

        HardwareKeyHooks.install { deviceId -> FukbdDecoder(deviceId) }
        Log.i(TAG, "installed: ${report.profiles.size} profiles for $product")
    }

    /** Re-reads the store, e.g. after an import; held keys keep their decisions. */
    @Synchronized
    fun reload() {
        val report = store.load()
        loadProblems = report.problems
        router.setProfiles(report.profiles)
    }

    fun deviceInfo(deviceId: Int): DeviceInfo {
        val d: InputDevice? = InputDevice.getDevice(deviceId)
        val sources = mutableSetOf<Source>()
        if (d != null) {
            if (d.supportsSource(InputDevice.SOURCE_KEYBOARD)) sources += Source.KEYBOARD
            if (d.supportsSource(InputDevice.SOURCE_DPAD)) sources += Source.DPAD
        }
        return DeviceInfo(
            deviceId = deviceId,
            name = d?.name,
            descriptor = d?.descriptor,
            vendorId = d?.vendorId,
            productId = d?.productId,
            isExternal = if (d != null && Build.VERSION.SDK_INT >= 29) d.isExternal else null,
            sources = sources,
            hostManufacturer = Build.MANUFACTURER,
            hostModel = Build.MODEL,
        )
    }

    fun keyInput(e: KeyEvent): KeyInput = KeyInput(
        deviceId = e.deviceId,
        keyCode = e.keyCode,
        scanCode = e.scanCode,
        down = e.action == KeyEvent.ACTION_DOWN,
        repeatCount = e.repeatCount,
        modifiers = modifiers(e.metaState),
    )

    /** Lock states (Caps, Num, Function) are deliberately dropped: they never take part in matching. */
    fun modifiers(meta: Int): Set<Modifier> = buildSet {
        if (meta and KeyEvent.META_SHIFT_ON != 0) add(Modifier.SHIFT)
        if (meta and KeyEvent.META_ALT_ON != 0) add(Modifier.ALT)
        if (meta and KeyEvent.META_CTRL_ON != 0) add(Modifier.CTRL)
        if (meta and KeyEvent.META_META_ON != 0) add(Modifier.META)
        if (meta and KeyEvent.META_SYM_ON != 0) add(Modifier.SYM)
    }
}
