// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

/** Refresh already visible owners through System events, without reopening their views. */
export default async function checkLanguageViews({window,check}) {
  const {document}=window,api=window.__api,$=(id)=>document.getElementById(id);
  const settle=(ms=20)=>new Promise((done)=>setTimeout(done,ms));
  const until=async(predicate)=>{for(let n=0;n<250;n++){if(await predicate())return true;await settle();}return false;};
  const prior=Object.getOwnPropertyDescriptor(window.navigator,'languages');
  const locale=(code)=>{Object.defineProperty(window.navigator,'languages',{configurable:true,value:[code]});window.dispatchEvent(new window.Event('languagechange'));};
  const t=(key,vars)=>window.__i18n.t(key,vars);
  const radio=(choice)=>document.querySelector(`input[name="language"][value="${choice}"]`);
  const enter=async(name)=>{api.showPanel('browse');await api.browseTo([]);[...$('browselist').children].find((row)=>row.querySelector('.bname')?.textContent===t(name)).click();await settle();};
  try {
    locale('en-US');api.showPanel(null);$('tab-settings').click();
    check(await until(()=>$('settingsfields').querySelectorAll('dt').length>4),'the language fixture opens the actual Settings storage rows');
    radio('system').click();
    const labels=[...$('settingsfields').querySelectorAll('dt')],storage=labels.find((node)=>node.textContent==='Modland index');
    locale('pl-PL');check(storage&&storage.isConnected&&storage.textContent===t('{source} index',{source:'Modland'}),'existing per-catalogue Storage labels translate in place');locale('en-US');

    await api.storeNow.save({id:'language-failed-target',name:'Language failure target',tracks:[]});
    await api.storeNow.save({id:'language-search-view',name:'Random',tracks:[{url:'https://example.test/language-views/search.it',name:'Search',file:'search.it'}]});
    await enter('Search');document.querySelector('[data-search-scope="playlists"]').click();
    const input=$('browsesearch');input.value='Search';input.dispatchEvent(new window.Event('input',{bubbles:true}));$('browsesubmit').click();
    check(await until(()=>!api.searchNow().loading&&api.searchNow().result?.query==='Search'&&$('browselist').querySelector('.btrack')),'live-language fixture submits Search through its integrated button');
    const result=api.searchNow().result,row=$('browselist').querySelector('.btrack');
    row.querySelector('.bmore').click();[...$('menu').querySelectorAll('button')].find((button)=>button.textContent==='Select').click();
    const tick=row.querySelector('.tick');tick.focus();const selection=api.browseSelectionNow(),commands=window.__toWorklet.length,plan=api.planNow(),note=$('browsenote').textContent;
    locale('pl-PL');
    check(row=== $('browselist').querySelector('.btrack')&&tick.checked&&document.activeElement===tick&&api.browseSelectionNow().owner===selection.owner,'System language refresh retains the selected result row checkbox focus and selection owner');
    check(tick.getAttribute('aria-label')===t('Select {name}',{name:'Search'})&&$('browse-select-all').textContent.includes(t('Select all')),'selection controls translate in place while their raw Search title stays literal');
    check(api.searchNow().result===result&&$('browsenote').textContent!==note,'the existing result description translates without replacing its search answer');
    locale('en-US');input.focus();input.value='Se';input.dispatchEvent(new window.Event('input',{bubbles:true}));input.setSelectionRange(1,2);
    const option=$('search-suggestions').firstElementChild,scopeLabel=input.getAttribute('aria-label');
    locale('pl-PL');
    check(input.value==='Se'&&input.selectionStart===1&&input.selectionEnd===2&&document.activeElement===input,'language refresh preserves an unsubmitted query cursor selection and keyboard focus');
    check(option&&option=== $('search-suggestions').firstElementChild&&option.textContent==='Search'&&!$('search-suggestions').hidden,'recent suggestions remain the same literal query nodes without closing their popup');
    check(input.getAttribute('aria-label')!==scopeLabel&&api.searchNow().result===result&&api.planNow().context===plan.context&&window.__toWorklet.length===commands,'scope-aware input labels translate without submission or playback commands');
    locale('en-US');$('sel-add').click();
    check(await until(()=>!$('addto').hidden),'the language fixture enters the actual multi-selection playlist chooser');
    const target=[...$('addtolist').children].find((node)=>node.querySelector('.pname')?.textContent==='Language failure target');
    const save=api.storeNow.save;let fail;
    api.storeNow.save=(record)=>record.id==='language-failed-target'?new Promise((resolve,reject)=>{fail=reject;}):save(record);
    try {
      target.click();check(await until(()=>!!fail),'the actual chosen target holds a pending storage write');locale('pl-PL');
      check($('addtonote').textContent===t('Adding…'),'an in-flight destination caption translates without retrying the write');
      fail(new Error('locale chooser refused'));
      check(await until(()=>$('snacktext').textContent.includes('locale chooser refused'))&&$('addtonote').textContent.startsWith(window.__i18n.tn(selection.count,'{n} track to add.','{n} tracks to add.')),'a failed destination write restores its original count in the current language');
      locale('en-US');check($('addtonote').textContent.startsWith(window.__i18n.tn(selection.count,'{n} track to add.','{n} tracks to add.'))&&target.isConnected,'the restored destination caption stays translatable and retains its target row');
    } finally {api.storeNow.save=save;}
    $('addto').querySelector('[data-close]').click();await settle();


    const key={catalogueId:'modland',format:'Unavailable language folder',author:'Random'};
    await window.__bookmarks.save({key,catalogueName:'Modland',savedAt:4000000});await window.__savedPlaces.refresh();await enter('Saved places');
    const savedRow=[...$('browselist').querySelectorAll('.saved-place')].find((node)=>JSON.parse(node.dataset.place)[1]===key.format);
    check(!!savedRow&&savedRow.querySelector('.saved-actions button').disabled,'saved-language fixture exposes a retained unavailable folder with an explanation');
    const reason=savedRow.querySelector('.saved-reason'),oldReason=reason.textContent,bookmark=savedRow.querySelector('.saved-actions button:last-child');bookmark.focus();
    locale('pl-PL');
    check(savedRow.isConnected&&savedRow.querySelector('.bname').textContent==='Random'&&document.activeElement===bookmark&&reason.textContent!==oldReason,'saved-place reason translates without replacing its raw author row or focused action');
    check(bookmark.textContent===t('Bookmark')&&savedRow.querySelector('.saved-actions button').textContent===t('Open')&&savedRow.querySelector('.saved-actions button').disabled,'translated bookmark controls retain the unavailable Open state');
    bookmark.click();check(await until(()=>$('snackundo').hidden===false&&!savedRow.isConnected),'actual bookmark removal offers Undo before another language change');
    const removedCaption=$('snacktext').textContent;locale('en-US');
    check($('snacktext').textContent!==removedCaption&&$('snacktext').textContent===t('Bookmark removed. Music and playlists are unchanged.')&&!$('snackundo').hidden,'the pending bookmark Undo message translates the same native action meaning');
    $('snackundo').click();check(await until(async()=>(await window.__bookmarks.list()).some((place)=>place.key.format===key.format)),'translated Undo still restores the exact removed bookmark');

    api.showPanel(null);$('showqueue').click();await settle();
    const grip=$('playback-list').querySelector('.queue-grip'),queueRows=[...$('playback-list').children],schedule=api.scheduleNow();
    if(grip)grip.focus();const aria=grip?.getAttribute('aria-label'),scroll=$('playback-list').scrollTop;
    locale('pl-PL');
    check(grip&&document.activeElement===grip&&grip.getAttribute('aria-label')!==aria,'the visible Queue grip translates without losing focus');
    check(queueRows.every((node,at)=>$('playback-list').children[at]===node)&&api.scheduleNow()===schedule&&$('playback-list').scrollTop===scroll,'Queue language changes retain physical rows duplicate occurrences scroll and schedule');
    const item=$('playback-list').querySelector('.queue-track');item.dispatchEvent(new window.MouseEvent('contextmenu',{bubbles:true,cancelable:true}));
    const menuButtons=[...$('menu').querySelectorAll('button')],menuText=$('menu').textContent;locale('en-US');
    check(menuButtons.length>0&&!$('menu').hidden&&menuButtons.every((button)=>button.isConnected)&&$('menu').textContent!==menuText,'an open context menu translates its owned captions without replacing its buttons');
    check(window.__toWorklet.length===commands&&api.planNow().context===plan.context,'all visible-owner translations and bookmark actions preserve transport ownership');
    api.showPanel('settings');$('open-sources').click();
    check(await until(()=>$('legalbody').querySelector('.source')),'language fixture opens the actual Music sources sheet');
    const link=$('legalbody').querySelector('.source a'),description=$('legalbody').querySelector('.source small'),sourceText=description.textContent;
    link.focus();locale('pl-PL');
    check(link.isConnected&&document.activeElement===link&&description.textContent!==sourceText,'source acknowledgements translate from the held bilingual table while preserving the Website link');
    $('legal').querySelector('[data-close]').click();api.showPanel('settings');$('open-privacy').click();
    check(await until(()=>$('legalbody').querySelector('h3')),'language fixture opens the actual Privacy policy sheet');
    const policyBody=$('legalbody');policyBody.scrollTop=75;const heading=policyBody.querySelector('h3').textContent;
    const fetch=window.fetch;let requests=0;window.fetch=(...args)=>{requests++;return fetch(...args);};
    try {
      locale('en-US');
      check(policyBody.querySelector('h3').textContent!==heading&&policyBody.querySelector('h3').textContent==='Who is responsible'&&policyBody.scrollTop===75&&requests===0,'visible policy prose follows language from its held source without refetching or resetting its scroll');
    } finally {window.fetch=fetch;}
    $('legal').querySelector('[data-close]').click();

  } finally {
    api.showPanel('settings');radio('en').click();
    if(prior)Object.defineProperty(window.navigator,'languages',prior);else delete window.navigator.languages;
    api.showPanel(null);
  }
}
