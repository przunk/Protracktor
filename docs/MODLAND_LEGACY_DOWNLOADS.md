# C109 — Legacy Modland unknown-author downloads

Recorded 2026-10-21. User-reported playback failure; candidate correction on
`fix/modland-download-failures`. Phone acceptance and integration remain pending.

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
  request replacing the author path segment `- unknown` with `_unknown`.
- Keep nested game folders and normal path escaping. Do not rewrite similarly named authors,
  filenames, foreign hosts, queries or fragments.
- Do not retry for connection exceptions, access/rate-limit failures, 410 or server errors.
  A missing replacement ends the operation; recovery does not recurse.
- Use the existing cookie-gate handling and User-Agent for the recovered address.
- Keep the original track ID and cache key. No playlist, History, bookmark, index or database
  migration is performed; schema remains 23. No automatic catalogue download is introduced.

The generic unavailable-file message remains the existing fallback for other failures. This
change does not claim to classify or repair every possible archive, network or decoder failure.

## Verification

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
**1.3.0 (1273)**; [artifact and verification](STATUS.md#c109c111--shared-candidate-ready-for-phone-checks).
Neither branch is approved for integration; phone behaviour remains unchecked.

## Phone checks

1. Without refreshing the existing Modland index, open the reported YM from `- unknown`.
   It should download and start playing, without the connection notice.
2. Try the previously reported Same Game SPC where the old group is still available.
3. Replay the YM from its existing saved playlist or History reference. Its identity and
   membership should remain unchanged; a repeated play should use the downloaded cache.
4. Check an ordinary Modland track and, if available, an UnExoticA download to guard the shared
   HTTP/User-Agent/cookie path. A genuinely unavailable file must still report failure.
5. After an intentional index refresh, current `_unknown` selections should work directly.

All of these device scenarios remain **unchecked** until reported from a phone.
