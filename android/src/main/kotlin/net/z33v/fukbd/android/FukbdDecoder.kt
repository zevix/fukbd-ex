package net.z33v.fukbd.android

import android.util.Log
import android.view.KeyEvent
import net.z33v.fukbd.core.profile.Action
import net.z33v.fukbd.core.profile.Direction
import net.z33v.fukbd.core.route.Decision
import org.futo.inputmethod.event.Event
import org.futo.inputmethod.event.HardwareEventDecoder
import org.futo.inputmethod.event.HardwareKeyHooks
import org.futo.inputmethod.latin.common.Constants

/**
 * FUTO's hardware decoder for one input device, backed by the shared [FukbdRuntime.router].
 * FUTO caches one decoder per device id; this class holds no state of its own, so the cache is
 * harmless (design.md Interface).
 */
class FukbdDecoder(private val deviceId: Int) : HardwareEventDecoder {

    override fun decodeHardwareKey(keyEvent: KeyEvent): Event {
        val decision = synchronized(FukbdRuntime) {
            FukbdRuntime.router.decide(FukbdRuntime.keyInput(keyEvent), FukbdRuntime.deviceInfo(deviceId))
        }
        return when (decision) {
            is Decision.PassThrough -> Event.createNotHandledEvent()
            is Decision.Ambiguous -> {
                Log.i("fukbd", "device $deviceId matches ${decision.profileIds} equally; passing keys through until assigned")
                Event.createNotHandledEvent()
            }
            is Decision.Consume -> toEvent(decision.actions.firstOrNull(), keyEvent)
        }
    }

    override fun consumesKeyUp(keyEvent: KeyEvent): Boolean {
        val decision = synchronized(FukbdRuntime) {
            FukbdRuntime.router.decide(FukbdRuntime.keyInput(keyEvent), FukbdRuntime.deviceInfo(deviceId))
        }
        return decision is Decision.Consume
    }

    /** One router action as one FUTO event. A consumed key with no FUTO-side effect is a no-op. */
    private fun toEvent(action: Action?, e: KeyEvent): Event {
        val repeat = e.repeatCount > 0
        return when (action) {
            is Action.TypeText -> {
                val cps = action.text.codePoints().toArray()
                // One code point goes through onCodeInput, so it joins the composing word and the
                // suggestions update, like FUTO's own soft keys. Longer text commits as a string.
                if (cps.size == 1) Event.createHardwareKeypressEvent(cps[0], e.keyCode, null, repeat)
                else Event.createSoftwareTextEvent(action.text, e.keyCode)
            }
            Action.Space -> Event.createHardwareKeypressEvent(Constants.CODE_SPACE, e.keyCode, null, repeat)
            Action.Enter -> Event.createHardwareKeypressEvent(Constants.CODE_ENTER, e.keyCode, null, repeat)
            Action.Backspace -> Event.createHardwareKeypressEvent(Event.NOT_A_CODE_POINT, Constants.CODE_DELETE, null, repeat)
            is Action.MoveCursor -> Event.createDownUpKeyEvent(
                if (action.direction == Direction.LEFT) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT, 0,
            )
            // Language and symbol mode live in the router's per-device state (already applied).
            // Voice, hide and TV focus are carried out by later milestones (M1, M3).
            Action.LanguageToggle, Action.SymbolsToggle, Action.VoiceToggle, Action.KeyboardHide,
            is Action.FocusMove, Action.FocusActivate, null -> HardwareKeyHooks.consumedNoOp()
        }
    }
}
