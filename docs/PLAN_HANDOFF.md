# Plan: sending music from the phone to a browser

Written 2026-09-08, taking over from `docs/PLAN_WEB.md` at the owner's request.

> *"moje marzenie: odpalić w pracy w przeglądarce naszego playera i mieć możliwość wysyłania muzyki
> z telefonu do kompa. Najlepiej jakiś płytki pairing bez kont."*

**The answer is yes, and the shallow version is shallower than the wish assumes** — because the
thing that has to travel is not music. Two of the three steps below need no server at all.

Labels follow `PLAN_WEB.md` §0: **measured** on this machine on 2026-09-08, **read** from somebody
else's work, **reasoned** from what we already decided.

---

## 1. The reframe: what crosses is a name, not a sound

The instinct is to stream audio from the phone to the browser — cast it. That is the expensive
design, and it is unnecessary for almost everything in this app.

A catalogue track's identity **is** its URL (`ARCHITECTURE` §11), and `PLAN_WEB.md` §6 measured that
a browser may fetch those URLs directly: `modland.com`, `asma.atari.org` and `hvsc.c64.org` all
answer `Access-Control-Allow-Origin: *`, and Modland answers a preflight with `Range` allowed and
`Content-Range` exposed. So the far end can fetch the bytes itself. What has to cross the gap is the
*pointer*.

*Measured*, from Modland's own index (516,107 files) and from packing real paths:

| what crosses | 10 tracks | 50 tracks | 200 tracks |
| --- | --- | --- | --- |
| **as identities**, compressed into a URL fragment | 464 chars | 1,992 chars | 6,872 chars |
| **as audio**, at Modland's median file (20 KB) | 200 KB | 1.0 MB | 4.0 MB |
| **as audio**, at the 90th percentile (469 KB) | 4.6 MB | 22.9 MB | 91 MB |

A playlist is **three kilobytes of text**. That is the whole finding, and every design below follows
from it.

**And when bytes must travel, they are still small.** Modland's median module is **20 KB**, mean 188
KB, p90 469 KB. This is chiptune music: the reason a byte transfer is affordable at all is the same
reason this app exists.

---

## 2. Two cases where bytes genuinely have to cross

1. **A local file on the phone.** Its identity is a storage-access grant to one app on one device
   (`PLAN_WEB.md` §8) and means nothing anywhere else. If it is to play at work, it travels.
2. **A catalogue the work network blocks.** A corporate proxy that blocks `modland.com` breaks the
   pointer design completely — and the phone, on mobile data, is not blocked. It has the file, or
   can get it.

Both are the same mechanism: **an optional byte transfer, used when the far end cannot resolve the
pointer itself.** Design for pointers, fall back to bytes.

**This inverts one of `PLAN_WEB.md` §8's conclusions, in our favour.** Account sync *cannot* carry a
playlist that mixes local files with catalogue tracks: the local rows have no portable identity, so
the synced copy silently loses them. A live pairing has the phone **present at the moment of
transfer**, so it can simply send those rows as bytes. The accountless design is the more capable
one here, not the compromise.

---

## 3. Three shapes, in the order they should be built

### H1 — a link, and no pairing at all

The phone packs the queue into a URL fragment and hands it to whatever channel the owner already
uses to talk to himself: a message, an email, a note. The browser opens it and plays.

- **No server, no pairing, no account, nothing to keep running.**
- A `#fragment` is **never sent to the server** by the browser, so even the page's own host learns
  nothing about what is in the list.
- Fits: 50 tracks in 1,992 characters, inside the 2,000-character limit that is safe everywhere.
  200 tracks needs 6,872 and works in current desktop browsers but is past the conservative bound.
- Costs one deliberate copy-paste per handoff, and cannot push to a tab that is already open.

**This is worth building first even if H2 is the goal**, because it answers "can I hear my playlist
at work" with no infrastructure whatsoever, and because it is the fallback when a corporate network
blocks whatever H2 connects to.

### H2 — an ephemeral room, opened by a QR code *(the recommendation)*

The phone is on mobile data, the browser is on a corporate LAN. They cannot discover each other; no
mDNS, no local sockets, no shared network. Something in the middle has to introduce them, and the
only real question is **how much it holds and for how long**.

1. The browser opens a socket to a small relay and is given a room with a random 128-bit id.
2. It draws that id **as a QR code on screen**. The phone has a camera; the browser has a screen.
   That is the shallowest pairing that exists, and it is the shape people already know from
   WhatsApp Web — nothing is typed, nothing is remembered, nothing is registered.
3. From then on the phone posts small JSON frames into the room: *play this*, *here is my queue*,
   *here are the bytes for the one you cannot fetch*.

**What the relay holds: nothing.** It forwards frames between two sockets and forgets the room when
the tab closes. No account, no database, no profile, no retention — so `docs/PRIVACY.md` stays true
as written, which is a claim this project should be reluctant to give up.

**Security is easier than it looks, because there is no account to steal.** The room id is 128 bits
of randomness carried in the QR, not a six-digit code, so there is nothing to guess and no rate
limiting to get wrong. A short typable code is only needed when the camera is unavailable, and *that*
one needs a short expiry and a guess limit — worth avoiding by simply not offering it at first.

**The honest limit: this works while the phone is awake.** Android will doze a background socket, so
the phone must be the one that *acts* — "send to my browser" is a deliberate press, not background
sync. Making the browser able to pull from a sleeping phone means a push service, which means
registration and identifiers, which is the account this design exists to avoid. The wish as written
is an act ("wysyłać z telefonu"), so this limit costs nothing today; it should be recorded before
somebody tries to make it symmetric.

### H3 — WebRTC, and why not yet

Same signalling as H2, then a peer-to-peer data channel so bytes never touch our relay.

**The measurement argues against it.** Bytes cross rarely (only the two cases in §2) and a module is
20 KB at the median. WebRTC costs a STUN server, and on a corporate network almost certainly a TURN
server — which is a relay again, with more moving parts and more to operate.

**What would change this:** if byte transfer stopped being the exception — a user whose library is
mostly local files — the relay's bandwidth becomes the running cost, and H3 becomes the way to make
it somebody else's. That is a number to watch, not a thing to build now.

---

## 4. Four things that will look like bugs

*Reasoned*, and each has cost somebody a day somewhere.

1. **Autoplay.** A browser will not start audio without a user gesture. The *first* tune pushed to a
   freshly opened tab cannot play by itself: the page has to show "tap to start" and then honour
   every push after that. Left unhandled this looks exactly like a handoff that silently failed.
2. **The work network may block `modland.com`.** Then the pointer design breaks and the fallback in
   §2 is the whole feature. Worth detecting deliberately — one HEAD at startup — rather than
   discovering it as "the player does not work at the office".
3. **The work network may block the relay.** H1 survives this; H2 does not. Another argument for
   building H1 first.
4. **Cross-origin isolation** (`PLAN_WEB.md` §5) is a precondition for the *player*, not for the
   handoff, but it is the same hosting: if `COOP`/`COEP` cannot be set, none of this matters.

---

## 5. What this does to B5 and to W3

`WISHLIST.md` **B5** asks for favourites and history synced through an account. The account exists to
hold state that **the phone already holds**.

**So: the phone is the account.** Pairing replaces login, the phone answers when asked, and the
browser is a viewer that can also fetch its own bytes. That is not a lesser version of W3 — it
removes the server-side data, the privacy policy change, the password reset, the export request and
the breach, and it is roughly a tenth of the work.

What it does *not* give: state that arrives at the browser when the phone is off, and a second
device that stays in step without being asked. If either of those is what the owner actually wants,
that is W3 and it should be chosen deliberately (`PLAN_WEB.md` §11.5), not arrived at.

---

## 5a. Three answers the owner asked for, 2026-09-08

### Pairing: a typed code first, the QR later and for free

The obvious design is a QR the phone's camera reads (§3, H2). It is right eventually and wrong to
start with, for a reason that is about Android rather than about taste:

- A scanner in our app means the **`CAMERA` permission** — a visible escalation in the store listing
  and in the user's head, for a music player, plus a scanning UI to build and debug.
- We would not need it. The app now declares `VIEW` intent filters, so **the phone's own camera app
  can scan the code and hand the link to us** — no permission, no scanner, no library. But on
  Android 12 and later an `https://` link only opens the app if the domain is verified with
  **App Links** (`assetlinks.json`), and during localhost and tunnel testing there is no stable
  domain to verify. The clean version of the QR is unavailable exactly while it would be developed.

So: **the browser shows an eight-digit code and the phone takes it.** No dependency, no permission,
one typing per session. When there is a stable domain the QR becomes an addition rather than a
rewrite, because what it carries is the same room id.

**Trust:** the room id is the secret, 128 bits, and the **browser shows it while the phone consumes
it** — the thing being controlled offers, the controller takes. The channel is **one-directional by
design**: the phone sends, the browser receives. Someone who steals a code can make a noise in a tab
and learns nothing about the phone, because the phone exposes nothing. "Bring it back to the phone"
is a later and separate decision, not a side effect of the transport.

### The browser is not a second front end

`ui/` is **5,672 lines across 23 files**, and nearly all of it is browsing, search, settings,
storage, playlists-as-files, folder grants, the notification and `MediaSession`. **The browser does
none of that.** What it needs is a title, transport, a position bar, a queue and perhaps the subsong
strip — a few percent of it.

**So the phone stays the browser and the search; the page at work is a speaker with a screen.** Then
there is no second front end to keep in step: the page is not a duplicate of anything, and the drift
risk `AGENTS.md` §8 names applies to *domain logic*, which the page barely has.

**Compose Multiplatform would give literally the same screens**, and is the wrong trade here: it
renders to a canvas (text that is not text, weaker accessibility, scrolling that is not the
browser's), it adds a runtime and Skia *on top of* the decoders, its web target is Beta, and it must
talk to Emscripten across JavaScript glue **at the audio boundary** — the one place §5 says must not
have glue in it. It buys "the same look" for screens we do not want there. If a full catalogue
browser in the browser is ever wanted, the question becomes real and the first step is measuring what
that runtime weighs.

### Where it lives: `web/` in this repository

**The decisive argument is that this is a second build target, not a second product.**
`native/engine/engine.cpp` is shared, and under it are seven vendored decoders pinned by
`.protracktor-version` files and fetched by our scripts. A separate repository would duplicate those
pins, and a version drifting between two repositories is the same failure as logic drifting, in data
where it is harder to see. One repository also makes GPL-3's written offer of source trivial: one
licence, one place to point.

*Measured*, because it was a real risk: `npm install` **works on this 9p mount** — the install
completed, `node_modules/.bin` symlinks resolved, the installed binary ran. Gradle's output cannot
live here (`PROTRACKTOR_BUILD_DIR_ROOT` exists for that reason), so this was worth checking rather
than assuming.

*Measured*, on the seam the port needs: `engine.cpp` is **1,868 lines**, and the Android-bound part
is **one class and one block** — `class Player : public oboe::AudioStreamDataCallback` at 1497, and
the `extern "C"` JNI functions from 1737. About 370 lines. Everything above is portable C++, and
`onAudioReady` has the same shape as an `AudioWorkletProcessor`'s `process()`: here is a buffer,
fill it, say whether to continue.

```
native/engine/engine.cpp        shared: backends, registry, dispatch
native/engine/player_oboe.cpp   extracted: the Oboe Player and the JNI block
native/engine/player_wasm.cpp   new: the worklet-side equivalent and its bindings
web/package.json
web/src/                        the page: title, transport, position, queue
web/vendor/                     generated engine.js + .wasm (gitignored)
web/relay/                      later: the pairing relay
scripts/fetch-emsdk.sh
scripts/build-web-engine.sh
scripts/probe-web.mjs           the host probe, in node
```

`web/relay/` is the one piece that **never links the decoders**, so it is the one piece GPL-3 does
not reach (`PLAN_WEB.md` §9). Its licence stays a decision rather than becoming a consequence.

**Sequencing, and this one is not negotiable.** Splitting `Player` and the JNI block out of
`engine.cpp` is a refactor of the heart of playback on the phone. It happens **on its own**, built
and tested on a device, before any web code exists — otherwise a browser experiment breaks the
player and the cause is three commits back.

---

## 6. Order of work

1. **W1** — `PLAN_WEB.md`'s throwaway: a page you drop a file onto. The byte count is now known
   (libopenmpt 0.8.9 as a worklet: 1.54 MB raw, 516 KB over the wire); what is unknown is whether
   seven backends decode in an AudioWorklet without dropouts.
2. **H1** — the link. No server. Turns the phone's playlist into something that plays at work.
3. **H2** — the room and the QR code, if step 2's copy-paste turns out to grate.

Each is useful if the next never happens, which is the same test `PLAN_WEB.md` §2 applied to
W1/W2/W3 and the reason to trust the order.

---

## 7. Decisions that are the owner's

- **Is the work browser meant to play his *playlists*, or to be handed *one tune at a time*?** H1
  serves the first well and the second clumsily; H2 serves both. This is the question that picks the
  step to stop at.
- **Does the phone's local library have to reach work?** If yes, byte transfer is not an edge case
  and §3's H3 note becomes live. If no, the whole feature is three kilobytes of text.
- **Who hosts the relay, and is it worth having anything to run at all?** H1 has nothing. H2 has one
  small always-on thing, and the first one this project would own.
