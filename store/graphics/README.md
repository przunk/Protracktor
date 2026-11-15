# Store graphics

Screenshots must show the final application on a real device. The icon and the feature graphic are
generated from the launcher's own geometry and are not drawn by hand.

## What goes where in Play Console

| File here | Play Console field |
| --- | --- |
| `play-store-icon-512.png` | **Grow users → Store presence → Main store listing → App icon** |
| `feature-graphic-1024x500.png` | the same page, **Feature graphic** |
| `Screenshot_*-store.png` | the same page, **Phone screenshots** — the `-store` files, never the originals |
| `store/listing/<locale>/*.txt` | title, short and full description, on the same page per language |

## The icon and the feature graphic are generated

```sh
java -Djava.awt.headless=true scripts/GenerateLauncherIcons.java .
```

One run writes the adaptive vectors, the ten raster fallbacks, `artwork/`, and both store files
here. **The mark is defined once**, in that generator, so the icon on the phone and the icon in the
store cannot drift apart — which is what happens the moment somebody exports a second version by
hand.

**The store icon is the launcher icon, on the same near-black `#05070C`.** Cyan `#00E7E8` on white
is a contrast ratio of **1.54:1** and the mark disappears into the card; on the app's own background
it is **13.05:1**. Play rounds the corners and casts the shadow itself, so the file carries neither,
and it is written without an alpha channel because Play refuses transparency here.

**The feature graphic carries no text, deliberately.** Play crops it differently on every surface it
appears on and overlays a play button on some of them, a word would have to exist in both listing
languages, and a brand should not depend on whichever font a build machine happens to have. What is
on it is the mark and the tracker pattern the letter is built from, fading east.

## Required assets

**A phone screenshot is not a store screenshot, and the gap is invisible until Play says no.** The
owner's device captures **864×1920** — 2.22:1, where Play's limit is 2:1, and 864 on the short side
where these surfaces want 1080. Both wrong, and nothing on the device says so.

`node scripts/fit-store-screenshots.mjs store/graphics/*.png` writes a `-store.png` beside each one:
**padded to 1080 wide, never scaled**, with the bars filled from the image's own top-left pixel. On
this app's dark screens that reads as a wider phone rather than as letterboxing, and every pixel of
the actual screen survives — which matters for a picture whose whole job is to show what the app
looks like. Upload the `-store.png` files. **Only those are kept** (decided 2026-11-15): a
`-store` file holds every pixel of the phone's original, so cropping its side bars gives the
original back, and a second copy of each screenshot was only clutter. Delete the originals once the
`-store` versions are checked.

- **Feature graphic:** exactly 1024×500 px, JPEG or 24-bit PNG without alpha.
- **Screenshots:** at least two to publish; JPEG or 24-bit PNG without alpha, dimensions from 320 to
  3840 px, with the long side no more than twice the short side.
- **Recommended phone set:** at least four portrait screenshots at 1080×1920 px or greater for
  eligibility in screenshot-led recommendation surfaces.

Source:
[Google Play preview-asset requirements](https://support.google.com/googleplay/android-developer/answer/9866151?hl=en).

## Current set — captured 2026-11-14 and 2026-11-15, after the redesign

Captured on the phone with 1.10.0 and, for 3 and 6, the C143 fix; these replace the screenshots of
**2026-09-24** (around 0.9), which are in git history. In upload order:

| # | file | screen |
|---|---|---|
| 1 | `Screenshot_20261115-012205-store.png` | a playlist playing, the full dock |
| 2 | `Screenshot_20261115-012212-store.png` | the same playlist, the dock collapsed |
| 3 | `Screenshot_20261115-014932-store.png` | Now Playing |
| 4 | `Screenshot_20261115-004740-store.png` | Browse |
| 5 | `Screenshot_20261115-004818-store.png` | Random |
| 6 | `Screenshot_20261115-015005-store.png` | the Queue |
| 7 | `Screenshot_20261115-004918-store.png` | the playlists sheet |
| 8 | `Screenshot_20261115-012648-store.png` | Alarms |

What follows is the plan this set was captured to.

The screenshots before it date from **2026-09-24** (around 0.9). Since then the app was redesigned
(1.10.0: the navigation bar, the collapsible dock, Settings in sections), and gained the playback
Queue with its count, Random with its dice, the playlists sheet as cards, Now Playing's single row of
actions, Saved places, played-before marks, music alarms and AMP. The set planned for 1.7.0 never
got captured and no longer shows the app; this one replaces it.

**Who:** captured by me on the phone -- the store requires the real application on a real device,
and there is no emulator here. **English only, dark theme**, one coherent library; Settings →
Language → English before capturing. No notification banners, no network-provider names (airplane
mode with the tunes already on the phone does it), no personal folder names. After capture,
`node scripts/fit-store-screenshots.mjs store/graphics/<new files>` makes the `-store` versions;
check them against the rules above before uploading.

The set, eight, in this order:

1. a playlist playing: the full dock, the navigation bar, a highlighted playing row;
2. the same playlist with the dock collapsed: more of the list on screen;
3. Now Playing: metadata, subsongs where the file has them (a SID), the row of actions;
4. Browse → Online catalogues: Modland, ASMA, AMP, The Mod Archive, UnExoticA;
5. Random: the dice and a few drawn tunes, played-before marks visible;
6. the Queue: a tune queued by hand ahead of its source, the count in the header and on the button;
7. the playlists sheet: several playlists as cards, the open one marked;
8. Alarms: the list, the next alarm at the top.

## Capture plan

Capture the same coherent library and theme in Polish and English:

1. active playlist with the persistent player dock and a playing row;
2. expanded Now Playing metadata and transport;
3. Online catalogues root showing Modland, ASMA and The Mod Archive;
4. an author or search result list with the row menu;
5. playlist switcher showing several named playlists;
6. play history or Random discovery.

Use only screens confirmed working on the phone. Avoid notification banners, personal folder names,
network-provider names and music the owner cannot show publicly. Do not claim unsupported formats
in screenshot captions. Capture clean status/navigation bars or crop them consistently.

Suggested filenames:

```text
phone-01-playlist-en-US.png
phone-01-playlist-pl-PL.png
phone-02-now-playing-en-US.png
...
feature-1024x500.png
```

## Feature-graphic brief

Use the launcher's cyan tracker bars on the dark indigo field as the visual anchor, with generous
safe space because Play may crop or overlay the image. Communicate “retro music player”, not a
specific manufacturer's machine. Do not use platform logos, archive logos, screenshots too small
to read, ranking claims or a duplicate enlarged launcher icon.
