// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

/** Whole-ASMA downloads, real row playback and confirmed deletion through the production UI. */
export default async function checkOfflineAsma({ window, check }) {
  const { document } = window, api = window.__api, archive = window.__archive, $ = (id) => document.getElementById(id);
  const pause = (ms = 15) => new Promise((resolve) => setTimeout(resolve, ms));
  const until = async (predicate) => { for (let i = 0; i < 300; i++) { if (await predicate()) return true; await pause(); } return false; };
  const target = 'https://asma.atari.org/asma/Composers/Aki/Robots.sap';
  const asmaRow = () => [...$('browselist').children].find((row) => row.querySelector('.bname')?.textContent === 'ASMA');
  const cataloguePage = async () => { api.showPanel('browse'); await api.browseTo(['catalogues']); return asmaRow(); };
  const fetch = window.fetch, confirm = window.confirm; let fileRequests = 0, archiveRequests = 0;
  window.fetch = (url, options) => {
    if (String(url).startsWith('https://asma.atari.org/asma/')) { fileRequests++; return Promise.reject(new TypeError('offline ASMA file fixture')); }
    if (String(url) === 'https://asma.atari.org/asmadb/asma.zip') archiveRequests++;
    return fetch(url, options);
  };
  try {
    await archive.forgetIndex('asma');
    const built = archive.toRecords('10\tComposers/Aki/Robots.sap', 'asma', () => true);
    await api.catalogueStore.replacePrefix('asma:', [...built.records, {key:'asma:meta',tracks:1,complete:true}]);
    const indexed = await cataloguePage();
    check(indexed.querySelector('.bmeta').textContent.includes('No offline archive') && api.asmaNow().state.bytes === 0,
      'a retained old ASMA index stays browsable but does not claim offline bytes');
    check(indexed.querySelector('.bdl').getAttribute('aria-label') === 'Download the ASMA archive (about 20 MB)',
      'ASMA explicitly names the complete archive and approximate download size');
    const beforeDownload = window.__toWorklet.length; indexed.querySelector('.bdl').click();
    check(await until(() => api.asmaNow().state.bytes === window.__asmaZip.length && !asmaRow()?.querySelector('.spinner')),
      'the real ASMA download action publishes the archive and refreshed catalogue');
    check(archiveRequests === 1 && window.__toWorklet.length === beforeDownload && api.asmaNow().state.files === 4,
      'one explicit archive request retains all files without starting or interrupting playback');
    check(asmaRow().querySelector('.bmeta').textContent.includes('3 tunes') && asmaRow().querySelector('.bmeta').textContent.includes('available offline'),
      'Online catalogues reports actual playable count and held-archive availability');

    const heldRow = asmaRow();document.querySelector('input[name="language"][value="pl"]').click();
    check(asmaRow() === heldRow && heldRow.querySelector('.bmeta').textContent.includes('dostępne offline')
      && heldRow.querySelector('.bdl').getAttribute('aria-label') === 'Zaktualizuj archiwum ASMA',
      'new ASMA availability and action labels change language on the same catalogue row');
    document.querySelector('input[name="language"][value="en"]').click();
    check(heldRow.querySelector('.bmeta').textContent.includes('available offline') && window.__toWorklet.length === beforeDownload,
      'restoring English keeps held-archive facts and sends no audio command');

    await api.browseTo(['asma','Composers','Aki']);
    const row = [...$('browselist').children].find((item) => item.dataset.url === target);
    check(row.querySelector('.leading-primary svg') && row.querySelector('.leading-primary').getAttribute('aria-label') === 'Downloaded copy — available offline',
      'archive member presence decorates the same native-style Browse offline slot');
    const opens = () => window.__toWorklet.filter((message) => message.type === 'open');
    const prior = opens().length; row.querySelector('.btext').click();
    check(await until(() => opens().length > prior && opens().at(-1).name === 'Robots.sap'),
      'tapping a real Browse row opens a retained ASMA member while individual HTTP files are offline');
    check(fileRequests === 0 && new TextDecoder().decode(opens().at(-1).bytes) === 'SAP Robots',
      'ASMA playback sends the exact archived bytes to the decoder without another network request');
    api.onWorklet({type:'opened',requestId:opens().at(-1).requestId,describe:'title\tRobots\nartist\tAki\nformat\tSAP',duration:90,subsongs:1,current:0,canSeek:true,rate:44100});
    const again = await api.bytesOf({url:target});
    check(new TextDecoder().decode(again) === 'SAP Robots' && opens().at(-1).bytes.byteLength > 0,
      'a decoder transfer cannot detach the separately retained archive member');
    const reopened = await import('../web/src/store.js?offline-asma-page');
    check((await reopened.asmaArchive.read()).body.size === window.__asmaZip.length,
      'a newly opened production database connection sees the complete immutable archive');reopened.forget();
    check(!(await api.musicStore.snapshot()).some((entry) => entry.url === target),
      'reading the permanent ASMA archive does not duplicate files into the fetched-music LRU');

    await api.storeNow.save({id:'asma-kept-document',name:'ASMA kept',tracks:[{url:target,name:'Robots'}]});
    await api.enqueueTrack({url:target,name:'Manual Robots'});api.onWorklet({type:'position',seconds:17});
    const state={context:api.planNow().context,schedule:api.scheduleNow(),editor:api.editorNow(),audio:api.contextNow(),actual:api.actualNow(),commands:window.__toWorklet.length,elapsed:$('elapsed').textContent};
    const continuity = () => api.planNow().context === state.context && api.scheduleNow() === state.schedule && api.editorNow() === state.editor
      && api.contextNow() === state.audio && api.actualNow() === state.actual && window.__toWorklet.length === state.commands && $('elapsed').textContent === state.elapsed;
    await api.musicStore.write('https://example.test/asma-ordinary.mod',await new window.Response(new Uint8Array([1,2,3])).blob());
    await until(() => api.musicNow().state.bytes > 0);
    api.showPanel(null);$('tab-settings').click();
    const storageRow = () => [...$('settingsfields').querySelectorAll('dt')].find((item) => item.textContent === 'ASMA index and archive')?.nextElementSibling;
    check(await until(() => storageRow()?.textContent.includes('available offline')),
      'Settings shows the ASMA index and archive as one storage item with actual archive size');
    const clear = [...$('music-cache-storage').querySelectorAll('button')].find((item) => item.textContent === 'Clear');clear.click();
    check(await until(() => api.musicNow().state.bytes === 0) && (await api.asmaStore.read()).body.size === window.__asmaZip.length,
      'the actual music-cache Clear action preserves the permanent ASMA archive');
    check(continuity() && new TextDecoder().decode(await api.bytesOf({url:target})) === 'SAP Robots',
      'cache Clear retains ASMA byte access, current sound ownership, context, manual requests and draft');
    const remove = [...storageRow().querySelectorAll('button')].find((item) => item.textContent === 'Delete');
    let asked = 0;window.confirm = () => {asked++;return false;};remove.click();await pause();
    check(asked === 1 && (await api.asmaStore.read())?.body.size === window.__asmaZip.length,
      'cancelling ASMA deletion leaves both archive and catalogue intact');
    window.confirm = () => true;remove.click();
    check(await until(async () => !await api.asmaStore.read() && !await api.archiveMeta('asma')),
      'confirming the ASMA storage action removes archive and index together');
    check(await until(() => api.asmaNow().state.bytes === 0) && continuity(),
      'archive deletion retires offline marks without stopping the playing file or changing its queue');
    check((await api.storeNow.get('asma-kept-document')).tracks[0].url === target && (await window.__played.recent()).some((item) => item.url === target),
      'ASMA deletion retains playlist URLs and History records for future downloads');

    const retryRow = await cataloguePage();retryRow.querySelector('.bdl').click();
    await until(() => api.asmaNow().state.bytes > 0 && !asmaRow()?.querySelector('.spinner'));
    const snapshot = await api.asmaStore.ticket();const objectStore = window.IDBObjectStore ?? globalThis.IDBObjectStore, put = objectStore.prototype.put;
    objectStore.prototype.put = function(...args) {if(this.name==='asmaArchives'&&args[0]?.id==='asma')throw new DOMException('ASMA page quota refused','QuotaExceededError');return put.apply(this,args);};
    try {asmaRow().querySelector('.bdl').click();check(await until(() => $('browsenote').textContent.includes('ASMA page quota refused')),
      'an actual archive-save refusal is visibly explained by the download action');}
    finally {objectStore.prototype.put=put;}
    check(await api.asmaStore.ticket() === snapshot && new TextDecoder().decode(await api.bytesOf({url:target})) === 'SAP Robots',
      'a failed archive update preserves the previous playable archive and publication revision');
    check(continuity(), 'failed redownload and its feedback never reopen or stop the current file');
    const retained = await api.asmaStore.read(), indexRecords = await api.catalogueStore.byPrefix('asma:');
    const visibleRow = asmaRow(), download = visibleRow.querySelector('.bdl');download.focus();
    const other = await import('../web/src/store.js?asma-page-focus');
    const read = api.catalogueStore.get;let releaseOld, enteredOld = false, intercept = true;
    api.catalogueStore.get = async (key) => {
      const value = await read.call(api.catalogueStore,key);
      if (key === 'asma:meta' && intercept) { intercept = false; enteredOld = true; await new Promise((resolve) => { releaseOld = resolve; }); }
      return value;
    };
    try {
      await api.asmaNow().refresh();
      check(await until(() => enteredOld), 'the actual presentation reader can hold a captured older ASMA index');
      await other.asmaArchive.remove();window.dispatchEvent(new window.Event('focus'));
      check(await until(() => api.asmaNow().state.bytes === 0 && visibleRow.querySelector('.bmeta').textContent.includes('No offline archive')
        && !visibleRow.querySelector('.bheld').classList.contains('yes')),
        'focus refresh withdraws removed archive facts and the index mark from the visible catalogue row');
      check(asmaRow() === visibleRow && visibleRow.querySelector('.bdl') === download && document.activeElement === download
        && download.getAttribute('aria-label') === 'Download the ASMA archive (about 20 MB)',
        'cross-tab archive changes preserve the focused Download control while updating its label and icon');
      releaseOld?.();api.catalogueStore.get=read;await pause(50);
      check(visibleRow.querySelector('.bmeta').textContent.includes('No offline archive') && !visibleRow.querySelector('.bheld').classList.contains('yes'),
        'a late older presentation read cannot restore removed archive facts or its index mark');
      visibleRow.click();await pause();
      check($('browsetitle').textContent === 'Online catalogues' && visibleRow.classList.contains('closed') && continuity(),
        'a removed catalogue cannot reopen an empty folder or interrupt current playback');
      api.showPanel(null);$('tab-settings').click();
      await until(() => storageRow()?.querySelector('button'));
      const storageBody = storageRow(), removeButton = storageBody.querySelector('button');
      check(removeButton.disabled && storageBody.textContent.includes('No offline archive'),
        'empty ASMA storage explains its unavailable Delete action');
      await other.asmaArchive.replace(retained,indexRecords,{ticket:await other.asmaArchive.ticket()});window.dispatchEvent(new window.Event('focus'));
      check(await until(() => !removeButton.disabled && storageBody.textContent.includes('available offline'))
        && storageRow() === storageBody && storageBody.querySelector('button') === removeButton,
        'a full archive installed in another connection updates the same visible storage row and Delete control');
      await other.asmaArchive.remove();window.dispatchEvent(new window.Event('focus'));
      check(await until(() => removeButton.disabled && storageBody.textContent.includes('No offline archive')) && continuity(),
        'cross-tab deletion refreshes storage facts without rebuilding controls or touching playback');
    } finally {releaseOld?.();api.catalogueStore.get=read;other.forget();}

  } finally {window.fetch=fetch;window.confirm=confirm;}
}
