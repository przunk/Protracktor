// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import { setUiText, setUiAttribute } from './ui-text.js';
import { message as uiMessageText, pluralMessage as uiPluralText } from './i18n.js';

/** Selection decorates existing rows; it never rebuilds a list or owns playback. */
export class BrowseSelectionView {
  constructor({ model, header, count, all, leading, changed }) {
    Object.assign(this, { model, header, count, all, leading, changed }); this.rows = [];
    all.onclick = () => model.toggleAll();
  }
  sync(owner, rows) {
    const next = rows.filter((row) => row.owner === owner && row.node.isConnected);
    const nodes = new Set(next.map((row) => row.node));
    for (const row of this.rows) if (!nodes.has(row.node)) {
      row.dispose(); row.node.classList.remove('ticked'); row.add.hidden = row.more.hidden = false;
      row.node.querySelector('.track-leading input')?.closest('.track-leading').replaceWith(this.leading(row.track.url));
    }
    this.rows = next;
    this.model.replace(owner, this.rows.map((row) => row.track));
  }
  render() {
    const selecting = this.model.active;
    this.header.hidden = !this.rows.length;
    setUiText(this.count, selecting ? uiPluralText(this.model.count, '{n} selected', '{n} selected')
      : uiPluralText(this.rows.length, '{n} track', '{n} tracks'));
    this.all.hidden = !selecting;
    setUiText(this.all.querySelector('span'), this.model.all ? uiMessageText('Select none') : uiMessageText('Select all'));
    for (const row of this.rows) {
      const slot = row.node.querySelector('.track-leading');
      row.node.classList.toggle('ticked', this.model.has(row.track.url));
      row.add.hidden = row.more.hidden = selecting;
      if (selecting) {
        let box = slot.querySelector('input');
        if (!box) {
          box = row.node.ownerDocument.createElement('input'); box.type = 'checkbox'; box.className = 'tick';
          box.onclick = (event) => event.stopPropagation();
          box.onchange = () => this.model.toggle(row.owner, row.track.url);
          slot.replaceChildren(box);
        }
        box.checked = this.model.has(row.track.url);
        setUiAttribute(box, 'aria-label', uiMessageText('Select {name}', { name: row.shown().name }));
      } else if (slot.querySelector('input')) {
        const focused = slot.contains(row.node.ownerDocument.activeElement);
        slot.replaceWith(this.leading(row.track.url));
        if (focused) row.text.focus({ preventScroll: true });
      }
    }
    this.changed();
  }
}
