// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

/** Actual Native SubsongStrip cases against page events; geometry is a host fixture. */
export default async function checkSubsongStrip({ window, check }) {
  const { document } = window, $ = (id) => document.getElementById(id), api = window.__api, strip = $('subsongs');
  const settle = (ms = 60) => new Promise((resolve) => setTimeout(resolve, ms));
  const chip = (n) => [...strip.children].find((node) => node.textContent === String(n));
  const tracks = ['first.kss', 'second.kss'].map((file) => ({ url: `https://example.test/subsong-strip/${file}`, name: file, file }));
  const fields = (name) => `title\t${name}\nartist\tStrip Author\nyear\t1998`;
  const opened = (current, count = 256, name = 'Native tune 69') => api.onWorklet({ type: 'opened',
    requestId: window.__toWorklet.filter((m) => m.type === 'open').at(-1)?.requestId,
    describe: fields(name), duration: 120, rate: 44100, canSeek: true, subsongs: count, current });
  const advance = async (current) => { api.onWorklet({ type: 'subsong', index: current, describe: fields(`Native tune ${current + 1}`), duration: 120 }); await settle(); };
  const originalRects = window.HTMLElement.prototype.getBoundingClientRect;
  const originalLeft = Object.getOwnPropertyDescriptor(strip, 'scrollLeft');
  const width = Object.getOwnPropertyDescriptor(strip, 'clientWidth');
  const scrollWidth = Object.getOwnPropertyDescriptor(strip, 'scrollWidth');
  Object.defineProperty(strip, 'clientWidth', { configurable: true, get: () => 330 });
  Object.defineProperty(strip, 'scrollWidth', { configurable: true, get: () => Math.max(0, strip.children.length * 42 - 6) });
  const scrolls = [];
  let scrollLeft = strip.scrollLeft;
  Object.defineProperty(strip, 'scrollLeft', { configurable: true, get: () => scrollLeft,
    set: (left) => { scrolls.push(left); scrollLeft = Math.max(0, Math.min(strip.scrollWidth - strip.clientWidth, left)); } });
  window.HTMLElement.prototype.getBoundingClientRect = function () {
    if (this === strip) return { left: 0, right: 330, top: 100, bottom: 136, width: 330, height: 36 };
    if (this.parentElement === strip) {
      const left = (Number(this.textContent) - 1) * 42 - strip.scrollLeft;
      return { left, right: left + 36, top: 100, bottom: 136, width: 36, height: 36 };
    }
    return originalRects.call(this);
  };
  try {
    await api.discardEdits(); api.showPanel(null);
    api.playFromBrowse(tracks, 0); await settle(); opened(68);
    check(window.getComputedStyle(strip).flexWrap === 'nowrap' && window.getComputedStyle(strip).overflowX === 'auto',
      'the native-style subsong strip is one horizontal row instead of a wrapping wall of chips');
    $('nowcard').click(); await settle();
    check(strip.scrollLeft === 68 * 42 && chip(69)?.getAttribute('aria-pressed') === 'true',
      'opening Now Playing brings its arbitrary current 69 of 256 into view');
    check(window.getComputedStyle(chip(69)).width === '36px' && window.getComputedStyle(chip(69)).height === '36px',
      'numeric chips retain the native 36px target and their own horizontal space');
    const oldNode = chip(69), left = strip.scrollLeft, moves = scrolls.length;
    await advance(69);
    check(chip(69) === oldNode && chip(70).getAttribute('aria-pressed') === 'true',
      'advancing keeps existing chip nodes while updating only the actual current mark');
    check(strip.scrollLeft === left && scrolls.length === moves, 'an already-visible advancing tune does not move the strip');
    strip.scrollLeft = 248 * 42; const distant = strip.scrollLeft;
    await advance(70);
    check(strip.scrollLeft === distant && chip(256).getBoundingClientRect().right === 330,
      'a distant region being browsed stays put when the audible tune advances');
    await advance(253); const still = scrolls.length;
    await advance(254);
    check(strip.scrollLeft === distant && scrolls.length === still, 'visible late tunes resume watching without unnecessary scroll');
    await advance(0);
    check(strip.scrollLeft === 0, 'after watching the previous chip a jump out of the viewport follows its actual new current');
    strip.scrollLeft = 248 * 42;
    const loadingChip = chip(20);
    api.playAt(1);
    const loadingCommands = window.__toWorklet.length; loadingChip.click();
    check(!strip.children.length && $('subsongbar').hidden && window.__toWorklet.length === loadingCommands,
      'starting a new file immediately retires old chip controls before its decoder answers');
    await settle(); opened(5); await settle();
    check(strip.scrollLeft === 5 * 42 && chip(6).getAttribute('aria-pressed') === 'true',
      'a different file resets opening even when its subsong count is identical');
    const before = window.__toWorklet.length;
    chip(7).click();
    check(window.__toWorklet.slice(before).filter((m) => m.type === 'subsong' && m.index === 6).length === 1,
      'the actual chip click selects exactly one tune with play-all either on or off');
    await advance(6);
    const duplicate = window.__toWorklet.length; chip(7).click();
    check(window.__toWorklet.length === duplicate, 'the actual currently selected chip is ignored as native controller selection is');
    const play = $('playpause').onclick; let shortcuts = 0; $('playpause').onclick = () => shortcuts++;
    try {
      chip(8).focus(); chip(8).dispatchEvent(new window.KeyboardEvent('keydown', { key: ' ', bubbles: true, cancelable: true })); chip(8).click();
      check(shortcuts === 0 && window.__toWorklet.at(-1)?.type === 'subsong' && window.__toWorklet.at(-1)?.index === 7,
        'Space on a chip reaches its one selection without invoking the global Play shortcut');
    } finally { $('playpause').onclick = play; }
    await advance(7); strip.scrollLeft = 248 * 42; api.showPanel(null);
    await advance(8); $('nowcard').click(); await settle();
    check(strip.scrollLeft === 8 * 42, 'a fresh Now Playing opening follows current even if that same file was previously browsed far away');
    const stale = chip(12);
    const description = api.informAbout(tracks[0]); await settle();
    const request = window.__toWorklet.filter((m) => m.type === 'describe').at(-1);
    if (request) api.onWorklet({ type: 'described', id: request.id, ok: true, describe: fields('Inspected only'), subsongs: 2 });
    await description;
    check($('np-title').textContent === 'Inspected only' && $('subsongbar').hidden && !strip.children.length,
      'Information describes another file without offering controls wired to the sounding decoder');
    const obsolete = window.__toWorklet.length; stale.click();
    check(window.__toWorklet.length === obsolete, 'a retired chip cannot send a selection into another strip visit');
    api.showPanel(null); $('nowcard').click(); await settle();
    check($('np-title').textContent === 'Native tune 9' && chip(9)?.getAttribute('aria-pressed') === 'true' && strip.scrollLeft === 8 * 42,
      'dock reentry restores actual current subsong metadata and strip after inspecting another file');
    const delayed = api.informAbout(tracks[0]); await settle();
    const late = window.__toWorklet.filter((m) => m.type === 'describe').at(-1);
    api.showPanel(null); $('nowcard').click(); await settle();
    if (late) api.onWorklet({ type: 'described', id: late.id, ok: true, describe: fields('Obsolete inspected title'), subsongs: 2 });
    await delayed;
    check($('np-title').textContent === 'Native tune 9' && chip(9)?.getAttribute('aria-pressed') === 'true',
      'a late Information reply cannot replace a freshly reopened live strip or metadata');
    opened(0, 5, 'Five tunes'); await settle();
    check(strip.children.length === 5 && strip.scrollLeft === 0 && chip(1).getAttribute('aria-pressed') === 'true',
      'a changed count starts at its own current instead of retaining an obsolete far offset');
    window.__i18n.useLanguage('pl'); opened(1, 5); await settle();
    check(chip(2)?.getAttribute('aria-label') === 'Utwór 2 z 5', 'chip accessible descriptions follow Polish');
    window.__i18n.useLanguage('en'); opened(0, 1); await settle();
    check(!strip.children.length && $('subsongbar').hidden, 'a single-tune file removes the strip and its old following state');
    api.showPanel(null);
  } finally {
    window.HTMLElement.prototype.getBoundingClientRect = originalRects;
    if (originalLeft) Object.defineProperty(strip, 'scrollLeft', originalLeft); else delete strip.scrollLeft;
    if (width) Object.defineProperty(strip, 'clientWidth', width); else delete strip.clientWidth;
    if (scrollWidth) Object.defineProperty(strip, 'scrollWidth', scrollWidth); else delete strip.scrollWidth;
  }
}
