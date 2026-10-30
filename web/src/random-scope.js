// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

import { PLATFORM_DATA } from './platforms.js';

export const PLATFORMS = PLATFORM_DATA.platforms;
const platformIdentity = JSON.stringify(PLATFORM_DATA);
let platformHash = 0;
for (const character of platformIdentity) platformHash = (Math.imul(platformHash, 31) + character.charCodeAt(0)) | 0;
export const PLATFORM_FINGERPRINT = `platforms/v1:${(platformHash >>> 0).toString(16).padStart(8, '0')}`;
const byId = new Map(PLATFORMS.map((platform) => [platform.id, platform]));
const byFormat = new Map(PLATFORMS.flatMap((platform) => platform.catalogueFormats.map((format) => [format, platform])));
const byName = new Map(PLATFORMS.flatMap((platform) => platform.names.map((name) => [name, platform])));
export const platformById = (id) => byId.get(id) ?? null;
export const platformForFormat = (format) => byFormat.get(String(format).trim().toLowerCase()) ?? null;
export function platformForFileName(fileName) {
  const name = String(fileName).toLowerCase();
  if (!name.includes('.')) return null;
  return byName.get(name.slice(name.lastIndexOf('.') + 1)) ?? byName.get(name.slice(0, name.indexOf('.'))) ?? null;
}
export function platformForCatalogueRow(source, format, fileName) {
  return platformById(PLATFORM_DATA.wholeCatalogues[source]) ?? platformForFormat(format) ?? platformForFileName(fileName);
}

/** The bucket keeps all formats; only its playable entries contribute to available filters. */
export function countPlatformBucket(counts, source, bucket) {
  const prefix = `${source}:tracks:`;
  if (!bucket.key.startsWith(prefix)) return;
  const format = bucket.key.slice(prefix.length).split('/')[0];
  const fixed = platformForCatalogueRow(source, format, '');
  for (const entry of bucket.tracks ?? []) {
    if (entry.p === 0) continue;
    const platform = fixed ?? platformForFileName(entry.t);
    if (platform) counts[platform.id] = (counts[platform.id] ?? 0) + 1;
  }
}

export function storedRandomScope(value) {
  return typeof value === 'string' && (value === 'favourites' || value.startsWith('platform:') && value.length > 9)
    ? value : 'everything';
}

/** Only a full native favourites row carries a path; shorter rows describe subsongs. */
export function parseModlandFavourites(text) {
  return String(text).split(/\r\n?|\n/).flatMap((line) => {
    const columns = line.split('\t');
    return columns.length === 11 && columns[10].trim() ? [columns[10]] : [];
  });
}

/** Native rememberRandomFilterState ownership, independent of a disposable sheet or Browse visit. */
export class RandomFilterState {
  #availability = null;
  #accepted = null;
  #loading = false;
  #failed = false;
  #blocked = false;
  #generation = 0;
  #inputs = null;
  #read = null;
  get state() {
    return Object.freeze({ availability: this.#blocked ? null : this.#availability,
      loading: this.#loading || this.#blocked, failed: this.#failed && !this.#blocked });
  }
  async refresh({ active, blocked = false, key, force = false }, load, publish = () => {}) {
    const inputs = JSON.stringify([!!active, !!blocked, key]);
    if (inputs === this.#inputs && !force) return;
    this.#inputs = inputs;
    const generation = ++this.#generation;
    this.#read?.abort();
    this.#read = new AbortController();
    this.#failed = false;
    this.#blocked = blocked;
    if (blocked || !active && key !== this.#accepted) {
      this.#availability = this.#accepted = null;
    }
    this.#loading = !!active;
    publish(this.state);
    if (!active || blocked) return;
    try {
      const result = await load(this.#read.signal);
      if (generation !== this.#generation) return;
      this.#availability = Object.freeze({ ...result, platformCounts: Object.freeze({ ...result.platformCounts }) });
      this.#accepted = key;
    } catch {
      if (generation !== this.#generation) return;
      this.#failed = true;
    }
    if (generation !== this.#generation) return;
    this.#loading = false;
    publish(this.state);
  }
}
