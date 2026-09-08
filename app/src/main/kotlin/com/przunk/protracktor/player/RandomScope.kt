// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.player

/**
 * What Random picks from.
 *
 * **A type rather than a nullable platform id**, because the owner raised two wishes on the same
 * day and they are one feature: "random within a platform" (`docs/WISHLIST.md` B22) and "random,
 * but only tunes considered good". The second is [Favourites], and it arrived as another case here
 * rather than as a second mechanism — which is what the type was for.
 *
 * **Not persisted, deliberately.** After a restart the dice means *anything* again. A scope that
 * outlives the session is an invisible mode, and a dice button that quietly remembers a setting has
 * stopped being a dice button; the row's subtitle says what is set while it is set, and that is the
 * whole of the state anybody has to keep in their head.
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
