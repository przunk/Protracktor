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

## Phase 1 — specified implementation

- [ ] **1. Replace sc68 2.2.1 with 3.0.0b and re-measure C1/C2**

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

- [ ] **2. Build the persistent local-library index and probe content instead of extensions (A6/C4)**

      Scanning remains an explicit user action. Launching or returning to the app must never scan a
      granted folder as a side effect. A completed scan writes a durable index that later launches
      can read without reopening and re-probing every file.

      Identify candidate files through the same production backend probing path used for playback,
      not through their names. Persist enough information to avoid repeating expensive work:
      source identity, detected backend/format, display metadata, duration where known, subsong
      count, size and the information needed to determine whether the source is still available.
      A renamed or misleading extension must not decide whether a playable file enters the index.

      Reserve the database version before editing the schema. Write a manual, non-destructive
      migration and test it against real SQLite with existing data. Test fresh creation and the
      migrated schema for equivalence, run the test through the production configuration path, and
      deliberately break the probing/indexing rule once to prove the regression test can fail.

- [ ] **3. Put a real bound on the fetched-file cache (Q5)**

      Adopt a **512 MB default ceiling with least-recently-used eviction**. This round establishes
      the mechanism and the default; exposing the value as a user setting remains part of A13.

      Enforce the budget after a successful cache write and on application start, so upgrading an
      existing installation also converges on the limit. Never count or evict permanent downloaded
      data such as the ASMA archive or HVSC song-length database, a file currently being read or
      prefetched, an incomplete download, or a `FileProvider` share copy still inside its retention
      window. Failed and interrupted downloads must not become valid cache entries.

      Keep eviction and ordering logic independent of Android time and filesystem APIs where
      practical. Add deterministic tests for the byte ceiling, LRU ordering, protected files,
      interrupted writes and start-up cleanup. Document exactly what counts toward the 512 MB.

- [ ] **4. Restore the correct Browse position on back navigation (A20)**

      Every Browse level keeps its own scroll state for the life of that Browse session. Returning
      from tracks to authors, or from authors to formats, must bring back the row the user entered,
      even when the list changed while they were below it. Restore by stable row identity first and
      use the saved index/offset only as a fallback.

      Reuse the existing near-target animate / far-target jump helper rather than creating another
      scrolling policy. Preserve the semantics settled in `docs/ARCHITECTURE.md` §17: leaving a
      selection is one back step, an ordinary descent returns one level, and a jump made by "More
      from this author" returns directly to the playlist. Do not implement A3's draggable scrollbar
      or top/bottom controls as part of this item.

- [ ] **5. A fresh entry into Online catalogues starts at the catalogue list**

      Reported by the owner on 2026-09-02. After leaving Browse and opening **Browse → Online
      catalogues** again, the app currently sometimes restores an old folder/search-like state or
      shows an empty view. A new entry must always show the root list of online catalogues.

      This reset happens when a new Browse session enters the Online catalogues domain, not during
      recomposition and not while navigating inside an existing session. It must clear stale
      catalogue hierarchy, query/result and transient selection state without deleting downloaded
      indexes or the per-level scroll state that item 4 needs within the current session. Add a
      state-level regression test that enters a catalogue deeply, leaves Browse, enters Online
      catalogues again and sees the catalogue root with real catalogue rows rather than an empty
      list.

- [ ] **6. "Add to playlist" from a Browse row menu stays in Browse**

      Reported by the owner on 2026-09-02. Choosing **Add to playlist** from a track's three-dot
      menu currently dismisses Browse and returns to the playlist. Adding is not navigation: keep
      the user at the same Browse domain, hierarchy level and scroll position, close only the menu
      or destination picker, and leave the added row visible.

      Confirm the action in place. When the target is not visible, the message names the playlist;
      when duplicate prevention rejects the addition, say so rather than silently navigating or
      pretending it succeeded. Preserve the existing immediate-write rule for a non-active
      playlist and the active playlist's draft semantics. Cover both the direct active-playlist
      action and the destination-picker route with regression tests through the same callbacks the
      UI uses.

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
