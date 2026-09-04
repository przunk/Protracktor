# Publishing to Google Play

What the store asks for, answered. Written 2026-09-04 as `GOAL.md` round 6 item 5.

**Publishing itself is not in here and is not an agent's to do.** The account, the upload key and
the name on the listing are the owner's (`AGENTS.md` §3). This is the paperwork that has to be right
before he presses anything, in a form he can copy.

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

`docs/PRIVACY.md`, which has to be reachable at a public URL for the listing. The repository is
public, so the file's own URL on GitHub satisfies that:

```
https://github.com/przunk/protracktor/blob/master/docs/PRIVACY.md
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
cloning a public repository gets every version of every file that was ever in it. The owner's
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
- **It changes every commit hash** from the first letter onward. Any other clone is dead; the merge
  history and the messages survive intact.

### What the rewrite does not remove, and is the part worth thinking about

The letters are drafts in the owner's own words. **The quotations are other people's.**
`docs/LICENSES.md` and `docs/PLAN_FORMATS.md` quote Heikki Orsila and Matti Tiainen verbatim — the
`players/` licence position, the invitation to download from zakalwe.fi, the limit on what counts as permission, the RMC note — and several commit messages do too.
Filtering `docs/letters` leaves all of it.

That is not obviously wrong: quoting a maintainer's technical answer, attributed, in the reasoning
it produced is ordinary engineering practice, and none of it is unflattering. But these were private
replies rather than a public list, so it is a courtesy question and not a legal one. Three ways to
settle it, cheapest first:

1. **Ask them.** They answered a cold question within a day and copied each other in; "may I quote
   your replies in the project's documentation?" would very likely get a yes, and a yes is worth
   more than either alternative.
2. **Keep the facts, drop the verbatim.** The measurements and the conclusions are ours; the
   sentences are theirs. Paraphrasing costs a little colour and no information.
3. **Leave it.** Defensible, and it is the option that cannot be taken back.

**Deciding late is the expensive version** — after the first public push the history is out and no
rewrite recalls it. Worth settling in the same hour as that push, not after.

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
