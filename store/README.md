# Google Play package

Prepared 2026-09-03 from application commit `3d2946b`; **the listing text was refreshed 2026-09-15**,
which is the point of this note — the app gained ASMA, a browser player, favourites, Random's scopes
and a great deal else in the twelve days between, and none of it was in the description. A store
listing is the easiest thing in a repository to let go stale, because nothing breaks when it does.

This directory contains copy-ready text and the evidence behind the Play Console answers. It does
**not** mean the application is ready to publish: the release gates below deliberately remain open.

## Contents

| Path | Purpose |
| --- | --- |
| `listing/en-US/` | English title, descriptions and release notes |
| `listing/pl-PL/` | Polish title, descriptions and release notes |
| `privacy-policy.md` | **the** privacy policy, bilingual — one copy, to publish as a static web page; `docs/PRIVACY.md` points here |
| `play-console/data-safety.md` | conservative Data safety answers tied to the code |
| `play-console/app-content.md` | remaining **Policy and programs → App content** answers |
| `play-console/foreground-service.md` | copy and video script for `mediaPlayback` declaration |
| `play-console/gpl-and-notices.md` | source-code and third-party-notice obligations |
| `play-console/release-checklist.md` | ordered hand-off from repository to testing track |
| `graphics/README.md` | exact asset requirements and the device screenshot plan |

`./scripts/check-store-metadata.sh` checks that listing fields stay within Play's character limits
and that both locales contain the same required files. `./scripts/test-protracktor.sh` runs that
check after the unit and resource checks.

## Release gates still open

- Publish the privacy policy at an active public HTML URL and put the same URL in the app. A file in
  a private repository is not a privacy-policy URL.
- Make the exact release source public and provide verified third-party notices. ~~Resolve the sc68
  replay-binary distribution question first.~~ **Settled 2026-09-04**: the APK ships one replay,
  `sndh_ice.bin`, which is sc68's own; the other 98 are fetched from sc68's SourceForge by the
  device, so this project distributes none of them (`docs/LICENSES.md`).
- Verify Oboe's licence from the exact artifact and generate notices from every shipped component.
- Capture real phone screenshots and make a 1024×500 feature graphic. Do not substitute mock UI.
- Record the foreground-media-playback demonstration and enter its public video URL.
- Complete a device pass for the formats and catalogue paths still marked unverified in
  `docs/STATUS.md`.
- Supply the owner-controlled support email, release key and Play Console declarations.

Nothing in this directory authorizes publishing, changes `versionCode`, accesses a keystore or
uploads an artifact.

## Official requirements checked

These links were checked on 2026-09-03; re-check them for every release because Play policy changes
outside the repository.

- [Store listing fields and character limits](https://support.google.com/googleplay/android-developer/answer/9859152?hl=en)
- [Release preparation and the 500-character release-note limit](https://support.google.com/googleplay/android-developer/answer/9859348?hl=en)
- [Preview assets and screenshot requirements](https://support.google.com/googleplay/android-developer/answer/9866151?hl=en)
- [Data safety form](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en)
- [Preparing an app for review](https://support.google.com/googleplay/android-developer/answer/9859455?hl=en)
- [Foreground-service declaration](https://support.google.com/googleplay/android-developer/answer/13392821?hl=en)
- [Target API requirements](https://developer.android.com/google/play/requirements/target-sdk)
- [Testing requirements for newer personal accounts](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en)
- [Package registration and developer verification](https://support.google.com/googleplay/android-developer/answer/16984799?hl=en)
- [Play App Signing](https://support.google.com/googleplay/android-developer/answer/9842756?hl=en)
