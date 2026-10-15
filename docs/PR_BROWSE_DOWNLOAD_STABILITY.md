# Browse and download stability: implementation record

Owner-selected maintenance, 2026-10-16. Branch `fix/browse-download-stability`, from
develop `59ba578`; [scope and delivery contract](PLAN_BROWSE_STABILITY.md).
No new feature, playback-queue design, schema/version-name change or publication is selected.

## Reports and reproductions

These are retained code-review findings, not newly reported phone failures. Controlled host
sequences do not establish occurrence frequency on a device.

The baseline run compiled unchanged production code with five correctness regressions. All five
failed (`/tmp/protracktor-Protracktor-stability-before-all.log`, 91.4 s). The disposable ignored
init script disabled fail-fast only for that reproduction so all affected classes could run;
the project's normal full gate retains its configured process isolation and fail-fast behaviour.

1. Suspend catalogue A, visit a newer catalogue (including A again), then release the old read:
   old groups replace the new groups and clear their loading state. Leaving and restoring an
   identical route also admits old rows.
2. Suspend a local-folder index read, close and reopen the folder, then release the first read:
   the older tree overwrites the new visit.
3. Let Back's parent reader publish immediately: Back subsequently copies its captured child
   snapshot and erases the parent's result and concurrent fields.
4. Complete a cancellation-resistant read after replacing read-ahead with an empty selection:
   obsolete bytes re-enter the in-memory map. The clear variant is retained in the same test.

## Implementation stages

### 1. Navigation ownership — in progress

Use a shared `BrowseReadOwner` and monotonic `BrowseState.navigationVisit`. Selection is published
at the call boundary, before queuing the read; completion checks both visit and route inside the
atomic state update. A route match alone cannot distinguish two visits to the same location.
Catalogue, format, author and local-folder reads use this boundary. Domain entry and snapshot
restoration advance the visit; restoration derives it from current state, not the old snapshot.
The controller's author jump checks ownership before navigation and before playback-side effects.
Its resolution intent borrows current visit ownership without retiring the existing read; an
unavailable jump must leave that read valid. A separate intent counter rejects older resolutions,
and a changed Search query/scope also retires that intent. A direct capture-preservation check was
added after the initial focused run. An initial full gate was deliberately interrupted after 75.2 s
to finish this edge-case correction; it is not a verification pass. The final forced run must start
again from the corrected committed candidate.
Independent catalogue/storage counts remain independent of navigation.

Checked: the final navigation-focused run passed all 47 tests in 116.2 s
(`/tmp/protracktor-Protracktor-browse-final.log`). The first focused run executed 43 tests, with 42 passing and one old assertion
expecting folder selection to remain deferred until after scan start. Selection now registers
synchronously to invalidate queued work; that assertion has been corrected. The final focused rerun
also includes four direct ownership checks. Cancellation/failure reporting policy
is not redesigned by this stage. Full tests/lint and device acceptance remain pending.

### 2. Back publication — implemented; focused verification pending

Back now atomically publishes its parent transition before invoking the parent reader. It modifies
current state instead of publishing a captured complete snapshot afterwards. Each successful
transition advances the navigation visit; immediate/reentrant results and unrelated fields survive.
The controlled synchronous-parent regression failed on the old code. Existing search-scope, jump,
saved-place and local hierarchy contracts remain covered by the workflow suite.

### 3. Read-ahead ownership — implemented; focused verification pending

Retire the previous generation before checking for missing bytes. Cancel after retiring ownership,
reject obsolete/cancelled completions and assign a lazy job before starting it. Empty and fully
held selections now cancel previous work. Clear retires the generation too; an immediate
cancellation callback cannot overwrite a preparation started reentrantly. Existing parallelism,
consume-once semantics and four/eight-megabyte limits remain unchanged. Duplicate candidates are
read once per request. The old implementation fails the controlled empty-selection regression.

### 4. Disk-cache transactions — pending

Historical review R7 predates `SharedFetches`: the current app already joins overlapping requests
for one URL. Recheck the actual disk boundary rather than claiming duplicate transfers reproduce.

## Final handoff

Full tests/lint, minified release APK and device checklist pending. Nothing merged or pushed.
Phone behaviour is unchecked; the owner must accept the offered build before integration.
