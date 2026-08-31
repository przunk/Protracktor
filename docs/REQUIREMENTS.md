# Requirements

Every requirement here comes from a specific complaint about ZXTune, recorded on 2026-08-31. They
are written as the behaviour we owe, not as the bug we are avoiding, because a requirement phrased
as "don't do what they did" cannot be tested.

## R1 — Atari ST SNDH playback

SNDH must play. This is the format that started the project; ZXTune does not appear to support it.

## R2 — The session survives leaving the app

Coming back to Protracktor shows exactly what was on screen when it was left: the same playlist,
the same scroll position, the same track, the same playback position where the format allows one.

**Scanning the library is a deliberate, user-initiated action and never a side effect of launching
the app.** ZXTune reloads its playlist on entry, which destroys the view the user had built.

## R3 — A persistent player bar

A compact player is docked to the bottom of the screen and visible in *every* view, expanding to
the full player. It is never a screen you have to navigate back to.

## R4 — The main screen shows the file, not a visualiser

The default content of the main screen is the track's metadata: format, author, subsongs, sample
and instrument names, channel count, size, origin. A visualiser is an option the user may turn on
later; it is not what the screen is for.

## R5 — Shuffle with a real history

Forward in shuffle picks the next track from a shuffled order. **Backward returns to the track that
was actually played before**, not to another random one. This requires a materialised queue and a
cursor into it, not a random draw at each transition.

## R6 — Several playlists

The user creates and names playlists (for example "Lotus 3"). Shuffle and repeat operate within
the active playlist, never across the whole library.

## R7 — Navigation without accidental side-swiping

Browsing is not a neighbouring page you slide onto by accident. It is entered deliberately.
The concrete layout is not decided — see `docs/OPEN_QUESTIONS.md`.

## R8 — Material 3 Expressive, and genuinely responsive

Compose with Material 3 Expressive. Phone and tablet, portrait and landscape, dynamic colour.
Accessibility is a requirement: no information carried by colour alone, everything reachable by
screen reader.

## R9 — Playback starts immediately

Pressing play on an indexed track starts audio without a perceptible wait. ZXTune takes 5–30
seconds, which is not decoding cost — a module is kilobytes and decodes in milliseconds. It is
re-opening the containing archive and re-probing the format on every play, with nothing remembered.

The answer is a persistent metadata index, a cache of extracted files, and preparing the next track
while the current one plays.

## R10 — Multi-language interface

Polish and English from the first screen. Retrofitting translations means rewriting every screen.
