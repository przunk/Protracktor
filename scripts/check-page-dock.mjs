// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

/** DockIdentityLayoutTest's identity/count/action cases through the browser's production page. */
export default async function checkDock({ window, check }) {
  const { document } = window, $ = (id) => document.getElementById(id), api = window.__api;
  const settle = (ms = 50) => new Promise((resolve) => setTimeout(resolve, ms));
  const tune = $('dock-tune'), slot = document.querySelector('.nowkeep-slot');
  check(!!tune && !!slot, 'the dock has independent Tune and permanently reserved Add slots');
  if (!tune || !slot) return;
  const style = (node) => window.getComputedStyle(node);
  const key = (target, name) => target.dispatchEvent(new window.KeyboardEvent('keydown', { key: name, bubbles: true, cancelable: true }));
  const tracks = [
    { url: 'https://modland.com/pub/modules/Impulsetracker/Treks/battle%20of%20honour.it', name: 'battle of honour.it', file: 'battle of honour.it' },
    { url: 'https://example.test/dock/one.mod', name: 'one.mod', file: 'one.mod' },
  ];
  const fields = 'title\tBattle of honour\nartist\tTreks\nyear\t1998\nformat\tImpulse Tracker';
  const opened = (count, current = 0) => api.onWorklet({ type: 'opened', subsongs: count, current,
    requestId: window.__toWorklet.filter((message) => message.type === 'open').at(-1)?.requestId,
    duration: 120, rate: 44100, canSeek: true, describe: fields });
  api.clearPlaybackQueue(); await api.discardEdits();
  await api.storeNow.save({ id: 'dock-editor', name: 'Dock draft', tracks: [] });
  await api.choosePlaylist('dock-editor'); api.showPanel(null);
  api.playFromBrowse(tracks, 0); await settle(); opened(5);
  check($('title').textContent === 'Battle of honour' && $('sub').textContent === 'Treks · PC · 1998',
    'native Battle of honour identity keeps title above its shared author/platform/year summary');
  check($('title').closest('.nowhead') === $('nowkeep').closest('.nowhead')
    && $('sub').closest('.nowfoot') === tune.closest('.nowfoot') && !$('showqueue').closest('#nowcard'),
    'title/Add share the upper row; metadata/Tune share the lower row; Queue stays outside');
  check(style($('nowcard')).minHeight === '64px' && style($('nowcard')).paddingTop === '0px'
    && style($('nowcard')).paddingBottom === '8px' && style(document.querySelector('.dock-summary')).height === '',
    'ordinary browser card retains its 64px minimum without a top spacer or fixed outer height');
  check(style(slot).width === '48px' && style(slot).height === '32px'
    && style($('nowkeep')).width === '48px' && style($('nowkeep')).height === '32px',
    'Add owns a reserved 48 by 32 target with no expanded hit area over the footer');
  check(style(document.querySelector('.nowfoot')).flexWrap === 'wrap' && style($('sub')).minWidth === '64px'
    && style(tune).whiteSpace === 'nowrap', 'large text can wrap the footer counter below metadata instead of overlapping');
  const upper = $('title').parentElement, titlePolicy = style(upper).gridTemplateColumns;
  for (const count of [1, 5, 256]) {
    opened(count, count - 1);
    check(tune.hidden === (count <= 1) && (count <= 1 || tune.textContent === `Tune ${count} of ${count}`),
      `actual opened count ${count} uses the full Tune label in its own footer`);
    check($('title').parentElement === upper && style(upper).display === 'grid' && style(upper).gridTemplateColumns === titlePolicy,
      `count ${count} cannot change the title row sizing policy`);
  }
  const plan = api.planNow(), before = window.__toWorklet.length;
  tune.click();
  check(!$('nowplaying').hidden && $('nowcard').getAttribute('aria-expanded') === 'true'
    && api.planNow().context === plan.context && api.scheduleNow() === plan.schedule
    && window.__toWorklet.length === before && !api.dirtyNow(), 'footer tap opens Now Playing without Add or transport changes');
  api.showPanel(null); $('nowkeep').click();
  check(api.editorNow().order.tracks.length === 1 && api.dirtyNow() && $('nowplaying').hidden
    && window.__toWorklet.length === before, 'actual Add edits the selected draft without also expanding or playing');
  const play = $('playpause').onclick; let shortcuts = 0; $('playpause').onclick = () => shortcuts++;
  try {
    $('nowcard').focus(); key($('nowcard'), ' ');
    check(!$('nowplaying').hidden && shortcuts === 0, 'Space on the focused identity card expands without the global Play shortcut');
    key($('nowcard'), 'Enter');
    check($('nowplaying').hidden && $('nowcard').getAttribute('aria-expanded') === 'false' && shortcuts === 0,
      'Enter on the identity card folds Now Playing through the same action');
  } finally { $('playpause').onclick = play; }
  $('showqueue').click();
  check(!$('playback-panel').hidden && $('nowplaying').hidden && window.__toWorklet.length === before,
    'actual Queue opens independently of the identity card and sounding decoder');
  $('queue-close').click();
  window.__i18n.useLanguage('pl'); opened(256, 255);
  check(tune.textContent === 'Utwór 256 z 256', 'the complete Tune counter follows Polish with a three-digit index');
  window.__i18n.useLanguage('en');
  api.onWorklet({ type: 'subsong', index: 1, duration: 90, describe: fields });
  check(tune.textContent === 'Tune 2 of 256', 'a live subsong answer updates the counter independently of the play-all mode');
  const describe = api.informAbout(tracks[1]); await settle();
  const request = window.__toWorklet.filter((message) => message.type === 'describe').at(-1);
  if (request) api.onWorklet({ type: 'described', ok: true, id: request.id, describe: 'title\tOther inspected file', subsongs: 2, current: 0 });
  await describe;
  check($('np-title').textContent === 'Other inspected file' && tune.textContent === 'Tune 2 of 256',
    'describing another file cannot replace the actual playing Tune counter');
  api.playAt(1); await settle();
  check(tune.hidden, 'a new load retires the preceding file counter before the decoder answers');
  api.onWorklet({ type: 'failed', reason: 'controlled dock refusal' });
  check(tune.hidden, 'a failed file never inherits the previous subsong counter');
  opened(1); check(tune.hidden, 'a single-tune file keeps the ordinary footer without an old count');
  await api.saveEdits(); await api.choosePlaylist('dock-editor'); api.showPanel(null);
  $('queue').querySelector('.track .text').click(); await settle(); opened(5);
  check($('nowkeep').hidden && slot.contains($('nowkeep')) && style(slot).width === '48px'
    && style(upper).gridTemplateColumns === titlePolicy,
    'an owned source hides Add while retaining its upper-row slot and title sizing');
}
