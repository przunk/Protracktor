// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import { t } from './i18n.js';

/** A text-only combobox; data refreshes never request focus or reopen a submitted field. */
export class SearchSuggestions {
  constructor({ root, input, submit, list, suggestions, onInput, onSubmit, onFocus = () => {} }) {
    Object.assign(this, { root, input, submit, list, suggestions, onInput, onSubmit });
    this.visible = false;
    this.allowed = false;
    this.active = -1;
    this.items = [];
    input.setAttribute('role', 'combobox');
    input.setAttribute('aria-autocomplete', 'list');
    input.setAttribute('aria-controls', list.id);
    input.setAttribute('autocomplete', 'off');
    list.setAttribute('role', 'listbox');
    input.addEventListener('focus', () => { this.allowed = true; this.refresh(); onFocus(); });
    input.addEventListener('blur', (event) => {
      if (!root.contains(event.relatedTarget)) this.close();
    });
    input.addEventListener('input', () => {
      this.allowed = true; this.active = -1;
      onInput(input.value);
      this.refresh();
    });
    input.addEventListener('keydown', (event) => {
      if (event.key === 'Escape') { event.preventDefault(); this.close(); return; }
      if (event.key === 'Tab') { this.close(); return; }
      if (event.key === 'Enter') {
        event.preventDefault();
        this.choose(this.active >= 0 && !list.hidden ? this.items[this.active] : input.value);
        return;
      }
      if (event.key !== 'ArrowDown' && event.key !== 'ArrowUp') return;
      this.allowed = true; this.refresh();
      if (list.hidden) return;
      event.preventDefault();
      const direction = event.key === 'ArrowDown' ? 1 : -1;
      this.active = this.active < 0 ? (direction > 0 ? 0 : this.items.length - 1)
        : (this.active + direction + this.items.length) % this.items.length;
      this.refresh();
    });
    submit.addEventListener('click', () => this.choose(input.value));
    root.ownerDocument.addEventListener('pointerdown', (event) => {
      if (!root.contains(event.target)) this.close();
    });
    const window = root.ownerDocument.defaultView;
    window.addEventListener('resize', () => this.place());
    window.visualViewport?.addEventListener('resize', () => this.place());
    window.visualViewport?.addEventListener('scroll', () => this.place());
    this.refresh();
  }

  setVisible(visible) {
    this.visible = visible;
    this.root.hidden = !visible;
    this.input.hidden = !visible;
    if (!visible) this.close(true);
    else this.refresh();
  }

  close(blur = false) {
    this.allowed = false; this.active = -1;
    if (blur && this.input.ownerDocument.activeElement === this.input) this.input.blur();
    this.refresh();
  }

  choose(query) {
    this.input.value = query;
    this.close(true);
    this.onSubmit(query);
  }

  refresh() {
    this.items = this.suggestions(this.input.value);
    if (this.active >= this.items.length) this.active = -1;
    const expanded = this.visible && this.allowed && !this.root.closest('[hidden]')
      && this.input.ownerDocument.activeElement === this.input && this.items.length > 0;
    this.input.setAttribute('aria-expanded', String(expanded));
    this.input.setAttribute('aria-label', t('Search'));
    this.submit.setAttribute('aria-label', t('Search'));
    this.submit.title = t('Search');
    this.list.setAttribute('aria-label', t('Recent searches'));
    this.list.hidden = !expanded;
    this.list.replaceChildren();
    if (!expanded) { this.input.removeAttribute('aria-activedescendant'); return; }
    this.items.forEach((query, index) => {
      const row = this.list.ownerDocument.createElement('li');
      row.id = `${this.list.id}-${index}`;
      row.setAttribute('role', 'option');
      row.setAttribute('aria-selected', String(this.active === index));
      row.textContent = query;
      row.addEventListener('pointerdown', (event) => event.preventDefault());
      row.addEventListener('mousedown', (event) => event.preventDefault());
      row.addEventListener('click', () => this.choose(query));
      this.list.append(row);
    });
    if (this.active >= 0) this.input.setAttribute('aria-activedescendant', `${this.list.id}-${this.active}`);
    else this.input.removeAttribute('aria-activedescendant');
    this.place();
    this.list.children[this.active]?.scrollIntoView?.({ block: 'nearest' });
  }

  place() {
    if (this.list.hidden) return;
    const window = this.root.ownerDocument.defaultView;
    const viewport = window.visualViewport;
    const top = viewport?.offsetTop ?? 0;
    const bottom = top + (viewport?.height ?? window.innerHeight);
    const rect = this.root.getBoundingClientRect();
    const above = Math.max(0, rect.top - top - 4), below = Math.max(0, bottom - rect.bottom - 4);
    const wanted = this.list.scrollHeight || this.items.length * 48;
    const up = below < Math.min(wanted, above);
    this.list.style.top = up ? 'auto' : '100%';
    this.list.style.bottom = up ? '100%' : 'auto';
    this.list.style.maxHeight = `${Math.min(wanted, up ? above : below)}px`;
  }
}
