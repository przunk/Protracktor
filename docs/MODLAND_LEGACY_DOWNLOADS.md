# Legacy Modland unknown-author downloads — C109/C119

Recorded 2026-10-21. User-reported playback failure; correction from
`fix/modland-download-failures` merged as **122368f** after satisfactory phone playback was
reported. Considered resolved for now, subject to further use and retained regression checks.

**Later follow-up, 2026-10-21:** [C119](STATUS.md#c119-nested-modland-legacy-unknown-author-paths-still-fail-to-download)
identifies two deeper layouts not covered by the correction below: AdLib/Screamtracker and
Video Game Music/Sega Megadrive. Their legacy URLs return 404, current `_unknown` URLs return
200 and the downloaded files decode non-silent audio on the host. C109's segment-1 guard does
not match their segment-2 unknown folder. The separate C119 correction below extends that rule;
phone verification is pending. Acceptance of the original YM/SPC examples is not withdrawn or
generalized to every legacy Modland path.

## Report and observed mechanism

Listening reports identified Same Game SPC on 2026-10-18 and
`YM/- unknown/conflict europe.ym` on 2026-10-21. Selecting either reported a download failure
with a connection suggestion. The installed phone build was not independently identified.

The agent fetched the current official [Modland index](https://modland.com/allmods.zip) and
checked the corresponding files with the application's User-Agent on 2026-10-21:

| Track | Retained legacy author folder | Current index/server path | Observed HTTP |
| --- | --- | --- | --- |
| Conflict Europe | `YM/- unknown/conflict europe.ym` | `YM/_unknown/conflict europe.ym` | Legacy 404; current 200, 5,333 bytes |
| Same Game (22) | `Nintendo SPC/- unknown/Same Game/same game (22).spc` | `Nintendo SPC/_unknown/Same Game/same game (22).spc` | Legacy 404; current 200, 66,048 bytes |

Current files: [YM](https://modland.com/pub/modules/YM/_unknown/conflict%20europe.ym),
[SPC](https://modland.com/pub/modules/Nintendo%20SPC/_unknown/Same%20Game/same%20game%20%2822%29.spc).
The folder listing also identifies `_unknown`. This establishes the address mismatch, not
when the archive changed or which other unidentified reports share the cause.

The parser and catalogue store retain source paths; Browse displays the stored author group.
The downloader previously returned null on HTTP 404, and playback displayed the same generic
message used for connection failures, without attempting a decoder. A new index avoids these
old paths for fresh selections but does not rewrite existing saved playlist/History references.

## Bounded correction

- Try the original address first; a successful original wins.
- Only after **HTTP 404**, and only for the exact HTTPS Modland file base, offer a second
  request replacing the author directory `- unknown` with `_unknown`. C109 recognized only
  segment 1; C119 recognizes one exact matching directory at any depth between root and filename.
  Paths with multiple matching directories are ambiguous and receive no guessed alternative.
- Keep nested game folders and normal path escaping. Do not rewrite similarly named authors,
  filenames, foreign hosts, queries or fragments.
- Do not retry for connection exceptions, access/rate-limit failures, 410 or server errors.
  A missing replacement ends the operation; recovery does not recurse.
- Use the existing cookie-gate handling and User-Agent for the recovered address.
- Keep the original track ID and cache key. No playlist, History, bookmark, index or database
  migration is performed; schema remains 23. No automatic catalogue download is introduced.

The generic unavailable-file message remains the existing fallback for other failures. This
change does not claim to classify or repair every possible archive, network or decoder failure.

## Nested layouts — C119 candidate, 2026-10-22

Branch `fix/modland-nested-legacy-paths` starts at develop **6fc91b2**. AdLib and Video Game
Music use format subdirectories, so the unknown-author folder need not follow the format root
immediately. Recovery is a request-only transformation; catalogue parsing and stored data do
not change. Selecting an old saved reference does not require re-indexing.

| Reported legacy path | Current folder | Fresh HTTP check, 2026-10-22 |
| --- | --- | --- |
| `Ad Lib/Screamtracker 3 AdLib/- unknown/(opl2) totally nuts.s3m` | `_unknown` | Legacy 404; current 200, 9,236 bytes |
| `Video Game Music/Sega Megadrive/- unknown/World Championship Soccer/06 ending theme.vgz` | `_unknown` | Legacy 404; current 200, 2,524 bytes |

The first extension is `.s3m`, confirmed after a typing error in the report; no filename-extension
repair is needed. Both production-downloader HTTP regressions failed separately before the
change. New checks cover nested request URLs, original cache identities, offline disk-cache
reuse after downloader recreation, variable-depth paths/escaping and refused ambiguous paths.
An encoded-separator regression also failed against the first broader rule; recovery now keeps
decoded segment boundaries and refuses encoded slashes instead of manufacturing directories.
Focused verification passed **26 tests / 4 suites**, zero failures/errors/skips. Deliberately
including the format root and filename in recovery broke the boundary regression as intended;
the correct range was restored. The full gate/lint and a minified APK are pending; this branch
is not merged or phone-accepted.

Fresh actual downloads also rendered non-silent PCM with the existing host WASM engine:
**2 played / 0 silent / 0 refused**. This is not an Android playback result. Raw HTTP, payloads,
decoder output and regression logs are retained in ignored `user/c119-verification/`.
[Current verification/artifact state](STATUS.md#c119-nested-modland-legacy-unknown-author-paths-still-fail-to-download).

## Original C109 verification

**Checked on the host:** the production downloader, with only its connection factory redirected
to a local HTTP server, failed the new Same Game regression before the correction. Fail-fast
stopped that baseline early; it is not a full eight-test result. A separate Conflict Europe
baseline also failed before the correction. With the correction, **36 focused tests / 5 suites**
passed, including eight new HTTP/cache/recovery tests and existing identity, gate and routing
checks. Raw HTTP observations and probe logs are retained in ignored `user/c109/` and task logs.
The status guard was also deliberately broadened to all HTTP errors: the access/server-error
regression failed, detecting the unwanted recovery. The 404-only condition was then restored.

The actual downloaded files also opened and produced non-silent PCM using the existing WASM
engine probe: YM via ZXTune and SPC via the console backend; **2 played / 0 refused / 0 silent**.
That is decoder/host evidence, not listening or Android device verification.

The separate stacked C111 padding candidate, source **d370ea2**, passed the forced full gate:
**1106 tests / 144 suites**, no failures/errors/skips, all companion checks and fresh lint
**0 errors / 72 warnings**, **631 s** overall. Its minified test-signed shared release APK is
**1.3.0 (1273)**; [artifact, acceptance and integration](STATUS.md#c109c111--accepted-and-merged-2026-10-21).
C109 and C111 are accepted and merged in order. Their application sources match the tested
candidate; no additional gate or APK build is claimed for the integration.

## Phone checks

1. Without refreshing the existing Modland index, open the reported YM from `- unknown`.
   It should download and start playing, without the connection notice.
2. Try the previously reported Same Game SPC where the old group is still available.
3. Replay the YM from its existing saved playlist or History reference. Its identity and
   membership should remain unchanged; a repeated play should use the downloaded cache.
4. Check an ordinary Modland track and, if available, an UnExoticA download to guard the shared
   HTTP/User-Agent/cookie path. A genuinely unavailable file must still report failure.
5. After an intentional index refresh, current `_unknown` selections should work directly.

Practical phone playback is accepted, but individual outcomes for every scenario above were not
separately supplied. Keep them for regression and longer-term verification.
