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
