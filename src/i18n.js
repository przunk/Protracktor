// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
//
// The page in English or Polish (`docs/PLAN_WEB_PARITY.md` W6), as the app is.
//
// **The English text is the key.** A call reads `t('Nothing playing')`, so the code still says what
// the screen says, and a string nobody translated shows in English rather than as a key. The Polish
// lives in `i18n-pl.js`; `check-page.mjs` fails when a `t(...)` in `app.js` or a text in `index.html`
// has no Polish, which is the compiler's exhaustive `when` for a language table.
//
// **Not translated, deliberately:** what a decoder or the network says about a file (`e.message`,
// a refusal's reason). The phone shows those as the engine wrote them too; they are about the
// machine, and a translation would be a guess at what it meant.

import { PL } from './i18n-pl.js';
import { uiMessage, isUiMessage, joinUiText } from './ui-message.js';
import { setUiText, setUiAttribute, refreshUiText } from './ui-text.js';

const TABLES = { pl: PL };

/** What Settings → Language offers, the app's own three. */
export const LANGUAGE_CHOICES = ['system', 'en', 'pl'];

/**
 * The language to speak: the one chosen, or the first of the browser's that this page has, or
 * English -- the phone's rule, which follows the system unless told otherwise.
 */
export function resolveLanguage(choice, preferred = []) {
  if (choice === 'en' || choice === 'pl') return choice;
  for (const tag of preferred) {
    const base = String(tag).toLowerCase().split('-')[0];
    if (base === 'pl' || base === 'en') return base;
  }
  return 'en';
}

let current = 'en';

/** The language in use now. */
export function language() { return current; }

/** Speaks [lang] from here on, and says so to the document, for screen readers and hyphenation. */
export function useLanguage(lang) {
  current = TABLES[lang] || lang === 'en' ? lang : 'en';
  document.documentElement.lang = current;
}

function fill(text, vars) {
  return vars ? text.replace(/\{(\w+)\}/g, (whole, name) => (name in vars ? String(vars[name]) : whole)) : text;
}

/** [text] in the language in use, with `{name}` filled from [vars]. */
export function t(text, vars) {
  const found = TABLES[current]?.[text];
  return fill(typeof found === 'string' ? found : text, vars);
}

/** Preserve an action's arguments while resolving its presentation language only when drawn. */
export function message(text, vars) {
  const snapshot = vars ? snapshotArguments(vars) : undefined;
  return uiMessage(() => t(text, snapshot));
}

function snapshotArguments(vars) {
  return Object.freeze(Object.fromEntries(Object.entries(vars)
    .map(([key, value]) => [key, isUiMessage(value) ? value : String(value)])));
}

/**
 * A count with its noun: [one] or [other] in English, and the Polish table's three forms --
 * `1 utwór`, `2 utwory`, `5 utworów` -- under [other]'s key. `{n}` is the count.
 */
export function tn(n, one, other, vars = {}) {
  const all = { n, ...vars };
  const forms = TABLES[current]?.[other];
  if (Array.isArray(forms)) {
    const rule = new Intl.PluralRules(current).select(n);
    const pick = rule === 'one' ? forms[0] : rule === 'few' ? forms[1] : forms[2];
    return fill(pick, all);
  }
  return fill(n === 1 ? one : other, all);
}

export function pluralMessage(n, one, other, vars = {}) {
  const snapshot = snapshotArguments(vars);
  return uiMessage(() => tn(n, one, other, snapshot));
}

/**
 * Capture original markup once, then refresh only explicit UI bindings in that root.
 * Keys fold whitespace because index.html wraps long sentences over lines.
 */
const staticRoots = new WeakSet();
export function translateStatic(root) {
  if (!staticRoots.has(root)) {
    // Capture the original markup even in English; later user data has no translation ownership.
    const walker = root.ownerDocument.createTreeWalker(root, 4 /* NodeFilter.SHOW_TEXT */);
    for (let node = walker.nextNode(); node; node = walker.nextNode()) {
      const tag = node.parentElement?.tagName;
      if (tag === 'SCRIPT' || tag === 'STYLE') continue;
      const key = node.textContent.replace(/\s+/g, ' ').trim();
      if (typeof PL[key] === 'string') {
        const lead = node.textContent.match(/^\s*/)[0], tail = node.textContent.match(/\s*$/)[0];
        setUiText(node, joinUiText(lead, message(key), tail));
      }
    }
    for (const el of root.querySelectorAll('[aria-label], [placeholder], [title]')) {
      for (const name of ['aria-label', 'placeholder', 'title']) {
        const key = el.getAttribute(name);
        if (key !== null && typeof PL[key] === 'string') setUiAttribute(el, name, message(key));
      }
    }
    staticRoots.add(root);
  }
  refreshUiText(root);
}

/** Every text `translateStatic` would look for in [root]: for the check that none is missing. */
export function staticTexts(root) {
  const texts = new Set();
  const walker = root.ownerDocument.createTreeWalker(root, 4);
  for (let node = walker.nextNode(); node; node = walker.nextNode()) {
    const tag = node.parentElement?.tagName;
    if (tag === 'SCRIPT' || tag === 'STYLE') continue;
    const key = node.textContent.replace(/\s+/g, ' ').trim();
    if (/[A-Za-z]{2}/.test(key)) texts.add(key);
  }
  for (const el of root.querySelectorAll('[aria-label], [placeholder], [title]')) {
    for (const name of ['aria-label', 'placeholder', 'title']) {
      const value = el.getAttribute(name);
      if (value && /[A-Za-z]{2}/.test(value)) texts.add(value);
    }
  }
  return texts;
}

/** Whether [text] has Polish -- a plain entry, or the three forms of a count. */
export function hasPolish(text) {
  return text in PL;
}
