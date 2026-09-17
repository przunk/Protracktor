# Release evidence

One section per artifact handed to Google Play. The point of the file is that a published
`versionCode` can be tied back to the exact source it was built from, which is what the GPL
obligation in `gpl-and-notices.md` rests on.

Record a build here **before** uploading it, and never edit a section afterwards.

## 0.5.0 — versionCode 702

Closed testing, the second build. What the first round of testers found.

| | |
| --- | --- |
| Artifact | *built by the owner with `./scripts/build-bundle.sh`; fill in on upload* |
| Size | |
| SHA-256 | |
| versionCode | 702 — `git rev-list --count HEAD`, not written by hand |
| versionName | 0.5.0 |
| Commit | *the commit `v0.5.0` points at* |
| Tag | `v0.5.0` |
| Built | |

**Signer**: the same upload key as 0.4.0; `build-bundle.sh` prints it from the bundle and it belongs
here once it has.

**Content of the release**: C61, C62, C63, C64, C65 and A46 — see `docs/STATUS.md` and the tag's own
message. No schema change, so an upgrade keeps everything a tester has downloaded.

## 0.4.0 — versionCode 679

Closed testing. The first build offered to testers outside the workshop.

| | |
| --- | --- |
| Artifact | `dist/protracktor-0.4.0-679.aab` |
| Size | 19,019,022 bytes |
| SHA-256 | `4524de07c9b5b9041d05321ed8cea9628f2fbeef9edf00237bbfb101ba9adb66` |
| versionCode | 679 — `git rev-list --count HEAD`, not written by hand |
| versionName | 0.4.0 |
| Commit | `d7a153c` |
| Tag | `v0.4.0` |
| Built | 2026-09-17 |

**Signer** (upload key, printed by `build-bundle.sh` from the bundle itself):

```
Owner:   CN=Przunk, OU=Przunk, O=Przunk, L=Wejherowo, ST=Pomorskie, C=PL
Issuer:  same (self-signed)
Serial:  1b7a8a96
Valid:   2026-09-02 to 2054-01-18
SHA-256: E2:02:EF:AD:A7:48:70:F0:8D:A3:63:28:D4:E0:74:00:04:93:EC:9E:FF:AA:43:39:95:84:13:60:D9:CC:A9:9D
Algorithm: SHA256withRSA
```

Play App Signing signs what users install with a different key. Both fingerprints belong here once
Play shows them: the upload key above, and the app-signing key Play holds.

**What the bundle contains**

- `com.przunk.protracktor`, minSdk 29, targetSdk 36, one `classes.dex`.
- Native code for `arm64-v8a`, `armeabi-v7a` and `x86_64` — 16.4 MB uncompressed, six libraries
  including `libprotracktor_engine.so` and `liboboe.so`.
- `base/assets/sc68/Replay/…`, the sc68 replay binaries (`docs/LICENSES.md` has the open question).
- Permissions: `INTERNET`, `CAMERA` (the pairing code), `POST_NOTIFICATIONS`,
  `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, and `DUMP`.
- `DUMP` is declared by `androidx.profileinstaller` and `androidx.work`, not by this app. It is
  `signature|privileged`, so it is never granted to an app installed from Play; it appears in the
  permission list Play shows and needs no declaration form.
- Not debuggable.

**Content of the release**: `docs/BACKLOG.md` A37 — comments cut back to implementation facts.
No behaviour changed since versionCode 666, which was built before that cleanup and was never
uploaded.
