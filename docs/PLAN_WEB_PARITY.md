<!--
SPDX-FileCopyrightText: 2026 Przunk
SPDX-License-Identifier: GPL-3.0-or-later
-->

# The page catches up with the APK

*Asked for on 2026-09-22: "bring the web version up to the APK's state; write down what went into
the APK since the web last changed, and prepare a stage."* Written before any code. `docs/BACKLOG.md`
A57 is the entry; the decisions W-D1 to W-D3 are the owner's, each with a recommendation.

**Decided 2026-09-22: W-D1, W-D2 and W-D3 as recommended**, the owner approving all three; W1–W3
first.

## Where the page stopped

The page was kept level round by round until **2026-09-16/17** (round 11 and the index rewrite of
step 0). From 2026-09-16 to 2026-09-22, 72 commits changed the app or the engine; 9 of them touched
`web/` too. The other 63 were read and sorted into the groups below.

### Already in the page, through the shared engine

The engine (`native/engine/`) is compiled for both, so these need **no page code** -- only the page
rebuilt and put back on the server (`scripts/package-web.sh`, then the owner's copy to the Pi):

- **A47** — accented titles read by one rule (UTF-8, CP437, ISO-8859-1), including the libopenmpt
  patch.
- **C80** — a BASIC SID is refused with its reason instead of playing silence.
- **`dc4fc5e`** — an NSF whose file states no length reports none, instead of game-music-emu's
  invented 2:30.

### Already in the page, done alongside the app

- **A53** — search without accents (`rules.js` `foldText`, shared cases).
- **Step 0** — the index keeps every row; HVSC song lengths; search by every word; icons drawn the
  same on every renderer; Amiga custom formats marked as the phone's only (`3dfce7a`, `8cb991b`).
- **C57** — the search box starts empty (the page already cleared it).

### Missing from the page — this stage

| # | what | from the app | size |
| --- | --- | --- | --- |
| **W1** | History keeps its order: a play started from History leaves its entry alone | A56 | small |
| **W2** | a length nobody knows: dashes instead of `0:00`, an empty bar that refuses a drag | `bb385c7` | small |
| **W3** | the now-playing card's lines scroll slowly when they do not fit; still with reduced motion | A54 | small |
| **W4** | open-source licences and the privacy policy, readable from the page's Settings | `41bcc15`, C78 | medium |
| **W5** | song metadata from songdb -- author, publisher, album, year -- downloaded on request | the "Song metadata" download | medium |

### Not for the page

- **Cannot be done in a browser:** the Amiga custom formats (UADE is a second process, and
  WebAssembly has no `fork`), and with them songdb's song lengths and lengths learnt by playing
  (A52), which exist only for those formats. **`.sc68` replay routines:** the page would have to
  fetch them from sc68's SVN, which sends no CORS header, and serving them from the owner's own
  server would be distributing them -- which the app deliberately never does (`docs/LICENSES.md`).
- **The phone's by nature:** the foreground service and its crash (C72), the launch measurement
  (A48), deep links, uninstall behaviour, the grouped download sheet's replay-routine row, the
  release script.

### Missing, and not recommended for this stage

- **W6 — Polish.** The app speaks the phone's language (`68ca5b5`); the page is English only
  (`<html lang="en">`, every string in `app.js` and `index.html`). About 3,500 lines to go through,
  and the app is what goes public on 2026-10-02. W-D3.
- **W7 — a light theme.** The app follows the system or a choice; the page is dark only
  (`color-scheme: dark`). Smaller, and it pairs with W6 as "the page's settings grow up". W-D3.

## Order

W1, W2, W3 are small and independent: one branch each, or one branch with three commits if the
owner prefers them tested together. W4 before W5, because W5 adds a download and the page should
be saying what it fetches, and from where, before it fetches more.

Nothing merges before the owner has seen it in a browser. The page's own checks (`check-page.mjs`,
jsdom) cover logic and markup; how it looks and moves is his.

## W1 — History keeps its order (A56)

The page already does half: it does not re-render History while a tune plays. What it lacks is
the rule itself -- `recordPlay` writes every play, so a tune played from History moves to the top
on the next visit. The page knows where a play came from (`away.kind === 'history'`), so
`recordPlay` skips those, as `HistoryRecording.records` does on the phone. A shared case in
`docs/rules/queue-cases.tsv` holds both to one answer.

## W2 — A length nobody knows

`clock(0)` prints `0:00` where the total belongs, which says the tune is zero seconds long, and the
bar's range is then the position itself. The phone shows `–:––` and an empty bar that refuses a
drag (`SeekBar`, `bb385c7`). Same here; the formatting goes into `rules.js` so it can be checked.

## W3 — Scrolling lines (A54)

`.nowcard .title` and `.meta` end in `…` today. A line that overflows scrolls slowly left -- about
30 px a second, two seconds still at the start of each pass -- and a line that fits stands still.
CSS animation, measured once per tune: no library. **`prefers-reduced-motion: reduce` keeps it
still**, the page's equivalent of Android's "Remove animations". The loading line never scrolls.

## W4 — Licences and privacy in the page

**Built 2026-09-22 on `feature/web-w4-legal`**: 14 components with 19 licence texts, the policy's
new section on the page, and the check that fails when the web build links something unlisted.
Not yet seen in a browser.

**Not optional.** Serving the wasm engine to a browser is conveying it (`docs/PLAN_WEB.md` §9): the
BSD components (libopenmpt, HivelyTracker, bencodetools…) require their notices to go with the
binary, and the GPL requires the offer of source. The app does this since 0.7.0; the page does not.

Two rows in the page's Settings, icon and label each: **Open-source licences** (the same component
list and texts the app shows, from `app/notices/`, packaged by `package-web.sh`) and **Privacy
policy** (from `store/privacy-policy.md`, rendered by the same rule as `LegalText`).

**W-D1 — which components the page lists.** The engine in the browser is built without UADE and
without the Android pieces (Oboe, AndroidX, Kotlin).

- **(a) Recommended:** the page's own list -- the app's `components.tsv` with a column saying which
  build each row belongs to, and a check in `check-page.mjs` that fails when the web engine's
  build (`scripts/build-web-engine.sh`) links something the page does not list. The same guard the
  app has (`NoticesCoverTheBuildTest`), for the other build.
- (b) The app's full list on the page too. Simpler, but it names Oboe and UADE on a page that
  contains neither, which is a document that lies.

**W-D2 — the privacy policy.** It was written for the app. The page stores its playlists, history
and indexes in the browser (IndexedDB) and fetches from the same archives.

- **(a) Recommended:** one policy for both. Add a short section on the page -- what the browser
  keeps, that nothing leaves it but requests to the archives, and that the person hosting the page
  sees the requests for the page itself -- and show that file in both. One policy stays one
  (`docs/PRIVACY.md`).
- (b) A separate policy for the page.

## W5 — Song metadata from songdb

**Built 2026-09-22 on `feature/web-w5-songdb-metadata`, branched from W4** because it extends the
policy section W4 adds (the page now contacts `raw.githubusercontent.com` too): parsed by
`web/src/songdb.js` on the rows `SongDbMetadataTest` uses, stored in 256 shards, looked up only
when something is stored, and filled in by `rules.fillFromSongDb`, which never overwrites what a
tune says. Measured on the host with the real file, 2026-09-22: **380,282 rows kept of 380,282**
(the phone's count), parsed in 0.3 s, **about 19 MB** as stored shards. Not yet seen in a browser,
and what IndexedDB makes of those 19 MB not measured in one.

The phone downloads songdb's `metadata.tsv` (14.1 MB, `raw.githubusercontent.com`, which sends CORS
`*` -- checked 2026-09-22) and shows author, publisher, album and year in Now Playing, and the year
in the dock. The page shows the year only when the file itself states one.

In the page: a **Song metadata** row among the downloads, icon and label, with its size; stored in
IndexedDB keyed as the phone keys `track_metadata` (the first twelve hex digits of the MD5); read
when a tune opens. Only `metadata.tsv` -- `songlengths.tsv` serves formats the page cannot play. The storage it
takes is measured in a real browser before it is offered, and shown in Settings with the rest.

## Second part — Browse, Back, storage, first run (asked 2026-09-22)

*The owner, after W1–W5: "align the icons in Browse with the APK (state, icons, sections), align what
Back does after More from this author, and the others we fixed lately."* Read against the app on
2026-09-22; the page's Browse is one flat list today, where the app has had domains, held-state
icons and grouped downloads since 2026-09-21.

| # | what | from the app | size |
| --- | --- | --- | --- |
| **W8** | Browse as the app draws it: the root's rows (online catalogues, Random with its scope in the title, History, Search); each catalogue with its held icon (tick in the accent colour, or a dimmed cloud), a line saying what is here, and one button that says what it will do (download, or refresh); the downloads grouped under the catalogues | `e01b26c`, `53d93bc`, `30e09a7` | medium |
| **W9** | Back after **More from this author** returns to where you were in one press, instead of climbing author → format → archive | `browseBack`, `arrivedByJump` | small |
| **W10** | Settings says what the page holds, per download, and deletes each one; a delete answers at the press, one message at a time | Storage section, C76, C77 | medium |
| **W11** | An empty page offers what to get, once it knows nothing is held -- never before, never a wrong offer | `355f198`, `3c40bc5`, A46 | small |

### W8 — what is and is not copied

- **The root.** The app's rows are Local folders, Online catalogues, Random, History, Search. The
  page has no local folders (a browser cannot list a phone's storage; its ways in are the pairing
  code and pasted links, which stay where they are), so its root is **Online catalogues, Random,
  History, Search**, with the app's icons, titles and one-line subtitles.
- **The catalogues.** Modland and ASMA, each with the held icon, the detail line (how many tunes, or
  "not downloaded"), and the trailing button: **Download** when absent, **Refresh** when held -- the
  app's two marks, not one arrow for both. While it runs: a spinner and "Downloading…" in the
  button's place. A catalogue opens only once it is held.
- **The groups.** One **Song metadata** row fetches everything the page can use of what a file
  cannot say about itself: HVSC's SID lengths and songdb's metadata (W5), under one tick, as the
  app decided (A52 D1). **This replaces W5's separate row.** No "Replay routines" row: neither
  set can reach a browser (see "Not for the page").
- **Nothing drawn before it is known** (C74, C75): the rows wait for the stored state to be read,
  so no tick appears and vanishes.

### W9 — Back after a jump

**Built 2026-09-22 on `feature/web-w9-back-after-jump`**; not yet seen in a browser.

The app's rule (`PlaybackController.browseBack`): "More from this author" lands three levels deep
without passing through them, so **the first Back leaves Browse** for where you were, rather than
climbing a hierarchy you never climbed into. The page climbs one level per press today, except in a
Random digression, where Back already returns to the dice -- which stays as it is.

**W-D4 — a jump made from inside Browse** (a search result's or History's row menu). The app leaves
Browse for the playlist in that case too.

**Decided 2026-09-22: (a)**, the owner approving W8–W11 as planned.

**State, 2026-09-22 evening — all built, none merged, none seen in a browser.** One branch each:
`feature/web-w9-back-after-jump` and `feature/web-w11-first-run` off `develop`;
`feature/web-w8-browse-like-app` off W5 (it folds W5's row into the group), and
`feature/web-w10-storage` off W8 (it lists the grouped row). All of W1–W11 together are on
`test/web-w1-w11`, packaged as `dist/protracktor-web-20260922-191146.tar.gz`. W11's button opens
Browse's root; with W8 merged, the catalogues are one row below it.

- **(a) Recommended: as the app does**, so the two players agree, and the rule goes into
  `docs/rules/queue-cases.tsv` so they keep agreeing.
- (b) Back returns to the screen the jump was made from -- the search results, History. Arguably
  kinder, but then the app should change too, and that is a change to the app, not parity.

### W10 — Storage in Settings

The app's Settings → Storage lists each download with what it holds and a delete (icon and label);
a delete shows at once and says so once (C76, C77). The page's Settings shows counts and can forget
only the SID lengths, with a word and no icon. W10 gives the page the same list -- Modland, ASMA,
Song metadata -- and a delete for each, icon and label.

### W11 — The first run

**Built 2026-09-22 on `feature/web-w11-first-run`**; the button opens Browse's root until W8 gives it
a catalogue list to open. Not yet seen in a browser.

The app's empty playlist says nothing until it knows what is held, then offers **Get some music to
browse** when nothing is (`355f198`). The page's empty playlist says "Scan the code with your
phone, or paste some URLs" whatever is held. W11 adds the offer beside those two ways in, only
when no catalogue is held, opening Browse on the catalogues.

## W-D3 — Polish and a light theme

- **(a) Recommended: a stage of their own, after the public launch.** Both touch every screen of
  the page, neither changes what it can play, and the page is not what launches on 2026-10-02.
- (b) In this stage.
- (c) Not at all.

## What the owner checks

In a browser, on the page served from the Pi after `package-web.sh`:

- **Engine fixes:** `Zalza/akes lekhorna.mod` reads `åkes lekhörna`; `Prelfugueinfmaj_BASIC.sid`
  says why it will not play.
- **W1:** play from History, next a few times, leave and come back: nothing moved.
- **W2:** a tune with no known length shows `–:––` and an empty bar.
- **W3:** a long title scrolls; a short one does not; with the OS's reduced motion on, nothing does.
- **W4:** Settings → Open-source licences lists what the page carries, each text opens; Settings →
  Privacy policy reads to its end.
- **W5:** Settings → Song metadata downloads; a MOD then shows its year and album in Now Playing.
