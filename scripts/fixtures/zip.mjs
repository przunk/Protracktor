// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import { deflateRawSync } from 'node:zlib';
const crcOf = (data) => {
  let crc = 0xffffffff;
  for (const byte of data) {
    crc ^= byte;
    for (let bit = 0; bit < 8; bit++) crc = (crc >>> 1) ^ ((crc & 1) * 0xedb88320);
  }
  return (crc ^ 0xffffffff) >>> 0;
};
export function zipFixture(files, { method = 8, descriptor = false, comment = '' } = {}) {
  const locals = [], central = []; let offset = 0;
  for (const file of files) {
    const name = Buffer.from(file.name), data = Buffer.from(file.body ?? 'SAP'), packed = method === 8 ? deflateRawSync(data) : data;
    const crc = crcOf(data), flags = descriptor ? 8 : 0;
    const local = Buffer.alloc(30); local.writeUInt32LE(0x04034b50); local.writeUInt16LE(20, 4);
    local.writeUInt16LE(flags, 6); local.writeUInt16LE(method, 8); local.writeUInt16LE(name.length, 26);
    if (!descriptor) { local.writeUInt32LE(crc, 14); local.writeUInt32LE(packed.length, 18); local.writeUInt32LE(data.length, 22); }
    const tail = descriptor ? Buffer.alloc(16) : Buffer.alloc(0);
    if (descriptor) { tail.writeUInt32LE(0x08074b50); tail.writeUInt32LE(crc, 4); tail.writeUInt32LE(packed.length, 8); tail.writeUInt32LE(data.length, 12); }
    const entry = Buffer.alloc(46); entry.writeUInt32LE(0x02014b50); entry.writeUInt16LE(20, 4); entry.writeUInt16LE(20, 6);
    entry.writeUInt16LE(flags, 8); entry.writeUInt16LE(method, 10); entry.writeUInt32LE(crc, 16); entry.writeUInt32LE(packed.length, 20); entry.writeUInt32LE(data.length, 24); entry.writeUInt16LE(name.length, 28); entry.writeUInt32LE(offset, 42);
    locals.push(local, name, packed, tail); central.push(entry, name); offset += local.length + name.length + packed.length + tail.length;
  }
  const directory = Buffer.concat(central), end = Buffer.alloc(22), commentBytes = Buffer.from(comment);
  end.writeUInt32LE(0x06054b50); end.writeUInt16LE(files.length, 8); end.writeUInt16LE(files.length, 10);
  end.writeUInt32LE(directory.length, 12); end.writeUInt32LE(offset, 16); end.writeUInt16LE(commentBytes.length, 20);
  const bytes = Buffer.concat([...locals, directory, end, commentBytes]);
  return bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength);
}
