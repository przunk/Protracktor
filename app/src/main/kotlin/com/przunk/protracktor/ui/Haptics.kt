// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * The three buzzes this app is allowed to make.
 *
 * Haptics on every tap is noise, and noise is what makes people turn the setting off system-wide —
 * at which point the app loses the few that would have been useful. So there are three, and each is
 * for a gesture the **screen does not already answer**: picking a row up, putting it down, and a row
 * crossing a position while your thumb is over it. Nothing with a visible result gets one.
 *
 * Compose's own `LocalHapticFeedback` offers only `LongPress` and `TextHandleMove`, which is too
 * thin for this. The useful constants live on the View and are gated by API level, so each call
 * degrades: the right effect where it exists, an older one where it does not.
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) HapticFeedbackConstants.SEGMENT_TICK
        else HapticFeedbackConstants.CLOCK_TICK
    )

    private fun perform(constant: Int) {
        view?.performHapticFeedback(constant)
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}
