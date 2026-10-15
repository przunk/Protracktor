# Release-version history reconciliation — 2026-10-15

## Decision and boundaries

The owner approved rewriting unpublished version-only bumps after reviewing the established
release process, then explicitly approved updating the project AGENTS version rule.
Keep versionName at the latest prepared release during development; release.sh selects the next
release name in its release-version commit/tag. The prepared baseline is **1.1.0** and the next
selected feature release is **1.2.0**. No release procedure, AAB signing, GitHub push or Play
publication is performed by this cleanup.

Original develop: `16bd60d5fa508bdf29893c27912ba6e6b207d23b`.
Immutable release base/master commit: `da4569bcc810d64f20329bca873bc20a69578880` (annotated tag `v1.1.0` unchanged).
Rewritten equivalent: `eca4c8de5b446047606781b5d0244dd17c984ec6`.
Scope is exactly **63 descendant commits**: 55 change SHA, eight remain identical.
The complete graph count remains **1193**, before subsequent ordinary audit/documentation commits.
versionCode remains Git-derived; do not type a replacement counter.

## Technical method and checks

A one-off Python standard-library driver uses Git object operations, including commit-tree, to
copy the existing graph in parent-first order. It changes only the versionName blob in
app/build.gradle.kts, preserving every other file, tree entry/mode, commit message, author and
committer identity/date, and the exact ordered parent mapping, including merges. Preflight rejects
unknown commit headers/signatures rather than pretending to preserve invalid signatures.
All scoped originals contain only tree/parent/author/committer headers.

All **63 pairs** pass independent old/new tree/path/blob, commit-message/identity/date and ordered
parent checks. Both the descendant count and total commit count are checked. Deliberate verifier
negative checks reject an unremoved trial version, an unrelated README edit and reversed merge
parents. Negative fixtures create only unreferenced Git objects, not source/worktree edits.
The candidate's complete source-tree difference from original develop is solely
`versionName = "1.4.1"` to `versionName = "1.1.0"`.
Full application tests/lint remain pending at this checkpoint.

The first read-only Node child-process preflight reported an EPERM handoff error in the sandbox;
the completed audit/rewrite uses Python subprocesses instead. No JDK, SDK or shared tooling changed.

## Original provenance and recovery

Before object rewriting, the original graph and scoped references were saved to the verified,
self-contained `dist/version-cleanup/pre-rewrite.bundle` (13,518,139 bytes, 12 refs), and original
develop was anchored at `refs/archive/version-cleanup-20261015/develop`.
The archive is deliberately outside heads/tags and must not be moved along with rewritten branches.
The bundle is ignored build/maintenance evidence, not a release artifact. Keep it until the owner
explicitly chooses to remove it. Original reference/worktree snapshots, the one-off tool, full
JSON mapping, remote snapshots and checksums are in ignored `dist/version-cleanup/`.

Preflight directly read GitHub heads/tags: remote develop was `d5dbe4a`, master `2aaeeeb`,
latest remote version tag `v1.0.0`; no scoped commit is public there. No tag, remote-tracking ref,
master or other checked-out worktree branch is part of the rewrite.
Promotion will compare expected old ref IDs and update affected completed local branches in a
single guarded Git ref transaction, only after candidate validation. No force push is needed.

Existing 1.3.3, 1.4.0 and 1.4.1 APKs and the 1.1.0 AAB retain their exact filenames, versions and
SHA-256 hashes. Dated build/test/acceptance records and commit messages keep their original SHAs
and artifact numbers; those point into the archived original history. Do not relabel an old
artifact, recompute its recorded version, or substitute a rewritten commit as its original source.
The [full commit map](VERSION_HISTORY_REWRITE_MAP.tsv) connects original and rewritten equivalents;
it is a provenance bridge, not a claim that old artifacts were built from new commits.

## Completed local branch mapping

These references remain unchanged until candidate verification and guarded promotion finish.
An identity mapping is intentional when a branch ends before the first corrected version bump.

| Local branch | Original tip | Rewritten equivalent |
| --- | --- | --- |
| `develop` | `16bd60d` | `eca4c8d` |
| `feature/history-live-search` | `1820c9b` | `3f8d6af` |
| `feature/notification-playlist-heart` | `6b4efca` | `2b8691c` |
| `feature/recent-search-suggestions` | `4c0a28b` | `9543753` |
| `feature/stereo-mix-modes` | `ee21f28` | `3a88552` |
| `fix/bluetooth-output-recovery` | `d398602` | `a232b7f` |
| `fix/latest-playlist-activation` | `ad9ec54` | `e2730f6` |
| `fix/playlist-write-safety` | `b134b3e` | `b134b3e` |
| `fix/preserve-newer-database` | `83468f4` | `ceb369e` |
| `fix/search-request-ownership` | `96a7298` | `966560a` |

## Verification state

**Checked:** backup bundle verification; original artifact SHA-256 checks; 63 graph/tree/metadata
pairs; unchanged total count; three negative verifier checks; remote publication boundary.
**Pending:** full forced application test/lint gate, documentation review and guarded promotion.
**Not performed:** new APK/AAB build, phone tests, push, tag/master move or store publication.
The history correction changes version metadata, not app behaviour or schema. This does not turn
prior host-only unsupported-schema UI checks into phone evidence.
