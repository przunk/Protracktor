# Privacy policy

**Protracktor collects nothing.**

Last updated 2026-09-04. This policy covers the Protracktor Android application published by Przunk.

## What is collected

Nothing. There is no analytics, no crash reporting, no advertising, no account, no sign-in, and no
identifier of any kind is read or generated. The app does not know who you are and has nowhere to
send it if it did.

This is verifiable rather than asserted: the source is public at
<https://github.com/przunk/protracktor>, and the application's entire dependency list is AndroidX,
Jetpack Compose and Oboe — Google's own audio library. There is no networking library, no analytics
SDK and no crash reporter, because there is nothing for them to do.

## What leaves your device

Only requests you ask for, and only to fetch music and the lists that describe it.

| Where | Why | When |
| --- | --- | --- |
| `modland.com` | its file index, and tunes from it | when you download the index, or play a tune from it |
| `asma.atari.org` | the ASMA archive | when you download that catalogue |
| `hvsc.c64.org` | the HVSC song-length database | when you press the download button for it |
| `modarchive.org`, `api.modarchive.org` | search results, and tunes from them | when you search, or play a result |

Each of these is a public archive of retro-platform music, run by other people. The request is an
ordinary HTTPS request for a file: no account, no cookie, no token, nothing about you attached. As
with any web request, those sites can see the address it came from, which is between you and them
and is the same thing that happens when you open their website in a browser.

**No request is made until you ask for one.** Opening the app, playing your own files and browsing
your own folders involve no network traffic at all.

## What stays on your device

Your playlists, your play history, the folders you granted access to, the downloaded indexes and the
cache of fetched tunes are stored in the app's private storage on your phone. They are never
uploaded. Deleting the app deletes all of it, and the app's Storage section lets you delete the
downloaded parts individually at any time.

## Files on your device

The app reads music files from folders **you choose**, through Android's own folder picker. It has
no broad storage permission and cannot see anything you have not handed it. It never modifies or
deletes your files.

## Permissions

- **Internet** — to fetch music from the archives listed above, when you ask.
- **Foreground service, media playback** — to keep playing when the screen is off, which is what a
  music player is for.
- **Notifications** — to show the playback controls in the notification shade.

## Children

The app is not directed at children and collects nothing from anyone.

## Changes

Any change to this policy will be committed to the public repository, so its history is the record.

## Contact

Through the repository at <https://github.com/przunk/protracktor>.
