# Publishing to Google Play

What the store asks for, answered. Written 2026-09-04 as `GOAL.md` round 6 item 5.

**Publishing itself is not in here and is not an agent's to do.** The account, the upload key and
the name on the listing are the owner's (`AGENTS.md` §3). This is the paperwork that has to be right
before anything is pressed, in a form that can be copied.

## The data-safety declaration

Play's Data safety form asks two questions and both have the same answer.

- **Does your app collect or share any of the required user data types?** — **No.**
- **Is all of the user data collected by your app encrypted in transit?** — not applicable, since
  none is collected. All network traffic is HTTPS regardless.

There is no data-deletion request mechanism to declare, because there is no account and no
server-side data to delete. Uninstalling removes everything the app ever stored, and the Storage
section removes the downloaded parts on request.

**Why this is genuinely "no" rather than an optimistic reading**, in the terms the form uses:

| Play's category | Ours |
| --- | --- |
| Personal info, financial info, health, messages, contacts, calendar | none touched |
| Location | never requested; no location permission |
| Photos and videos, audio files | reads music from folders **the user picks**, on device only, never uploaded |
| App activity, app info and performance | no analytics, no crash reporting, no diagnostics |
| Device or other IDs | none read or generated — no advertising ID, no `ANDROID_ID`, nothing |

The last row is the one worth checking rather than believing, and it is checkable: nothing in the
source reads an identifier, and the dependency list is AndroidX, Compose and Oboe with no analytics
SDK to do it behind our backs.

**The "audio files" row is the one that needs care on the form.** Play distinguishes *collecting*
files from *accessing* them. Protracktor accesses audio files the user explicitly grants through
Android's folder picker, reads them to play them, and never transmits them. That is access, not
collection, and it is declared as such.

## The privacy policy

`store/privacy-policy.md` — the single copy, bilingual, as of 2026-09-15. It has to be reachable at
a public URL for the listing, and the file's own URL on GitHub satisfies that **once the repository
is public**, which it is not yet:

```
https://github.com/przunk/protracktor/blob/master/store/privacy-policy.md
```

Nothing needs hosting. If a nicer URL is wanted later, GitHub Pages serves the same file.

## The GPL and the store

**Distributing GPL-3 software through Google Play is fine**, and this is written down once so it is
not re-litigated every time it comes up.

The GPL's obligation is to the people who receive the binary: they must be offered the corresponding
source under the same licence. A public repository at the address in the listing discharges that,
and every recipient can find it. That is the whole of it.

Two things people worry about here, and why neither applies:

- **Play's Developer Distribution Agreement does not conflict.** It asks for the right to
  distribute; it does not claim exclusivity and does not forbid the user's GPL rights. The often-
  cited problem is with the *Apple* App Store's terms, which impose device limits the GPL forbids
  passing on. Play imposes no such limit.
- **Signing does not restrict anything.** The upload key proves who published a build. It does not
  stop anyone building the source themselves and installing it, which is what the GPL requires be
  possible.

**The one real obligation to keep**: whatever version is published must correspond to source that is
actually available. `versionCode` is the commit count (`docs/BUILD.md`), so a published build names
the commit it came from — which is the cheapest possible way to keep that promise honest.

### The replay binaries are a separate question, and it is open

None of the above covers `docs/LICENSES.md`'s open item: sc68 ships 99 replay binaries whose
provenance is unclear, and the same question would arrive with UADE's 176 if it were ever
integrated. That is not a GPL question — it is whether we have the right to distribute other
people's code at all — and it is the owner's to answer **before** publishing, not after.

## Before the repository goes public

The listing points at the source, so publishing the app publishes the repository. One thing has to
happen first.

**`docs/letters/` must go.** It holds drafts and sent correspondence with the maintainers of
libraries we use — their replies, their names, and half-finished thoughts addressed to them. Useful
to keep locally; not ours to publish. It is in `.gitignore` as of 2026-09-05, which stops new ones
being added.

**Ignoring is not removing.** Five letters are already committed across ten commits, and anybody
cloning a public repository gets every version of every file that was ever in it. The
decision, 2026-09-05: **rewrite the history and force-push.**

### The recipe, tried on a copy first

`git-filter-repo` is a single Python file and is not installed here; it does not need to be.

```bash
curl -sSLo /tmp/git-filter-repo \
  https://raw.githubusercontent.com/newren/git-filter-repo/main/git-filter-repo
cd /mnt/workspace/Protracktor
python3 /tmp/git-filter-repo --path docs/letters --invert-paths --force
```

Run against a copy of this repository on 2026-09-05 it finished in under a second, left 309 commits
and **no file ever named `docs/letters/*` in any of them**. Two things it does that are worth
knowing before running it for real:

- **It removes the `origin` remote**, by design, so a stale push cannot restore what was cut. Add it
  back and force-push every branch afterwards.
- **And with it the remote-tracking refs**, which is what `--force-with-lease` compares against — so
  the safer force refuses with "stale info" until something fetches again. Push with
  `PROTRACKTOR_FORCE_PUSH=hard ./scripts/push-protracktor.sh develop master`, which uses a plain
  `--force`. A lease you cannot take is not a safety net, and the script says which one it is using.
- **`--set-upstream` is not optional afterwards** either: the rewritten branches have no upstream,
  which is the first error a push reports and the least informative one. The script always passes
  it.
- **It changes every commit hash** from the first letter onward. Any other clone is dead; the merge
  history and the messages survive intact.

### What the rewrite did not remove, and what was done about it — settled 2026-09-15

The letters were drafts of correspondence. **The substance around them was other people's**,
and filtering `docs/letters` left all of that in place: `docs/LICENSES.md` and
`docs/PLAN_FORMATS.md` reported what UADE's two maintainers had written in a private reply — the
`players/` licence position, the invitation to fetch the binaries from zakalwe.fi, the limit on what
counts as permission, the Hippel `song.conf`, the RMC note.

Quoting a maintainer's technical answer, attributed, in the reasoning it produced is ordinary
engineering practice and none of it was unflattering. But a private reply is not a public list, so
it was a courtesy question rather than a legal one, and it was settled twice:

- **2026-09-05 — their sentences came out.** Five passages were rewritten so that nothing reproduced
  their words. `a9a645e`.
- **2026-09-15 — their attribution came out too.** What stands now rests on documents UADE
  **publishes** — `amigasrc/README`, `COPYING`, the download page, `song.conf` — quoted as such, or
  on our own measurements. The exchange is recorded as having happened and as having settled things;
  its content is not reconstructed and the manner of the people who wrote it is not characterised.
  Exactly one fact has no public source (the single objection in twenty years, and whose it was); it
  is load-bearing for the argument above, so it is kept and **marked** as coming from the reply.

**The rule for anything similar in future**, so this is decided once: *quote what a project
publishes; report only what a private reply established, never how it said it.* Quoting a project's
own licence text while reasoning about its licence is the right thing to do. A reply sent to one
person is not that.

**Contact addresses are decided by where they already are**, and this was got wrong once in the
direction of caution. `heikki.orsila@iki.fi` was taken out of the letters table on 2026-09-15 and
**put back the same day**: UADE's own `AUTHORS` publishes it as the way to reach its main author, so
repeating it is not exposing it, and it is the fact that row exists to record. An address that
appeared only in a reply would be the other case, and there is none here.

**What the history still holds, and why it stays.** The pre-2026-09-05 versions of those two
documents are in the commits, and so are the commit messages that discussed them — but they never
carried a quotation-marked private sentence either: those passages were reported speech from the day
they were written, and what 2026-09-05 removed was closeness to their phrasing rather than
quotation. `docs/letters/` itself is absent from every commit on every branch, local and on the
remote, which was checked rather than assumed.

**Nothing is public yet**: the GitHub repository is private, so no version of any of this has left
local machines. That is what makes the cleanup above complete rather than mitigating, and it
is also why **no further history rewrite is planned**. Two things were raised as candidates on
2026-09-15, and both were decided against:

* **The three phone screenshots**, committed by accident and removed from the tree that day.
  Judged not a problem — some version of those screens goes on the store listing anyway.
* **The contact address**, for the reason two paragraphs up.

So the repository goes public with its history intact when the owner is ready. The standing warning
still applies to anything *new*: after the first public push no rewrite recalls it, so a thing worth
removing is worth removing beforehand.

## What is still missing, and is his

Mechanical, and none of it exists:

- Listing text, in English and Polish.
- Screenshots, at the sizes Play requires, and a feature graphic.
- Content rating questionnaire — the answers are all "no" but the form must be filled in by the
  account holder.
- The upload key itself, and the first upload.

## Already done

- `./scripts/build-bundle.sh` produces the `.aab` and refuses to hand over a debug-signed one.
- The launcher icon, adaptive with a monochrome layer, at every density.
- `versionCode` from the commit count, after it blocked an upload once.
- `targetSdk` 36, `minSdk` 29.
- `ACCESS_NETWORK_STATE` removed 2026-09-04: it was declared and never used, and an unused
  permission is one more thing to justify on a form for no benefit at all.
