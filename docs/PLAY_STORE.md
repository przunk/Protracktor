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

**Ignoring is not removing, and this is the part that needs deciding rather than doing.** Five
letters are already committed across ten commits. `git rm -r docs/letters` takes them out of the
head, and they remain in the history — anybody who clones a public repository has every version of
every file that was ever in it. So there are two honest options:

1. **Rewrite the history** before the first push to a public remote — `git filter-repo --path
   docs/letters --invert-paths`, or a fresh orphan commit if the history is not worth keeping. Only
   the owner can do this: it changes every commit hash after the first letter landed, and
   `AGENTS.md` §3 puts pushes in his hands anyway.
2. **Accept that they are public.** Re-read them first with that in mind. They were written to be
   sent, not to be found — but nothing in them is a secret, and one of them is a measurement other
   people might genuinely want.

**Deciding late is the expensive version**, because after the first public push the history is out
and no rewrite recalls it. Worth settling in the same hour as the first push, not after.

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
