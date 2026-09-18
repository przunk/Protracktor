<!--
SPDX-FileCopyrightText: 2026 Przunk
SPDX-License-Identifier: GPL-3.0-or-later
-->

# Instrument and sample names in Now Playing

*Asked for on 2026-09-11, right after C40 (the whole module message) was merged: show the instrument
names too, because authors sometimes write their message into them.* Written down before any code,
so it can be picked up cold by whoever builds it.

**Status: built 2026-09-11, waiting for a device test.** Branch: `feature/instrument-names` (this
file is its first commit).

**Checked on real files with the rebuilt web engine** before the phone: `zoolook.mod` (Modland,
Jogeir Liljedahl) gives `sample_names` = "by jogeir liljedahl", two empty names, "original by
j.m.jarre" -- and none of its 27 trailing empty names, and no `instrument_names` line, a MOD having
no instruments. `aces high.ahx` (Modland, 451) gives 38 instrument names that are a whole letter,
from "Put into tracker by (451) back in 2014" to ASCII art: the whole point, in one file. The first
name is "Put into tracker", so HivelyTracker's instruments are 1-based as the loader says.

---

## Why

A ProTracker MOD has no field for a message. The scene wrote its greetings, credits and ASCII art
into the **sample names** instead: 31 lines of 22 characters. XM and IT have a real message field
and authors still use the instrument and sample names as well. Today the page and the phone show
the message (`message_raw`) and nothing else, so for most MODs the text the author left is not
shown anywhere.

## What was agreed (three worries, answered)

**Would it be too much text?** It would be, shown flat. So:

- a section at the bottom of Now Playing, **collapsed by default**, opened with a tap: "Sample names
  (31)" / "Instrument names (12)", an icon with its label, like every control here;
- **shown only when at least one name is non-empty**: SID, SAP, NSF, SPC, SNDH and friends have no
  instruments and get nothing;
- **empty names inside the list are kept** (authors built text and pictures out of them), **trailing
  empty ones are dropped**;
- XM and IT have both lists: **show both when they differ**, one when they are the same or one is
  empty.

**Would it break the layout?** Names are short (22 characters in MOD and XM, a few more in IT)
and are drawn **monospaced, one per line, never wrapped**; a line too wide for the screen scrolls
sideways inside its own box. The page already draws the message this way (`.np-message pre`); the
phone's section gets `horizontalScroll`. Rows are numbered as a tracker numbers them: 01, 02, and on.

**Could it break something else?** The risk is in three places, each with its guard:

1. **The describe block's shape.** `message` must stay the **last** field, because it is the only
   value with line breaks in it (`docs/STATUS.md` C40, `DescribeBlock`, the page's `describeFields`).
   The names go **before** it, each list on **one line**: names joined by the unit separator
   (U+001F), and every tab, carriage return and line feed inside a name replaced by a space first.
2. **Key names.** `instruments` and `samples` already exist and hold **counts** (shown in the field
   list on both sides). The new keys are `instrument_names` and `sample_names`, so nothing is
   overwritten. Both parsers ignore keys they do not know, so indexing, titles, authors and the
   songdb merge are untouched.
3. **A native rebuild on both runtimes.** The change is inside `describe()` only, not playback. The
   JVM tests cannot see native code, so it is checked on a phone before anything merges.

## Build steps

### 1. Engine: `native/engine/engine.cpp`

- A helper next to the backends, `joinNames(const std::vector<std::string>&)`: replace tab,
  carriage return and line feed with a space in each name, drop trailing names that are empty after
  trimming, join with U+001F. Return an empty string when every name is empty.
- `OpenmptBackend::describe()` (around line 152): before the `message` line, add
  `sample_names<TAB><joined>` and `instrument_names<TAB><joined>` lines from
  `module_->get_sample_names()` and `module_->get_instrument_names()` (`libopenmpt.hpp` around line
  1007). **Omit a line entirely when its join is empty.** Keep `message` last. `message_raw` stays:
  libopenmpt's plain `message` would fall back to the sample names by itself, which is exactly the
  mixing this design avoids.
- `HivelyBackend::describe()` (around line 1308): add `instrument_names` from
  `ht_->ht_Instruments[i].ins_Name` over `ht_->ht_InstrumentNr`. **Check in
  `native/vendor/hively/Replayer_Windows/hvl_replay.c` whether index 0 is a dummy** (HVL instruments
  are 1-based in the tracker) before choosing the loop range.
- Other backends: nothing; they have no instrument names to give.

### 2. Phone

- `DescribeBlock` needs no change (the new fields are single lines before `message`). Add a case to
  `DescribeBlockTest`: a block with `sample_names` holding U+001F-joined names *and* a multi-line
  message after it; both come back intact, the names still one field.
- `ui/NowPlaying.kt`: after the message section, one collapsible section per list present. Split on
  U+001F, number from 01, monospaced `bodySmall`, `horizontalScroll`, collapsed by default
  (`rememberSaveable` keyed by the track id, so it closes for the next tune). Header row: a
  `PlayerIcons` chevron (`Expand` exists), the label and the count.
- Strings in `values` and `values-pl`: `field_sample_names` "Sample names" / "Nazwy sampli",
  `field_instrument_names` "Instrument names" / "Nazwy instrumentów". Do **not** reuse
  `field_instruments` / `field_samples`, which label the counts.
- The FIELDS list in `NowPlaying.kt` is untouched; the new keys must **not** be added to it.

### 3. Page

- `describeFields` needs no change; add a check that a `sample_names` line before a multi-line
  message survives as one field.
- `renderNowPlaying` in `web/src/app.js`: after `#np-message`, one `<details>` per list present,
  closed by default, its `<summary>` an icon and "Sample names (31)"; the body a `<pre>` of numbered
  lines, styled like `.np-message pre` (monospace, `white-space: pre`, `overflow-x: auto`). Removed or
  closed in `renderNothingPlaying` and on every new tune.
- Do **not** add the keys to `FIELD_ORDER`.
- Rebuild the web engine with `scripts/build-web-engine.sh` (emsdk in `/mnt/workspace/.tooling/emsdk`).
  `web/vendor/` is gitignored build output, and `scripts/package-web.sh` packs it.

### 4. Checks

- `DescribeBlockTest` (JVM): the case above.
- `scripts/check-page.mjs`: drive `onWorklet({ type: 'opened', describe })` with names. The section
  is present and closed; numbering starts at 01; inner empty names are kept and trailing ones gone;
  there is no section when every name is empty or the key is absent; the message is still whole; the
  field list is unchanged.
- A real file on the host before the phone: the `scripts/probe-openmpt.py` / `build-*-probe.sh`
  route, or the rebuilt web engine in a browser, on a MOD known to carry text in its samples.

### 5. Handover

A release APK (`scripts/build-release.sh`) and the web bundle (`scripts/package-web.sh`, after
`scripts/build-web-engine.sh`). Tested on the phone and in the browser with a MOD with text
in its samples, an XM with both lists, an AHX or HVL, and a SID, which must show nothing new. Merge
into `develop` only on approval.

## Out of scope

- Instrument names for sc68, ASAP, game-music-emu, libsidplayfp and ZXTune: their formats carry none.
- Searching or indexing by instrument names.
- Changing what the message section shows.
