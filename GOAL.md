# GOAL

The work to do when the owner is away. Written 2026-09-02.

**How to use it:** the owner runs `/goal` pointing here, and the agent works through the list in
order until it is done or genuinely blocked.

---

## The rules

These are not new — they are `AGENTS.md` and `/mnt/workspace/AGENTS.md`, restated because an agent
working unattended is the case they were written for.

1. **One item, one branch, one merge.** Branch off `develop`, merge back to `develop` when the item
   is finished and building. Do **not** touch `master`: merging there is the owner's decision.
2. **Every item ends with `./scripts/test-protracktor.sh` and `./scripts/build-debug.sh` green.**
   A stage that does not build is not a stage.
3. **Do not claim anything works on a device.** Nothing here can be confirmed without the owner's
   phone. Say what was verified and by what.
4. **If an item turns out to need a decision, stop that item, write the decision needed into
   `docs/BACKLOG.md`, and move to the next one.** Do not guess on the owner's behalf. Guessing on
   your own behalf about *implementation* is fine and expected — say so in the commit.
5. **Update `docs/STATUS.md` as you go.** A document that lies is worse than one that is missing.
6. Tick items off in this file as they land, so progress is visible at a glance.

## Explicitly out of scope

- **`docs/BACKLOG.md` A2 (subsongs).** The owner has decided what a subsong *is*; the UI is an open
  conversation he asked to have. Do not design it.
- **The Commodore ROM decision.** Integrating libsidplayfp is item 3; deciding whether to ship ROMs,
  ask the user for them, or live without them is not. Measure and report.
- **`master`.** See rule 1.
- **`docs/STATUS.md` C3.** Measuring R9 needs his phone and his network share.
- **`docs/BACKLOG.md` A3 (fast scrolling).** It was on this list and the owner pulled it: he wants to
  talk about it first. Note that item 2 builds part of the same machinery, so leave the scrolling
  helpers general enough not to prejudge A3 — but do not build A3.

---

## The list

- [x] **1. The follow-track button is too heavy**
      Reported 2026-09-02: it covers too much of the list. It is currently an
      `ExtendedFloatingActionButton` with a label — the label is what makes it large. Make it
      subtle: a small icon-only button, or something that fades to the edge when the list is idle.
      It still has to be discoverable enough that the owner finds it without being told, which is why
      the label was there in the first place — so shrinking it is a trade, not a free win. Keep the
      behaviour exactly as it is; this is about weight on screen, nothing else.

- [x] **2. Adding tracks should show them**
      Reported 2026-09-02. Adding from Browse appends to the end of the playlist and leaves the view
      where it was, so nothing visibly happens — which is worse now that the confirming snackbar has
      been removed on purpose. Scroll to the first newly added track.

      Note this is the same machinery as item 1 and as `docs/BACKLOG.md` A3, which the owner has
      reserved for a conversation. Use the scrolling helper that already exists; do not build A3.

- [x] **3. libsidplayfp — Commodore 64** *(30 of 30 played with no ROMs at all)*
      The format chosen for this round, and the reasoning is the point of writing it down: it is the
      largest single body of music left (**~72,000 files in Modland alone** — HVSC 60,572, Sidplayer
      5,032, RealSID 3,540), it is one library rather than several, and C64 is closer to the heart of
      this app than the `*SF` console dumps that rival it on volume.

      Follow `docs/PLAN_FORMATS.md` §1, and **prove it on the host before integrating**, the way
      sc68, ASAP and game-music-emu were. That has caught something every time.

      **The ROM question stays the owner's.** Some SID tunes need Commodore's KERNAL, BASIC and
      CHARGEN images, which cannot be shipped. Do **not** decide what to do about that. Do the thing
      that makes his decision easy: integrate, play what plays without them, and **measure how many
      of thirty random SIDs actually need them**. A number turns that question from abstract into a
      choice.

- [x] **4. ASMA — the Atari 8-bit archive** *(it publishes the whole collection as one zip, which is a second kind of catalogue)*
      Unblocked by ASAP landing: it is the reference collection for `.sap`, and we can now play what
      it holds. `docs/PLAN_CATALOGUES.md` records what is known — the site responds, `asma.zip`
      does not, and **the distribution URL and its index shape are unknown**. Finding them is the
      first half of the job.

      If it turns out ASMA publishes no machine-readable index, stop and write that down rather than
      scraping HTML. A catalogue that depends on the shape of somebody's web page is a catalogue that
      breaks silently.

- [x] **5. HVSC — at least its song lengths** *(done; full HVSC browsing deliberately not built)*
      `Songlengths.md5` fetches directly (5.2 MB, verified 2026-08-31) and gives SID tunes the
      durations they otherwise lack entirely. Worth having **even if the full HVSC catalogue is not
      built**, because without it every SID will show as unknown length and the scrubber will be
      disabled.

      Full HVSC browsing is a different shape from Modland — one large collection rather than an
      index plus per-track fetches — and `docs/PLAN_CATALOGUES.md` says so. If that shape turns out
      to need a second kind of `Catalogue`, build the song lengths and write up the rest.

## When the list is done

Stop. Do not start `docs/BACKLOG.md` A2 (subsongs), A3 (fast scrolling) or A4 (bulk operations) —
all three are reserved for a conversation.

Write what happened, what is unverified, and what you would do next. In particular, say plainly how
much of items 3, 4 and 5 was **measured** rather than merely built: the last round established that
a backend proven on the host before integration is worth several device round trips, and the same
goes for a catalogue whose index has actually been parsed.

---

## Round 2 — closed 2026-09-02

All five items done and merged into `develop`. What is worth knowing beyond that:

- **Item 3 (libsidplayfp)** needed no Commodore ROMs for any of thirty test files, which shrinks the
  ROM question rather than answering it — none of the thirty was BASIC-compatible, and those are the
  ones that would need them.
- **Item 4 (ASMA)** was not the shape the plan assumed. It has no index; it publishes the whole
  collection as one 20 MB zip. That produced a second kind of catalogue rather than another parser
  (`docs/ARCHITECTURE.md` §13).
- **Item 5 (HVSC song lengths)** is keyed on the plain MD5 of the file, not the header hash older
  HVSC releases used and that libsidplayfp still exposes. Written from the older documentation it
  would have found nothing and looked empty rather than wrong.

**Nothing in this round has been confirmed on a device.** Five formats and two online archives were
built and measured on the host; none has been seen working on a phone. That is the honest state and
the first thing worth doing next.

---

# Round 3 — set 2026-09-02

The owner picked these five by number and asked for them to be built, not planned. The rules at the
top of this file still apply, unchanged.

**Out of scope for this round**, in addition to the standing list above: `docs/BACKLOG.md` A13
(application settings) was added the same day and is deliberately **not** here — it needs a
navigation decision the owner has not made (`docs/OPEN_QUESTIONS.md` Q1 touches the same surface).

Order is chosen so each item stands on the one before it, not by the owner's numbering.

- [x] **1. A12 — Random keeps going when a track ends**
      Smallest of the five and the one the rest is felt through. A transient track ending should go
      to the next random pick. **The existing comment's reasoning survives** and must be narrowed
      rather than deleted: rolling on into the *playlist* is still the wrong answer. A search result
      is also a transient track and must not be touched — it has its own queue, handled earlier in
      the same function.

- [x] **2. A11 — Random reads ahead**
      Decide two or three picks in advance into `randomHistory` past `randomCursor`, so the
      read-ahead that already exists has something to read. `prefetched` holds one track today;
      several means a small cache with an eviction rule. Going back must never re-roll the past.

- [x] **3. B8 — A history of what was played**
      Persisted, not the in-memory `randomHistory`. What it is *for* is the thing to get right: not
      an audit log, but "that tune two days ago, what was it". Needs a decision on how much is kept
      and whether it is browsable as a queue — decide the implementation, and if the **UI** turns
      out to need the owner, write it down and move on (rule 4).

- [x] **4. B2 — Jump to a tune's neighbours** *(catalogue tracks; local files stated as out, with the reason)*
      From a playing or listed track, open Browse at the place it came from: the author's folder.
      The catalogue path is already in `TrackRef.subtitle` and B3 proved it survives the trip. A
      **local** file's neighbours are its folder, which is a different mechanism — do the catalogue
      case first and say plainly whether the local one is in or out.

- [x] **5. A10 — Sharing, the file and a link**
      Two actions, not one. A local file needs a `FileProvider` copy because a SAF URI cannot be
      handed to another app. **Not every track has a link**: Modland ids are `https://` URLs, ASMA
      ids are `asma://<entry>` and mean nothing off this device — decide and act, do not let it
      fail quietly. If choosing *what a shared link should say* needs the owner, that part stops
      and the rest ships.

## Round 3 — closed 2026-09-02

All five done and merged into `develop`. Two things came out of it that were not in the plan:

- **A latent null in `Catalogue.all`**, found by the first test that reached it. A companion's
  properties are static fields of the outer class, so touching a catalogue object directly built
  the list while that object was still initialising and put a `null` in it for the life of the
  process. Nothing in the app did that yet; the next person to write `Modland.something` would have.
- **With a queue read ahead, the dice and next stop meaning the same thing.** Next means forward;
  the dice means surprise me, so it drops unheard picks and re-rolls. Say if that is wrong.

Still true, and now longer: **nothing in rounds 2 or 3 has been confirmed on a device.**

---

# Round 4 — set 2026-09-02, list handling

One item, agreed in detail with the owner first. `docs/ARCHITECTURE.md` §17 is the specification;
this is the order to build it in.

**Scope: Browse, search and history. Not the playlist** — the owner chose that, and `docs/BACKLOG.md`
A4 stays open for the playlist half.

- [x] **1. Tap plays, long-press selects**
      Rows lose the always-visible checkbox and the play button. Tap plays; the playing row is
      marked. Long-press starts selecting: the checkbox appears in the leading slot, tap ticks,
      and back leaves the selection with nothing ticked. Use the platform long-press timeout and
      make sure a scroll cancels it — the owner's complaint about another player is a long-press
      that fires at a twentieth of a second while he is scrolling, and then back throws him out of
      the list entirely.

- [x] **2. A three-dot menu on every row**
      Add to playlist, information, share the file, share a link, more from this author. The same
      actions the playlist row already offers, minus the ones that only make sense in a playlist.
      `TrackInfoDialog` exists in `PlaylistScreen` and should be shared rather than copied.

- [x] **3. The plus, for anything playing outside the playlist**
      The dock shows it only for Random today; playing from Browse or a search goes through
      `resultsQueue` and offers nothing. The owner called this the natural place to keep a track.

- [x] **4. No ordinals in Browse**
      The space goes to the checkbox, so a row does not change width when selection starts.

**Out of scope, written down instead:** `docs/WISHLIST.md` B17 (a mis-tap now costs a download) and
`docs/BACKLOG.md` A4, A16, A20.

## Round 4 — closed 2026-09-02

Done and merged. Two things found on the way that had nothing to do with lists:

- **Fifteen strings had no Polish**, accumulated across rounds 2, 3 and 4 — including every string
  added today. The app is bilingual from the first screen and I had been adding English only.
- **`%,1$d` throws.** The argument index has to precede the flag; written the other way round it
  is not a format specifier at all. It was in the song-length count, whose quantity is always
  `other` at 61,157, so the online screen would have crashed the moment the database was
  downloaded. Nobody had run that path yet.

---

# Round 5 — set 2026-09-02, reliability, indexing and review

This round has four phases and they happen in this order:

1. implement the six specified changes below;
2. review the complete codebase and write the findings down;
3. fix the most important confirmed findings from that review;
4. reconcile the complete project documentation with the resulting code.

Do not begin the review while one of items 1–6 is merely half-built, and do not update the final
project status before the review fixes have landed. The point of this order is to review the code
that will actually be handed back, then make the documentation describe that final state.

The rules at the top of this file continue to apply. In particular, each numbered implementation
item gets its own branch from `develop`, tests, debug build, commit and merge back into `develop`.
The review itself gets a branch and commit; each independent correction selected from it gets its
own branch and commit. Never merge to `master`.

> **On the size of this round.** Six implementation items — item 2 is plausibly the largest single
> piece of work this project has had — then a whole-tree review, then corrections, then a full
> documentation reconciliation. The risk is not failing to finish; it is Phase 2 being done on an
> exhausted context and Phase 4 then describing a state that was only half reviewed.
>
> So: **stopping cleanly between phases is a good outcome, not a failure.** If the work has to end
> early, end it on a merged, building, documented item with an honest note in `docs/STATUS.md`
> saying where it stopped and what was not reached. That is worth more than reaching Phase 4 with
> a review nobody should trust.

## Phase 1 — specified implementation

- [x] **1. Replace sc68 2.2.1 with 3.0.0b and re-measure C1/C2** *(14/30 → 30/30 SNDH; integrated)*

      Follow `docs/PLAN_FORMATS.md` §0. Fetch only the required SourceForge SVN tree, excluding
      the plugin and SDK ballast; pin the exact upstream revision and verify the downloaded content.
      Build and exercise the new library on the host before changing the Android integration.

      Re-run the same representative measurement as the existing baseline: thirty random SNDH
      files and ten `.sc68` files through the real backend path. Record plays, silent renders and
      load failures separately, and compare them with **16/30 SNDH** and **8/10 `.sc68`**. Integrate
      3.0.0b only if the evidence shows a material improvement and no regression in the already
      working cases; otherwise keep 2.2.1, record the measured blocker, and continue with item 2.

      Update the generated configuration deliberately, verify the actual source-file licences
      rather than trusting `COPYING`, preserve the replay-data licensing warning, build every
      Android ABI, and make the host probe a repeatable command rather than a one-off experiment.

- [x] **2. Build the persistent local-library index and probe content instead of extensions (A6/C4)**

      Scanning remains an explicit user action. Launching or returning to the app must never scan a
      granted folder as a side effect. A completed scan writes a durable index that later launches
      can read without reopening and re-probing every file.

      Identify candidate files through the same production backend probing path used for playback,
      not through their names. Persist enough information to avoid repeating expensive work:
      source identity, detected backend/format, display metadata, duration where known, subsong
      count, size and the information needed to determine whether the source is still available.
      A renamed or misleading extension must not decide whether a playable file enters the index.

      **Two collisions this item inherits, neither of them yours to solve by guessing.**

      *Probing while something plays.* sc68 keeps its 68000 emulator in global state, so opening a
      second instance while one is playing clobbers it — which is why background metadata resolution
      already waits for playback to stop (`docs/STATUS.md`, known limitations). Probing content is
      the same act, at library scale. The options are at least: refuse to scan while playing and say
      so; probe with the backends that are safe concurrently and defer the rest; serialise all
      native opens behind one lock; or keep filename filtering as a cheap first pass and probe only
      what it admits. Pick one, say which and why in the commit, and record the rejected ones in
      `docs/BACKLOG.md` — do not silently pick the easiest.

      *Backends change, and the index was built by them.* Item 1 may replace sc68, which changes
      what is playable. `docs/BACKLOG.md` A7 already carries this problem for catalogue indexes
      ("re-index after adding any backend") and it is handled there by a note to a human. A local
      index should do better, but how much better is open: a stored backend-set fingerprint that
      marks the index stale, a per-row record of which backend claimed the file so only the affected
      rows are re-probed, or an explicit rescan the user triggers. Whatever is chosen, an index built
      by a set of backends must not silently outlive them.

      Reserve the database version before editing the schema. Write a manual, non-destructive
      migration and test it against real SQLite with existing data. Test fresh creation and the
      migrated schema for equivalence, run the test through the production configuration path, and
      deliberately break the probing/indexing rule once to prove the regression test can fail.

- [x] **3. Put a real bound on the fetched-file cache (Q5)**

      Adopt a **512 MB default ceiling with least-recently-used eviction**. This round establishes
      the mechanism and the default; exposing the value as a user setting remains part of A13.

      Enforce the budget after a successful cache write and on application start, so upgrading an
      existing installation also converges on the limit. Never count or evict permanent downloaded
      data such as the ASMA archive or HVSC song-length database, a file currently being read or
      prefetched, an incomplete download, or a `FileProvider` share copy still inside its retention
      window. Failed and interrupted downloads must not become valid cache entries.

      **A ceiling stops growth; it does not give the disk back.** ASMA (20 MB) and the HVSC song
      lengths (5.2 MB) are deliberately exempt, so real occupancy is the cap plus permanent
      downloads plus the index, and nothing in the app can delete any of it (`docs/BACKLOG.md` A13).
      Deleting stays in A13, but **showing the numbers is nearly free and should not wait for it**:
      surface what the cache holds and what the permanent downloads hold, wherever it costs least.
      If even that turns out to need a UI decision, stop and write it down rather than inventing a
      screen.

      Keep eviction and ordering logic independent of Android time and filesystem APIs where
      practical. Add deterministic tests for the byte ceiling, LRU ordering, protected files,
      interrupted writes and start-up cleanup. Document exactly what counts toward the 512 MB.

- [x] **4. Restore the correct Browse position on back navigation (A20)**

      Every Browse level keeps its own scroll state for the life of that Browse session. Returning
      from tracks to authors, or from authors to formats, must bring back the row the user entered,
      even when the list changed while they were below it. Restore by stable row identity first and
      use the saved index/offset only as a fallback.

      Reuse the existing near-target animate / far-target jump helper rather than creating another
      scrolling policy. Preserve the semantics settled in `docs/ARCHITECTURE.md` §17: leaving a
      selection is one back step, an ordinary descent returns one level, and a jump made by "More
      from this author" returns directly to the playlist. Do not implement A3's draggable scrollbar
      or top/bottom controls as part of this item.

> **Items 5 and 6 ask for tests that cannot be written against the code as it stands, and this is
> the one thing to settle before starting either.**
>
> Both call for regression tests at the level of browse state, item 6 explicitly "through the same
> callbacks the UI uses". Today that is impossible: the test dependencies are `junit` and
> `sqlite-jdbc`, there is no Robolectric and no emulator, and `PlaybackController` takes a `Context`
> and owns a `SQLiteOpenHelper`, so it cannot be constructed on the JVM. Every existing test file
> tests something deliberately free of Android — `PlayQueue`, `SchemaSql`, `Catalogue`,
> `SongLengths`.
>
> The options, none of them chosen here:
>
> - **Extract the browse navigation state** into a plain Kotlin unit and test that, the way
>   `PlayQueue` was extracted from playback and `SchemaSql` from storage. The project has a strong
>   precedent and A20 would gain a single place to live — but it is a refactor sitting underneath
>   two bug fixes, and refactoring to make a bug testable can be how a small fix becomes a large one.
> - **Add a test dependency** (Robolectric or similar) and test the controller as it is. Honest and
>   quick to reach, and it adds a heavyweight dependency to a project that has kept its test
>   toolchain to two artifacts on purpose.
> - **Fix both without a state-level test**, verifying by build and by reasoning, and say plainly in
>   `docs/STATUS.md` that they rest on a device check. Cheapest, and it is exactly the bar that let
>   every defect the owner found today through.
>
> Choose per item rather than once for both if that fits better — C7 may well be reachable more
> cheaply than C6. State the choice and its reason in the commit, and record the rejected options in
> `docs/BACKLOG.md` so the next person does not re-derive them. **Do not silently downgrade the
> requested test to "it compiles".**

- [x] **5. C6 — a fresh entry into Online catalogues starts at the catalogue list**

      Reported by the owner on 2026-09-02. After leaving Browse and opening **Browse → Online
      catalogues** again, the app currently sometimes restores an old folder/search-like state or
      shows an empty view. A new entry must always show the root list of online catalogues.

      This is the known defect recorded as `docs/STATUS.md` C6 and shares state machinery with A20.
      The reset happens when a new Browse session enters the Online catalogues domain, not during
      recomposition and not while navigating inside an existing session. It must clear stale
      catalogue hierarchy, query/result and transient selection state without deleting downloaded
      indexes or the per-level scroll state that item 4 needs within the current session. Add a
      state-level regression test that enters a catalogue deeply, leaves Browse, enters Online
      catalogues again and sees the catalogue root with real catalogue rows rather than an empty
      list.

- [x] **6. C7 — "Add to playlist" from a Browse row menu stays in Browse**

      Reported by the owner on 2026-09-02. Choosing **Add to playlist** from a track's three-dot
      menu currently dismisses Browse and returns to the playlist. Adding is not navigation: keep
      the user at the same Browse domain, hierarchy level and scroll position, close only the menu
      or destination picker, and leave the added row visible.

      This is the known defect recorded as `docs/STATUS.md` C7. It applies to an action initiated
      from one row's menu, including its destination-picker route. Preserve the existing behaviour
      of the explicit bulk-add completion action, which closes Browse because that flow is finished.

      Confirm the row-menu action in place. When the target is not visible, the message names the
      playlist; when duplicate prevention rejects the addition, say so rather than silently
      navigating or pretending it succeeded. Preserve the existing immediate-write rule for a
      non-active playlist and the active playlist's draft semantics. Cover both the direct
      active-playlist action and the destination-picker route with regression tests through the
      same callbacks the UI uses.

## Phase 2 — thorough code review

After all six items above are complete, review the **entire current `develop` tree**, including the
Kotlin application and domain code, JNI/native engine and every decoder adapter, SQLite schema and
migrations, services/media session, catalogue and network/cache code, Compose state and gestures,
resources, build configuration and project scripts. This is not a formatting pass.

Look especially for:

- data loss, destructive migration paths and identities that can collide;
- crashes, ANRs, lifecycle leaks, stale Compose state and coroutine/native concurrency races;
- use-after-close, double ownership, cancellation and audio-thread work that can block;
- corrupt or partial downloads being accepted, unbounded storage and unsafe URI/file exposure;
- production wiring that differs from what tests instantiate;
- actions that silently do nothing, accessibility gaps, English/Polish drift and malformed formats;
- incorrect capability claims, queue/history edge cases and regressions between local, indexed,
  archive and live-search sources;
- release-build, R8/JNI, signing and dependency/licence risks.

**Two rules about the review itself, because it is being done by whoever wrote the code.** A review
of one's own work mostly re-affirms its own decisions, and every defect found in this project so far
was found by the owner using it rather than by anyone reading it.

1. **`confirmed` requires evidence that can fail** — a failing test, a deterministic diagnostic, a
   measurement. Reasoning, however good, produces an `unverified risk`, and there is no shame in a
   review that is mostly those.
2. Findings in code written earlier in this same run deserve more suspicion, not less. Where a
   choice was made for a stated reason, check the reason still holds rather than the code still
   matches it.

Write the result to **`docs/review.md`** in English. Give each finding a stable identifier, severity
(`critical`, `high`, `medium`, `low`), concrete file/location, mechanism, user impact, evidence or
reproduction route, proposed correction and status. Separate confirmed defects from unverified
risks and from optional improvements. Do not inflate the report with style preferences, and do not
call something fixed until the correcting commit and its verification are named.

## Phase 3 — corrections selected from the review

Fix every confirmed **critical** and **high** finding that can be corrected without a new product
decision or credentials, then the highest-impact **medium** findings while they remain independent
and testable. Priority is: data loss/corruption, crash or native-memory safety, playback ownership
and concurrency, security/privacy, migrations and persistent state, then user-visible behavioural
failures. A broad architectural rewrite is not an automatic consequence of a review finding.

For every selected finding:

1. reproduce the mechanism with a failing test or a deterministic diagnostic;
2. make the smallest correction that removes the cause rather than masking the symptom;
3. break the corrected behaviour deliberately and confirm that its test fails;
4. run `./scripts/test-protracktor.sh` and `./scripts/build-debug.sh`;
5. update the finding in `docs/review.md` with its fixing commit and verification.

Leave findings that require the owner's UI/product choice, external credentials, unavailable
hardware or a large independent feature open. Put the required decision and realistic options in
`docs/OPEN_QUESTIONS.md` or the work in `docs/BACKLOG.md`; do not guess and do not label the review
blocked merely because one finding cannot be fixed unattended.

When all selected corrections are in, run the full test suite from genuinely executed tasks rather
than trusting `UP-TO-DATE`, then build both debug and release artifacts. Verify non-zero test counts,
the packaged JNI symbols and resources, and report artifact paths and signing identity. This still
does not confirm any behaviour on a phone.

## Phase 4 — reconcile all documentation

Only after the implementation and review corrections are final, read every project Markdown file
again and make the set agree with the code and with itself. At minimum reconcile `README.md`, this
file, `docs/STATUS.md`, `docs/ARCHITECTURE.md`, `docs/BACKLOG.md`, `docs/WISHLIST.md`,
`docs/OPEN_QUESTIONS.md`, both plan documents, `docs/BUILD.md`, `docs/LICENSES.md`,
`docs/REQUIREMENTS.md` and `docs/review.md`.

Move or strike completed work without erasing its history; keep open decisions open; correct stale
claims rather than adding a contradictory paragraph. Update schema versions, dependency versions,
test counts, measured decoder results, known defects, branch/merge state and build instructions to
their exact final values. Distinguish host measurement, compilation and unit-test evidence from
things the owner has actually confirmed on a device.

Finish with one documentation commit, `./scripts/test-protracktor.sh`,
`./scripts/build-debug.sh`, `git diff --check`, and a concise hand-off containing:

- the six requested outcomes and how each was verified;
- sc68 before/after measurements;
- review findings by severity, including what was fixed and what remains;
- database/cache compatibility notes;
- debug and release artifact paths and signing status;
- an explicit list of everything still requiring a device check or owner decision.

## Round 5 — closed 2026-09-03

All four phases done. Six implementation items, a whole-tree review, its corrections, and this
reconciliation.

**What the review was for.** It found four confirmed defects, and the first of them —
`docs/review.md` R1 — would have made the largest piece of work in the round look like it had broken
Atari ST playback entirely: every SNDH and `.sc68` file stopped on the first audio callback. That
code was a few hours old. The rule written into this file before the round started — *findings in
code written earlier in the same run deserve more suspicion, not less* — earned its place on the
first serious look, and would not have been there if the round had gone straight from building to
documenting.

**Three constraints added before the round changed what got built.**

- The **sc68 global-state collision** turned out not to exist in 3.0.0b, which is instance-based and
  measured safe across four threads. A documented limitation was lifted rather than worked around.
- **Index invalidation** stopped being theoretical the moment item 1 landed: replacing sc68 made
  every earlier "nothing can play this" wrong on the same morning.
- The **testability question** for items 5 and 6 was answered per item, as the note allowed. C6 was
  extracted and has real tests; C7 is two callbacks with no logic to test, and says so rather than
  inventing a seam to claim coverage.

**Stopping cleanly between phases was allowed and was not needed.**

**What nobody has done: run any of it.** `AGENTS.md` says to leave a branch unmerged until the owner
has tested it on his phone; the exception is an unattended run, which this was. Everything here is
in `develop` having been verified by compilation, 87 unit tests and host probes — and by nothing
else.

---

# Round 7 — set 2026-09-09, the web player's face

*The owner, at half past midnight: "przerobienie GUI webowego. Musi wyglądać prawie tak, jak
aplikacja androidowa." He asked whether it can be done and said there is no hurry.*

## The answer, before the list

**Yes, and the reason it is achievable is that it is a smaller job than it sounds.** "Look almost
like the app" is a question about *appearance*, and appearance is the half of the Android UI that
can be read out of source and reproduced exactly. The expensive half — browsing 516,107 Modland
rows, the search filters, the storage screen, the folder grants — is **not** being asked for and
must not be built (see out of scope).

**One risk, stated first because it shapes everything.** I cannot see either screen. There is no
emulator here and no browser; the Android UI exists to me as Compose source, and the page exists as
markup. So fidelity comes from reading the source carefully, and the only judge is the owner. That
argues for small steps with a build at the end of each, not one long stretch ending in a reveal.

**The most useful thing the owner could add** was three or four screenshots — and he sent two within
the hour, of the playlist with the dock and of Browse. They earned their place immediately: they
corrected the palette (below), and they settle proportion in a way that reading `Modifier.padding`
values does not. Still missing, and worth having when convenient: **Now Playing expanded**.

## What "almost like the app" means, concretely

The app's theme is **stock Material 3** — `darkColorScheme()` and `lightColorScheme()` with no
overrides, plus wallpaper colours on Android 12+ (`ui/theme/Theme.kt`). So looking like the app means
looking like Material 3, and the exact tokens are already extracted:
**`docs/reference/material3-dark.json`**, disassembled from the material3 artifact this build links.
Do not retype them from memory or sample them from a screenshot.

The anchors, so the shape is visible without opening the file:

| role | dark |
| --- | --- |
| background / surface | `#141218` |
| surface container | `#211F26` |
| surface container high | `#2B2930` |
| on surface | `#E6E0E9` |
| on surface variant | `#CAC4D0` |
| primary | `#D0BCFF` |
| on primary | `#381E72` |
| secondary container | `#4A4458` |
| on secondary container | `#E8DEF8` |
| outline variant | `#49454F` |

### Corrected the same night, from the owner's screenshots

He sent two screenshots, and they overturn the paragraph above: **his phone is not showing the
baseline palette.** It runs dynamic colour from his wallpaper, so the app is magenta and violet where
the baseline is lavender on near-black. Sampled from the PNGs rather than eyeballed —
`docs/reference/app-colours.json`:

| role | his phone | baseline |
| --- | --- | --- |
| background | `#180523` | `#141218` |
| surface container | `#2D133C` | `#211F26` |
| secondary container (the labelled actions) | `#622B80` | `#4A4458` |
| primary (the play button) | `#EE83ED` | `#D0BCFF` |
| on surface | `#F5DDFD` | `#E6E0E9` |
| on surface variant | `#BCA0C7` | `#CAC4D0` |

**So "looks like my app" means his scheme, not the baseline.** The page ships his colours as its
default tokens, because that is what was asked for; a browser cannot read a wallpaper, so anyone
else sees his scheme rather than their own. Keep both files: the baseline is still what a phone with
dynamic colour off shows, and the page should be built on tokens so swapping is one block of CSS.

## The rules

Unchanged from every round: `AGENTS.md`, and `/mnt/workspace/AGENTS.md`.

1. **One item, one branch, one merge**, off `develop`. Never `master`.
2. **Every item ends with `./scripts/test-protracktor.sh` green and the page loading.** The web work
   has no unit tests worth the name yet; "the page loads and the console is clean" is the bar, and
   the server is the way to check it (`scripts/serve-web.sh`).
3. **Do not claim anything was seen.** Nothing in this round can be confirmed by the agent doing it.
   Say what was changed and what remains unjudged.
4. **A decision the owner has not made is not yours.** Write it into `docs/BACKLOG.md` and move on.
   Implementation choices are yours and always were.
5. Tick items off here as they land.

## Explicitly out of scope

- **Browsing the catalogues in the browser.** 5,672 lines of `ui/` exist on the phone and the phone
  keeps them (`docs/PLAN_HANDOFF.md` §5a). The page plays what it is handed.
- **Search, settings, storage, folder grants, the notification.** Same reason, and several of them
  have no meaning in a browser at all.
- **Compose Multiplatform.** It was considered and rejected with reasons in `PLAN_HANDOFF.md` §5a;
  this round is the cheaper half of what it would have bought.
- **The remote-control mode** — phone as pilot, browser as player. That is the owner's wishlist item
  from 2026-09-08 and it waits on the transport being fixed (see round 7 item 1).
- **Light theme**, unless it falls out for free. The app follows the system; the page may start dark.

## The list

- [x] **1. Replace the event stream with long polling** *(done 2026-09-09; verified locally, not yet through the tunnel — the Pi runs its own copy)*
      *First, because nothing else in this round can be seen working without it.* Measured
      2026-09-09 (`docs/PLAN_HANDOFF.md` §5c): `text/event-stream` does not survive a Cloudflare
      quick tunnel — the server reports delivering, the page receives nothing, and two rounds of
      anti-buffering headers and two kilobytes of padding did not move it.

      Replace `/pair/<room>/events` with `/pair/<room>/next?since=<n>`: the server holds the request
      until there is a message or about twenty-five seconds pass, then answers with an ordinary JSON
      body. The page loops. Keep the sequence number so a reconnect cannot miss a message, and keep
      the "last message" behaviour that lets a reloaded page pick the queue back up.

      What `EventSource` did for free and now has to be written: reconnection with a backoff, and
      not hammering the server when it is unreachable.

- [x] **2. The dock** *(done 2026-09-09)*
      The one component the owner sees more than any other, and the one that decides whether the
      page reads as Protracktor. Source: `ui/PlayerDock.kt`. Reproduce, in order of visible
      importance: the surface and its elevation, the title and subtitle block with the format label,
      the transport row — shuffle, previous, the 64dp filled play button, next, repeat — the seek
      bar, and the subsong strip when a file has more than one tune.

      The transport buttons are 48dp targets with a circular ripple and a 34dp glyph on the play
      button. Long press on next and previous skips a whole file on the phone; the page has no
      equivalent yet and does not need one this round.

- [x] **3. The playlist** *(done 2026-09-09)*
      Source: `ui/PlaylistScreen.kt`. A row is a leading position or playing indicator, a title, a
      subtitle that is the source path, and a trailing overflow. The playing row is tinted with
      `primary`. Reproduce the row, the spacing and the tint; the drag handle, the selection mode
      and the swipe actions are phone-only and out of scope.

- [x] **4. The top bar and the shell** *(done 2026-09-09)*
      Source: `ui/ProtracktorApp.kt`. A title, and actions drawn as an icon with its name
      underneath — the owner asked for that shape twice (`docs/BACKLOG.md` A16, A17, A23) and it is
      one of the app's few departures from stock Material. `ui/LabelledAction.kt` is the component.

      The page's actions are not the phone's: it has no Browse and no Settings. What it has is the
      pairing code and the paste box, and both should live in this vocabulary rather than in the
      improvised one they use now.

- [x] **5. Now Playing** *(done 2026-09-09, with the subsong strip)*
      Source: `ui/NowPlaying.kt`. On the phone it expands upward from the dock over the playlist.
      In the browser the same content can simply be a panel; what matters is that it carries the
      same fields — title, author, format, year, duration, subsong strip — laid out the same way.

- [ ] **6. Type and spacing**
      Last on purpose: it is the pass that turns "the same components" into "the same app". The app
      uses Material 3 typography unmodified, which maps onto a browser as a scale, not as a font —
      Roboto is not on every desktop and Google Fonts is a network dependency the page does not
      otherwise have. Use the system stack and match the *scale* and the weights; record the choice
      and why in the page's own comments.

## What this round is not allowed to lose

The page today is small, fast and has one job. Every item above adds markup, and the failure mode is
a page that looks like Protracktor and takes three seconds to become useful. **Measure the page
weight before and after** — it is a `wc -c` on three files, and the engine that dwarfs them is
already 0.76 MB over the wire. If the front end approaches a tenth of that, something has gone
wrong.


---

# Round 6 — set 2026-09-04, Amiga, the archives it unlocks, and disk

## What belongs in a goal round, and why this one may be large

The owner asked for the *character* of this work to be defined before the list, so that the list can
afford big items. The rule that comes out of five rounds of evidence:

> **An item belongs in a goal round when its verdict can be produced on this machine.**

Not "can be written without him" — **can be proven** without him. The distinction is the whole
lesson of the project so far.

Everything that ended in a number or a test went well unattended: sc68 3.0.0b (14/30 → 30/30 SNDH),
libsidplayfp (30 of 30 with no ROMs), ASMA, the Android-free extractions and their tests, and the
Phase-2 review — which found R1, a bug that would have stopped **every** SNDH on its first audio
callback and that no amount of careful writing had caught.

Everything whose verdict lived on his phone or in his taste went badly: C10 took four wrong
diagnoses before the answer turned out to be "it was a debug build"; the subsong UI, the placement
of its toggle, and the silence after adding to a playlist were each decided alone and each decided
wrongly; and a single SMB log line became a "fact" in five documents.

So three shapes qualify, and they are what allows an item to be big:

- **A backend or a catalogue** — probed on the host against a real corpus, verdict is a number.
- **A defect or a piece of machinery** — extracted to Android-free code and covered by tests.
- **A document** — correctness checkable by reading it.

And a fourth phase that has earned its place: **review, then corrections selected from it**.

Disqualified regardless of how tempting: anything that ends in "it works on the device"
(`docs/STATUS.md` C3, the unconfirmed console formats), and anything where the owner's taste is the
judge — the shape of a settings screen, per-format playback UI, what a control should be called.

## The rules

The rules at the top of this file apply unchanged. Restating the two that matter most here:

- **One item, one branch from `develop`, one merge back.** Never `master`.
- **Every item ends with `./scripts/test-protracktor.sh --really` and `./scripts/build-debug.sh`
  green.** `--really` exists because a cached run once reported 87 tests "passing" in one second.
- **Do not claim anything works on a device**, and do not decide anything reserved for the owner.
  Where an item needs his decision, produce the *measurement* that makes his decision easy — that is
  what turned the sc68 replay-binary question from an argument into a choice.

## Explicitly out of scope

- **The sc68 replay-binary licence decision.** Deferred by the owner on 2026-09-03 and still
  deferred on 2026-09-04: *"pogadamy jutro"*. Item 1 raises the identical question for UADE. **Do
  not settle either.** Measure both and write the numbers down.
- **The settings screen as a screen** (`docs/BACKLOG.md` A13). Item 3 takes only the half that is a
  defect. Its layout, where it is reached from, and `docs/OPEN_QUESTIONS.md` Q1 are a conversation.
- **`docs/STATUS.md` C3** — needs his phone and his network.
- **`docs/BACKLOG.md` A19** — deferred, and he asked not to have it raised unprompted.
- **`master`.**

## Phases

1. implement the items below, in order;
2. review the complete codebase and write the findings down;
3. fix the most important confirmed findings;
4. reconcile all documentation with the code that resulted.

**Stopping cleanly between items is a good outcome.** Item 1 is the largest single piece of work
this project has attempted and may be the entire round. Ending on a merged, building, documented
item with an honest note in `docs/STATUS.md` about where it stopped is worth more than reaching
Phase 4 on an exhausted context.

## Phase 1 — the list

- [x] **1. UADE — the Amiga custom formats** *(measured, not integrated; corrected same-corpus result: 196/300 plays — `docs/PLAN_FORMATS.md` §4)*

      The largest body of music left. TFMX, Hippel, Future Composer, David Whittaker, Jochen Hippel,
      Mark Cooksey and the rest of the Amiga custom players are tens of thousands of files in
      Modland that this app currently cannot open at all. `docs/BACKLOG.md` A5 has kept this last on
      purpose; the reasons it was last are now the reasons to do it — everything easier is done.

      **Prove it on the host before integrating.** That has caught something every single time: sc68
      2.2.1's half-broken SNDH, the SidMon files masquerading as `.sid`, and R1. Build the probe,
      run a real Modland corpus through it, and report a pass rate the way sc68 and libsidplayfp
      were reported.

      **This is not a library, it is an emulator.** UADE runs the original m68k replay routines
      under an emulated Amiga, so expect the integration to look unlike the other four backends:
      a core to build, replay binaries to locate, and a score file mapping formats to players. Budget
      for that honestly rather than assuming the `Backend` interface absorbs it — and if it does not
      fit that interface, say so in the commit rather than distorting the interface quietly.

      **The replay binaries are the owner's decision, not this item's.** They are the same question
      sc68 raised and he has deferred twice. Do exactly what was done for sc68: **measure** what
      each bundling choice costs in tracks played, and write the numbers into `docs/LICENSES.md`.
      A number turns his decision into a choice; an argument does not.

      **Subsongs come free and must be wired.** UADE files are frequently multi-tune, and the
      `subsongCount`/`selectSubsong` boundary already exists, zero-based, precisely so a new backend
      only has to fill it in.

- [x] **2. Re-index, and the archives UADE unlocks** (`docs/BACKLOG.md` A7) *(the staleness marker would **not** have fired — fixed; AMP publishes no index and its `robots.txt` asks automated clients off its modules)*

      **Re-indexing is not optional after item 1.** The catalogue index deliberately keeps only
      entries whose filename some backend might handle, so every Amiga custom format is *absent*
      from the existing Modland index rather than present-and-failing. Item 1 lands and changes
      nothing visible until this runs. The decoder-set staleness marker added in round 5 is what
      makes this detectable rather than silent — check that it actually fires here, because this is
      the first real occasion it has had.

      **AMP (Amiga Music Preservation)** is the archive that was blocked on this. `docs/PLAN_CATALOGUES.md`
      records what is known. Two halves, and the second is the usual blocker: an index parser or a
      live search against whatever it publishes, and `pathFrom` — the inverse of `urlFor` — because
      "more from this author" is abstract and the compiler will ask.

      **If AMP publishes nothing machine-readable, stop and write that down.** Do not scrape a
      website into a fragile parser to avoid reporting an obstacle. The same instruction was given
      for ASMA and it was the right one.

- [x] **3. Disk the app takes and cannot give back** *(every stored thing now has a delete beside it; placed in Browse as a stated placeholder for A13)* (the half of `docs/BACKLOG.md` A13 that is a defect)

      The ASMA archive (20 MB), the HVSC song lengths (5.2 MB) and every downloaded catalogue index
      have **no way to be deleted at all**. That is not a missing preference, it is the app taking
      storage with no route back — and on a phone that is a defect. The fetched-file cache already
      has a budget (`CacheBudget`, 512 MB, LRU); these do not.

      Show what is held and let it go, per item, with sizes. What is deleted must be re-fetchable —
      deleting the ASMA zip has to leave the app able to download it again, not broken.

      **Where it lives is an implementation guess, and should be made as one.** The app has no
      top-level overflow menu and adding one is a navigation change the owner has reserved
      (`docs/OPEN_QUESTIONS.md` Q1). Granted folders are managed from Browse today, so Browse is
      the honest place for "what this app is storing" and needs no new navigation. Say in the commit
      that this is a guess made to avoid prejudging Q1, so it can be moved cheaply when A13 is
      actually designed.

- [x] **4. Repeat-one and subsongs** (`docs/STATUS.md` C13) *(both faults; the sc68 one demonstrated on 6 of 6 multi-tune files, the Kotlin one extracted to `SubsongAdvance` with tests)*

      Reported 2026-09-04. Two independent faults behind one symptom, and C13 has both in full.

      `Sc68Backend::rewind()` hardcodes `sc68_play(sc68_, 1, ...)`, so repeat-one on a multi-tune
      SNDH replays the *first* subsong rather than the one playing. Provable on the host through
      `probe_render.c`: select a subsong, rewind, see which one comes back. ASAP, GME and
      libsidplayfp already remember; sc68 is alone.

      Separately, `onTrackEnded` walks to the next subsong **before** consulting the repeat mode, so
      under "play all subsongs" repeat-one is never consulted at all — and the last subsong then
      loops forever on three backends and jumps to the first on the fourth.

      **What repeat-one means here is a judgement, so make it explicitly.** "One" everywhere else in
      this app means one row of the playlist, and a multi-tune file is one row: with all-subsongs on,
      the end of the last subsong returns to the first and the file loops; with it off, the current
      subsong loops. Implement that and name it as a guess in the commit — the owner has corrected
      exactly this class of reasoning before, when a transport button would have meant different
      things depending on the file.

- [x] **5. The documents the Play Store needs** (`docs/BACKLOG.md` A14) *(`docs/PRIVACY.md`, `docs/PLAY_STORE.md`; every claim checked against the source, and one unused permission removed)*

      No device, no taste, and they block publishing. Three things, all writing:

      A **privacy policy** and the matching **data-safety declaration**. Ours is unusually short and
      that is worth stating plainly rather than padding: no analytics, no accounts, no crash
      reporting, nothing collected, and the only network traffic is fetching music from archives the
      user chose. Write it so it can be published at a URL, because the listing requires one.

      A note on **the GPL and the store** — distributing GPL-3 software through Play is fine, the
      obligation is that source is offered to recipients, and the public repository discharges it.
      Written down once so it is not re-litigated.

      **Not the listing itself.** Screenshots, feature graphic, content rating and the store text are
      his account and his name.

## Phase 2 — review — DONE 2026-09-04, `docs/review-round-6.md`

Six findings, four of them in code written earlier the same day, and the most serious is a data race
introduced *while fixing* C13 — eleven lines above the comment explaining why its neighbours are
atomic. The rule about suspecting this run's own code earned its place for the second round running.

The same standard as round 5, which is the reason that phase exists: read the whole tree with fresh
suspicion, write every finding down with its evidence, and rank by what would actually hurt someone
using the app. Round 5's review found a bug that would have stopped every SNDH on its first audio
callback — after the same code had been written, reviewed in passing and merged.

Treat code written earlier in this same run with **more** suspicion, not less. That instruction was
what earned R1.

## Phase 3 — corrections — DONE 2026-09-04

All five findings fixed, on two branches — `fix/review-round-6-engine` (R1, R2) and
`fix/review-round-6-storage` (R3, R4, R5) — because each branch holds one problem rather than one
finding, and R1/R2 are the same problem seen twice, as are R3/R4.

Each independent correction gets its own branch and commit. Fix what the review confirmed; record
what was deliberately not fixed and why. A finding dismissed with a reason is a result; a finding
quietly dropped is not.

## Phase 4 — reconcile the documentation — DONE 2026-09-04

`docs/STATUS.md`, `docs/ARCHITECTURE.md`, `docs/BACKLOG.md`, `docs/WISHLIST.md`, `docs/LICENSES.md`,
`docs/PLAN_FORMATS.md`, `docs/PLAN_CATALOGUES.md`, `BUILD.md`, `AGENTS.md`. A document that lies is
worse than one that is missing.

State plainly, as round 5 did, that **nobody has run any of this on a device** — the unattended
exception in `AGENTS.md` is why it is in `develop` at all.

## Round 6 — closed 2026-09-04

**All five items, the review, the corrections and the documentation.** Stopping cleanly between
items was allowed and was not needed.

**The round's own subject turned out to be the least of what it produced.** Item 1 was UADE, and the
honest answer is not to integrate it yet: the current top-25 sample proves successful reach into
format directories holding 5,799 Modland files rather than the tens of thousands
`docs/BACKLOG.md` A5 assumed, it needs a licence decision worse-documented than sc68's with no
middle option, and it is either a second process or a single-instance emulator inside ours.
Measuring that found something cheaper — **5,653 files the app could already play and
never offered**, because `SupportedFormats` listed `med` and `okt` while Modland files them as
`.mmd1` and `.okta`. Five strings and a re-index beat a whole backend.

**Correction after upstream reproduced Hippel COSO:** the original probe enabled UADE's strict
content-only mode and therefore falsely reported Hippel COSO as 0/12. With uade123-compatible
defaults, the same historical corpus is 196/300 rather than 184/300, and Hippel COSO is 11 full
plus 1 silent first buffer. `docs/PLAN_FORMATS.md` §4 records the rerun and the separate current-app
measurement.

**Two things went wrong on the way, and both were caught by measuring rather than reasoning.**
Counting UADE's reach by filename prefix alone returned 807 files where the truth is 29,127, because
Modland stores half these formats with the marker at the other end of the name. And the first probe
used an API whose own header says it does not do multifile — reporting TFMX, the largest Amiga
custom format in the archive, as unplayable. Either would have argued this backend was worthless.

**The rule about suspecting this run's own code earned its place for the second round running.**
Four of the review's six findings are from code written earlier the same day, and the most serious
is a data race introduced *while fixing* C13 — a plain `int` shared with the audio thread, eleven
lines above the comment explaining why its neighbours are atomic.

**Three items were answered by finding out rather than by building.** AMP publishes no index and its
`robots.txt` asks automated clients off its modules, so it is not blocked on UADE at all. The
re-index half of item 2 turned out to be a defect: the staleness marker watched the decoders and not
the name list that equally decides what an index holds, so it would not have fired on its first real
occasion. And item 5's privacy claims were checked against the source instead of written from
memory, which is how `ACCESS_NETWORK_STATE` was found declared and unused.

**What nobody has done: run any of it.** `AGENTS.md` says to leave a branch unmerged until the owner
has tested it on his phone; the exception is an unattended run, which this was. Everything is in
`develop` having been verified by 124 unit tests, host probes and compilation — and by nothing else.
