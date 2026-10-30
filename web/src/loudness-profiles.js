// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import { zipCrc32 } from './zip-archive.js';

export const LOUDNESS_PROFILE_LIMIT = 512;
export const loudnessKeyValid = (value) => typeof value === 'string' && /^[a-f0-9]{64}$/.test(value);

/** Native LoudnessProfile.usable: cache facts describe input, never an output gain. */
export function loudnessProfile(value) {
  if (!value || !Number.isFinite(value.seconds) || value.seconds < .4 || !Number.isFinite(value.lufs)
    || value.lufs < -70 || value.lufs > 30 || !Number.isFinite(value.peak) || value.peak <= 0) return null;
  return Object.freeze({ seconds: value.seconds, lufs: value.lufs, peak: value.peak, complete: value.complete === true });
}
export function nativeLoudnessProfile(values) {
  if (!values || values.length < 4) return null;
  return loudnessProfile({ seconds: values[0], lufs: values[1], peak: values[2], complete: values[3] === 1 });
}
export function measuredSubsong(value) {
  return Number.isInteger(value) && value >= 0 && value <= 65535 ? value : null;
}
export function improvesLoudness(previous, next) {
  const candidate = loudnessProfile(next), old = loudnessProfile(previous);
  return !!candidate && (!old || candidate.complete && !old.complete
    || candidate.complete === old.complete && candidate.seconds > old.seconds);
}

/** Native's 29-byte big-endian payload and CRC32 stored in a 64-bit container. */
export function encodeLoudness(value) {
  const profile = loudnessProfile(value); if (!profile) return null;
  const bytes = new Uint8Array(37), view = new DataView(bytes.buffer);
  view.setInt32(0, 1); view.setFloat64(4, profile.seconds); view.setFloat64(12, profile.lufs);
  view.setFloat64(20, profile.peak); bytes[28] = profile.complete ? 1 : 0;
  view.setUint32(33, zipCrc32(bytes.subarray(0, 29)));
  return bytes;
}
export function decodeLoudness(bytes) {
  if (Object.prototype.toString.call(bytes) !== '[object Uint8Array]' || bytes.length !== 37) return null;
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  if (view.getInt32(0) !== 1 || view.getUint32(29) !== 0 || bytes[28] > 1
    || view.getUint32(33) !== zipCrc32(bytes.subarray(0, 29))) return null;
  return loudnessProfile({ seconds: view.getFloat64(4), lufs: view.getFloat64(12), peak: view.getFloat64(20), complete: bytes[28] === 1 });
}
const hex = (bytes) => [...bytes].map((byte) => byte.toString(16).padStart(2, '0')).join('');
export function loudnessRecord(key, profile) {
  const bytes = encodeLoudness(profile);
  return loudnessKeyValid(key) && bytes ? Object.freeze({ key, payload: hex(bytes) }) : null;
}
export function recordProfile(record) {
  if (!loudnessKeyValid(record?.key) || typeof record.payload !== 'string' || !/^[a-f0-9]{74}$/.test(record.payload)) return null;
  return decodeLoudness(Uint8Array.from(record.payload.match(/../g), (byte) => parseInt(byte, 16)));
}

/** Oldest successful write first; inferior observations neither replace nor promote an entry. */
export function mergedLoudnessRecords(previous, key, profile) {
  const next = loudnessRecord(key, profile); if (!next) return null;
  const records = new Map();
  for (const row of Array.isArray(previous) ? previous : []) {
    const value = recordProfile(row); if (!value) continue;
    if (!records.has(row.key) || improvesLoudness(recordProfile(records.get(row.key)), value)) {
      records.delete(row.key); records.set(row.key, Object.freeze({ key: row.key, payload: row.payload }));
    }
  }
  if (!improvesLoudness(recordProfile(records.get(key)), profile)) return null;
  records.delete(key); records.set(key, next);
  return Object.freeze([...records.values()].slice(-LOUDNESS_PROFILE_LIMIT));
}

const bytesOf = (value) => {
  if (Object.prototype.toString.call(value) === '[object ArrayBuffer]') return new Uint8Array(value);
  if (ArrayBuffer.isView(value)) return new Uint8Array(value.buffer, value.byteOffset, value.byteLength);
  throw new TypeError('Loudness identity requires source bytes');
};
async function hash(bytes, crypto) {
  if (!crypto?.subtle) throw new Error('Content hashing is unavailable');
  return hex(new Uint8Array(await crypto.subtle.digest('SHA-256', bytes)));
}
/** Native length-prefixed UTF-8 scheme; actual WEB callers supply no decoder companions. */
export async function loudnessContentKey(bytes, name, companions, revision, crypto = globalThis.crypto) {
  if (typeof name !== 'string' || typeof revision !== 'string') throw new TypeError('Invalid loudness content identity');
  const encoder = new TextEncoder();
  const parts = [encoder.encode(`loudness-v1:${revision}`), encoder.encode(name), bytesOf(bytes)];
  for (const [name, data] of [...companions].sort(([a], [b]) => a < b ? -1 : a > b ? 1 : 0)) parts.push(encoder.encode(name), bytesOf(data));
  const size = parts.reduce((sum, part) => sum + part.length + 4, 0);
  const joined = new Uint8Array(size), view = new DataView(joined.buffer); let at = 0;
  for (const part of parts) {
    if (part.length > 0xffffffff) throw new RangeError('Loudness source part is too large');
    view.setUint32(at, part.length); at += 4; joined.set(part, at); at += part.length;
  }
  return hash(joined, crypto);
}
export async function loudnessVariantKey(base, subsong, width, crypto = globalThis.crypto) {
  if (!loudnessKeyValid(base) || !(measuredSubsong(subsong) !== null || subsong === -1) || ![0, 50, 100].includes(width))
    throw new TypeError('Invalid loudness variant identity');
  return hash(new TextEncoder().encode(`${base}:${subsong}:${width}`), crypto);
}
