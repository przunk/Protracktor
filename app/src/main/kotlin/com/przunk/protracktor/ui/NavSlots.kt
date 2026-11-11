// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
package com.przunk.protracktor.ui

import com.przunk.protracktor.player.BrowseDomain
import com.przunk.protracktor.player.BrowseState
import com.przunk.protracktor.player.SearchScope

/**
 * The navigation bar's two chosen places (redesign round 2, 2026-11-11): the bar is
 * **Playlist · Browse · ○ · ○ · More**, three fixed and two chosen in Settings, Search and Random
 * until chosen otherwise. The two are always different places.
 */
data class NavSlots(val first: ShellTab, val second: ShellTab) {
    init {
        require(first in CHOOSABLE && second in CHOOSABLE && first != second) { "Not two chosen places: $first, $second" }
    }

    operator fun contains(tab: ShellTab): Boolean = tab == first || tab == second

    /** The bar, left to right. */
    val bar: List<ShellTab> get() = listOf(ShellTab.PLAYLIST, ShellTab.BROWSE, first, second, ShellTab.MORE)

    fun at(slot: Int): ShellTab = if (slot == 0) first else second

    /**
     * [place] into [slot]. **The place already in the other slot swaps with it**, rather than being
     * refused or appearing twice: choosing it says where it is wanted, and what was there goes
     * to where it came from.
     */
    fun choose(slot: Int, place: ShellTab): NavSlots {
        require(place in CHOOSABLE)
        val mine = at(slot)
        val other = at(1 - slot)
        val (newMine, newOther) = if (place == other) place to mine else place to other
        return if (slot == 0) NavSlots(newMine, newOther) else NavSlots(newOther, newMine)
    }

    companion object {
        /** What a slot can hold, in the order the chooser lists them. */
        val CHOOSABLE = listOf(
            ShellTab.SEARCH, ShellTab.RANDOM, ShellTab.LOCAL, ShellTab.SAVED,
            ShellTab.ONLINE, ShellTab.HISTORY, ShellTab.ALARMS,
        )
        val DEFAULT = NavSlots(ShellTab.SEARCH, ShellTab.RANDOM)

        /** What this build offers: Alarms only where it has music alarms. */
        fun available(alarms: Boolean): List<ShellTab> = if (alarms) CHOOSABLE else CHOOSABLE - ShellTab.ALARMS

        /**
         * The two places as stored. **Anything unknown, unavailable or doubled falls back to that
         * place's own default**, or to the other default where the other place holds it, so a build
         * without alarms opens with a bar it can use.
         */
        fun restore(first: String?, second: String?, available: List<ShellTab>): NavSlots {
            fun read(name: String?) = ShellTab.entries.firstOrNull { it.name == name }?.takeIf { it in available }
            val a = read(first) ?: DEFAULT.first.takeIf { it != read(second) } ?: DEFAULT.second
            val b = read(second)?.takeIf { it != a } ?: listOf(DEFAULT.second, DEFAULT.first).first { it != a }
            return NavSlots(a, b)
        }
    }
}

/** The Browse place a bar tab opens, or null for a tab that is not one. */
internal fun ShellTab.domain(): BrowseDomain? = when (this) {
    ShellTab.SEARCH -> BrowseDomain.SEARCH
    ShellTab.LOCAL -> BrowseDomain.LOCAL
    ShellTab.SAVED -> BrowseDomain.SAVED
    ShellTab.ONLINE -> BrowseDomain.ONLINE
    ShellTab.HISTORY -> BrowseDomain.HISTORY
    ShellTab.PLAYLIST, ShellTab.BROWSE, ShellTab.RANDOM, ShellTab.ALARMS, ShellTab.MORE -> null
}

internal object BrowseEntry {
    /**
     * Whether Browse shows the very top of the place a tab opened ([entry]): there Back leaves to
     * the playlist, as from any tab; anywhere deeper it steps up first. Search's top is its widest
     * scope (Back widens a narrowed one first).
     */
    fun atTop(entry: BrowseDomain?, browse: BrowseState): Boolean =
        entry != null && entry != BrowseDomain.ROOT && browse.domain == entry &&
            !browse.arrivedByJump && !browse.returnToSavedPlaces &&
            browse.openCatalogue == null && browse.openFormat == null && browse.openAuthor == null &&
            browse.openFolder == null &&
            (entry != BrowseDomain.SEARCH || browse.searchScope == SearchScope.Everywhere)
}
