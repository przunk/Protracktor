// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.saveable.Saver
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * The full-screen places over the playlist, **in the order one hides another**: the scanner over
 * Settings over Browse over Random. Several can be open at once -- Browse opened from Random, say --
 * and the first open one in this order is what shows; closing it shows the next. That was six
 * separate flags and an `if` chain in `ProtracktorApp`; it is one state now (redesign E0,
 * `docs/UI_REDESIGN.md`), with the same rules, so the bottom navigation has one thing to change.
 */
enum class ShellScreen { SCANNER, SETTINGS, BROWSE, RANDOM }

/**
 * Where the shell is: which places are open, and whether the queue or Now Playing covers them.
 *
 * Pure and immutable, so a JVM test holds the precedence and Back's order.
 */
@Immutable
data class ShellState(
    val open: Set<ShellScreen> = emptySet(),
    /** The queue panel over whatever is shown. Not kept across a rebuild: it reopens on a tap. */
    val queue: Boolean = false,
    /** Now Playing's sheet. */
    val nowPlaying: Boolean = false,
) {
    /** What shows, or null for the playlist. */
    val shown: ShellScreen? get() = ShellScreen.entries.firstOrNull { it in open }

    fun isOpen(screen: ShellScreen) = screen in open

    fun with(screen: ShellScreen, on: Boolean) = copy(open = if (on) open + screen else open - screen)

    /** Whether anything at all covers the playlist. */
    val covered: Boolean get() = queue || nowPlaying || open.isNotEmpty()

    /**
     * What Back does to the places, of the shown one: the scanner, Settings and Random close
     * (Random back to Browse's start), Browse steps up its own levels first. The queue and Now
     * Playing are layers with Back handlers of their own, registered so that they come first.
     */
    fun back(): ShellBack = when (shown) {
        ShellScreen.SCANNER -> ShellBack.Close(ShellScreen.SCANNER)
        ShellScreen.SETTINGS -> ShellBack.Close(ShellScreen.SETTINGS)
        ShellScreen.BROWSE -> ShellBack.BrowseUp
        ShellScreen.RANDOM -> ShellBack.RandomToBrowse
        null -> ShellBack.Nothing
    }

    companion object {
        /** Kept across a rebuild (language, theme, rotation), all but the queue. */
        val Saver: Saver<ShellState, List<String>> = Saver(
            save = { state -> state.open.map { it.name } + listOfNotNull("nowPlaying".takeIf { state.nowPlaying }) },
            restore = { saved ->
                ShellState(
                    open = saved.mapNotNull { name -> ShellScreen.entries.firstOrNull { it.name == name } }.toSet(),
                    nowPlaying = "nowPlaying" in saved,
                )
            },
        )
    }
}

/** What Back does to the shell's places; see [ShellState.back]. */
sealed interface ShellBack {
    data class Close(val screen: ShellScreen) : ShellBack
    /** Browse goes up a level, and closes from its top. */
    data object BrowseUp : ShellBack
    /** Random closes onto Browse. */
    data object RandomToBrowse : ShellBack
    data object Nothing : ShellBack
}

/**
 * A view of one place's openness as a plain `var`, so the shell's call sites read as before while
 * the state underneath is one [ShellState].
 */
internal fun MutableState<ShellState>.screen(screen: ShellScreen) = object : ReadWriteProperty<Any?, Boolean> {
    override fun getValue(thisRef: Any?, property: KProperty<*>) = value.isOpen(screen)
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
        this@screen.value = this@screen.value.with(screen, value)
    }
}

internal fun MutableState<ShellState>.layer(get: (ShellState) -> Boolean, set: ShellState.(Boolean) -> ShellState) =
    object : ReadWriteProperty<Any?, Boolean> {
        override fun getValue(thisRef: Any?, property: KProperty<*>) = get(value)
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
            this@layer.value = this@layer.value.set(value)
        }
    }
