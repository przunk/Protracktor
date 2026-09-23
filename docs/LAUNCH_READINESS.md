<!--
SPDX-FileCopyrightText: 2026 Przunk
SPDX-License-Identifier: GPL-3.0-or-later
-->

# Ready for production? — reviewed 2026-09-22

*Asked for on 2026-09-22, with 0.8.0 in closed testing and the public launch planned for Friday
2026-10-02. The owner added one item of his own: **the web player at a standard, permanent address,
on a remote server rather than the Raspberry Pi.***

Sources: `store/README.md`, `store/play-console/*.md`, `docs/WEB_SERVER.md`, the app and the page as
they are on `develop`, and three things checked live today (below). Play Console itself cannot be
seen from here, so every console step is marked **confirm** unless the repository holds evidence
that it was done.

## The owner's answers, 2026-09-22

- **P1 package registration: done.** **P2:** the 14 days are expected to hold — about 15 testers
  are signed up.
- **P3 listing and P4 screenshots: the owner's.** The listing text is refreshed in
  `store/listing/` (L1, done) for him to paste; the screenshots he retakes.
- **The web player is an add-on, not part of the app.** Anyone can run it themselves; that is what
  is deployed. A public copy at a permanent address is wanted, as cheaply as possible, without a
  domain of his own (L-D1, L-D2 — open: GitHub Pages or `pages.dev`).
- **L-D3: say it where it is decided** — a notice on the app's pairing screen with the privacy
  policy one press away, and the same in the page's Settings and pairing sheet. Built:
  `feature/app-pairing-privacy-notice` (app) and `feature/web-legal-and-pairing-notice` (page, with
  the Source code link L2).
- **L-D4: keep** the app's cleartext exception for pages on a private network.
- **L-D1, L-D2: GitHub Pages**, decided the same evening: a static public copy at
  `https://przunk.github.io/Protracktor/`, no domain, no server of ours, no pairing there. So L3
  (Data safety for a server of ours) is **not needed**: nothing a phone sends reaches that copy; the
  policy only names GitHub as its host. L4 is `scripts/publish-web-pages.sh`, run once on 2026-09-22
  from `develop` (1a47b5b) into the local `gh-pages` branch, for the owner to push.
- Also asked: the page to carry the licences and policies it must — W4 (licences, privacy policy)
  is merged; the Source code link and the pairing notice are on the branch above.

## Checked live today

| what | result |
| --- | --- |
| the repository, `github.com/przunk/protracktor` | **public** (HTTP 200 without signing in) — the source is published and `v0.8.0` is tagged on it |
| the privacy policy link, `…/blob/master/store/privacy-policy.md` | **public**, and current since the 0.8.0 push |
| the store listing text | last refreshed **2026-09-15**; it does not mention the Amiga custom formats (UADE, 0.7.0), songdb's metadata, or accent-blind search |

## 1. Dates that decide whether 2026-10-02 holds

- **Package registration (Android developer verification) — mandatory from 2026-09-30.** Two days
  before the launch. **Confirm** that `com.przunk.protracktor` is registered: Play Console → the
  account's **Developer account** / **Android developer verification** page (the name shown there
  is the one to follow). If it is not, nothing else here matters on 10-02.
- **Production access.** A personal account created after 2023-11-13 has to run a closed test with
  at least 12 testers opted in for **14 continuous days**, then **apply** for production, and Google
  reviews the application — up to about a week. The owner reached 12 testers on or about
  2026-09-19, which makes the 14 days end on or about **2026-10-03**. **So 2026-10-02 may be the
  earliest day to apply rather than the day it goes public.** Confirm the date Play Console gives:
  **Test and release → Production** shows when **Apply for production** becomes available.
- **Target API 36** — already met.

## 2. Play Console — what the owner does or confirms

Exact names from the English console.

| # | where | what | state |
| --- | --- | --- | --- |
| P1 | **Android developer verification** | package registration for `com.przunk.protracktor` | **confirm — deadline 2026-09-30** |
| P2 | **Test and release → Production** | apply for production access once the 14 days are complete; answer the questions about the closed test | open |
| P3 | **Grow users → Store presence → Main store listing** | paste the refreshed listing (W-L1 below), EN and PL | open — text to be refreshed |
| P4 | same page | screenshots: the ones in `store/graphics/` are from **2026-09-17**, before Browse was regrouped (2026-09-21) — retake at least Browse and Now Playing on the phone | open |
| P5 | **Policy and programs → App content → Data safety** | re-submit if the web player moves to a server of ours (L-D3) | depends on L-D3 |
| P6 | **App content → Privacy policy** | the URL above; unchanged unless the policy moves to the web host (L-D2) | done, confirm |
| P7 | **App content → Content rating** | answered 2026-09-17 (`app-content.md`) | done, confirm certificate |
| P8 | **App content → Target audience and content** | 13+ (`app-content.md`) | confirm |
| P9 | **App content → Foreground service permissions** | `mediaPlayback`, with its video (`foreground-service.md`) | confirm |
| P10 | **Grow users → Store presence → Store settings** | category **Music & Audio**, support email | confirm |
| P11 | **Test and release → Setup → App signing** | Play App Signing enrolled; keep both certificate fingerprints with `releases.md` | confirm |
| P12 | **Test and release → Pre-launch report** | read the report for 0.8.0; crashes, ANRs, accessibility | open |
| P13 | **Test and release → Production → Create new release** | the release itself, with a **staged rollout** percentage | at launch |

## 3. The repository — what I can do

| # | what | size |
| --- | --- | --- |
| **L1** | refresh the store listing, EN and PL: the Amiga custom formats, song metadata, accent-blind search, the licences screen; within Play's limits, checked by `check-store-metadata.sh` | small |
| **L2** | the page offers its source, as the GPL asks of a page that conveys the engine: a **Source code** row in the page's Settings, icon and label, linking to the tagged repository | small |
| **L3** | the privacy policy and `data-safety.md` for a web player on our own server (L-D3) | small, after L-D3 |
| **L4** | the page at a permanent address on a remote server (L-D1, L-D2): deployment files, TLS, a service definition, a deploy script the owner runs, and `WEB_SERVER.md` rewritten | medium |
| **L5** | the app's pairing with an `https://` page: already accepted (`WebRemote.looksLikePairing`); nothing to change unless L-D4 | none or small |
| **L6** | `store/play-console/release-checklist.md` brought up to date — it still lists the sc68 question (settled 2026-09-04) and the in-app legal surface as open | small |

## 4. The web player on a remote server

What it needs is settled in `docs/WEB_SERVER.md` ("Where this should go", 2026-09-15): the page,
the engine and the catalogues need **only static hosting over TLS** — the browser fetches from
Modland, ASMA and HVSC itself, no COOP/COEP headers are needed — and **pairing** (`/pair/*`) needs
one small process with memory. `scripts/serve-web.mjs` is already both.

### L-D1 — where it runs

- **(a) Recommended: a small VPS running the existing `serve-web.mjs` behind Caddy.** Caddy obtains
  and renews the TLS certificate by itself; the node server stays exactly as it is, pairing
  included, on one origin. A few euros a month (Hetzner, OVH, Netcup and the like); an afternoon to
  set up; **no code changes before the launch**. The Pi is out of the picture.
- (b) Cloudflare Pages, pairing rewritten as Workers with Durable Objects. Free or nearly, but the
  pairing relay has to be rewritten for a stateless platform, and that is not a change to make ten
  days before a launch.
- (c) The page on a static host, the relay on a VPS: two addresses and two things to run, for
  nothing (a) does not already give.

### L-D2 — the address

A domain the owner controls, e.g. `play.<domain>` or `protracktor.<domain>`. **Needed from the
owner: the domain, and who holds its DNS.** A permanent address is also what `docs/BACKLOG.md` A40
(links opening in the app) has been waiting for.

### L-D3 — what the privacy policy and Data safety must then say

**Today both rest on "no first-party server"**: the policy says the page is "served by whoever hosts
it — usually the user", and `data-safety.md` says "the application has no first-party server". A
page on **our** server changes that for one feature: **Send to web player** posts the chosen queue —
titles, archive addresses, and files from the phone of up to 8 MB — to that server, which relays
them to the browser. The server also sees every visitor's IP address in its requests.

- **(a) Recommended: say it plainly.** Policy: the developer runs the public web player; its server
  relays what **Send** sends, holds it in memory only until the browser has it, and keeps no copy
  and no log of it; access logs off (Caddy logs nothing unless told to). Data safety: declare
  **Audio → Music files** (and the titles as **App activity → Other user-generated content**)
  as **collected**, **processed ephemerally**, **optional**, for **App functionality**,
  **encrypted in transit**. True, and honest about the one feature that sends anything.
- (b) Keep the public page but switch its pairing off, so nothing of the user's reaches our server;
  the Pi or a self-hosted page keeps pairing. Simpler declarations, a weaker public page.

### L-D4 — the app and plain `http://`

The app accepts cleartext only for private addresses, for a page on the owner's own network. With
the public page on `https://`, that exception can stay (a LAN Pi still works) or go. **Recommended:
keep it** — it costs nothing and removing it breaks self-hosting.

### What the owner does, once L-D1 and L-D2 are decided

1. Rent the VPS; point an `A` record (and `AAAA` if it has IPv6) for the chosen name at it.
2. Run the deploy script from L4, which copies the packaged page, installs the service and Caddy's
   configuration, and prints the address to check.
3. Open the address on the phone and in a browser; pair once; send a queue with a local file.

Passwords, SSH keys and the provider account stay the owner's; the script asks and stores nothing.

## Recommended order

1. **Today/tomorrow — owner:** P1 (package registration) and the production date in P2.
2. **Me:** L1, L2, L6 (no decisions needed), then L3 and L4 once L-D1…L-D3 are answered.
3. **Owner:** P3, P4 (new screenshots), P5 after L3; the VPS and DNS.
4. **Before 2026-10-02:** 0.8.1 only if L1 needs anything in the APK (it does not); the listing and
   declarations change without a new build.
