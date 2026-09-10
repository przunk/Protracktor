# Running the web player on another machine

*Written 2026-09-08, when the owner asked to put it on a Raspberry Pi.*

The page and the pairing service are one small node program. It needs **node and nothing else** —
no build tools, no Emscripten, no Android SDK. The engine is compiled to WebAssembly on a
development machine and copied; that is what WebAssembly is for, and it is why a Pi can serve a
player built from seven C and C++ decoders.

## What to copy

```
scripts/build-web-engine.sh     # on the development machine, once
scripts/package-web.sh          # makes dist/protracktor-web-*.tar.gz
```

The archive holds `web/` (the page, the vendored QR encoder, and the built `engine.wasm`),
`scripts/serve-web.mjs`, this file, and the licence. About 3 MB.

On the other machine:

```
tar xzf protracktor-web-*.tar.gz
cd protracktor-web
node scripts/serve-web.mjs
```

It prints two addresses and writes `server.json` beside itself on the first run.

## server.json

```json
{
  "port": 8173,
  "host": null,
  "bind": "0.0.0.0"
}
```

| | |
| --- | --- |
| `port` | what to listen on. A command-line argument or `PROTRACKTOR_PORT` still wins, which is how you try a second one without editing anything |
| `host` | **the address the QR code carries** — the one the *phone* must be able to reach. `null` works it out from the network interfaces, which is right on a Pi and wrong inside WSL |
| `bind` | which interface to listen on. `0.0.0.0` is every one; `127.0.0.1` makes it local-only |

It is deliberately **outside `web/`**: everything under that directory is served to whoever asks,
and a configuration file is not a page. It is gitignored, because a port and a LAN address are facts
about one machine.

## A Raspberry Pi

Nothing special, and simpler than a laptop running WSL:

```
sudo apt install nodejs          # 20 or newer
tar xzf protracktor-web-*.tar.gz
cd protracktor-web
node scripts/serve-web.mjs
```

The Pi has an ordinary LAN address, so `host` can stay `null` and the QR code will carry the right
thing without being told. **This is the case WSL is not:** there, the interface belongs to a private
network the phone cannot see, so the address has to be the Windows one and Windows has to forward
the port. The server detects that and prints the commands.

To keep it running, a systemd unit:

```ini
[Unit]
Description=Protracktor web player
After=network-online.target

[Service]
WorkingDirectory=/home/pi/protracktor-web
ExecStart=/usr/bin/node scripts/serve-web.mjs
Restart=on-failure
User=pi

[Install]
WantedBy=multi-user.target
```

`sudo systemctl enable --now protracktor-web`.

## What the phone needs

**Nothing configured.** The pairing code carries the address, so a phone that scans a code on a Pi
sends to the Pi. The *Web player address* in Settings is only for the link — the fallback on a long
press when there is no pairing — and it is the one place a machine's address is typed by hand.

## Two things this is not

**It is not a public server.** Plain HTTP, no authentication beyond the 128-bit room id in the code,
and the app refuses to send to a cleartext address outside the private ranges. Putting it on the
open internet needs TLS first, and then the app's cleartext exception can be removed rather than
widened.

**It does not hold anything.** A room is a set of listeners and the last message, in memory, gone
when the process stops. There is no database, no account and no log of what was played.
