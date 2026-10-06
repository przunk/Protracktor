# Random feedback: stacked handoff and merge record

Owner-authorised on 2026-10-06. The local PR drafts below were not opened on GitHub. After the
combined release APK handoff the owner reported successful operation and authorised sequential
local merges. All three are now in develop; report sources and uncertainty are preserved in
STATUS C97–C99 and TESTING. VersionName remains 1.0.0. No push or publication occurred.

| Item | Head | PR base | State |
| --- | --- | --- | --- |
| C97 | `fix/random-scope-history` | `develop` (`a72d098` at start) | Accepted; merged first as `43963a0` |
| C98 | `fix/random-return-navigation` | `fix/random-scope-history` (`f7dfceb`) | Accepted; merged second as `ed2b4aa` |
| C99 | `fix/random-filter-responsiveness` | `fix/random-return-navigation` (`fccc802`) | Accepted; merged third as `98e605f` |

**Merge verification:** each merge tree matched its branch tip exactly. The final merge's
application sources match `9110e62`; only handoff documents differ. The previous 676-test/full-lint
evidence and owner acceptance concern that candidate, not a freshly built post-merge APK.
Tests/builds were not rerun for this content-identical integration. The owner's overall acceptance
does not establish individual scenario results, precise timings or a second external-user retest.

**Subsequent remote synchronization, 2026-10-06:** at the owner's request, fetched origin/develop
to `485660b` and merged both accepted web PRs as `3cea6b0`. PR #2 contains `2390307`/`75f3903`;
PR #1 contains `31b9eb9`/`485660b`. The integrated sources passed the forced 676-test/full-lint
project gate again (341 s). No push or publication; origin/develop is an ancestor of local develop.
Real iOS playback and browser layout remain unverified. The mapping above preserves the original
local review stack, not existing remote PRs. Branches/worktrees remain intact; new work should
start from updated develop rather than those old task tips.

## PR 1 — Preserve Random history across scope changes (C97)

**Report:** a user selected an earlier Random track, changed scope and pressed Next. Heard rows
disappeared and speculative rows became visible. The exact second-Next highlight/audio pairing
is unconfirmed.

**Analysis:** truncating at the cursor while retaining the old played boundary confused history
and read-ahead. This predates the architecture extraction. The small-pool fallback also had an
unguarded suspension. See STATUS for the reproduction and owner's chosen semantics.

**Change:** preserve heard history, discard only unheard old-scope picks, prepare replacements
in the background, traverse history first, and expose one newly played entry at a time. Keep
explicit dice re-roll distinct. The shared `RandomHistory` owns boundaries and stale-result
protection; the controller keeps catalogue IO, coroutine lifetime and playback.

**Evidence:** 15 targeted tests passed. The original truncation failed the history assertion
before the fix; removing the fallback generation guard failed its regression check. Both broken
variants were restored. Local evidence: worktree `dist/c97-verification/`. The forced full gate
passed **654 tests**, lint without errors, 581 page checks, 49 checks against the existing WASM,
8 SAP checks, the web storage upgrade, 135 icon paths and 8 server checks (216 s). This is host
evidence, not a rebuilt WASM or phone/audio test. Release artifact details follow below.
The later combined candidate received owner acceptance as recorded above.

**Candidate:** clean source `c855fab`, version **1.0.0 (1085)**, built in 108 s:
`dist/protracktor-release-1.0.0-1085-c855fab-20261006-231633.apk` in the main checkout,
11,679,890 bytes; SHA-256
`2e4dc9a35346e0a40398f3661ea0737fa846c67854cd3685e2fe698cf25bac86`.
Minified release, non-debuggable, arm64-v8a/armeabi-v7a/x86_64. `apksigner verify` passed;
test certificate SHA-256 `f97af50c2d869a2591dd82d24555894a2cc95927afb6c0a1fcd4bd197221e47e`,
matching the earlier 1084 test APK. Installation over the actual phone copy is unverified;
do not uninstall or clear data to test. No publication key was used.

**Scope note:** this first PR is based on develop, without the older unmerged filter-availability
fix. That correction is carried forward with C99, not silently merged to develop here.

## PR 2 — Disable Browse and Web behind the return-choice cover (C98)

**Report:** another user could open Browse from the covered playlist while Random kept playing.
The owner also requested Web be unavailable there; Web was an expectation, not an original
independent observation.

**Analysis:** the cover's `awayFromPlaylist` condition only blocked its body, not the separate
top bar. **Fix:** reuse that condition for Browse and WEB `enabled`, covering both touch and hold.
Settings, both return actions and normal uncovered navigation stay available. The same condition
also covers existing search/external sessions rather than introducing a second cover rule.

**Evidence:** five production-component Compose/Robolectric checks passed; three failed on the
missing guard before the fix. They cover paired/unpaired Web, disabled gestures, Settings/return
callbacks and re-enabling. Local evidence: worktree `dist/c98-verification/`. The forced full gate
passed **659 tests**, lint without errors and the same page/WASM/SAP/storage/icon/server checks
listed for C97 (226 s). The release artifact is recorded below; the later combined candidate
received owner acceptance as recorded above.

**Candidate:** clean source `7a602ba`, version **1.0.0 (1087)**, built in 94 s:
`dist/protracktor-release-1.0.0-1087-7a602ba-20261006-232527.apk` in the main checkout,
11,679,982 bytes; SHA-256
`d904b255a2f34ace75f008c8cf07cf08bf9158ac333a46970334b0f3d3b3858a`.
Minified, non-debuggable release for all three ABIs. `apksigner verify` passed with the same
test certificate as C97/1084. Includes C97; excludes the older availability fix until C99.

## PR 3 — Keep Random filters responsive while refreshing availability (C99)

**Report:** owner testing found a small delay and briefly disabled chips. Installed build and the
exact sequence were not confirmed; no independent phone timing has been measured.

**Analysis:** the 1084 candidate discarded sheet-local availability on reopening and explicitly
cleared it before refresh, so an already known usable filter appeared unavailable while a new
query ran. Its query already runs off the UI dispatcher. Two new component tests failed on that
reset before the fix. There is no basis for blaming all perceived opening latency on the query.

**Change:** incorporate `fix/random-filter-availability` (`1718ee6`, code `0ab8c7d`) into this
stacked branch without rewriting its history or merging develop. Lift the ephemeral availability
state above the sheet; prepare it when entering Random, retain valid results during refresh, and
avoid recounts for sheet-only reopenings. Observe relevant catalogue/count/download changes;
deletion/preparation and inactive data changes invalidate cached answers. Reject obsolete reads
even if the loader ignores cancellation. Keep honest cold/error states with Retry and existing
PL/EN labels; no schema, dependency or persistent-cache change.

**Evidence:** 16 panel Compose tests passed; removing the final cancellation check failed the
late-response regression. Local evidence: worktree `dist/c99-verification/`. The forced full gate
passed **676 tests**, lint without errors and all page/WASM/SAP/storage/icon/server checks listed
above (273 s). The final release artifact follows. The owner later accepted the combined candidate;
quantitative phone timing and the original uncertain interaction remain unmeasured. TESTING retains
the C97, C98 and C99 scenarios for regression checks.

**Final combined candidate:** clean source `9110e62`, version **1.0.0 (1091)**, built in 162 s:
`dist/protracktor-release-1.0.0-1091-9110e62-20261006-234043.apk` in the main checkout,
11,687,250 bytes; SHA-256
`7504b8aa980a198f28e100b51819205d24ddcf91c13460563f4894c8b4951cb7`.
R8 and resource shrinking executed; non-debuggable release for arm64-v8a/armeabi-v7a/x86_64.
`apksigner verify` passed with the same test certificate recorded for C97/1084. Source and build
were clean; subsequent handoff commits change documentation only. At handoff, phone acceptance
and merge permission were still pending; both were subsequently supplied by the owner as recorded
above. No Play upload or publication occurred. Keep the user's app data intact when installing.
