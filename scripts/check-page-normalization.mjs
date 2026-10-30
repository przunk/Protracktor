// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

/** Actual Settings events; the separate real-engine bridge verifies their PCM consequence. */
export default async function checkNormalization({ window, check }) {
  const { document } = window, api = window.__api, $ = (id) => document.getElementById(id);
  const toggle = $('settings-normalization'), KEY = 'protracktor.volumeNormalization';
  check(!!toggle && toggle.type === 'checkbox', 'normalization has the native single opt-in switch');
  if (!toggle) return;
  const until = async (predicate) => {
    const deadline = Date.now() + 5000;
    while (Date.now() < deadline) { if (predicate()) return true; await new Promise((done) => setTimeout(done, 5)); }
    return false;
  };
  const set = (enabled) => { if (toggle.checked !== enabled) toggle.click(); };
  const opens = () => window.__toWorklet.filter((row) => row.type === 'open');
  const changes = () => window.__toWorklet.filter((row) => row.type === 'normalization');
  const tracks = ['a.mod', 'b.mod'].map((file) => ({ url: `https://example.test/normalization-ui/${file}`, name: file, file }));
  await api.discardEdits(); api.showPanel(null); api.clearPlaybackQueue();
  await api.storeNow.save({ id: 'normalization-source', name: 'Normalization source', tracks, index: 0 });
  await api.choosePlaylist('normalization-source');
  const before = opens().length; $('queue').querySelector('li.track').click(); document.body.click();
  check(await until(() => opens().length > before), 'an actual playlist tap establishes the live source for normalization Settings');
  api.onWorklet({ type: 'opened', requestId: opens().at(-1)?.requestId, describe: 'title\tNormalization fixture',
    duration: 120, rate: 44100, subsongs: 3, current: 0, canSeek: true });
  await until(() => api.contextNow()?.state === 'running');
  api.onWorklet({ type: 'position', seconds: 12, requestId: opens().at(-1)?.requestId });
  await api.enqueueTrack(tracks[1]); await api.enqueueTrack(tracks[1]);
  await api.storeNow.save({ id: 'normalization-editor', name: 'Other editor', tracks: [tracks[1]], index: 0 });
  await api.choosePlaylist('normalization-editor'); api.showPanel('settings'); set(false);
  const held = { track: api.actualNow(), source: api.planNow().context, context: api.contextNow(),
    editor: JSON.stringify(api.editorNow()), pending: api.scheduleNow().pending.map((r) => r.id).join('|'),
    opens: opens().length, clock: $('elapsed').textContent };
  const first = changes().length; toggle.click();
  check(toggle.checked && changes().length === first + 1 && changes().at(-1).enabled === true,
    'the real switch sends one live enable command to the existing worklet');
  check(window.localStorage.getItem(KEY) === '1', 'normalization persists only its opt-in preference');
  toggle.focus();
  check(document.activeElement === toggle && toggle.closest('label').querySelector('svg')?.getAttribute('fill') === 'currentColor'
    && toggle.closest('label').textContent.includes('Volume normalization'), 'the switch has keyboard focus, icon, readable name and theme-aware colour');
  check(toggle.getAttribute('aria-describedby') === 'normalization-help' && $('normalization-help').textContent.includes('First-play matching'),
    'normalization explains first-play limits and exported-audio independence');
  check(api.actualNow() === held.track && api.planNow().context === held.source && api.contextNow() === held.context
    && opens().length === held.opens && $('elapsed').textContent === held.clock && $('playpause').title === 'Pause',
    'live normalization preserves track, source, clock and audio instance');
  check(JSON.stringify(api.editorNow()) === held.editor && api.scheduleNow().pending.map((r) => r.id).join('|') === held.pending,
    'normalization preserves another editor and duplicate manual requests');
  $('playpause').click();
  check(await until(() => $('playpause').title === 'Play'), 'the actual transport finishes pausing before normalization is changed');
  toggle.click();
  check(!toggle.checked && changes().at(-1).enabled === false && $('playpause').title === 'Play', 'normalization Off does not resume paused playback');
  api.showPanel(null); api.showPanel('settings');
  check(!toggle.checked, 'reopening Settings keeps the current normalization choice');
  document.querySelector('input[name="language"][value="pl"]').click();
  check(toggle.closest('label').textContent.includes('Normalizacja głośności') && $('normalization-help').textContent.includes('Nie wpływa na eksportowane audio'),
    'the actual language action translates the native normalization label and explanation');
  toggle.click(); check(changes().at(-1).enabled === true, 'the Polish control reaches the same normalization action');
  document.querySelector('input[name="language"][value="en"]').click();
  const write = window.Storage.prototype.setItem;
  window.Storage.prototype.setItem = function (key, value) { if (key === KEY) throw new Error('normalization write refused'); return write.call(this, key, value); };
  try {
    toggle.click();
    check(!toggle.checked && changes().at(-1).enabled === false && window.localStorage.getItem(KEY) === '1',
      'refused normalization persistence keeps safe live state without claiming a saved change');
    check(!$('snackbar').hidden && $('snacktext').textContent.includes('normalization write refused'), 'a normalization write refusal gives visible feedback');
  } finally { window.Storage.prototype.setItem = write; }
  toggle.click(); check(window.localStorage.getItem(KEY) === '1', 'a later successful normalization choice can persist after refusal');
  set(false); api.showPanel(null);
  await api.choosePlaylist('normalization-editor');
  const beforeFailedOpen = opens().length; $('queue').querySelector('li.track').click();
  check(await until(() => opens().length > beforeFailedOpen), 'a fresh private startup can receive a global engine failure');
  const requestId = opens().at(-1).requestId;
  window.__workletPort.onmessage({ data: { type: 'failed', reason: 'initial engine failure' } });
  const prepared = window.__toWorklet.filter((row) => row.type === 'prepared').length;
  await window.__workletPort.onmessage({ data: { type: 'decoded', requestId, current: 0 } });
  check(window.__toWorklet.filter((row) => row.type === 'prepared').length === prepared,
    'a global engine failure retires private startup bytes and rejects late preparation');
}
