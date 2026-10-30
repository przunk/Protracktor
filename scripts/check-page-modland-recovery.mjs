// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

/** Native C109/C119 inputs through real playlist actions and the common byte resolver. */
export default async function checkModlandRecovery({ window, check }) {
  const api = window.__api, { document } = window, $ = (id) => document.getElementById(id);
  const pause = () => new Promise((resolve) => setTimeout(resolve, 15));
  const until = async (predicate) => { for (let i = 0; i < 300; i++) { if (await predicate()) return true; await pause(); } return false; };
  const paths = [
    ['YM', '- unknown', 'conflict europe.ym'],
    ['Nintendo SPC', '- unknown', 'Same Game/same game (22).spc'],
    ['Ad Lib/Screamtracker 3 AdLib', '- unknown', '(opl2) totally nuts.s3m'],
    ['Video Game Music/Sega Megadrive', '- unknown', 'World Championship Soccer/06 ending theme.vgz'],
  ];
  const tracks = paths.map(([format, author, path], i) => ({
    url: window.__archive.fileUrlFor([format, author, path].join('/')),
    name: 'Legacy recovery ' + i, file: path.split('/').at(-1),
  }));
  const originals = tracks.map((track) => track.url), alternatives = originals.map((url) => url.replace('/-%20unknown/', '/_unknown/'));
  const opens = () => window.__toWorklet.filter((message) => message.type === 'open');
  const fetch = window.fetch, requests = []; let offline = false;
  window.fetch = (input, options) => {
    const url = String(input), original = originals.indexOf(url), alternate = alternatives.indexOf(url);
    if (original === -1 && alternate === -1) return fetch(input, options);
    requests.push(url);
    if (offline) return Promise.reject(new TypeError('controlled offline replay'));
    return Promise.resolve(new window.Response(new Uint8Array([41 + alternate, 99]), { status: original >= 0 ? 404 : 200 }));
  };
  try {
    api.showPanel(null); await api.discardEdits(); api.clearPlaybackQueue(); await api.musicNow().clear();
    await api.storeNow.save({ id: 'modland-legacy-ui', name: 'Legacy files', tracks, index: 0 });
    await api.choosePlaylist('modland-legacy-ui');
    for (let i = 0; i < tracks.length; i++) {
      const before = opens().length;
      $('queue').querySelectorAll('li.track')[i].click();
      check(await until(() => opens().length > before && opens().at(-1).name === tracks[i].file),
        `the real playlist tap recovers ${tracks[i].file} through the shared downloader`);
      if (opens().length === before) continue;
      check(new Uint8Array(opens().at(-1).bytes)[0] === 41 + i
        && requests.filter((url) => url === originals[i] || url === alternatives[i]).join('|') === [originals[i], alternatives[i]].join('|'),
        `the original then recovered request supply exact bytes for ${tracks[i].file}`);
      api.onWorklet({ type: 'opened', requestId: opens().at(-1).requestId,
        describe: `title\tLegacy ${i}\nartist\tUnknown\nformat\tTest`, duration: 90, subsongs: 1, current: 0, canSeek: true, rate: 44100 });
      check(await until(async () => (await window.__played.recent()).some((entry) => entry.url === originals[i]))
        && !(await window.__played.recent()).some((entry) => entry.url === alternatives[i]),
        'recovered playback records only the original History identity');
    }
    check(JSON.stringify(api.queueNow()) === JSON.stringify(originals)
      && JSON.stringify((await api.storeNow.get('modland-legacy-ui')).tracks.map((track) => track.url)) === JSON.stringify(originals),
      'legacy recovery never rewrites visible order, source IDs or saved playlist references');
    check((await api.musicStore.snapshot()).map((entry) => entry.url).sort().join('|') === [...originals].sort().join('|'),
      'only the four original positive payloads enter retained music storage');
    offline = true; const requestsBefore = requests.length, beforeReplay = opens().length;
    $('queue').querySelectorAll('li.track')[0].click();
    check(await until(() => opens().length > beforeReplay) && requests.length === requestsBefore
      && new Uint8Array(opens().at(-1).bytes)[0] === 41,
      'the same real playlist action replays a recovered file offline without trying either URL');
    const beforeAction = window.__toWorklet.length, fileBytes = await api.bytesOf(tracks[2]);
    check(new Uint8Array(fileBytes)[0] === 43 && requests.length === requestsBefore && window.__toWorklet.length === beforeAction,
      'file Information/export resolution reuses recovered bytes without changing playback');
  } finally { window.fetch = fetch; }
}
