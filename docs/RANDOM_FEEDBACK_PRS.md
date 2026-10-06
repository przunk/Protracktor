# Random feedback: stacked PR handoff

Owner-authorised on 2026-10-06. These are local PR drafts, **not GitHub PRs**: the owner pushes
and opens them. No branch is merged or approved on a device. Report sources and uncertainty are
preserved in STATUS C97–C99 and the TESTING feedback record. VersionName remains 1.0.0.

| Item | Head | PR base | State |
| --- | --- | --- | --- |
| C97 | `fix/random-scope-history` | `develop` (`a72d098` at start) | Host-verified; release APK built; phone pending |
| C98 | `fix/random-return-navigation` | `fix/random-scope-history` (`f7dfceb`) | Implemented; verification in progress |
| C99 | `fix/random-filter-responsiveness` | `fix/random-return-navigation` | Planned |

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
evidence, not a rebuilt WASM or phone/audio test. Release artifact details follow after building.
Phone checks are in TESTING and remain pending.

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
listed for C97 (226 s). The release build is next; actual phone navigation and visual feedback
remain unverified.

## PR 3 — Keep Random filters responsive while refreshing availability (C99)

Pending analysis and implementation. Reuse the earlier unmerged availability correction; check
cold/warm opens, delayed refresh, download/removal, stale results and retry. Do not claim measured
phone speed or independent reproduction of the owner's uncertain sequence without evidence.
