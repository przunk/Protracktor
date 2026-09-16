// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
//
// The queue rules, as functions of nothing but their arguments.
//
// **Pulled out of `app.js` so they can be checked against `docs/rules/queue-cases.json`**, which is
// the same file `RuleCasesTest.kt` reads. The rules exist twice because a browser cannot run Kotlin;
// what this stops is them being *decided* twice. `docs/PLAN_WEB_LIBRARY.md` S1 has the argument, and
// C23, C30 and C31 are what it costs when they are.
//
// Nothing here touches the DOM, the worklet or any of the module's own state. That is the property
// that makes it testable, and it is the reason `app.js` passes its state in rather than these
// reaching for it.

/** Where `next` goes, or null when the queue is finished. Shuffle is the caller's business. */
export function nextIndex({ tracks, at, repeat }) {
  if (tracks <= 0 || at < 0) return null;
  // Checked first, and deliberately: repeat-one means "this tune again", so it answers before the
  // end of the queue is even considered.
  if (repeat === 'one') return at;
  if (at + 1 < tracks) return at + 1;
  return repeat === 'all' ? 0 : null;
}

/**
 * Where `previous` goes, or null.
 *
 * **Repeat-one does not trap it.** A listener pressing back means back; a mode that repeats the
 * current tune is about what happens when it *ends*, not about what a button does.
 */
export function previousIndex({ tracks, at, repeat }) {
  if (tracks <= 0 || at < 0) return null;
  if (at - 1 >= 0) return at - 1;
  return repeat === 'all' ? tracks - 1 : null;
}

/**
 * The next tune inside the open file, or null when the queue should move instead.
 *
 * `SubsongAdvance.kt`'s rule, and its comment is the one to read: this decision has been wrong
 * twice on the phone alone.
 */
export function nextSubsong({ playAll, subsong, count, repeatOne }) {
  if (!playAll || repeatOne) return null;
  return subsong + 1 < count ? subsong + 1 : null;
}

/**
 * Whether pressing play should start a track again rather than resume it.
 *
 * `PlayFromEnd.kt`'s rule. A duration of zero means nobody knows the length, not zero seconds long
 * — without that guard every paused tune with no stated duration restarts instead of resuming.
 */
export function shouldRestart({ engineFinished, position, duration }) {
  if (engineFinished) return true;
  return duration > 0 && position >= duration;
}

/**
 * Random: where next goes in the record the dice has made -- **the next row while there is one, and
 * a new pick (`'roll'`) only at the end** (`docs/PLAN_RANDOM.md`).
 *
 * The phone was briefly made to roll on every press and the owner sent it back: with the record on
 * screen, next means the next row. Repeat is the caller's -- the end of a tune checks repeat-one
 * first and the button does not, exactly as the phone does it.
 */
export function randomNext({ length, at }) {
  return at + 1 < length ? at + 1 : 'roll';
}

/** Random: previous walks back through the record, and has nothing before the first pick. */
export function randomPrevious({ at }) {
  return at > 0 ? at - 1 : null;
}

/**
 * The first drawn tune the session has not had -- or, when every draw repeats, the first draw
 * anyway. A small pool forces that, and a dice that repeats beats one that stops dead.
 */
export function freshPick({ drawn, seen }) {
  const had = seen instanceof Set ? seen : new Set(seen);
  for (const url of drawn) if (!had.has(url)) return url;
  return drawn[0] ?? null;
}

/**
 * What a typed search means: **every word, anywhere, in any order.**
 *
 * The phone's `data/SearchTerms.kt`, and the cases they share are in `docs/rules/queue-cases.tsv`.
 * Until 2026-09-16 both sides matched the whole query as one substring, which fails on the way
 * tracker files are actually named -- `space ninja` did not find `space_ninja`, one character in
 * the middle being a separator rather than a space.
 *
 * Splitting the query covers `space_ninja`, `spaceninja`, `Space Ninja` and `ninja space` alike.
 * What it does not cover is the other direction -- typing `spaceninja` for a file called
 * `space_ninja` -- which needs the *stored* side stripped of separators too, and that was measured
 * before it was rejected: 36 ms to 217 ms on 500,000 rows, or a normalised column and twelve
 * megabytes. This costs nothing.
 */
export function searchTerms(query) {
  return String(query ?? '').trim().toLowerCase().split(/\s+/).filter(Boolean);
}

/**
 * Whether any of [texts] satisfies [query] **as a whole**.
 *
 * Not "some text matches every word": a tune whose title holds one word and whose author holds the
 * other is a hit, so each word is looked for across all of them.
 */
export function searchMatches(query, ...texts) {
  const haystacks = texts.filter((t) => t != null).map((t) => String(t).toLowerCase());
  return searchTerms(query).every((word) => haystacks.some((text) => text.includes(word)));
}
