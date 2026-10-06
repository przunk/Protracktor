# Random feedback: stacked PR handoff

Owner-authorised on 2026-10-06. These are local PR drafts, **not GitHub PRs**: the owner pushes
and opens them. No branch is merged or approved on a device. Report sources and uncertainty are
preserved in STATUS C97–C99 and the TESTING feedback record. VersionName remains 1.0.0.

| Item | Head | PR base | State |
| --- | --- | --- | --- |
| C97 | `fix/random-scope-history` | `develop` (`a72d098` at start) | Implemented, verification in progress |
| C98 | `fix/random-return-navigation` | `fix/random-scope-history` | Planned |
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

**Scope note:** this first PR is based on develop, without the older unmerged filter-availability
fix. That correction is carried forward with C99, not silently merged to develop here.

## PR 2 — Disable Browse and Web behind the return-choice cover (C98)

Pending analysis and implementation. Expected: Browse and Web visibly unavailable, Settings and
both return choices active. Check both paired/unpaired Web actions and normal uncovered navigation.

## PR 3 — Keep Random filters responsive while refreshing availability (C99)

Pending analysis and implementation. Reuse the earlier unmerged availability correction; check
cold/warm opens, delayed refresh, download/removal, stale results and retry. Do not claim measured
phone speed or independent reproduction of the owner's uncertain sequence without evidence.
