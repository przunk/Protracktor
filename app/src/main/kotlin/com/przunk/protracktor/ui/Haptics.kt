// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * The buzzes this app is allowed to make.
 *
 * **The rule used to be "nothing with a visible result gets one", and the owner has changed it**
 * (2026-09-10). He asked for haptics on the transport, on moving between views and folders, and on
 * holding the seek bar — all of which the screen answers perfectly well on its own. That is his
 * call to make: he is the one holding the phone, and the old rule was written from the armchair.
 *
 * What survives of it is the reason behind it. Haptics on every tap is noise, and noise is what
 * makes people turn the setting off system-wide — at which point the app loses the few that would
 * have been useful. So the answer is not "no", it is **weight**: the things that carry information
 * a finger cannot otherwise get keep the firm effects, and the things he asked for as an *accent*
 * get the lightest ones the platform has. A confirmation you can feel and a tick you can barely
 * feel are both haptics; only one of them becomes noise at fifty a minute.
 *
 * Compose's own `LocalHapticFeedback` offers only `LongPress` and `TextHandleMove`, which is too
 * thin for this. The useful constants live on the View and are gated by API level, so each call
 * degrades: the right effect where it exists, an older one where it does not. This app runs from
 * API 29, and the interesting half of these constants arrived in 30 and 34, so the fallbacks are
 * not decoration — on a phone from 2019 they are what actually fires.
 *
 * Android already honours the user's system haptics setting, so there is nothing of ours to check
 * first — adding a preference would be inventing one the platform owns.
 */
@Immutable
class Haptics(private val view: View?) {

    /** A gesture has taken hold — a row picked up. */
    fun gestureStart() = perform(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.GESTURE_START
        else HapticFeedbackConstants.LONG_PRESS
    )

    /** A gesture has let go — a row put down. */
    fun gestureEnd() = perform(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.GESTURE_END
        else HapticFeedbackConstants.CLOCK_TICK
    )

    /**
     * One notch passed.
     *
     * This is the one that earns its place: it says a move happened without looking, which is
     * exactly when you cannot look, because your thumb is over the row.
     */
    fun tick() = perform(
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
                HapticFeedbackConstants.SEGMENT_TICK
            // **CONTEXT_CLICK rather than CLOCK_TICK below 14.** This one says a row *actually
            // changed places* -- a thing that has happened, not a thing that is passing -- and
            // CLOCK_TICK is faint enough on an older phone to be missed under a moving thumb.
            else -> HapticFeedbackConstants.CONTEXT_CLICK
        }
    )

    /**
     * A button that has started something you cannot see yet.
     *
     * The indexing arrows and the pairing camera: both answer a tap with a spinner or a screen that
     * takes a moment to arrive, and in that moment the only thing that can say "yes, I heard you"
     * is the phone.
     */
    fun press() = perform(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
        else HapticFeedbackConstants.KEYBOARD_TAP
    )

    /**
     * An option flipped, and **which way it went**.
     *
     * Android 14 gives on and off two different effects, which is the whole point of putting this
     * in its own method: shuffle going on should not feel identical to shuffle going off. Below 14
     * they collapse into one, and that is simply what those phones can do.
     */
    fun toggle(on: Boolean) = perform(
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
                if (on) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.TOGGLE_OFF
            else -> HapticFeedbackConstants.CONTEXT_CLICK
        }
    )

    /** The seek thumb taken hold of — the start of a drag rather than of a gesture. */
    fun grab() = perform(
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
                HapticFeedbackConstants.DRAG_START
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> HapticFeedbackConstants.GESTURE_START
            else -> HapticFeedbackConstants.LONG_PRESS
        }
    )

    /**
     * One notch while dragging along a continuum.
     *
     * **Deliberately the lightest thing here.** It fires tens of times during one drag of the seek
     * bar, which is the point — *"taki haptic żebym czuł że go trzymam"* — and anything heavier
     * repeated forty times is not texture, it is a phone buzzing in your hand.
     */
    fun scrub() = perform(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
        else HapticFeedbackConstants.CLOCK_TICK
    )

    /**
     * One of several picked, lightly — a subsong out of a strip of them.
     *
     * *"Delikatnie na subsong"* (owner, 2026-09-10). `SEGMENT_TICK` is the constant for a discrete
     * step among many, which is exactly what a subsong is, and it is the lightest of the firm
     * effects rather than the firmest of the light ones.
     */
    fun pick() = perform(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            HapticFeedbackConstants.SEGMENT_TICK
        else HapticFeedbackConstants.CLOCK_TICK
    )

    /**
     * Arriving somewhere else — another view, another folder.
     *
     * The lightest effect that is still a distinct event, because this one fires on every step
     * down a catalogue and every press of Back. If any of these becomes tiresome it will be this,
     * and it is one line to take out.
     */
    fun transition() = perform(HapticFeedbackConstants.CLOCK_TICK)

    private fun perform(constant: Int) {
        view?.performHapticFeedback(constant)
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}

/**
 * Buzz when [key] becomes something else, and never on the way in.
 *
 * **One of these beats a call at every place that navigates.** Moving between views is set from a
 * dozen sites — buttons, Back, a scan finishing, a link arriving — and a call in each is a list
 * that will be one short the next time somebody adds a route. The destination itself is the
 * signal, so watching it catches every way of reaching it, including the ones not written yet.
 *
 * The first composition is not a change. Without that guard the app would buzz at launch, and
 * again every time the screen it is watching is recreated.
 */
@Composable
fun HapticOnChange(key: Any?, effect: Haptics.() -> Unit) {
    val haptics = rememberHaptics()
    val settled = remember { mutableStateOf(false) }
    LaunchedEffect(key) {
        if (!settled.value) {
            settled.value = true
            return@LaunchedEffect
        }
        haptics.effect()
    }
}
