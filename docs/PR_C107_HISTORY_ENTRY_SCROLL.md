# C107 — start each History visit at the top

## Report and decision — 2026-10-15

The owner observed History opening below the top and selected a reset on every new entry.
Exact installed build/device were not separately supplied. This is owner feedback, not an
invented independent tester report. With explicit owner approval this follow-up stays on
`fix/search-request-ownership`, alongside pending C106/C105; no merge is authorized.

## Mechanism and scope

History opens page zero, but its list position belongs to the Browse session's shared scroll map.
Reopening History reuses `history/0`, including its old offset. This predates C106: neither the
scroll cache nor History's list changed in that fix. Data-page reset is not scroll-position reset.

Each explicit History entry should start a fresh visit. History keeps page positions only within
that visit; paging and asynchronous row updates must not reset a position. Other Browse domains
retain their existing per-level scroll restoration. Playback, search, storage and schema are out
of scope. The correction must also work when loading completes without an intermediate UI frame.

## Regression verification

Three host Compose checks exercise the actual `BrowseScreen` and `BrowseWorkflow` entry path:
return through Browse root; reopen History without an intermediate frame; page-position retention
within a visit and reset on the next visit. The read port completes immediately on purpose.
**Checked before correction:** all three checks executed and failed by assertion when the first
row should be visible after reopening. The paging check had already confirmed that positions
survive within the current visit. Zero errors/skips; the cold Gradle run took 229 s. Log/XML:
ignored `dist/c107-regression-before.log` and `.xml`. Implementation, full gate and replacement
release are pending at this checkpoint. No phone reproduction or successful phone retest is claimed.

## Phone checklist

1. Open History, scroll down, return to Browse root and open History again. The newest row should
   be at the top with no leftover offset. Repeat from another Browse domain and after closing Browse.
2. Within History scroll page zero, open Older, scroll there, then Newer. The existing page positions
   should survive. Leave/reenter History: page zero starts at the top, and Older starts fresh too.
3. Check while music plays, with a fast/cached history read, and after a new history row is recorded.
   Ordinary row updates must not force a jump. Smoke-test Search and catalogue/author Back restoration.

Device behaviour remains unverified until the owner reports the relevant scenarios. No push,
master/tag, AAB or Play action is included.
