# Protracktor — project-specific rules

`/mnt/workspace/AGENTS.md` governs this project too. This file records only where Protracktor
deviates from it, and per that document's §1 the more specific rule wins here.

## Documentation language: English

| what | language |
| --- | --- |
| code, comments, KDoc | English (unchanged) |
| branch names, commit messages, PR descriptions | English (unchanged) |
| **documents in `docs/` and the project root** | **English** — overrides workspace §4, which says Polish |
| conversation with the owner | Polish (unchanged) |
| strings in the app | Polish + English (R10) |

Decided by the owner on 2026-08-31, and on 2026-08-31 confirmed to apply **to this project only**.
The workspace-wide rule is unchanged and still says Polish for every other project.

## Merging waits for the owner

Stated by the owner on 2026-09-02: *"szkoda, że już zmergowałeś — merguj jak zatwierdzę"*.

**Finish an item on its branch and stop there.** Build it, test it, commit it, say it is ready — and
leave it unmerged until he has run it on his phone and said so. This applies to `develop`, not only
to `master`, which was already his call.

**Why it is not a formality.** Everything built here is verified by compiling and by unit tests, and
neither of those can tell whether a gesture feels right, whether a list jumps under a thumb, or
whether pressing a button appears to do anything. Those are the defects this project actually
produces, and every one of them so far has been found by the owner on a device. Merging before he
looks puts them in `develop` as though they had passed something.

**The exception** is an unattended run against `GOAL.md`, where the whole point is that he is not
there: those rules stay as written in that file. Say plainly, when he comes back, what was merged
without his having seen it.

## Documents go straight to `develop`

Stated by the owner on 2026-09-21: *"można dać regułę, że dokumenty aktualizujemy od razu do
developa"*. **This overrides, for this project, the workspace rule that nobody commits to `develop`
directly** (`/mnt/workspace/AGENTS.md` §5).

**A change that is only documents is committed on `develop` itself** — a noted defect, a backlog
item, a plan, an open question, a corrected record. No branch, no merge. Documents waited on
branches for approval nobody needed to give, and a second branch touching `STATUS.md` then collided
with the first.

**Except a document describing code that is still on a branch.** That goes on the branch with the
code: `develop` saying a defect is fixed while the fix waits for the phone is a document that lies
(`/mnt/workspace/AGENTS.md` §9). Once the branch merges, its record is corrected on `develop` like any
other document.

## An action is an icon with a label, not a word

Stated by the owner on 2026-09-03, after asking for the same thing three times in two days: the way
out of Browse, the actions on a track, and the way into Browse.

**Any control that performs an action is drawn as an icon with its name underneath.** Use
`LabelledAction`; do not reach for a bare `TextButton`. The name is not optional — an icon alone does
not say where it goes — and neither is the icon, because a word alone does not read as a control and
several words in a row do not read as a set.

**Why it is a rule and not a preference.** Text buttons are what everyone reaches for out of habit,
including me: I built `LabelledAction` and then used a `TextButton` for the selection bar two hours
later. Written down, it is one line to check in review rather than a thing the owner has to notice
again.

**Where it does not apply:** a dialogue's confirm and cancel, which are Material's own convention and
where a row of icons would be worse; and the transport controls in the dock, which are icons without
labels because everyone alive knows what a triangle does.

## Performance is judged on a release build

Learned on 2026-09-03, expensively. The owner reported the playlist stuttering for the first ten to
twenty seconds after launch. Four explanations followed, each plausible, each a real problem, none of
them the cause — and then the release build turned out to have none of it.

A **debug** APK is `debuggable=true`. That gives up a great deal of ART's optimisation, holds the JIT
back, and skips R8 entirely. It is the right build for *does this work* and the wrong one for *is
this fast*.

**So: before investigating anything that feels slow, build release and check there.** It costs one
command. It came fifth, after four rounds of the owner testing builds that could not have answered
the question.

A useful tell, and the one that should have prompted it: **"it gets better the more you use it."**
Warm-up curves — more items making it faster, repeated scrolling curing it — are about compilation,
not about the code being wrong.

### So the build handed over is the release build

Asked for by the owner on 2026-09-03, immediately after the above: **build release, not debug, when
handing work over for testing.**

- **While iterating** — a compile error, a quick check that something links — use
  `./scripts/build-debug.sh`. It is two to three times faster and nobody is judging anything by it.
- **When handing an APK to the owner** — use `./scripts/build-release.sh`. It is what he will
  actually experience, it goes through R8, and it is a quarter of the size.

It also catches a class of problem debug cannot: a missing `keep` rule, a stripped resource, a JNI
symbol renamed. Those only exist in a minified build, and finding them at hand-over is better than
finding them at a release.

**The one thing to remember:** when something *crashes* and a stack trace matters, a debug build is
the readable one. Diagnose on debug, judge on release.

## Versions

`docs/BUILD.md` has the scheme. The short of it:

- **`versionCode` is the commit count** and nobody touches it. It moves on every merge by itself.
- **`versionName` is typed and means something** — patch for a batch of fixes, minor for a round of
  work that added capability, major reserved for "publishable". Bump it at hand-over, not at merge.

The owner asked for a minor bump per merge. It was talked through and rejected for a reason worth
keeping: twenty merges in a day would put it at 0.22.0 by evening and say nothing a timestamp does
not. The half of the instinct that was right — *something* must move on every merge — is the
versionCode, which now does.
