// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

function argumentsOf(source, match) {
  if (!match) throw new Error('Missing native data declaration');
  const start = match.index + match[0].length;
  let depth = 1, quoted = false, escaped = false;
  for (let at = start; at < source.length; at++) {
    const character = source[at];
    if (escaped) { escaped = false; continue; }
    if (quoted && character === '\\') { escaped = true; continue; }
    if (character === '"') { quoted = !quoted; continue; }
    if (quoted) continue;
    if (character === '(') depth++;
    if (character === ')' && --depth === 0) return source.slice(start, at);
  }
  throw new Error('Unterminated native data declaration');
}

/** Project only the explicit native data declarations, not executable Kotlin. */
export function platformProjection(source) {
  const declarations = new Map();
  for (const [, key, raw] of source.matchAll(/    val (\w+) = Platform\((.*?)\n    \)/gs)) {
    const block = raw.replace(/\/\/[^\n]*/g, '');
    const value = {};
    for (const field of ['id', 'name']) {
      const literal = block.match(new RegExp(`\\b${field}\\s*=\\s*("[^"\\n]*")`))?.[1];
      if (!literal) throw new Error(`Missing platform ${key}.${field}`);
      value[field] = JSON.parse(literal);
    }
    for (const field of ['catalogueFormats', 'names']) {
      const match = block.match(new RegExp(`\\b${field}\\s*=\\s*setOf\\(`));
      if (!match && !new RegExp(`\\b${field}\\s*=\\s*emptySet\\(\\)`).test(block))
        throw new Error(`Unsupported platform ${key}.${field}`);
      const list = match ? argumentsOf(block, match) : '';
      value[field] = [...list.matchAll(/"(?:[^"\\]|\\.)*"/g)].map(([literal]) => JSON.parse(literal));
    }
    declarations.set(key, value);
  }
  const order = source.match(/val all: List<Platform> = listOf\((.*?)\)/s)?.[1];
  const keys = order?.split(',').map((key) => key.trim()).filter(Boolean);
  if (!keys?.length || keys.some((key) => !declarations.has(key)) || keys.length !== declarations.size)
    throw new Error('The native platform declarations/order have changed shape');
  const whole = argumentsOf(source, source.match(/val wholeCatalogues.*?mapOf\(/s));
  const wholeCatalogues = Object.fromEntries([...whole.matchAll(/"([^"\n]+)"\s+to\s+(\w+)/g)]
    .map(([, name, key]) => { if (!declarations.has(key)) throw new Error(`Unknown platform ${key}`); return [name, declarations.get(key).id]; }));
  return { platforms: keys.map((key) => declarations.get(key)), wholeCatalogues };
}

export function platformModule(data) {
  return `// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
// Generated from native Platforms.kt by node scripts/generate-web-platforms.mjs.
const data = ${JSON.stringify(data, null, 2)};
for (const platform of data.platforms) {
  Object.freeze(platform.catalogueFormats); Object.freeze(platform.names); Object.freeze(platform);
}
Object.freeze(data.platforms); Object.freeze(data.wholeCatalogues);
export const PLATFORM_DATA = Object.freeze(data);
`;
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
  const native = fs.readFileSync(path.join(root, 'app/src/main/kotlin/com/przunk/protracktor/player/Platforms.kt'), 'utf8');
  const destination = path.join(root, 'web/src/platforms.js');
  const projected = platformModule(platformProjection(native));
  if (process.argv.includes('--check')) {
    if (fs.readFileSync(destination, 'utf8') !== projected) throw new Error('WEB platforms differ from Android; regenerate them');
    console.log('✓ WEB platform data matches the native declarations');
  } else {
    fs.writeFileSync(destination, projected);
    console.log('Generated web/src/platforms.js from native Platforms.kt');
  }
}
