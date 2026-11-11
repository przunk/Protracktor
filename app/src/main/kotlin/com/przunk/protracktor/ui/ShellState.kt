// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.saveable.Saver
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** The full-screen places over the playlist. */
enum class ShellScreen { SCANNER, SETTINGS, BROWSE, RANDOM }

/** The bottom navigation's tabs (redesign E1, `docs/UI_REDESIGN.md`). */
enum class ShellTab { PLAYLIST, BROWSE, SEARCH, RANDOM, MORE }

/**
 * Where the shell is: **a stack of places**, the last one showing, and the queue and Now Playing
 * over them (redesign E0, a stack since E1).
 *
 * A stack because Back is "the place before": Browse, then Random opened from it, then an author
 * opened from Random, is three places, and Back walks them in reverse -- Random, Browse, the
 * playlist. The six flags this replaced had a fixed order instead, which sent Back from Random to
 * Browse whatever came before it. A tab replaces the whole stack with its own place.
 *
 * [browseEntry] is the Browse domain a tab or More opened Browse at -- Search, History, Saved
 * places -- whose own top is the tab's top, so Back leaves there rather than walking to Browse's
 * start. Null for Browse's start or Browse reached any other way.
 *
 * Pure and immutable, so a JVM test holds the stack and Back's order.
 */
@Immutable
data class ShellState(
    val stack: List<ShellScreen> = emptyList(),
    /** The queue panel over whatever is shown. Not kept across a rebuild: it reopens on a tap. */
    val queue: Boolean = false,
    /** Now Playing's sheet. */
    val nowPlaying: Boolean = false,
    val browseEntry: com.przunk.protracktor.player.BrowseDomain? = null,
) {
    /** What shows, or null for the playlist. */
    val shown: ShellScreen? get() = stack.lastOrNull()

    fun isOpen(screen: ShellScreen) = screen in stack

    /** Opens [screen] over what shows (unless it already shows), or closes its topmost instance. */
    fun with(screen: ShellScreen, on: Boolean): ShellState = if (on) {
        if (shown == screen) this else copy(stack = stack + screen)
    } else {
        val at = stack.lastIndexOf(screen)
        if (at < 0) this
        else copy(stack = stack.filterIndexed { i, _ -> i != at },
            browseEntry = browseEntry.takeIf { screen != ShellScreen.BROWSE || ShellScreen.BROWSE in stack.take(at) })
    }

    /** A tab: the whole stack becomes its one place (or none, for the playlist). */
    fun tab(screen: ShellScreen?, entry: com.przunk.protracktor.player.BrowseDomain? = null) =
        copy(stack = listOfNotNull(screen), queue = false, browseEntry = entry)

    /** Whether anything at all covers the playlist. */
    val covered: Boolean get() = queue || nowPlaying || stack.isNotEmpty()

    /**
     * What Back does to the places, of the shown one: the scanner, Settings and Random close onto
     * the place before; Browse steps up its own levels first.
     */
    fun back(): ShellBack = when (val top = shown) {
        ShellScreen.BROWSE -> ShellBack.BrowseUp
        null -> ShellBack.Nothing
        else -> ShellBack.Close(top)
    }

    /** Which tab is current, for the bar to mark; [domain] is Browse's, which tells Search apart. */
    fun tabOf(domain: com.przunk.protracktor.player.BrowseDomain): ShellTab = when (shown) {
        null -> ShellTab.PLAYLIST
        ShellScreen.RANDOM -> ShellTab.RANDOM
        ShellScreen.SETTINGS, ShellScreen.SCANNER -> ShellTab.MORE
        ShellScreen.BROWSE -> when (domain) {
            com.przunk.protracktor.player.BrowseDomain.SEARCH -> ShellTab.SEARCH
            com.przunk.protracktor.player.BrowseDomain.HISTORY,
            com.przunk.protracktor.player.BrowseDomain.SAVED -> ShellTab.MORE
            else -> ShellTab.BROWSE
        }
    }

    companion object {
        /** Kept across a rebuild (language, theme, rotation), all but the queue. */
        val Saver: Saver<ShellState, List<String>> = Saver(
            save = { state ->
                state.stack.map { it.name } + listOfNotNull("nowPlaying".takeIf { state.nowPlaying },
                    state.browseEntry?.let { "entry:${it.name}" })
            },
            restore = { saved ->
                ShellState(
                    stack = saved.mapNotNull { name -> ShellScreen.entries.firstOrNull { it.name == name } },
                    nowPlaying = "nowPlaying" in saved,
                    browseEntry = saved.firstOrNull { it.startsWith("entry:") }?.removePrefix("entry:")
                        ?.let { name -> com.przunk.protracktor.player.BrowseDomain.entries.firstOrNull { it.name == name } },
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
