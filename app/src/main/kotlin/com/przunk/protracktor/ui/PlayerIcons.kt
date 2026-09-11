// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

package com.przunk.protracktor.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * The transport icons, defined here rather than pulled from `material-icons-extended`.
 *
 * That artifact carries some two thousand icons to supply the six the dock needs. Defining them is
 * a few lines of path data and it keeps a heavyweight dependency out of a project that has more
 * demanding uses for its size budget.
 *
 * Repeat and repeat-one are deliberately *different shapes*, not one shape in two colours: state
 * that only a colour distinguishes is state some users cannot read (AGENTS.md §8).
 */
private fun icon(name: String, pathData: String, hollow: Boolean = false): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        addPath(
            pathData = PathParser().parsePathString(pathData).toNodes(),
            // Non-zero winding by default, which is what most single-shape icons want. `hollow`
            // switches to even-odd, where any enclosed subpath is a hole whichever way round it was
            // drawn. Needed when an icon has holes and its subpaths wind the same way as its
            // outline -- under non-zero those fill in, and the icon becomes a solid blob.
            pathFillType = if (hollow) PathFillType.EvenOdd else PathFillType.NonZero,
            fill = SolidColor(Color.Black),
        )
    }.build()

object PlayerIcons {
    val Play: ImageVector by lazy {
        icon("Play", "M8 5v14l11-7z")
    }
    val More: ImageVector by lazy {
        icon(
            "More",
            "M12 8c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm0 2c-1.1 0-2 .9-2 2s.9 2 2 2 " +
                "2-.9 2-2-.9-2-2-2zm0 6c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z",
        )
    }
    val DragHandle: ImageVector by lazy {
        icon("DragHandle", "M20 9H4v2h16V9zM4 15h16v-2H4v2z")
    }
    val Settings: ImageVector by lazy {
        icon(
            "Settings",
            "M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58c.18-.14.23-.41.12-.61" +
                "l-1.92-3.32c-.12-.22-.37-.29-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54" +
                "c-.04-.24-.24-.41-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62" +
                ".94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3" +
                "-.09.63-.09.94s.02.64.07.94l-2.03 1.58c-.18.14-.23.41-.12.61l1.92 3.32c.12.22.37.29" +
                ".59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17" +
                ".47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12" +
                "-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6" +
                " 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z",
        )
    }
    val Info: ImageVector by lazy {
        icon(
            "Info",
            "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0" +
                "-8h-2V7h2v2z",
        )
    }
    val Locate: ImageVector by lazy {
        icon(
            "Locate",
            "M12 8c-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4-1.79-4-4-4zm8.94 3A8.994 8.994 0 0 0 " +
                "13 3.06V1h-2v2.06A8.994 8.994 0 0 0 3.06 11H1v2h2.06A8.994 8.994 0 0 0 11 " +
                "20.94V23h2v-2.06A8.994 8.994 0 0 0 20.94 13H23v-2h-2.06zM12 19c-3.87 " +
                "0-7-3.13-7-7s3.13-7 7-7 7 3.13 7 7-3.13 7-7 7z",
        )
    }
    val Add: ImageVector by lazy {
        icon("Add", "M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z")
    }
    val Back: ImageVector by lazy {
        icon("Back", "M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z")
    }
    val Folder: ImageVector by lazy {
        icon("Folder", "M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z")
    }
    /**
     * A CX40 — the Atari 2600 stick — for the "by platform" filter.
     *
     * Drawn as three subpaths with **even-odd** fill so the fire button is a hole rather than a
     * shape of its own. That is also why the ball and the shaft are one outline instead of a circle
     * overlapping a rectangle: under even-odd an overlap cancels, and the first attempt had a notch
     * bitten out of the stick where the two met. The path was rasterised and looked at before it
     * went in, which is how that was found.
     */
    /**
     * A calendar, for the history of what was played.
     *
     * It used to be the information "i", which said nothing about time. A clock would be the
     * conventional choice for "recent"; the owner asked for a calendar, and it suits what the list
     * actually shows -- entries carry the day they were played (`docs/WISHLIST.md` B15).
     *
     * Even-odd again, so the page is a hole and the header a band. The two tabs stop exactly at the
     * body's top edge rather than overlapping it, because an overlap under even-odd cancels.
     */
    val History: ImageVector by lazy {
        icon(
            "History",
            "M5,4 L19,4 A2,2 0 0,1 21,6 L21,19 A2,2 0 0,1 19,21 L5,21 " +
                "A2,2 0 0,1 3,19 L3,6 A2,2 0 0,1 5,4 Z " +
                "M5.5,9.5 L18.5,9.5 L18.5,18.5 L5.5,18.5 Z " +
                "M7,2 L9,2 L9,4 L7,4 Z M15,2 L17,2 L17,4 L15,4 Z " +
                "M8,12 L11,12 L11,15 L8,15 Z",
            hollow = true,
        )
    }

    val Joystick: ImageVector by lazy {
        icon(
            "Joystick",
            "M5,16 L19,16 A2,2 0 0,1 21,18 L21,20 A2,2 0 0,1 19,22 L5,22 " +
                "A2,2 0 0,1 3,20 L3,18 A2,2 0 0,1 5,16 Z " +
                "M10.6,16 L10.6,8.75 A3,3 0 1,1 13.4,8.75 L13.4,16 Z " +
                "M5.2,19 A1.8,1.8 0 1,1 8.8,19 A1.8,1.8 0 1,1 5.2,19 Z",
            hollow = true,
        )
    }

    val Cloud: ImageVector by lazy {
        icon(
            "Cloud",
            "M19.35 10.04C18.67 6.59 15.64 4 12 4 9.11 4 6.6 5.64 5.35 8.04 2.34 8.36 0 10.91 0 " +
                "14c0 3.31 2.69 6 6 6h13c2.76 0 5-2.24 5-5 0-2.64-2.05-4.78-4.65-4.96z",
        )
    }
    /**
     * A die, and it has to read as one at 24dp.
     *
     * This was Material's `casino` glyph, whose pips wind the same way as its outline: under
     * non-zero winding they filled in and the icon became a solid rounded square. The owner's
     * description was "it does not look like a die, it looks like the Excel logo", which is exactly
     * what a solid green-adjacent rounded square looks like.
     *
     * Drawn as an outline plus five pips and rendered even-odd, so the frame is a frame and the
     * pips are holes in the face rather than part of it.
     */
    val Dice: ImageVector by lazy {
        icon(
            "Dice",
            // Outer rounded square, then the face cut out of it, then the five pips.
            "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z" +
                "M5 5v14h14V5H5z" +
                "M7.2 8.5a1.3 1.3 0 1 0 2.6 0a1.3 1.3 0 1 0-2.6 0z" +
                "M14.2 8.5a1.3 1.3 0 1 0 2.6 0a1.3 1.3 0 1 0-2.6 0z" +
                "M10.7 12a1.3 1.3 0 1 0 2.6 0a1.3 1.3 0 1 0-2.6 0z" +
                "M7.2 15.5a1.3 1.3 0 1 0 2.6 0a1.3 1.3 0 1 0-2.6 0z" +
                "M14.2 15.5a1.3 1.3 0 1 0 2.6 0a1.3 1.3 0 1 0-2.6 0z",
            hollow = true,
        )
    }
    val Search: ImageVector by lazy {
        icon(
            "Search",
            "M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 " +
                "9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 " +
                "0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z",
        )
    }
    val Share: ImageVector by lazy {
        icon(
            "Share",
            "M18 16.08c-.76 0-1.44.3-1.96.77L8.91 12.7c.05-.23.09-.46.09-.7s-.04-.47-.09-.7l7.05-4.11" +
                "c.54.5 1.25.81 2.04.81 1.66 0 3-1.34 3-3s-1.34-3-3-3-3 1.34-3 3c0 .24.04.47.09.7L8.04 " +
                "9.81C7.5 9.31 6.79 9 6 9c-1.66 0-3 1.34-3 3s1.34 3 3 3c.79 0 1.5-.31 2.04-.81l7.12 " +
                "4.16c-.05.21-.08.43-.08.65 0 1.61 1.31 2.92 2.92 2.92s2.92-1.31 2.92-2.92-1.31-2.92-2.92-2.92z",
        )
    }
    /** For sharing a **link**. Deliberately a different shape from [Share], which sends the file. */
    /**
     * A QR code, for "there is nobody to send to yet — point me at a screen".
     *
     * The owner's rule for that button: **the icon says which of the two things pressing it will
     * do.** Unpaired it opens a camera, so it is a code; paired it sends, so it is a link.
     */
    val QrCode: ImageVector by lazy {
        icon(
            "QrCode",
            "M3 11h8V3H3v8zm2-6h4v4H5V5zm-2 16h8v-8H3v8zm2-6h4v4H5v-4zM13 3v8h8V3h-8zm6 6h-4V5h4v4z" +
                "M19 19h2v2h-2v-2zm-6-6h2v2h-2v-2zm2 2h2v2h-2v-2zm-2 2h2v2h-2v-2zm2 2h2v2h-2v-2z" +
                "M17 15h2v2h-2v-2zm0-4h2v2h-2v-2zm2 2h2v2h-2v-2z",
        )
    }

    /** A window with an arrow into it: "open this in the browser", for Share with Protracktor. */
    val Web: ImageVector by lazy {
        icon(
            "Web",
            "M19 4H5c-1.11 0-2 .9-2 2v12c0 1.1.89 2 2 2h4v-2H5V8h14v10h-4v2h4c1.1 0 2-.9 2-2V6c0-1.1-.89-2-2-2z" +
                "m-7 6l-4 4h3v6h2v-6h3l-4-4z",
        )
    }

    val Link: ImageVector by lazy {
        icon(
            "Link",
            "M3.9 12c0-1.71 1.39-3.1 3.1-3.1h4V7H7c-2.76 0-5 2.24-5 5s2.24 5 5 5h4v-1.9H7c-1.71 " +
                "0-3.1-1.39-3.1-3.1zM8 13h8v-2H8v2zm9-6h-4v1.9h4c1.71 0 3.1 1.39 3.1 3.1s-1.39 " +
                "3.1-3.1 3.1h-4V17h4c2.76 0 5-2.24 5-5s-2.24-5-5-5z",
        )
    }
    /** The playlist itself. Used by the way out of Browse, which is not the same as "back". */
    val Playlist: ImageVector by lazy {
        icon(
            "Playlist",
            "M3 9h10v2H3V9zm0-4h10v2H3V5zm0 8h6v2H3v-2zm11-1v6l5-3-5-3z",
        )
    }
    val PlaylistAdd: ImageVector by lazy {
        icon(
            "PlaylistAdd",
            "M14 10H2v2h12v-2zm0-4H2v2h12V6zm4 8v-4h-2v4h-4v2h4v4h2v-4h4v-2h-4zM2 16h8v-2H2v2z",
        )
    }
    /** Several tunes inside one file. A stack of layers rather than a list, which "playlist" is. */
    val Subsongs: ImageVector by lazy {
        icon(
            "Subsongs",
            "M12 2L2 8l10 6 10-6-10-6zm0 13.5L4.2 10.7 2 12l10 6 10-6-2.2-1.3L12 15.5z",
        )
    }
    /** The chevron that says the row above the transport opens something. */
    val Expand: ImageVector by lazy {
        icon("Expand", "M12 8l-6 6 1.4 1.4L12 10.8l4.6 4.6L18 14z")
    }
    /**
     * Out of here. The mirror of [Download], and deliberately not [Share].
     *
     * Share is "hand this to somebody"; export is "get this out of the app", and the arrow says
     * which way things are going without anybody reading the label.
     */
    val Export: ImageVector by lazy {
        icon("Export", "M5 20h14v-2H5v2zM12 4l-7 7h4v6h6v-6h4l-7-7z")
    }
    val Download: ImageVector by lazy {
        icon("Download", "M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z")
    }
    val DropDown: ImageVector by lazy {
        icon("DropDown", "M7 10l5 5 5-5z")
    }
    val Save: ImageVector by lazy {
        icon(
            "Save",
            "M17 3H5c-1.11 0-2 .9-2 2v14c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V7l-4-4zm-5 16c-1.66 " +
                "0-3-1.34-3-3s1.34-3 3-3 3 1.34 3 3-1.34 3-3 3zm3-10H5V5h10v4z",
        )
    }
    val Discard: ImageVector by lazy {
        icon(
            "Discard",
            "M12 5V1L7 6l5 5V7c3.31 0 6 2.69 6 6s-2.69 6-6 6-6-2.69-6-6H4c0 4.42 3.58 8 8 " +
                "8s8-3.58 8-8-3.58-8-8-8z",
        )
    }
    val Rename: ImageVector by lazy {
        icon(
            "Rename",
            "M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34a.9959.9959 0 0 0-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z",
        )
    }
    val Remove: ImageVector by lazy {
        icon(
            "Remove",
            "M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 " +
                "19 19 17.59 13.41 12z",
        )
    }
    /**
     * A square, for "stop waiting for this".
     *
     * Shown on the play button **while a track is being fetched**, because that is what pressing it
     * then does. Without it the button says "play" for up to ten seconds on a slow connection and
     * a second press restarts the same download, which is the opposite of what anybody means by it.
     */
    val Stop: ImageVector by lazy { icon("Stop", "M6 6h12v12H6z") }

    // Three bars narrowing downwards -- the shape everything else in the world uses for a
    // filter, which is the whole argument for drawing it rather than inventing one.
    val Filter: ImageVector by lazy {
        icon("Filter", "M3 6h18v2H3zm3 5h12v2H6zm4 5h4v2h-4z")
    }

    val Pause: ImageVector by lazy {
        icon("Pause", "M6 19h4V5H6v14zm8-14v14h4V5h-4z")
    }
    val SkipNext: ImageVector by lazy {
        icon("SkipNext", "M6 18l8.5-6L6 6v12zM16 6v12h2V6h-2z")
    }
    val SkipPrevious: ImageVector by lazy {
        icon("SkipPrevious", "M6 6h2v12H6zm3.5 6l8.5 6V6z")
    }
    val Shuffle: ImageVector by lazy {
        icon(
            "Shuffle",
            "M10.59 9.17L5.41 4 4 5.41l5.17 5.17 1.42-1.41zM14.5 4l2.04 2.04L4 18.59 5.41 20 " +
                "17.96 7.46 20 9.5V4h-5.5zm.33 9.41l-1.41 1.41 3.13 3.13L14.5 20H20v-5.5l-2.04 " +
                "2.04-3.13-3.13z",
        )
    }
    val Repeat: ImageVector by lazy {
        icon("Repeat", "M7 7h10v3l4-4-4-4v3H5v6h2V7zm10 10H7v-3l-4 4 4 4v-3h12v-6h-2v4z")
    }

    /** A loop with a numeral in it. Distinguishable from [Repeat] with the colour removed. */
    val RepeatOne: ImageVector by lazy {
        icon(
            "RepeatOne",
            "M7 7h10v3l4-4-4-4v3H5v6h2V7zm10 10H7v-3l-4 4 4 4v-3h12v-6h-2v4zm-4-2V9h-1l-2 " +
                "1v1h1.5v4H13z",
        )
    }
}
