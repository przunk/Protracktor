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
 * Rolling on every press is wrong: with the record on screen, next means the next row. Repeat is
 * the caller's -- the end of a tune checks repeat-one first and the button does not, exactly as the
 * phone does it.
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
 * Matching the whole query as one substring fails on the way tracker files are actually named:
 * `space ninja` does not find `space_ninja`, one character in the middle being a separator rather
 * than a space.
 *
 * Splitting the query covers `space_ninja`, `spaceninja`, `Space Ninja` and `ninja space` alike.
 * What it does not cover is the other direction -- typing `spaceninja` for a file called
 * `space_ninja` -- which needs the *stored* side stripped of separators too, and that was measured
 * before it was rejected: 36 ms to 217 ms on 500,000 rows, or a normalised column and twelve
 * megabytes. This costs nothing.
 */
export function searchTerms(query) {
  return foldText(String(query ?? '').trim()).split(/\s+/).filter(Boolean);
}

// Letters with no combining mark to drop, and what they fold to. The same table as the phone's
// `SearchTerms.UNDECOMPOSED`; `docs/rules/queue-cases.tsv` holds the two to one answer.
const UNDECOMPOSED = { 'ł': 'l', 'đ': 'd', 'ø': 'o', 'ß': 'ss', 'æ': 'ae', 'œ': 'oe', 'þ': 'th', 'ħ': 'h', 'ı': 'i' };
const UNDECOMPOSED_ANY = /[łđøßæœþħı]/g;

/**
 * Text as search compares it (`docs/BACKLOG.md` A53): lower case, compatibility-decomposed
 * (NFKD), combining marks dropped, and the letters Unicode does not decompose mapped by hand --
 * so `michal` finds `Michał` and `akes lekhorna` finds `Åkes lekhörna`. The phone's
 * `SearchTerms.fold` is the same function.
 */
export function foldText(text) {
  return String(text ?? '')
    .toLowerCase()
    .normalize('NFKD')
    .replace(/\p{Mn}/gu, '')
    .replace(UNDECOMPOSED_ANY, (c) => UNDECOMPOSED[c]);
}

/**
 * [text] ready to be searched: folded when it holds anything outside ASCII, merely lower-cased
 * when it does not. The catalogue search runs this half a million times a keystroke, and nearly
 * every title is plain ASCII, for which folding and lower-casing are the same thing.
 */
// eslint-disable-next-line no-control-regex
const NON_ASCII = /[^\x00-\x7f]/;
export function searchable(text) {
  const s = String(text ?? '');
  return NON_ASCII.test(s) ? foldText(s) : s.toLowerCase();
}

/**
 * Whether any of [texts] satisfies [query] **as a whole**.
 *
 * Not "some text matches every word": a tune whose title holds one word and whose author holds the
 * other is a hit, so each word is looked for across all of them.
 */
export function searchMatches(query, ...texts) {
  const haystacks = texts.filter((t) => t != null).map((t) => searchable(t));
  return searchTerms(query).every((word) => haystacks.some((text) => text.includes(word)));
}

/**
 * The rows of the licence table (`app/notices/components.tsv`) that [build] carries -- `web` for
 * the page -- the phone's `OpenSourceNotices.parse`, rule for rule: comments and blank lines
 * skipped, a row short of its six cells refused, and each file named by where the staging puts it,
 * `notices/<id>/<file name>`.
 */
export function parseNotices(text, build = 'web') {
  const rows = [];
  for (const line of String(text ?? '').split('\n')) {
    if (!line.trim() || line.startsWith('#')) continue;
    const cells = line.split('\t');
    if (cells.length < 6) continue;
    const builds = cells[5].split(',').map((b) => b.trim()).filter(Boolean);
    if (!builds.includes(build)) continue;
    const id = cells[0].trim();
    rows.push({
      id,
      name: cells[1].trim(),
      version: cells[2].trim(),
      licence: cells[3].trim(),
      files: cells[4].split(',').map((f) => f.trim()).filter(Boolean)
        .map((f) => `notices/${id}/${f.slice(f.lastIndexOf('/') + 1)}`),
    });
  }
  return rows;
}

/** Markdown's emphasis, code and angle-bracket links taken off; the words stay. `LegalText.clean`. */
export function cleanLegal(text) {
  return String(text).replaceAll('**', '').replaceAll('`', '').replace(/<(https?:\/\/[^>]+)>/g, '$1').trim();
}

/**
 * The English section of the privacy policy as blocks -- headings, paragraphs and bullets -- headed
 * by its effective date: the phone's `LegalText.privacyBlocks` for the language the page speaks.
 */
export function privacyBlocks(markdown) {
  const lines = String(markdown ?? '').split('\n');
  const dateLine = lines.find((l) => l.startsWith('Effective date:'));
  const blocks = dateLine ? [{ kind: 'paragraph', text: cleanLegal(dateLine) }] : [];
  const start = lines.findIndex((l) => l.trim() === '## English');
  if (start < 0) return blocks;
  let end = lines.findIndex((l, i) => i > start && l.startsWith('## '));
  if (end < 0) end = lines.length;
  let pending = '';
  let bullet = false;
  const flush = () => {
    if (pending) blocks.push({ kind: bullet ? 'bullet' : 'paragraph', text: cleanLegal(pending) });
    pending = '';
    bullet = false;
  };
  for (const line of lines.slice(start + 1, end)) {
    const trimmed = line.trim();
    if (!trimmed) flush();
    else if (trimmed.startsWith('### ')) { flush(); blocks.push({ kind: 'heading', text: cleanLegal(trimmed.slice(4)) }); }
    else if (trimmed.startsWith('- ')) { flush(); bullet = true; pending = trimmed.slice(2); }
    else pending = pending ? `${pending} ${trimmed}` : trimmed;
  }
  flush();
  return blocks;
}

/**
 * A tune's own fields with the gaps filled from songdb (W5) -- the phone's `merged`: songdb **fills
 * and never overwrites**, because what a tune says about itself is not a guess and a database row
 * can be stale or wrong. The year needs its own test for "the file said nothing": sc68 writes `0`
 * or `unknown` when it does not know, which is not blank, so [hasYear] -- the page's `releaseYear`
 * -- decides whether the file named one.
 */
export function fillFromSongDb(fields, found, hasYear) {
  if (!found) return fields;
  const out = { ...fields };
  const fill = (key, value) => { if (value && !String(out[key] ?? '').trim()) out[key] = value; };
  fill('artist', found.author);
  fill('album', found.album);
  fill('publisher', found.publisher);
  if (found.year && !hasYear(out)) out.year = found.year;
  return out;
}

/**
 * Whether a play goes into History (`docs/BACKLOG.md` A56) -- the phone's `HistoryRecording.records`.
 *
 * A play History itself started, and the tunes walked after it with next or at a tune's end, leaves
 * its entry alone: no new time, no new place. History says what was played elsewhere, and does not
 * rearrange itself under the person reading it.
 */
export function recordsPlay({ walkingResults, fromHistory }) {
  return !(walkingResults && fromHistory);
}
