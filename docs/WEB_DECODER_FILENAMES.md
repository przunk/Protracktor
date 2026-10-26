# C121 — decoder filenames in the web player

Reported and implemented 2026-10-26 on `fix/web-decoder-selection`, from develop `e581a25`.
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

Full project gate/lint and the distributable web bundle are pending. Browser/PWA playback,
Information and actual AAC export remain unchecked until a browser trial.

## Browser trial

Use a title-bearing shared link or a saved entry with a different display title and no filename.
Play both reported files, inspect their Information and replay from History. Keep the title
visible while playback chooses ZXTune for PT3 and ASAP for SAP. Also check an explicit filename
from phone handoff, a Mod Archive filename fragment and audio export where the browser supports
AAC encoding. Existing playlists and indexes should be retained throughout the update.
