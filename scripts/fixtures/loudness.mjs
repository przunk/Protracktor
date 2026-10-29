// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

/** Legal generated NSF: track zero is silent; track one holds a NES pulse wave. */
export function nonzeroAudibleNsf() {
  const header = Buffer.alloc(0x80);
  header.write('NESM\x1a', 0, 'latin1'); header[5] = 1; header[6] = 2; header[7] = 1;
  header.writeUInt16LE(0x8000, 8); header.writeUInt16LE(0x8000, 10); header.writeUInt16LE(0x8020, 12);
  header.write('nonzero audible track', 0x0e); header.writeUInt16LE(16666, 0x6e);
  const body = Buffer.alloc(0x40, 0xea);
  Buffer.from([0xc9, 0, 0xf0, 20, // CMP #0 / BEQ RTS; the init argument is the zero-based track.
    0xa9, 1, 0x8d, 0x15, 0x40, 0xa9, 0xbf, 0x8d, 0, 0x40,
    0xa9, 0xfd, 0x8d, 2, 0x40, 0xa9, 0, 0x8d, 3, 0x40, 0x60]).copy(body);
  body[0x20] = 0x60;
  return Buffer.concat([header, body]);
}
