// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import fs from 'node:fs';
import path from 'node:path';
import { SourceTextModule } from 'node:vm';

/** Use the real module scopes/imports with the existing jsdom audio/network/storage bootstrap. */
export async function loadPageModules({ dom, capture }) {
  const root = path.resolve('web/src'), entry = path.join(root, 'app.js'), cache = new Map();
  const context = dom.getInternalVMContext();
  function moduleFor(file) {
    if (!file.startsWith(root + path.sep) || path.extname(file) !== '.js') throw new Error(`Unexpected frontend module: ${file}`);
    if (cache.has(file)) return cache.get(file);
    let source = fs.readFileSync(file, 'utf8');
    if (file === entry) {
      // Debug-only aliases must not make a missing production import available to the app.
      const exposed = capture.replace('translateStatic, staticTexts, hasPolish, language',
        'translateStatic, staticTexts: pageTestI18n.staticTexts, hasPolish: pageTestI18n.hasPolish, language')
        .replace('catalogueStore: catalogue', 'catalogueStore: pageTestCatalogue');
      source += `\nimport * as pageTestI18n from './i18n.js';\nimport { catalogue as pageTestCatalogue } from './store.js';\n${exposed}`;
    }
    const module = new SourceTextModule(source, { context, identifier: file,
      initializeImportMeta(meta) { meta.url = new URL(path.relative(root, file), dom.window.location.href).href; } });
    cache.set(file, module); return module;
  }
  const app = moduleFor(entry);
  await app.link((specifier, parent) => {
    if (!specifier.startsWith('.')) throw new Error(`Unexpected external frontend import: ${specifier}`);
    return moduleFor(path.resolve(path.dirname(parent.identifier), specifier));
  });
  await app.evaluate();
  return cache.size;
}
