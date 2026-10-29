// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {
  artworkSeed, artworkHue, artworkBackground, artworkBackgroundForHue, artworkLabel,
  ARTWORK_FOREGROUND, TrackArtwork,
} from '../web/src/track-artwork.js';
import { parseFormats } from '../web/src/catalogue.js';

let checks = 0;
function test(name, run) {
  run();
  checks++;
  console.log(`✓ ${name}`);
}
const text = fs.readFileSync('docs/rules/artwork-cases.tsv', 'utf8');
const rows = text.split('\n').filter(line => line && !line.startsWith('#'));
assert.equal(rows.shift(), 'kind\tseed\thue\targb');
assert.equal(rows.length, Number(text.match(/^# cases: (\d+)$/m)?.[1]));
const cases = rows.map(line => line.split('\t'));
assert.equal(cases.filter(row => row[0] === 'hue').length, 360);
assert.equal(cases.filter(row => row[0] === 'seed').length, 7);
for (const row of cases) {
  test(`Native artwork ${row[0]} ${JSON.stringify(row[1])} hue${row[2]}`, () => {
    assert.equal(row.length, 4);
    if (row[0] === 'hue') {
      assert.equal(artworkBackgroundForHue(Number(row[2])), '#' + row[3].slice(2));
    } else {
      assert.equal(row[0], 'seed');
      const seed = row[1] === '<empty>' ? '' : row[1];
      assert.equal(artworkHue(seed), Number(row[2]));
      assert.equal(artworkBackground(seed), '#' + row[3].slice(2));
    }
  });
}
test('all 360 generated colours retain the native label contrast and uniformity', () => {
  const luminance = hex => {
    const rgb = hex.slice(1).match(/../g).map(v => Number.parseInt(v, 16) / 255)
      .map(v => v <= .03928 ? v / 12.92 : ((v + .055) / 1.055) ** 2.4);
    return rgb[0] * .2126 + rgb[1] * .7152 + rgb[2] * .0722;
  };
  const foreground = luminance(ARTWORK_FOREGROUND);
  const ratios = Array.from({ length: 360 }, (_, h) =>
    (foreground + .05) / (luminance(artworkBackgroundForHue(h)) + .05));
  assert(Math.min(...ratios) >= 4.5);
  assert(Math.max(...ratios) - Math.min(...ratios) < .2);
});
test('the filename wins before a learned title and empty values fall back through title and ID', () => {
  assert.equal(artworkSeed('a.mod', 'Learned title', 'id'), 'a.mod');
  assert.equal(artworkSeed('  ', 'Title', 'id'), 'Title');
  assert.equal(artworkSeed('', '', 'id'), 'id');
});
const table = parseFormats(fs.readFileSync('web/src/formats.tsv', 'utf8'));
for (const [name, label] of [
  ['mod.elysium', 'MOD'], ['track.MoD', 'MOD'], ['mod.song.sid', 'SID'], ['.mod', 'MOD'],
  ['mod.', 'MOD'], ['dotless', ''], ['track.custom', 'CUSTOM'], ['song.mptm', 'MPTM'],
]) test(`Native format label ${JSON.stringify(name)}`, () => assert.equal(artworkLabel(name, table), label));

function drawing({ missing = false, refused = false } = {}) {
  const commands = [];
  let canvases = 0, font = '';
  const ctx = {
    set fillStyle(value) { commands.push(['colour', value]); },
    set textAlign(value) { commands.push(['align', value]); },
    set font(value) { font = value; commands.push(['font', value]); },
    fillRect(...args) { commands.push(['background', ...args]); },
    measureText(label) {
      return {
        width: label.length * parseFloat(font.slice(5)) / 2,
        actualBoundingBoxAscent: parseFloat(font.slice(5)) * .75,
        actualBoundingBoxDescent: 0,
      };
    },
    fillText(...args) { commands.push(['text', ...args]); },
  };
  const doc = {
    baseURI: 'https://example.test/Protracktor/src/',
    createElement(name) {
      assert.equal(name, 'canvas');
      canvases++;
      return {
        getContext() {
          if (refused) throw Error('refused');
          return missing ? null : ctx;
        },
        toDataURL(type) {
          assert.equal(type, 'image/png');
          return 'data:image/png;base64,host-adapter';
        },
      };
    },
  };
  return { artwork: new TrackArtwork(doc), commands, canvases: () => canvases };
}
test('local 512 PNG artwork uses the native colour warm label and centered measured format', () => {
  const f = drawing(), images = f.artwork.forTrack('a.mod', 'A', 'id', table);
  assert.equal(images[0].type, 'image/png');
  assert.equal(images[0].sizes, '512x512');
  assert(images[0].src.startsWith('data:image/png;'));
  assert.deepEqual(f.commands[0], ['colour', artworkBackground('a.mod')]);
  assert(f.commands.some(c => c[0] === 'colour' && c[1] === ARTWORK_FOREGROUND));
  assert.deepEqual(f.commands.find(c => c[0] === 'background'), ['background', 0, 0, 512, 512]);
  const drawn = f.commands.find(c => c[0] === 'text');
  assert.equal(drawn[1], 'MOD');
  assert.equal(drawn[2], 256);
});
test('learned title changes reuse exactly one cover and another filename replaces it', () => {
  const f = drawing(), first = f.artwork.forTrack('a.mod', 'A', 'id', table);
  assert.equal(f.artwork.forTrack('a.mod', 'Changed', 'id', table), first);
  assert.equal(f.canvases(), 1);
  f.artwork.forTrack('b.mod', 'B', 'id2', table);
  assert.equal(f.canvases(), 2);
});
test('dotless unknown format draws a question mark rather than an empty label', () => {
  const f = drawing();
  f.artwork.forTrack('dotless', 'A', 'id', table);
  assert.equal(f.commands.find(c => c[0] === 'text')[1], '?');
});
test('unavailable or refused canvas falls back to the packaged icon without fetching art', () => {
  for (const options of [{ missing: true }, { refused: true }]) {
    const f = drawing(options), image = f.artwork.forTrack('a.mod', 'A', 'id', table)[0];
    assert.equal(image.src, 'https://example.test/Protracktor/src/icons/icon-512.png');
    assert.equal(image.type, 'image/png');
  }
});
assert.equal(checks, 381);
console.log(`${checks} artwork cases passed`);
