# Data safety declaration

Prepared 2026-09-03 against commit `3d2946b`; re-audited 2026-09-21 for 0.7.0. Re-audit the release artifact and every dependency
before submission. The developer is responsible for the final form; this document records the
evidence and the conservative answer, not a promise that Play Console will keep the same wording.

## Recommended top-level answer

**Does your app collect or share any of the required user data types? — Yes.**

The application has no first-party server or telemetry, but its optional live search sends the
user's query to The Mod Archive. Google defines collection as transmitting data off the device,
including directly to a third party. Calling this “no data collected” would omit a real transfer.

## Data type to declare

### App activity → In-app search history

| Question | Answer | Evidence |
| --- | --- | --- |
| Collected | **Yes** | `ModArchive.search` sends the entered query to `modarchive.org` |
| Shared | **No, using the user-initiated-action exemption** | the user explicitly selects Online search and expects the query to reach that archive |
| Processed ephemerally | **No / do not claim it** | the app does not retain the query, but the archive's server-log retention is unknown |
| Required or optional | **Optional** | local playback and downloaded catalogues work without live search |
| Purpose | **App functionality** | the query is used only to return matching music |
| Encryption in transit | **Yes** | the production endpoint is HTTPS |

If Play Console does not offer the user-initiated exemption in the displayed flow, declare this
data as shared too. Do not change the answer to “not collected” merely to avoid that label.

## Transfers that do not add another declared data type

- Local file identifiers, metadata, playlists, settings and play history remain on-device.
- Catalogue and track downloads send ordinary HTTPS requests. The application does not use the
  resulting IP address to infer location and does not add a device or advertising identifier.
- **Share file** and **Share link** transfer exactly what the user selected to the app chosen in the
  Android share sheet. This is a specific user-initiated transfer.
- **System backup is off** (`android:allowBackup="false"` and `dataExtractionRules` excluding every
  domain, since 0.5.0, `docs/STATUS.md` C64), so the operating system does not copy app data off the
  device either.
- **Send to a web player** transfers the chosen queue — titles, archive addresses, position — and
  device-only tracks as files (up to 8 MB), to the server named by a pairing code the user scanned.
  Specific and user-initiated, like **Share**, and to a server running the page the user opened.
- The downloads added since the first audit are all of the same kind as the catalogue downloads:
  `files.exotica.org.uk` (UnExoticA), `raw.githubusercontent.com` (songdb metadata and lengths,
  Modland's favourites, UADE's song database), `svn.code.sf.net` (sc68's replay routines) and
  `gitlab.com` (UADE's replay routines). Ordinary HTTPS requests for public files; no identifier is
  added.

## Security and deletion answers

- **Is collected data encrypted in transit? — Yes.** The declared query uses HTTPS.
- **Can users request deletion? — Not applicable to a first-party account; no account or
  developer-held record exists.** The app cannot promise deletion from an independent archive's
  server logs.
- **Does the app provide account creation? — No.** The account-deletion requirement does not apply.
- **Independent security review? — No**, unless one is actually completed before release.

## Evidence inspected

- `app/src/main/kotlin/com/przunk/protracktor/net/ModArchive.kt`
- `app/src/main/kotlin/com/przunk/protracktor/net/RemoteFiles.kt`
- `app/src/main/kotlin/com/przunk/protracktor/net/Catalogue.kt`
- `app/src/main/AndroidManifest.xml`
- `gradle/libs.versions.toml` — no advertising, analytics or crash-reporting SDK

Official definition and exemptions:
[Google Play Data safety](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en).
