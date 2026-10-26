# C121 — decoder filenames in the web player

Reported and implemented 2026-10-26 on `fix/web-decoder-selection`, from develop `e581a25`.

## Integration — 2026-10-26

Integrated on request as **`188db10ad0c5b7eeda129b969923671d42da852c`**. The merge tree equals
branch `4cd637f`, and application/native/script/web/store sources match tested candidate
`3bfa0ea`. The agent checked those identities; no new test run or APK build was needed for the
unchanged sources. The retained test bundle keeps its original source and checksums. A separate
browser-trial result was not supplied; the checklist below remains available for verification.
Local GitHub Pages preparation and the subsequent manual push are separate publication steps.
Site commit **`b6a3c5384c557b9e39baefb720e38962943e2ab3`** was prepared from source
**`e1a428127f6517712699accc4ae98066c883b8c8`**. Its 50 files include the checked JavaScript and
WASM, exact source metadata, the root redirect and a valid worker for 46 cached startup assets.
The site revision is `6b7828441432848524f34f032131ff4dd78709f9232ce0644d6357c2f04498ce`.
No remote push or deployment has been performed; push source and site together with
`./scripts/push-protracktor.sh develop gh-pages`.

## Report

The two files play in the APK but were refused by the web player:

- [african.pt3](https://modland.com/pub/modules/Spectrum/Pro%20Tracker%203/Mr.%20Beeper/african.pt3):
  the reported refusal named libopenmpt and a loading error; Modland identifies it as Spectrum.
- [Quest_For_Cool.sap](https://asma.atari.org/asma/Composers/Rogers_Charles/Quest_For_Cool.sap):
  the reported refusal named game-music-emu and an incompatible file type.

## Mechanism

The existing WebAssembly engine opens both files with their filenames. Given display titles
without extensions, the same bytes reproduce the reported errors: ZXTune is not offered the PT3,
and ASAP does not claim the SAP before game-music-emu rejects it.

An entry can have a display `name` and no explicit `file`, for example after a titled phone link
or handoff. The page used `entry.file ?? entry.name` for playback, length lookup and audio export,
and a similar fallback for Information. That substitutes the display title for the decoder name.
History could then persist that substitution. The existing `fileOf` already resolves an explicit
filename or the original URL/fragment, but these producers did not use it.

## Correction

Playback, Information, audio-export requests, length lookup and History recording now use
`fileOf`. A provided filename keeps priority; a missing or empty field falls back to the URL,
including The Mod Archive's filename fragment. The visible title remains unchanged. Local
phone transfers retain their provided filename. No decoder, format table, catalogue identity,
database schema or Android implementation was changed.

## Verification

The agent downloaded the two supplied files once, receiving HTTP 200. Fixtures, checksums,
probe results and test logs are retained in ignored `user/web-decoder-regression-20261026/`.
The downloaded music is not added to the repository.

| File | Bytes | SHA-256 |
| --- | ---: | --- |
| african.pt3 | 1,335 | `be3f633a476bb7501d4bdbdf82aa0709147e227dddf9f84923af622cf5f66778` |
| Quest_For_Cool.sap | 8,753 | `ecc987fa75c8b63bb8e40ccf8b80f14f5f3f50c7f5a5d14f5766b00f8ef8369d` |

The existing local WASM was exercised under Node with each file's display title and filename.
Both title-only opens fail with the reported decoder messages; both filename opens succeed.
Each positive case rendered **221,184 stereo frames at 44,100 Hz**, all finite, with peaks
**0.5665** for ZXTune/PT3 and **0.8731** for ASAP/SAP. This is host decoding evidence, not
browser audio-device verification. No new WASM build was required for this JavaScript correction.

Twenty-five new page checks cover playback messages, visible titles, stored History filenames,
Information messages and export-worker messages across the two reported URLs, an empty filename,
a filename fragment and a local transfer. **Sixteen assertions failed before the correction**;
the provided-filename and display-title controls passed. After correction the complete page
suite passed **606 checks** in **33.2 s**.

The full project gate passed on **`3bfa0eabfa75c067a461d43c1370814ce058e57f`** in **701 s**:
**1217 tests / 159 suites**, zero failures/errors/skips, with fresh unit execution in **493.9 s**.
All companion checks passed, including the repeated **606 page checks**, **49 WASM checks**,
SAP measurement, native audio, web database upgrade, icons, PWA and server checks. Fresh lint
completed in **100.3 s** with **zero errors / 79 warnings**. This is the verified candidate;
the later verification record changes documentation only.

## Test bundle

| Field | Value |
| --- | --- |
| File | [protracktor-web-20261026-224832.tar.gz](../dist/protracktor-web-20261026-224832.tar.gz) |
| Size | 1,489,871 bytes |
| SHA-256 | `85b25a9fca25bd85dcc8ec1fb72bcb71091dd400a64e9d86a6fa05f78eddb8c1` |
| Source | `3bfa0eabfa75c067a461d43c1370814ce058e57f` |
| WASM SHA-256 | `ef14ae1a028aa36ca35c9425f7f2f3cee8c2fbdd5e298e715241377a814259ef` |
| PWA revision | `d58e4103e43535fcad6de6085ef13efc28ecceedac36779bc99b4f5d623ad506` |

The archive's JavaScript matches the committed fix, its WASM matches the real-file probe, and
the legal build record identifies the exact source commit. The staged worker matches its
calculated revision and 46 startup assets. No music fixtures or nested archives are bundled.
SHA-256 and build metadata sidecars are beside the archive in `dist/`; inspection records are
retained with the local test evidence. Test-bundle preparation did not build an APK, deploy
remotely or merge the branch; the subsequent integration is recorded above.

Browser/PWA playback, Information and actual AAC export remain **unchecked** until a browser trial.

## Browser trial

Use a title-bearing shared link or a saved entry with a different display title and no filename.
Play both reported files, inspect their Information and replay from History. Keep the title
visible while playback chooses ZXTune for PT3 and ASAP for SAP. Also check an explicit filename
from phone handoff, a Mod Archive filename fragment and audio export where the browser supports
AAC encoding. Existing playlists and indexes should be retained throughout the update.
For an installed PWA, close its windows and reopen after updating the files so the new worker
can activate; preserve the browser's existing app data.
