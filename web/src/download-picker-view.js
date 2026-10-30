// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import { DOWNLOAD_CHOICES, downloadSteps, downloadMegabytes, missingDownloads } from './library-downloads.js';
import { message as uiMessageText, pluralMessage as uiPluralText } from './i18n.js';
import { joinUiText } from './ui-message.js';
import { setUiText, setUiAttribute } from './ui-text.js';

/** Resolve labels at presentation time; app notices and picker rows share this mapping. */
export function downloadStepLabel(id) {
  switch (id) {
    case 'modland': return 'Modland';
    case 'asma': return 'ASMA';
    case 'favourites': return uiMessageText('Modland favourites');
    case 'songlengths': return uiMessageText('SID song lengths');
    case 'trackmetadata': return uiMessageText('Song credits');
    default: throw new TypeError(`Unknown download label: ${id}`);
  }
}
const choiceName = (id) => id === 'songmetadata' ? uiMessageText('Song metadata') : downloadStepLabel(id);
const statusText = (row) => {
  if (row.status === 'failed') return uiMessageText('Failed: {reason}', { reason: row.error.message });
  if (row.status === 'done') return uiMessageText('Downloaded');
  if (row.status === 'busy') return uiMessageText('Already downloading');
  if (row.status !== 'running') return uiMessageText('Waiting');
  const progress = row.progress;
  if (progress?.stage === 'fetching' && progress.done > 0) {
    const size = (progress.done / (1024 * 1024)).toFixed(1);
    return progress.total > 0 ? uiMessageText('Downloading {done} of {total} MB', { done: size, total: (progress.total / (1024 * 1024)).toFixed(1) })
      : uiMessageText('Downloading {size} MB', { size });
  }
  if (progress?.stage === 'storing') return uiMessageText('Saving');
  if (['reading', 'sorting', 'verifying'].includes(progress?.stage)) return uiMessageText('Preparing');
  return uiMessageText('Downloading');
};

/** Stable controls retain focus while the current per-dataset snapshot changes beneath them. */
export class DownloadPickerView {
  #selected = new Set();
  #rows = new Map();
  #state = null;
  constructor({ root, runner, icon, onStart, onStop, onRetry }) {
    Object.assign(this, { root, runner, icon, onStart, onStop, onRetry });
    const document = root.ownerDocument;
    this.fields = document.createElement('fieldset'); this.fields.className = 'download-choices';
    this.legend = document.createElement('legend'); this.fields.append(this.legend);
    for (const id of DOWNLOAD_CHOICES) {
      const label = document.createElement('label'); label.className = 'download-choice'; label.dataset.downloadChoice = id;
      const input = document.createElement('input'); input.type = 'checkbox'; input.value = id;
      input.onchange = () => { if (input.checked) this.#selected.add(id); else this.#selected.delete(id); this.render(); };
      const text = document.createElement('span'); text.className = 'download-choice-text';
      const name = document.createElement('strong'), size = document.createElement('span'), detail = document.createElement('span');
      size.className = 'storage'; detail.className = 'storage'; text.append(name, size, detail);
      const progress = document.createElement('span'); progress.className = 'download-choice-progress'; progress.setAttribute('aria-hidden', 'true');
      label.append(input, text, progress); this.fields.append(label); this.#rows.set(id, { input, name, size, detail, progress });
    }
    this.note = document.createElement('p'); this.note.className = 'note'; this.note.setAttribute('role', 'status');
    this.footer = document.createElement('div'); this.footer.className = 'download-actions';
    this.action = document.createElement('button'); this.action.type = 'button'; this.action.className = 'outlined';
    this.action.onclick = () => runner.running ? onStop() : onStart([...this.#selected]);
    this.retry = document.createElement('button'); this.retry.type = 'button'; this.retry.className = 'outlined'; this.retry.onclick = onRetry;
    this.footer.append(this.action, this.retry); root.append(this.fields, this.note, this.footer);
    this.unsubscribe = runner.subscribe((state) => { this.#state = state; this.render(); });
    this.#state = runner.state; this.render();
    const focusRoot = root.closest('[role="dialog"]') ?? root;
    focusRoot.addEventListener('keydown', (event) => {
      if (event.key !== 'Tab') return;
      const controls = [...focusRoot.querySelectorAll('input:not(:disabled), button:not(:disabled), [tabindex="0"]')]
        .filter((element) => !element.closest('[hidden]'));
      const first = controls[0], last = controls.at(-1);
      if (!first) { event.preventDefault(); return; }
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
    });
  }
  selectMissing(held) { if (!this.runner.running) this.#selected = new Set(missingDownloads(held)); this.render(); }
  render() {
    const state = this.#state, running = this.runner.running;
    setUiText(this.legend, uiMessageText('Choose what to download'));
    for (const [id, row] of this.#rows) {
      const steps = downloadSteps([id]), reports = state.rows.filter((item) => steps.includes(item.id));
      const selected = running ? reports.length > 0 : this.#selected.has(id);
      row.input.checked = selected; row.input.disabled = running;
      setUiText(row.name, choiceName(id)); setUiText(row.size, uiMessageText('About {size} MB', { size: downloadMegabytes([id]) }));
      const report = reports.find((item) => item.status === 'running') ?? reports.find((item) => item.status === 'failed' || item.status === 'busy')
        ?? (reports.length && reports.every((item) => item.status === 'done') ? reports.at(-1) : reports.find((item) => item.status === 'pending'));
      setUiText(row.detail, report ? joinUiText(downloadStepLabel(report.id), ': ', statusText(report)) : '');
      const busy = reports.some((item) => item.status === 'running' || item.status === 'busy');
      if (row.progress.dataset.busy !== String(busy)) { row.progress.dataset.busy = String(busy); row.progress.innerHTML = busy ? '<span class="spinner"></span>' : ''; }
    }
    const retryCount = this.runner.retryable.length;
    setUiText(this.note, state.phase === 'stopping' ? uiMessageText('Stopping downloads — completed data will be kept.')
      : state.phase === 'stopped' ? uiMessageText('Downloads stopped. Completed data is kept; Retry continues unfinished steps.')
      : !running && !this.#selected.size ? uiMessageText('Select at least one dataset to download.') : '');
    this.action.disabled = state.phase === 'stopping' || !running && !this.#selected.size;
    const caption = running ? uiMessageText('Stop') : uiMessageText('Download ({size} MB)', { size: downloadMegabytes(this.#selected) });
    this.action.innerHTML = this.icon(running ? 'stop' : 'download');
    const label = this.root.ownerDocument.createElement('span'); setUiText(label, caption); this.action.append(label);
    setUiAttribute(this.action, 'aria-label', caption);
    this.retry.hidden = running || !retryCount;
    this.retry.innerHTML = this.icon('refresh'); const retryLabel = this.root.ownerDocument.createElement('span');
    setUiText(retryLabel, uiPluralText(retryCount, 'Retry {count} unfinished step', 'Retry {count} unfinished steps', { count: retryCount })); this.retry.append(retryLabel);
  }
}
