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
