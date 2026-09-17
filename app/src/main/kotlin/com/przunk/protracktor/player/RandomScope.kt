// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

/**
 * What Random picks from.
 *
 * **A type rather than a nullable platform id**, because "random within a platform"
 * (`docs/WISHLIST.md` B22) and "random, but only tunes considered good" are one feature.
 * [Favourites] is another case here rather than a second mechanism, which is what the type is for.
 *
 * **Persisted**, which is only safe because it is visible: a scope outliving the session would
 * otherwise be an invisible mode, and a dice button that quietly remembers a setting has stopped
 * being a dice button. The Random view shows the scope beside its Filter button for as long as you
 * are in there (`docs/PLAN_RANDOM.md`). A setting you can see is a setting, not a trap.
 */
sealed interface RandomScope {

    /** Everything in every indexed catalogue. What the dice has always meant. */
    data object Everything : RandomScope

    /** One machine, using the same table the search filter narrows by. */
    data class OnPlatform(val platformId: String) : RandomScope

    /**
     * Modland's own favourites — somebody else's judgement, published.
     *
     * "Only tunes considered good" cannot be answered by this app: it has no plays, no ratings and
     * no opinion, and inventing a score from what happens to be indexed would be a number with
     * nothing behind it. Modland keeps a favourites list, so the feature is to *use* it.
     *
     * Not a platform, and not combined with one. The list is 991 tunes and almost all of them are
     * Amiga tracker music, so `Favourites ∩ C64` would be a chip that returns nothing — the scope
     * is one choice, not two filters (`docs/WISHLIST.md` B27).
     */
    data object Favourites : RandomScope
}

/**
 * The name this scope goes under on disk.
 *
 * **Its own vocabulary, not the class names.** Renaming a Kotlin type must not silently reset
 * everybody's saved setting, and a platform id is already a stored identifier used by the search
 * filter and the catalogue tables.
 */
fun RandomScope.stored(): String = when (this) {
    is RandomScope.Everything -> "everything"
    is RandomScope.Favourites -> "favourites"
    is RandomScope.OnPlatform -> "platform:$platformId"
}

/**
 * Reads back what [stored] wrote, and answers `Everything` to anything it does not recognise.
 *
 * An empty column is a phone upgraded from before this was kept; an unknown word is a value written
 * by a newer build. Both mean "we do not know what you had", and the honest answer to that is the
 * dice's own default rather than a crash or a guess.
 */
fun storedRandomScope(value: String?): RandomScope = when {
    value == "favourites" -> RandomScope.Favourites
    value != null && value.startsWith("platform:") ->
        value.removePrefix("platform:").takeIf { it.isNotEmpty() }
            ?.let { RandomScope.OnPlatform(it) }
            ?: RandomScope.Everything
    else -> RandomScope.Everything
}
