# Architecture

This document records what has been decided and **why**. A decision without its reason gets
re-litigated every few weeks, or worse, applied in a situation it was never meant for.

Nothing below is built yet. It is the shape we are building toward, and it will be corrected here
when reality disagrees.

---

## 1. Platform

| Setting | Value | Reason |
| --- | --- | --- |
| Language | Kotlin | — |
| UI | Jetpack Compose, Material 3 Expressive | R8 |
| `minSdk` | 29 (Android 10) | Below this, scoped storage and current Media3 both become a fight. The owner's own device is API 36; 29 exists so other people can install it. |
| `targetSdk` | 36 (Android 16) | Owner's device generation. |
| Native | NDK 29.0.14206865, CMake 3.31.6 | Pinned. A native toolchain that changes underneath you produces failures nobody can reproduce. |

## 2. Licence: GPL-3.0-or-later

Decided 2026-08-31.

The decoders worth having are GPL — `libsidplayfp` (C64), UADE (Amiga custom formats), `sc68`
(Atari ST), ASAP (Atari 8-bit). Linking them makes the whole application GPL. That part is not a
choice.

Choosing **3** rather than 2 is a choice, and the reason is our own stack, not the decoders:
Jetpack Compose, Media3, Room, Kotlin's standard library and Oboe are all **Apache-2.0**, which is
incompatible with GPL-2 and compatible with GPL-3. Under GPL-2 we would have a licence conflict
with our own UI framework. GPL-3 additionally carries an explicit patent grant that GPL-2 lacks.

This requires every GPL dependency to be "**or later**" so it can be taken to 3. That is verified
per dependency at integration time and recorded in `docs/LICENSES.md`. A GPL-2-only dependency
would invalidate this decision and must be raised with the owner rather than worked around.

Consequence accepted by the owner: when the repository goes public, sources must stay available.
F-Droid is the natural channel. Google Play does not object to GPL-3 (the App Store conflict people
remember is Apple's, and does not apply).

## 3. We do not fork ZXTune

ZXTune's decoding core is good and its format coverage is the reference. But it is a long-lived C++
codebase with its own framework, and its architecture is the source of most of what
`docs/REQUIREMENTS.md` complains about. Understanding it well enough to change it safely costs
about as much as writing a new shell, and leaves us owning its structure.

So: **new application, upstream decoders.** ZXTune is consulted for its format list and its
content-probing logic, not vendored.

## 4. The audio path stays native

```
  ┌──────────────── Kotlin ─────────────────┐   ┌────────── C/C++ ──────────┐
  │ MediaSessionService                     │   │                           │
  │   └─ SimpleBasePlayer implementation ───┼──▶│ engine facade (JNI)       │
  │        control, position, queue         │   │   ├─ backend registry     │
  │                                         │   │   ├─ libopenmpt  (trackers)│
  │ Room: library index, playlists, cache   │   │   ├─ sc68        (SNDH/YM) │
  │ Compose UI                              │   │   ├─ libsidplayfp (SID)   │
  │                                         │   │   ├─ UADE        (Amiga)  │
  │                                         │   │   └─ game-music-emu (NSF…) │
  │                                         │   │                           │
  │                                         │   │ Oboe callback ──▶ speaker │
  └─────────────────────────────────────────┘   └───────────────────────────┘
```

**Rendering and output both live in native code; JNI carries control and metadata only.** The
alternative — rendering natively and pushing PCM buffers into an `AudioTrack` from Kotlin — crosses
the JNI boundary every few milliseconds on the one thread that must never be late. Keeping the
render callback and the decoders on the same side of the boundary removes that whole class of
glitch.

Oboe rather than raw `AudioTrack` because Oboe already solves device-specific buffer sizing and
stream recovery on Android, and it is Apache-2.0.

On the Kotlin side, Media3's `SimpleBasePlayer` is what makes this affordable: it lets a custom
player expose itself to `MediaSession`, so notification controls, audio focus, becoming-noisy,
Bluetooth and Android Auto all work without writing an ExoPlayer `Renderer` for a synthesiser.

## 5. One facade, many backends

Every decoder sits behind a single native interface: probe, open, render, seek, subsong select,
metadata, close. Adding a format means adding a backend to a registry, not touching the player.

**Format is decided by content, not by file extension.** Extensions in this world are unreliable,
absent, or shared between unrelated formats. Probing is what ZXTune gets right and it is worth
copying the approach.

Capabilities differ per backend and must be *declared*, not assumed — `libopenmpt` and
game-music-emu can seek; `libsidplayfp`, UADE and `sc68` effectively cannot, because they are CPU
emulators with no way back except re-running from the start. The UI reflects what the current
backend can actually do rather than offering a control that silently does nothing (AGENTS.md §7:
a press that does nothing and says nothing is a defect).

## 6. Startup latency is an indexing problem, not a decoding one

R9's 5–30 second wait is not decode time — a module is kilobytes and renders in milliseconds. It is
re-opening the containing archive and re-probing the format on every single play.

Three mechanisms, in order of effect:

1. **Persistent index (Room).** Path, container, offset, detected format, metadata, duration,
   subsong count — written once at scan, read instantly afterwards. This alone removes the probe.
2. **Extracted-file cache.** Entries pulled out of ZIP/LHA archives are kept on disk keyed by
   container identity, so playing the second track from an archive does not re-open it.
3. **Prepare-ahead.** While a track plays, the next one is opened and its first buffers rendered.

The index is also what makes R2 possible: a playlist that refers to index rows does not need the
filesystem to exist at launch, so opening the app cannot destroy the view.

## 7. Domain logic stays off Android

Shuffle order and history (R5), queue and cursor semantics, repeat scope within a playlist (R6),
duration policy — these are pure Kotlin, testable without an emulator, and they are the parts most
worth testing. There is no emulator in this environment (AGENTS.md §3), so anything that can only
be verified by running the UI is verified by the owner on a real phone, and everything else is
verified by tests that run here.
