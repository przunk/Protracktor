# Foreground-service declaration

The release manifest declares only `mediaPlayback`:

- permission: `android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK`;
- service type: `android:foregroundServiceType="mediaPlayback"`;
- component: `.player.PlaybackService`, not exported.

This is an appropriate foreground-service use: playback is the app's core function, begins after a
user presses Play, is audible and represented by a media notification, and can be paused or stopped
by the user. Targeting Android 14 or later requires the matching manifest declaration **and** a
Play Console declaration with a demonstration video.

## Functionality description

Paste into the `mediaPlayback` declaration:

> Protracktor plays tracker and retro-platform music selected by the user. Playback continues when
> the activity is no longer visible so the user can listen with the screen locked or while using
> another app. A persistent media notification and MediaSession expose the current track and
> previous, play/pause, next and stop controls. The service starts only in response to a user
> playback action and stops when playback is stopped.

## Impact if deferred or interrupted

> Deferring the service would make a user-initiated track fail to continue when the app leaves the
> foreground. Interrupting it would stop audible playback and discard the listening action the
> user is currently controlling. Audio focus still pauses playback when another app or a call needs
> the output.

## Demonstration video script

Record a real Android device in one continuous, short video:

1. Launch Protracktor and show the playlist or Browse screen.
2. Select a playable track and press Play, making the user initiation visible.
3. Press Home while the track continues audibly.
4. Pull down notifications and show the Protracktor media notification and its track identity.
5. Pause and resume from the notification.
6. Lock the device and show lock-screen media controls if the recording setup permits it.
7. Stop playback from the notification and show that audio and the foreground operation end.

Use an unlisted YouTube or publicly viewable cloud-storage URL that does not require the reviewer to
request access. Record the final release behaviour; a mock-up or another app is not evidence.

Official references:

- [Play Console foreground-service requirements](https://support.google.com/googleplay/android-developer/answer/13392821?hl=en)
- [Android `mediaPlayback` service type](https://developer.android.com/about/versions/14/changes/fgs-types-required)
