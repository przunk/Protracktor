// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

/** Settings gestures against the real page; PCM and worklet delivery have separate real-engine checks. */
export default async function checkStereoMix({ window, check }) {
  const { document } = window, api = window.__api, $ = (id) => document.getElementById(id);
  const radio = (mode) => document.querySelector(`input[name="stereomix"][value="${mode}"]`);
  check(!!radio('original') && !!radio('narrow') && !!radio('mono'), 'actual Settings offers all three native stereo modes');
  if (!radio('original') || !radio('narrow') || !radio('mono')) return;
  const wait = (ms = 60) => new Promise((done) => setTimeout(done, ms));
  const until = async (predicate) => { for (let i = 0; i < 300; i++) { if (predicate()) return true; await wait(10); } return false; };
  const opens = () => window.__toWorklet.filter((message) => message.type === 'open');
  const mixes = () => window.__toWorklet.filter((message) => message.type === 'stereo');
  const tracks = ['first.mod', 'second.mod'].map((file) => ({ url: `https://example.test/stereo/${file}`, file, name: file }));
  await api.discardEdits(); api.showPanel(null); api.clearPlaybackQueue();
  await api.storeNow.save({ id: 'stereo-live', name: 'Stereo live', tracks, index: 0 });
  await api.choosePlaylist('stereo-live');
  const previousOpens = opens().length; $('queue').querySelector('li.track').click();
  check(await until(() => opens().length > previousOpens), 'a real playlist tap opens the fixture before changing stereo');
  document.body.click();
  check(await until(() => api.contextNow()?.state === 'running'), 'an actual user gesture clears the browser autoplay gate before pause checks');
  const send = (message) => api.onWorklet({ ...message, requestId: opens().at(-1)?.requestId });
  send({ type: 'opened', describe: 'title\tStereo fixture', duration: 120, rate: 44100, canSeek: true, subsongs: 3, current: 0 });
  send({ type: 'position', seconds: 12 });
  await api.enqueueTrack(tracks[1]); await api.enqueueTrack(tracks[1]);
  await api.storeNow.save({ id: 'stereo-editor', name: 'Unrelated editor', tracks: [tracks[1]], index: 0 });
  await api.choosePlaylist('stereo-editor');
  api.showPanel('settings'); radio('original').click();
  const held = { actual: api.actualNow(), context: api.planNow().context, audio: api.contextNow(),
    editor: JSON.stringify(api.editorNow()), pending: api.scheduleNow().pending.map((request) => request.id).join('|'),
    opens: opens().length, clock: $('elapsed').textContent };
  for (const [mode, percent, label] of [['narrow', 50, 'Narrow'], ['mono', 0, 'Mono'], ['original', 100, 'Original']]) {
    const before = mixes().length; radio(mode).click();
    check(radio(mode).checked && mixes().length === before + 1 && mixes().at(-1).percent === percent,
      `actual ${label} choice sends exactly the native width to the existing worklet`);
    check(window.localStorage.getItem('protracktor.stereoMix') === mode && $('stereo-current').textContent === `Current: ${label}`,
      `${label} persists its stable value and explains the current selection`);
    radio(mode).focus();
    check(document.activeElement === radio(mode) && radio(mode).closest('label').querySelector('svg')
      && radio(mode).closest('label').textContent.includes(label), `${label} has a focusable native radio, icon and associated label`);
  }
  const repeated = window.__toWorklet.length; radio('original').click();
  check(window.__toWorklet.length === repeated, 'choosing the already-selected mix does not reconfigure or reopen playback');
  check(api.actualNow() === held.actual && api.planNow().context === held.context && api.contextNow() === held.audio
    && opens().length === held.opens && $('playpause').title === 'Pause' && $('elapsed').textContent === held.clock,
  'live stereo choices preserve the actual track, playback source, clock and running audio instance');
  check(JSON.stringify(api.editorNow()) === held.editor && !api.dirtyNow()
    && api.scheduleNow().pending.map((request) => request.id).join('|') === held.pending,
  'stereo changes preserve another visible playlist and pending duplicate FIFO requests');
  $('playpause').click(); await wait();
  const pausedAt = window.__toWorklet.length; radio('mono').click();
  check($('playpause').title === 'Play' && window.__toWorklet.slice(pausedAt).every((message) => message.type === 'stereo')
    && mixes().at(-1).percent === 0, 'a paused stereo choice updates the target without starting audio');
  api.showPanel(null); api.showPanel('settings');
  check(radio('mono').checked && $('stereo-current').textContent === 'Current: Mono', 'reopening Settings retains the latest choice without resetting it');
  document.querySelector('input[name="language"][value="pl"]').click();
  check(radio('narrow').closest('label').textContent.includes('Zwężone') && radio('original').closest('label').textContent.includes('Oryginał')
    && $('stereo-current').textContent === 'Wybrane: Mono', 'the actual language choice translates all stereo labels and the selected explanation');
  radio('narrow').click();
  check($('stereo-current').textContent === 'Wybrane: Zwężone' && mixes().at(-1).percent === 50, 'a Polish choice reaches the same live setting');
  document.querySelector('input[name="language"][value="en"]').click();
  radio('original').click();
  const set = window.Storage.prototype.setItem, stored = window.localStorage.getItem('protracktor.stereoMix');
  try {
    window.Storage.prototype.setItem = function (key, value) {
      if (key === 'protracktor.stereoMix') throw new Error('stereo quota refused');
      return set.call(this, key, value);
    };
    radio('narrow').click();
    check(radio('narrow').checked && mixes().at(-1).percent === 50 && window.localStorage.getItem('protracktor.stereoMix') === stored,
      'refused stereo persistence keeps the live in-memory selection without claiming a saved change');
    check(!$('snackbar').hidden && $('snacktext').textContent.includes('stereo quota refused'), 'a real stereo write refusal gives visible feedback');
  } finally { window.Storage.prototype.setItem = set; }
  radio('mono').click();
  check(window.localStorage.getItem('protracktor.stereoMix') === 'mono', 'a later successful stereo choice persists after refusal');
  $('playpause').click(); await wait();
  check($('playpause').title === 'Pause' && opens().length === held.opens && radio('mono').checked,
    'resuming keeps the selected mix without reopening the decoder');
  radio('original').click(); api.showPanel(null);
}
