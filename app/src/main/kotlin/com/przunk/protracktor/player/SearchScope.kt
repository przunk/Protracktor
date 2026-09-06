/*
 * Protracktor -- a player for retro platform music formats.
 * Copyright (C) 2026 Przunk
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See
 * the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If
 * not, see <https://www.gnu.org/licenses/>.
 */
package com.przunk.protracktor.player

/**
 * What a search covers, as one value.
 *
 * The filter used to be three independent switches — local, online, and a set of catalogues — and
 * the trouble was never the switches but that **the scope was not anywhere**. You worked it out by
 * reading five chips, and "online and by platform at once" had no defined meaning because nothing
 * had to state it.
 *
 * One value, shown in the search field's own label. That is what makes the rest simple: there is no
 * conjunction to resolve, an empty selection can safely mean "all of these" because the label says
 * so out loud, and back has something singular to undo (`docs/WISHLIST.md` B23).
 */
sealed interface SearchScope {

    /** Everything: the scanned library and every indexed catalogue, whatever platform. */
    data object Everywhere : SearchScope

    /** Only folders the user granted. */
    data object Local : SearchScope

    /** Online only. An empty set means every indexed catalogue, which is what tapping the tile does. */
    data class Online(val catalogueIds: Set<String> = emptySet()) : SearchScope

    /**
     * Everything, narrowed to machines. An empty set means every platform, so it searches as widely
     * as [Everywhere] while leaving the platform row on screen to choose from.
     *
     * Local files included, not only catalogues: "Amiga music" is a statement about the music, and a
     * filter that quietly meant "Amiga music somebody else is hosting" would be the same kind of lie
     * the service-name chips were.
     */
    data class ByPlatform(val platformIds: Set<String> = emptySet()) : SearchScope

    val searchesLocal: Boolean
        get() = this is Everywhere || this is Local || this is ByPlatform

    val searchesOnline: Boolean
        get() = this is Everywhere || this is Online || this is ByPlatform

    companion object {
        /**
         * How many names the label spells out before it starts counting.
         *
         * Two. "Online: Modland, Aminet, ASMA, The Mod Archive" does not fit a field label on a
         * phone, and a label that truncates mid-word says less than one that says `+2`.
         */
        const val MAX_NAMES = 2

        /** The names a label shows, and how many it had to leave out. */
        data class Parts(val shown: List<String>, val hidden: Int)

        fun parts(names: List<String>): Parts =
            Parts(names.take(MAX_NAMES), (names.size - MAX_NAMES).coerceAtLeast(0))
    }
}
