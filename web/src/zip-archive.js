// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
export const ZIP_MEMBER_LIMIT = 64 * 1024 * 1024;
export const ZIP_EXPANDED_LIMIT = 512 * 1024 * 1024;
const cancelled = (signal) => { if (signal?.aborted) throw new DOMException('Aborted', 'AbortError'); };
const invalid = (why) => new Error(`Invalid ASMA archive: ${why}`);
const crcTable = Uint32Array.from({ length: 256 }, (_, index) => {
  let value = index;
  for (let bit = 0; bit < 8; bit++) value = (value >>> 1) ^ (value & 1 ? 0xedb88320 : 0);
  return value >>> 0;
});
export function zipCrc32(bytes) {
  let crc = 0xffffffff;
  for (const value of bytes) crc = (crc >>> 8) ^ crcTable[(crc ^ value) & 255];
  return (crc ^ 0xffffffff) >>> 0;
}

/** Bounded single-disk stored/deflated ZIP; compressed extents exclude descriptors and directory. */
export function zipDirectory(bytes) {
  const view = new DataView(bytes), length = view.byteLength;
  const fits = (at, size) => Number.isSafeInteger(at) && at >= 0 && size >= 0 && at + size <= length;
  let end = -1;
  for (let at = length - 22; at >= Math.max(0, length - 65557); at--) {
    if (view.getUint32(at, true) === 0x06054b50 && at + 22 + view.getUint16(at + 20, true) === length) { end = at; break; }
  }
  if (end < 0) throw invalid('missing end record');
  const count = view.getUint16(end + 10, true), directory = view.getUint32(end + 16, true), size = view.getUint32(end + 12, true);
  if (view.getUint16(end + 4, true) || view.getUint16(end + 6, true)
      || count !== view.getUint16(end + 8, true)) throw invalid('split archives are unsupported');
  if (count === 65535 || directory === 0xffffffff || size === 0xffffffff) throw invalid('ZIP64 is unsupported');
  if (!fits(directory, size) || directory + size !== end) throw invalid('central directory bounds');
  const decode = new TextDecoder('utf-8', { fatal: true }), entries = [], names = new Set();
  let at = directory, expanded = 0;
  for (let index = 0; index < count; index++) {
    if (!fits(at, 46) || at + 46 > end || view.getUint32(at, true) !== 0x02014b50) throw invalid('central entry header');
    const flags = view.getUint16(at + 8, true), method = view.getUint16(at + 10, true);
    const packed = view.getUint32(at + 20, true), unpacked = view.getUint32(at + 24, true), crc = view.getUint32(at + 16, true);
    const nameSize = view.getUint16(at + 28, true), extraSize = view.getUint16(at + 30, true), commentSize = view.getUint16(at + 32, true);
    const local = view.getUint32(at + 42, true), next = at + 46 + nameSize + extraSize + commentSize;
    if (next > end || !nameSize) throw invalid('central entry name bounds');
    if (flags & 0x41 || ![0, 8].includes(method)) throw invalid('encrypted or unsupported compression');
    if (view.getUint16(at + 34, true) || [packed, unpacked, local].includes(0xffffffff)) throw invalid('split or ZIP64 entry');
    let name;
    try { name = decode.decode(new Uint8Array(bytes, at + 46, nameSize)); } catch { throw invalid('invalid UTF-8 name'); }
    if (name.includes('\\') || name.includes('\0') || name.startsWith('/') || name.replace(/\/$/, '').split('/').some((part) => !part || part === '.' || part === '..')) throw invalid('ambiguous entry path');
    if (names.has(name)) throw invalid('duplicate entry path'); names.add(name);
    if (!fits(local, 30) || local + 30 > directory || view.getUint32(local, true) !== 0x04034b50) throw invalid('local header bounds');
    const localFlags = view.getUint16(local + 6, true), localMethod = view.getUint16(local + 8, true);
    const localName = view.getUint16(local + 26, true), localExtra = view.getUint16(local + 28, true);
    const start = local + 30 + localName + localExtra, finish = start + packed;
    if (localFlags !== flags || localMethod !== method || localName !== nameSize || finish > directory) throw invalid('local entry disagrees with directory');
    const localNameBytes = new Uint8Array(bytes, local + 30, localName), centralNameBytes = new Uint8Array(bytes, at + 46, nameSize);
    if (localNameBytes.some((value, index) => value !== centralNameBytes[index])) throw invalid('local name disagrees with directory');
    if (!(flags & 8) && (view.getUint32(local + 14, true) !== crc || view.getUint32(local + 18, true) !== packed || view.getUint32(local + 22, true) !== unpacked)) throw invalid('local sizes or checksum disagree');
    // ASMA is currently 52 MB expanded. Bound resource use before handing a member to the inflater.
    expanded += unpacked;
    if (unpacked > ZIP_MEMBER_LIMIT || expanded > ZIP_EXPANDED_LIMIT) throw invalid('expanded archive exceeds browser limit');
    entries.push(Object.freeze({ name, local, start, end: finish, size: unpacked, method, crc })); at = next;
  }
  if (at !== end) throw invalid('central directory entry count');
  entries.sort((left, right) => left.local - right.local);
  for (let index = 1; index < entries.length; index++) if (entries[index].local < entries[index - 1].end) throw invalid('overlapping members');
  return Object.freeze(entries.filter((entry) => !entry.name.endsWith('/')));
}

/** One fresh verified member; never feed ZIP trailer bytes to a raw inflater. */
export async function readZipMember(blob, entry, { signal } = {}) {
  cancelled(signal);
  if (!Number.isSafeInteger(entry.start) || !Number.isSafeInteger(entry.end) || entry.start < 0
      || entry.end < entry.start || entry.end > blob.size || !Number.isSafeInteger(entry.size)
      || entry.size < 0 || entry.size > ZIP_MEMBER_LIMIT || ![0, 8].includes(entry.method)) throw invalid('stored member bounds');
  const packed = await blob.slice(entry.start, entry.end).arrayBuffer(); cancelled(signal);
  let output;
  if (entry.method === 0) output = new Uint8Array(packed);
  else {
    let inflater;
    try { inflater = new DecompressionStream('deflate-raw'); }
    catch { throw new Error('This browser cannot decompress an offline ASMA archive (raw DEFLATE is unavailable).'); }
    const reader = new Response(packed).body.pipeThrough(inflater).getReader(), chunks = [];
    let size = 0, complete = false;
    const abort = () => { void reader.cancel().catch(() => {}); };
    signal?.addEventListener('abort', abort, { once: true });
    try {
      cancelled(signal);
      for (;;) {
        const { value, done } = await reader.read(); cancelled(signal);
        if (done) { complete = true; break; }
        size += value.byteLength;
        if (size > entry.size) throw invalid('member expanded beyond its declared size');
        chunks.push(value);
      }
      output = new Uint8Array(size); let at = 0;
      for (const chunk of chunks) { output.set(chunk, at); at += chunk.byteLength; }
    } finally {
      signal?.removeEventListener('abort', abort);
      if (!complete) void reader.cancel().catch(() => {});
      reader.releaseLock();
    }
  }
  cancelled(signal);
  if (output.byteLength !== entry.size || zipCrc32(output) !== entry.crc) throw invalid('member size or checksum mismatch');
  return output.buffer;
}

export async function validateZip(blob, entries, { signal, onProgress } = {}) {
  for (let index = 0; index < entries.length; index++) {
    await readZipMember(blob, entries[index], { signal });
    if (index % 64 === 0 || index === entries.length - 1) {
      onProgress?.({ stage: 'verifying', done: index + 1, total: entries.length });
      await new Promise((resolve) => setTimeout(resolve, 0)); cancelled(signal);
    }
  }
}
