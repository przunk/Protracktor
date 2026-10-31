# C118: live playback History

Selected: **2026-10-31**. Branch: `fix/history-live-updates`, from develop `0cd80c1`.
The original user report is retained in [Status](STATUS.md#c118-history-does-not-show-newly-recorded-plays-while-the-view-remains-open).

## Contract

Refresh the active Android History view after completed History writes without requiring a
navigation round trip. Preserve the current phrase, page and visit. New rows should be visible
when the list is at its top; a scrolled list should retain its visible row when that row remains
on the page. Automatic refresh keeps existing rows on screen without replacing them with a
loading indicator.
Existing follow-playing behaviour on an actual track change is retained; a background row update
alone does not request a jump to the playing track.

This refines A56's original frozen-view decision. Playback started in History, including its
subsequent context entries, still does not record another play or change recency/counts. A play
from another source continues to update recency and can now update the open view. Qualification
after five seconds or natural completion remains independent of recording. No playback context
is rebuilt by a History refresh: only a track tap captures the visible result collection.

Keep schema 23, one row per track, newest-first ordering and bounded pages of 100. A new insertion
can move an item between pages; retaining a row that has left the requested page is not promised.
No new controls, preferences, dependencies, release version or WEB changes are included.

## Mechanism and implementation

`HistoryWrites.revision` already changes after completed records/qualification and around an
ordered clear. Presentation reads observe it, but `HistoryBrowser` previously only initiated
reads on entry, query/page changes and clear. That explains why reopening revealed stored rows
which the already-open view did not show. This is a code mechanism and host reproduction, not a
new reproduction on the reporting phone.

`HistoryBrowser` now observes that revision, History domain/visit and `HistoryReads.busy`.
It remembers the revision used for a request. An automatic refresh reads the current
phrase/page only when History is active, no clear is in progress and the current explicit
read/debounce/clear has finished. A write during an in-flight read remains pending through the
revision difference and is read when that request becomes idle. A route change alone does not
force a read: Queue's source action retains its captured rows/filter/page. Subsequent committed
writes refresh that view independently of the captured playback context. Ordinary explicit
History entry still reads the newest unfiltered page through `open()`.

The existing ticket/query/visit ownership still rejects obsolete rows and errors. The observer
does not cancel an explicit clear or bypass the 200 ms phrase debounce. Clear still uses the
ordered writer; later plays can repopulate History normally. Read errors retain the existing
localized failure/retry path and do not cause an automatic retry loop.

Compose's stable item keys preserve the visible row when rows are inserted above it. At the
absolute top this otherwise hides a new first row above the viewport. A History-only
`KeepHistoryAtTop` effect requests item zero for a changed list only when already at zero offset
and not actively scrolling. Other list domains retain their existing scroll behaviour. New
History entry/query resets and per-page positions retain the existing C107/B49 ownership.

## Verification checkpoints

- Before the fix, eight host scenarios exercise the real `HistoryBrowser`, `HistoryWrites` and
  filtering rules; **five fail** because committed writes are not reflected in the view.
- With the observer but before the top-anchor adjustment, the actual Compose top-of-History
  check fails: the new row exists but is not displayed. This confirms the UI assertion checks
  visibility rather than only stored state. The intermediate focused run has one failure and
  one fail-fast skip; it is not a passing gate.
- Initial focused checks pass **41 tests**, zero failures/errors/skips, **124 s**, including nine
  live-update domain cases and fourteen actual Compose History checks. This includes the top
  and scrolled-anchor assertions, focus and unchanged captured playback rows.
- The first full gate at `fe489a9` catches an integration regression: automatically reloading
  on a restored History visit replaces Queue's captured source rows. It stops with **1208 tests,
  one failure and one fail-fast skip**. The fix removes that unconditional visit reload while
  retaining revision-driven freshness; a dedicated case preserves captured rows until the next
  write, then checks that their saved phrase/page refresh correctly. Existing source-navigation
  assertions are retained.
- Corrected integration checks pass **55 tests / 7 suites**, zero failures/errors/skips,
  **129 s**, including the unchanged Queue source-navigation suite and the strengthened
  virtual-time debounce guard. Evidence is kept under ignored `user/c118-history-live/`; no phone
  outcome is inferred.

## Verified candidate and APK — 2026-10-31

Clean source **`5a0b4a6f6c341cdcdf07ddffe730d862f37bd19d`** passes the complete project gate:
**1278 tests / 174 suites**, zero failures/errors/skips, **1145 s** overall. The JVM unit task
executes in **376.8 s**. All **85** gate phases pass, including **1368** page assertions,
**57** engine assertions, native audio, PWA and server checks. Fresh lint analyses execute:
**0 errors / 79 warnings**, **142.3 s**. All **840** frozen inputs match after checks and APK build.
Existing JDK17 with the process-local C1 verification configuration is retained.

Minified, test-signed release **1.4.0 (1403)** builds in **98 s**:
[protracktor-release-1.4.0-1403-5a0b4a6-20261031-220709.apk](../dist/protracktor-release-1.4.0-1403-5a0b4a6-20261031-220709.apk),
**11,979,371 bytes**, SHA-256
`53b2272d345afc78c4eda28d3fcfbbf2eb0ef712db7ebf7c0777b70dd6be8cab`.
Independent checks confirm package/version, non-debuggable release, signature with the test
certificate, ZIP integrity, 16 KB ZIP/native alignment, R8 mapping, one DEX and 21 native libraries.
The filename and verification receipt identify source `5a0b4a6`; subsequent documentation does
not relabel this APK or its versionCode.

**Ready for the phone trial, unmerged.** Host/Robolectric evidence does not establish physical
touch, scroll comfort, timing or playback on the reporting device. No push, release tag, AAB or
store publication is performed.

## Phone trial

1. Play from a playlist or Random, open History and use Next several times without leaving it.
   At the top, newly recorded entries should appear. Playback and the Queue context should stay
   with their original source.
2. Scroll down before another play. A data refresh alone should preserve the visible row if it
   remains on the same page; an actual playing-track change retains the existing follow rule.
   Check page navigation and returning to the top; a fresh History entry still starts at the top.
3. Keep a History phrase active while another matching/nonmatching play is recorded. Matching
   rows/counts should refresh without clearing the field or losing keyboard focus.
4. Start playback by tapping History and use Next/Previous. Its existing entries must not jump
   to the top or acquire new counts from that History-origin playback. Queue/source navigation
   must keep the captured context while the visible History can refresh independently.
5. Clear History during playback or a pending filter, then start another track. Old in-flight
   reads must not restore erased rows; subsequent recorded plays may appear. A normal new History entry reads
   the current database. Queue's source action retains its captured view; another committed
   play refreshes that view without resetting its phrase/page or changing playback.

Integration waits for explicit approval after the trial. No push, master/tag change, AAB or
store publication is part of this fix.
