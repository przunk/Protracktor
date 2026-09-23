# Running the web player on another machine

*Written 2026-09-08, when it went on a Raspberry Pi.*

The page and the pairing service are one small node program. It needs **node and nothing else** —
no build tools, no Emscripten, no Android SDK. The engine is compiled to WebAssembly on a
development machine and copied; that is what WebAssembly is for, and it is why a Pi can serve a
player built from seven C and C++ decoders.

## What to copy

```
scripts/build-web-engine.sh     # on the development machine, once
scripts/package-web.sh          # makes dist/protracktor-web-*.tar.gz
```

The archive holds `web/` (the page, the vendored QR encoder, the built `engine.wasm`, and beside it
the licence texts and the privacy policy the page's Settings show), `scripts/serve-web.mjs`, this
file, and the licence. About 3 MB.

On the other machine:

```
tar xzf protracktor-web-*.tar.gz
cd protracktor-web
node scripts/serve-web.mjs
```

It prints two addresses and writes `server.json` beside itself on the first run.

## The public copy — GitHub Pages

*Decided 2026-09-22: the web player is an add-on to the app, which anyone can run themselves with
this archive; one public copy lives at **https://przunk.github.io/Protracktor/**, free and without a
domain.*

```
./scripts/build-web-engine.sh      # when the engine's code has changed
./scripts/publish-web-pages.sh     # commits the site to the gh-pages branch
git push origin gh-pages
```

The first time only, on GitHub: **Settings → Pages → Build and deployment → Source: Deploy from a
branch → Branch: gh-pages, / (root) → Save**.

**The path keeps the repository's capital `P`**: GitHub Pages serves `/Protracktor/`, and
`/protracktor/` is a 404 (checked 2026-09-22).

**Everything works there except pairing**, which needs a running process -- this server -- and
GitHub Pages serves files only. The pairing sheet says so. The consequence is a good one: nothing a
phone sends ever reaches the public copy, so it changes nothing in the privacy policy or in Play's
Data safety answers beyond naming GitHub as its host.

The script refuses uncommitted changes and an engine older than its code, because the page's
**Source code** link names the commit it was built from.

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

## Where this should go, and why the address is the problem — surveyed 2026-09-15

*a different random address every time**. Recorded as a direction, not a decision; nothing here is
built.*

**The thing being tunnelled is two things with opposite requirements**, and every option below is
really a question about which half goes where.

| | needs | today |
| --- | --- | --- |
| the page, the engine, the catalogues | **nothing but static hosting over TLS** | node, on the Pi, behind the tunnel |
| pairing — `/pair/*` | a process with memory, reachable from the phone | the same node |

The first row is the surprise and it is worth being precise about, because it decides everything:

- The catalogue is fetched **by the browser, straight from Modland and ASMA**. Their CORS is the
  reason `catalogue.js` is shaped the way it is; nothing is proxied through us.
- A `#play:` share link is a **URL fragment**. It never reaches a server.
- The engine needs **no cross-origin isolation** — no `SharedArrayBuffer`, which `processor.js` says
  in its own comment and is the reason an `AudioWorklet` is enough. So no COOP/COEP headers, and
  therefore no host is disqualified for being unable to set them.

So the page could sit on any static host today, at a permanent address, and only pairing would still
need something running. Pairing is not optional and cannot be replaced by a link: it is the only
route by which **files out of the phone's own storage** reach the page.

### The options, and what each costs

**1. Cloudflare Pages, with the pairing routes as Functions.** The page gets a permanent `pages.dev`
address and Functions sit on the **same origin** — which removes more than it looks. `/pair/host`
exists only because the page cannot work out where the phone should post: it knows its own origin,
and that is usually `localhost`, which from a phone means the phone. Same origin makes the answer
"me", and the route and the whole `advertisedBase()` question go away with it.

**The catch to settle before choosing this**: `rooms` is a `Map` in the process. Workers are
stateless, so long polling with held requests wants **Durable Objects**, and whether those are
within the free plan needs checking against Cloudflare's current pricing rather than assumed. If
they are not, the fallbacks are short polling through KV (worse, but the protocol already tolerates
it — `seq` exists exactly so a poller cannot miss a message) or leaving the relay on the Pi.

**2. GitHub Pages.** Fine for the page — static and TLS — and disqualified for the rest: it is
static *only*, so pairing needs a second host and a second address, which is the problem this
started as. It would also mean the web build living in a public repository, and the repository is
still private.

**3. A named Cloudflare tunnel instead of a quick one.** The smallest possible change: a fixed
hostname, the Pi still serving both halves, the script otherwise untouched. Needs a domain on
Cloudflare. **This is the one to reach for if the goal is to stop the address moving and nothing
else** — an hour, and no code changes at all.

**4. The page on Pages, the relay left on the Pi** at a fixed subdomain. Costs cross-origin between
the two, which is already handled — `serve-web.mjs` sends `access-control-allow-origin: *` on every
pairing response.

**5. Resolving the moving address through a REST endpoint** (a suggestion worth recording: the app asks
a service on a personal DNS, NAT-forwarded to the Pi, where the tunnel currently is). It works, and it is
the option with the most moving parts: the random tunnel stays and an always-on resolver is added in
front of it. **And it answers itself** — a resolver has to live at a stable address, and anything
with a stable address could serve the page directly, which is the thing the resolver was for.

### What a permanent address unlocks

Not only convenience. **`docs/BACKLOG.md` A40** — Protracktor links opening in the app — is parked
*specifically* on not having a stable address, and a share link that outlives the tunnel it was
created in is the whole point of that feature. `docs/PLAN_HANDOFF.md` §7 asks "who hosts the relay,
and is it worth having anything to run at all"; the table above is the shape of that answer.

**The QR pairing stays regardless of where anything is hosted.** A stable address makes links work;
it does not give a page access to the phone's storage.
