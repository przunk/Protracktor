# Play Console app-content answers

Prepared 2026-09-03. Labels below use the English Play Console names. Re-read every displayed
question at submission time; the console adds and changes declarations independently of the app.

## Store settings

- **App category:** Music & Audio.
- **App or game:** App.
- **Free or paid:** Free.
- **Contains ads:** No.
- **In-app purchases:** No.
- **Primary language:** English (United States); add Polish (Poland) as a full translation.
- **Support contact:** owner supplies the public email in **Store settings** before submission.

## App access

Select **All functionality is available without special access**. There is no account, login,
membership, paywall or location restriction imposed by the app.

Reviewer notes, ready to paste:

> Protracktor requires no account or credentials. To exercise online playback, open Browse, choose
> Online catalogues, download the Modland index or ASMA archive, then select a track. The Mod
> Archive participates in Search and requires an active network connection. Local-file playback
> uses Android's system folder picker and therefore requires the reviewer to choose a folder
> containing a supported module. Background audio begins only after the reviewer presses Play and
> can be stopped from the persistent media notification.

## Target audience and content

Recommended target groups: **13–15, 16–17, and 18 and over**. Do not select an under-13 group.
The app is a specialist music utility and is not designed for children. Selecting a child audience
would introduce Families obligations that the product was not designed to meet.

## Content rating

Choose **All Other App Types** when the questionnaire asks for a category. The app itself contains
no violence, sexual material, gambling, controlled substances or offensive language. It does,
however, provide access to music and metadata held by independent online archives and to files
chosen by the user. Answer any dynamic question about access to third-party or online content
truthfully; do not promise that every title, embedded sample or comment in those archives has been
editorially reviewed by Protracktor.

### The answers the questionnaire actually asked, 2026-09-17

Recorded because two of them are judgement calls and one is backed by a measurement nobody should
have to repeat.

| Question | Answer | Why |
| --- | --- | --- |
| Online content not in the initial download | **Yes** | Modland, ASMA, The Mod Archive and UnExoticA are browsed and fetched on demand — the questionnaire's own Spotify example |
| A web browser or search engine | **No** | Fixed archives, no address bar, no arbitrary URL. The in-app search searches the *indexed catalogues*, not the web |
| Potentially offensive language | **Yes** | measured, below |

**The language answer is measured, not guessed.** Searching Modland's index for a small set of
unambiguous words, in **filenames alone**:

| | files |
| --- | --- |
| `shit*` | 876 |
| `fuck*` | 581 |
| `bitch*` | 214 + `wank*` 214 |
| the rest | 123 |
| **total** | **2,013 of 516,118 — 0.39%** |

**And that is a floor.** It counts filenames only. The app also shows author names, **instrument and
sample names** (`docs/BACKLOG.md` A34, built because the owner asked — *"czasem autorzy w
instrumentach kodują treść"*) and module messages, which is exactly where the demoscene put its
greetings and its jokes.

**The "not user-generated content" exclusion does not apply.** IARC means content made by *users of
this app* — uploads, comments, chat. These are third-party files, published elsewhere, that
Protracktor displays. Answering No would be a claim to have editorially reviewed half a million
files, which is the claim the paragraph above this one says not to make.

The calculated IARC ratings, not this document, are the release record. Save the certificate with
the release evidence.

## Other declarations

| Declaration | Answer |
| --- | --- |
| Ads | No |
| News and Magazine apps | No |
| Health apps | No health features |
| Financial features | No financial features |
| Government apps | No |
| COVID-19 contact tracing or status | No |
| Data deletion | No accounts; local data is removed by Clear storage or uninstall |
| Permissions Declaration Form | No high-risk permission expected; verify after uploading AAB |
| Foreground service permissions | Yes — `mediaPlayback`; use `foreground-service.md` |

## Content and rights

No music is bundled. Users choose local files or independently operated archives. Before public
release, confirm that each archive permits the way the client accesses its catalogue and files.
Separately resolve the sc68 replay-binary distribution question in `docs/LICENSES.md`; it concerns
bytes shipped inside the APK and cannot be answered by a store declaration.

Official references:

- [Prepare your app for review](https://support.google.com/googleplay/android-developer/answer/9859455?hl=en)
- [Content rating requirements](https://support.google.com/googleplay/android-developer/answer/9859655?hl=en)
- [Target audience and content](https://support.google.com/googleplay/android-developer/answer/9867159?hl=en)
