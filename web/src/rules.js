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
