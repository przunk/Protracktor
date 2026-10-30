// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import { isUiMessage, renderUiValue } from './ui-message.js';

const texts = new WeakMap(), attributes = new WeakMap();
const rendererOf = (value) => typeof value === 'function' ? () => renderUiValue(value())
  : isUiMessage(value) ? () => renderUiValue(value) : null;

/** Binding the actual text node prevents a raw title with the same letters inheriting a label. */
export function setUiText(target, value) {
  const render = rendererOf(value), next = render ? render() : renderUiValue(value);
  let node = target;
  if (target.nodeType !== 3) {
    node = target.childNodes.length === 1 && target.firstChild.nodeType === 3 ? target.firstChild : null;
    if (!node) { node = target.ownerDocument.createTextNode(next); target.replaceChildren(node); }
  }
  if (node.textContent !== next) node.textContent = next;
  if (render) texts.set(node, { render, last: next }); else texts.delete(node);
  return node;
}

export function makeUiText(document, value) {
  const node = document.createTextNode('');
  setUiText(node, value);
  return node;
}

export function setUiAttribute(element, name, value) {
  const render = rendererOf(value), next = render ? render() : renderUiValue(value);
  if (element.getAttribute(name) !== next) element.setAttribute(name, next);
  let bindings = attributes.get(element);
  if (render) {
    if (!bindings) { bindings = new Map(); attributes.set(element, bindings); }
    bindings.set(name, { render, last: next });
  } else bindings?.delete(name);
}

/** Walk only current descendants; weak bindings do not retain removed rows, messages or files. */
export function refreshUiText(root) {
  const document = root.ownerDocument ?? root;
  const visitText = (node) => {
    const binding = texts.get(node);
    if (!binding) return;
    if (node.textContent !== binding.last) { texts.delete(node); return; }
    const next = binding.render();
    if (node.textContent !== next) node.textContent = next;
    binding.last = next;
  };
  if (root.nodeType === 3) visitText(root);
  const walker = document.createTreeWalker(root, 4);
  for (let node = walker.nextNode(); node; node = walker.nextNode()) visitText(node);
  const elements = [...(root.nodeType === 1 ? [root] : []), ...(root.querySelectorAll?.('*') ?? [])];
  for (const element of elements) {
    const bindings = attributes.get(element);
    if (!bindings) continue;
    for (const [name, binding] of bindings) {
      if (element.getAttribute(name) !== binding.last) { bindings.delete(name); continue; }
      const next = binding.render();
      if (element.getAttribute(name) !== next) element.setAttribute(name, next);
      binding.last = next;
    }
  }
}
