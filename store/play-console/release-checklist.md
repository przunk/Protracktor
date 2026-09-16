# Google Play release checklist

Follow in order. A checked repository build is not permission to publish; account, identity,
signing and rollout remain the owner's actions.

## 1. Resolve legal and privacy gates

- [ ] Resolve the sc68 replay-binary distribution question.
- [ ] Verify Oboe's licence and produce notices from every exact shipped dependency.
- [ ] Add an in-app legal surface containing the privacy link, GPL source link and notices.
- [ ] Publish the privacy policy as a public, non-geofenced HTML page; no PDF or private-repository
      URL.
- [ ] Make the exact corresponding source public and tag the release commit.
- [ ] Confirm the access terms for Modland, ASMA, HVSC and The Mod Archive.
- [ ] Re-audit network/data behaviour and reconcile the privacy policy with **Data safety**.

## 2. Complete the application record

- [ ] In **Settings → Developer account**, confirm developer identity and public contact details.
- [ ] Confirm `com.przunk.protracktor` is registered under Android developer verification. Package
      registration becomes mandatory on 30 September 2026.
- [ ] In **Grow users → Store presence → Store settings**, enter the owner-controlled support
      email, category **Music & Audio**, and required developer details.
- [ ] In **Grow users → Store presence → Main store listing**, paste both locale packages from
      `store/listing/`.
- [ ] Upload the real screenshots and feature graphic described in `store/graphics/README.md`.
- [ ] Complete **Policy and programs → App content** from `app-content.md`.
- [ ] Complete **Data safety** from `data-safety.md` and provide the hosted privacy URL.
- [ ] Complete **Foreground service permissions → mediaPlayback**, including its video.
- [ ] Complete the IARC content-rating questionnaire and archive its resulting certificate.

## 3. Prepare the release tree

- [ ] Merge only owner-approved branches into `develop` and run the complete final documentation
      pass.
- [ ] Run `./scripts/test-protracktor.sh --rerun-tasks` and confirm a non-zero test count.
- [ ] Run `./scripts/build-debug.sh` and complete the outstanding phone test list in
      `docs/STATUS.md`.
- [ ] Build release and inspect R8/JNI/resources: `./scripts/build-release.sh`.
- [ ] The merger bumps `versionCode` exactly once. Set `versionName` according to the release scope.
- [ ] Commit the version change and create the immutable release tag.

## 4. Build and inspect the Play artifact

- [ ] Configure the owner-controlled upload keystore outside the repository.
- [ ] Run `./scripts/build-bundle.sh`; it must reject a debug-signed bundle.
- [ ] Record the AAB path, size, SHA-256, versionCode, commit, tag and printed signer identity.
- [ ] Enrol in **Play App Signing** and confirm which key is the app-signing key versus the upload
      key. Keep both certificate fingerprints with release evidence.
- [ ] Upload to **Test and release → Testing → Internal testing** first.
- [ ] Inspect **Latest releases and bundles**, generated APKs, permissions and supported devices.
- [ ] Install what Play generated on a real device, not merely the locally built APK.

## 5. Test and roll out

- [ ] Exercise local SAF playback, each online catalogue, download failure, offline behaviour,
      background/lock-screen playback, notification denial, sharing and session restore.
- [ ] Review the automated pre-launch report and fix confirmed crashes, ANRs and accessibility
      failures.
- [ ] If the personal developer account was created after 13 November 2023, complete a closed test
      with at least 12 continuously opted-in testers for 14 days before requesting production
      access.
- [ ] Upload localized release notes matching the final versionCode.
- [ ] Use a staged production rollout; monitor Play Console vitals before expanding it.

Current Play requirements checked 2026-09-03:

- API 36 is required for new phone/tablet submissions from 31 August 2026; the project already
  targets 36.
- New apps publish as Android App Bundles.
- Personal accounts created after 13 November 2023 may be subject to the 12-testers/14-days gate.
- Package registration is required by 30 September 2026.

Official references are collected in `store/README.md`.
