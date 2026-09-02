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
