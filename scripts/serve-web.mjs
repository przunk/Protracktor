// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later
import http from 'http';
import fs from 'fs';
import path from 'path';

const port = Number(process.argv[2] ?? 8173);
const root = path.resolve('web');
const types = {
  '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8', '.wasm': 'application/wasm',
  '.css': 'text/css; charset=utf-8', '.json': 'application/json',
};

http.createServer((request, response) => {
  const url = new URL(request.url, `http://localhost:${port}`);
  // The page lives at /src/ because the engine it loads lives at /vendor/, and both are under
  // web/. Somebody typing the bare address gets sent there rather than a 404 that says nothing --
  // which is exactly what happened the first time this was opened.
  if (url.pathname === '/') {
    response.writeHead(302, { location: '/src/' }).end();
    return;
  }
  let file = path.join(root, decodeURIComponent(url.pathname));
  if (!file.startsWith(root)) { response.writeHead(403).end(); return; }
  if (fs.existsSync(file) && fs.statSync(file).isDirectory()) file = path.join(file, 'index.html');
  fs.readFile(file, (error, data) => {
    if (error) {
      response.writeHead(404, { 'content-type': 'text/plain; charset=utf-8' })
        .end(`not here: ${url.pathname}\n\nthe player is at http://localhost:${port}/src/\n`);
      return;
    }
    response.writeHead(200, {
      'content-type': types[path.extname(file)] ?? 'application/octet-stream',
      'cache-control': 'no-store',
    });
    response.end(data);
  });
}).listen(port);
