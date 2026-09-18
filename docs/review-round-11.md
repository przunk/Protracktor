# Review — round 11

*A shallow pass over round 11's own diff, asked for when the work was done: C56's
fallback length, A32's ZXTune in the browser, and the refreshed Play listing. 43 files, ~1,590 lines
added.*

**Shallow, and it still found two faults that would have reached a phone** — both in work committed as
finished earlier in the same session. That is the argument for doing this at all: the tests were
green before the review and green after, because neither fault was a thing a test had been written
for.

## Fixed while reviewing

### R1. The ZXTune patch never reached an existing checkout — **the serious one**

`scripts/fetch-zxtune.py` returns early when the stamp file already names the pinned revision:

```python
if stamp.is_file() and stamp.read_text().strip() == REVISION and not args.force:
    print(f"  ✅ zxtune {REVISION} (already present)")
    return 0
```

The patches were applied after the clone, **below that return**. So the machine this was written on
worked — its tree had been patched by hand while the work was being done — and every other checkout
in the world would have failed the web build with a compiler error about `__wrap_iter`, on a tree
that is perfectly up to date and that the fetch script had just called fine.

This is the shape of fault that is invisible to the author by construction: the state that makes it
work is the state the author is in. Fixed by patching on that path too — `apply_patches` is
idempotent, so it costs five file reads — and checked by restoring one file from upstream and
watching the "already present" path repatch it.

### R2. The slider could store 239 seconds and call it three minutes

`SettingsScreen` read the Compose `Slider` with `it.toInt()`. A stepped slider hands back a float
that is only *nearly* its notch — 239.99997 for four minutes — so truncation stored 239 while the
label, dividing by 60 with integer division, still read "3 minutes". The number stored was not the
number shown, in a setting whose whole job is to be a number.

Fixed by rounding, and **not in the screen**: `FallbackLength.snap` now owns the invariant, and
`fromStored` and `PlaybackController.setFallbackLength` both go through it. A rule enforced in one
control is a rule the next control breaks. Two tests.

### R3. The store checker was checking a filename, not a field

`check-store-metadata.sh` validated `release-notes/2.txt` **by name**. Renaming that file — the
obvious thing to do, since `versionCode` is the commit count and a file named after versionCode 2
was never going to be right again — would have silently stopped checking release notes at all,
while the script went on printing its tick.

It globs now, and it also checks that the two locales hold the same files: a listing present in
English and absent in Polish is shown by Play in the default language, silently, and this app is
bilingual from its first screen. Both behaviours were tested by making them fail before trusting
them.

### R4. Two privacy policies, both reading as the source

`docs/PRIVACY.md` and `store/privacy-policy.md`, written a day apart, saying the same thing in
different words, each presenting itself as the one to publish. Nothing had gone wrong yet; it was
one edit away from going wrong permanently, because the policy is the one document where two
versions is a legal statement rather than an inconvenience.

One copy now — the bilingual one, since the app is bilingual from its first screen — and the other
is a pointer. `docs/PLAY_STORE.md` and `docs/BACKLOG.md` follow it.

### R5. Two stale instructions in the release material

`store/play-console/release-checklist.md` told the releaser to bump `versionCode` by hand; it has
been `git rev-list --count HEAD` since it blocked an upload once, precisely because a number a
person has to remember is a number that eventually is not remembered. It also named a flag
(`--rerun-tasks`) that `test-protracktor.sh` does not take. `store/README.md` still listed the sc68
replay-binary question as a blocking gate, settled on 2026-09-04.

## Checked, and found sound

Recorded because "we looked" is worth as much as "we fixed", and because each of these was a real
suspicion rather than a formality.

- **Could a stale `position` end the wrong tune?** The web fallback clears its flag on `opened`, so
  a position message from the *previous* tune arriving afterwards would end the new one instantly.
  It cannot: both messages come from the same worklet over one `MessagePort`, which is FIFO, and
  `opened` is posted after the open — so anything posted before it arrives before it.
- **`fallbackFired` against `finished`.** Deliberately two flags. `finished` means "played to its
  end with nothing after it", the state that turns play into "again"; a tune the fallback moves on
  from is not that, and borrowing the flag would have left the transport lying for as long as the
  next open took.
- **Compose's `steps` off-by-one.** `steps` counts notches *between* the ends, so eight positions
  need six. `FallbackLength.STEPS - 2`, with a test, because this is wrong in most codebases.
- **Does the ZXTune patch hurt the phone?** `auto` binds a raw pointer as happily as a wrapper, so
  the NDK build compiles the same sources — confirmed by building it, not by reasoning.
- **Is ZXTune in the browser real or merely linked?** `.pt3`, `.asc` and `.stp` fetched from Modland
  and played through the built engine: audible, ~380× realtime. `.vt2` is refused, and is refused on
  the phone too — it is not in `formats.tsv` at all, so the two agree rather than diverge.

## Left open, on purpose

- **The fallback cuts *any* lengthless tune at three minutes**, not only SID. That is the decision
  (`docs/STATUS.md` C56) and it is right, but it is the thing to watch on the phone: a format that
  reports no length and deserves longer will now stop, and nobody will think to report it as a
  fault because stopping looks normal.
- **`docs/TESTING.md` names build 628.** By design — it takes a section per fix that needs its own
  check — but the next handover needs a section, and an empty one is worse than none.
- **`versionName` is still `0.3.0`.** An alpha on a store is a moment to decide whether that is
  still what it says; `docs/BUILD.md` has the rule and this review has no opinion.
- **Only the parts of `store/play-console/` that this round touched were read.** `app-content.md`,
  `data-safety.md`, `foreground-service.md` and `graphics/README.md` were written 2026-09-03 and
  have not been re-checked against the app as it is now. The listing text had drifted badly in
  twelve days; there is no reason to assume those did not.

## The pattern in R1, R3 and R5

Three of the five are the same fault wearing different clothes: **something that verifies was
checking the world it was written in rather than the world it would run in.** A patch step below an
early return, a checker pinned to a filename, a checklist describing a build process that had since
been automated. All three printed success while doing nothing.

The cheap defence is the one used on R3 and on the ZXTune patch table: make the verifier state what
it expects to find and fail loudly when it does not. The patch table says how many times each
pattern must match, and that caught a real mistake during the work — one pattern occurs **twice** in
`encoding.cpp`, where a blind replace had quietly done both.
