# Wishlist

Ideas, not defects. A thing that does not work as intended belongs in `docs/STATUS.md`.

Format: what, who raised it, when.

---

- **A history of what was played** — owner, 2026-09-01. Somewhere in settings. Note that
  `PlayQueue` already keeps a history, but only within a session and only for the active playlist;
  a real one is a table of plays with timestamps, which is a small schema change and a screen.
  There is no settings screen yet either.

- **Optional visualiser on the main screen** — owner, 2026-08-31. R4 makes metadata the default;
  the visualiser returns as something the user switches on. Worth doing properly (a real scope or
  per-channel VU driven by the render callback) rather than the "stiff" one being replaced.

- **Online catalogue browsing (Modland, HVSC, AMP)** — Claude, 2026-08-31. These archives are how
  people actually get this music. Streaming straight from them would remove the "download and
  unpack it yourself" step entirely. Large feature; nothing before the local player is good.

- **Per-format playback settings** — Claude, 2026-08-31. Interpolation and stereo separation for
  trackers, SID model (6581 vs 8580) and filter curve, Amiga LED filter. This audience cares, and
  it is cheap once the backend facade exposes it.

- **Gapless / crossfade between subsongs** — Claude, 2026-08-31.
