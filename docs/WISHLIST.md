# Wishlist

Ideas, not defects. A thing that does not work as intended belongs in `docs/STATUS.md`.

Format: what, who raised it, when.

---

- **A web player, with favourites and history synced to an account** — owner, 2026-09-01. The same
  music in a browser, sharing state with the phone through a Google or Cloudflare account. Recorded
  as a thought to return to, not a plan.

  Worth noting now, because it would change decisions we are making today: the decoders are C and
  would need WebAssembly builds (libopenmpt already ships one; sc68 does not), and syncing state to
  an account means a server, accounts, and somebody's data in someone else's hands — which is a
  different kind of project from an app that reads files off a phone. The parts that would carry
  over unchanged are the ones already kept free of Android: `PlayQueue`, and the schema in
  `SchemaSql`.

- **Fold hard-panned channels together** — owner, 2026-09-01. Amiga modules pan channels hard left
  and right by convention, and on his phone one "speaker" is the screen vibrator: half the music is
  effectively inaudible. Wanted is a mix control — full stereo, narrowed, or mono — applied in the
  render callback where the gain already is. Note that libopenmpt has a stereo-separation setting of
  its own, so part of this may be a backend option rather than a mix of ours.

- **Full metadata for any supported file** — owner, 2026-09-01. An info button or a long press,
  showing everything the backend knows about a track rather than the handful of fields the player
  screen has room for. Should work on any file the current version can open, not only on what is
  playing.

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
