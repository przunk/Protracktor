# B50 — Animated first-run tutorial

Recorded: 2026-10-21. Status: **TODO — analysis only; implementation not authorized**.
Source: an app user's request, relayed during user testing. The request is for a short,
multi-page introduction with animated pointers demonstrating app usage, available on first
launch and/or manually from Settings. Recording this task does not select the final design,
schedule implementation or establish that a prototype has been tested.

[Wishlist entry](WISHLIST.md#b50-animated-first-run-tutorial--todo-recorded-2026-10-21)
and [implementation inventory](IMPLEMENTATION_ROADMAP.md).

## Assessment

Feasible and useful, particularly for discovering the difference between browsing and the
active playback source, manual queue requests, and gestures that are not obvious from a row.
This is a medium-sized UI task rather than an audio-engine change. The main work is clear
content, convincing animation, lifecycle integration and accessibility; building a pager alone
is not the finished feature. No fixed delivery estimate is established before a prototype.

Two approaches are possible:

- **Recommended: illustrated pages with animated, isolated app-like previews.** Use safe sample
  data and reuse suitable presentation components, icons, colours and wording. A pointer can
  demonstrate taps, swipes, holding and dragging without touching real user data.
- **Alternative: a guided overlay on live screens.** Highlight actual controls and follow real
  navigation. This is more fragile: target positions, scroll state, existing data, permissions
  and screen changes all affect the flow. Do not choose it merely to make the first prototype
  look more integrated.

The recommendation is not an approved implementation decision. Start with a small visual
prototype if the task is selected, then settle the page count and presentation after review.

## Proposed content

Keep each page focused on one practical outcome. Five or six pages are a starting point,
not a requirement to cover every feature:

1. **Find music:** online catalogues and local files; explain that an index lets the app find
   music and is not a download of all the music. Playing uncached online files still needs a
   connection. Do not imply that every source or metadata package is mandatory.
2. **Search and Random:** selecting a result starts its playback context; introduce scopes
   and available filters, and show a dice double-tap reroll. Distinguish rerolling the list from
   Next, and do not imply that merely reopening Random replaces the current context.
3. **Playlists:** adding tracks, selecting a saved list and saving changes. Browsing another
   list does not itself replace what is playing; explicitly playing one of its rows does.
4. **Queue:** one combined view of the source and manual requests. Manual requests are FIFO,
   may contain duplicates, and disappear after being left/consumed; playback then continues
   from the source. They are not permanent playlist edits.
5. **Gestures:** animate right swipe to add a manual request; within Queue, left swipe removes
   that occurrence from the session, holding opens Information, and grips reorder only waiting
   manual requests among themselves. Do not advertise Queue-specific removal/holding as a
   universal action on every screen. Queue removal does not delete a file or saved playlist row.
6. **Player:** Now Playing, main playback controls and subsongs when present. Prefer a short
   practical example over an exhaustive catalogue of formats and settings.

Final copy must follow the accepted [queue behaviour](QUEUE_UNIFIED_LIST.md),
[manual reordering](QUEUE_MANUAL_REORDER.md) and [Clear semantics](QUEUE_CLEAR.md).
Queue's existing Help remains useful for quick reminders after onboarding.

## Proposed interaction and accessibility

- Offer a skippable first-run introduction and a labelled **Settings → Tutorial** entry that
  always reopens it. First-run timing and the meaning of Skip still need decisions below.
- Allow explicit Back/Next/Finish controls as well as page swiping, with a clear page indicator.
  Use project-standard icon-and-label actions; tutorial navigation is not an automatic exception
  to that convention.
- Let the user control progress; do not automatically advance pages while text is being read.
  Limit or pause animation when a page is not visible.
- Explain each demonstrated action in readable text. Neither motion nor colour may be the sole
  source of meaning. Provide TalkBack semantics, sensible focus, large-font layouts and a
  usable static/reduced-motion presentation when animations are disabled.
- Localise all content and action descriptions in English and Polish from the start. Reuse
  existing resources where the meaning is identical rather than maintaining a second glossary.

## Technical direction and integration risks

The following are implementation candidates, not changes made by this document:

- Compose Foundation's `HorizontalPager` is a candidate for paging and page indicators;
  no additional third-party onboarding library is proposed.
  [Official pager documentation](https://developer.android.com/develop/ui/compose/layouts/pager).
- Keep tutorial presentation and demonstration state separate from the playback controller.
  Share rendering where practical, but never bind demonstration taps or gestures to production
  enqueue, playback, downloads, History, playlist or bookmark mutations. A tutorial replay must
  not interrupt existing playback or replace its context.
- Keep animation definitions small and data-driven where helpful. Avoid a second playback/queue
  engine just to animate a demonstration. Ensure previews remain truthful when the real UI changes.
- `ui/ProtracktorApp.kt` owns screen presentation and external-open handling, and currently
  launches the notification-permission request at startup. `MainActivity.kt` passes external
  files/links into that shell after the database gate. The introduction must not consume or lose
  those requests, hide an error from the gate, or compete with a system permission dialog.
- `ui/SettingsScreen.kt` provides the manual entry point. `AppLocale.kt` and `AppTheme.kt` show
  the existing preference approach; onboarding acknowledgement should be small local preference
  state, not a music-database schema change. Keep durable acknowledgement separate from the
  transient current page so recreation does not accidentally complete the introduction.
- Existing `ui/QueueHelp.kt` and the tutorial should use consistent action terminology. Prefer
  common resources or small shared presentation pieces where applicable, not duplicated rules
  embedded in independent screens.

Source inspection is partial feasibility evidence, not a working UI prototype. No tutorial code,
animations or phone behaviour have been implemented or verified.

## Relationship to existing work

This is the broader tutorial request **B50**, not a replacement for
[A46's index/download guidance](BACKLOG.md#a46-nobody-knew-they-had-to-index-anything--one-press-download-built-2026-09-17-branch).
The download picker and empty-playlist offer already exist. Share the index explanation with
A46 rather than opening another task for the same copy. Metered-network notices, clearer
download controls and within-step progress remain separate A46 residuals; onboarding does not
silently approve them or count them as completed. It must not start automatic index downloads.

Scope for a first implementation is the Android app. Web onboarding, a mandatory live-screen
walkthrough, analytics, automatic catalogue setup and a tour of every format are not selected.

## Open choices before implementation

1. **Entry timing:** automatically show on a normal fresh launch, or offer an invitation first?
   Recommendation: a skippable first-run flow, deferred when opening an external track/link.
   Define how that deferred offer returns without interrupting playback.
2. **Skip and completion:** suppress automatic reopening after either explicit Skip or Finish,
   or resume an incomplete tour next launch? Recommendation: respect explicit dismissal and
   keep replay available in Settings; interruption/recreation is not the same as dismissal.
3. **Existing installations:** Settings-only discovery, or a one-time optional invitation after
   upgrade? Do not unexpectedly treat every existing user as a first-time user.
4. **Content versioning:** simple acknowledgement, or a tutorial revision with optional offers
   for genuinely new content? An app version bump alone should not force the same tour again.
5. **Page grouping and demonstrations:** agree the final content and preview fidelity after a
   prototype; avoid six dense pages becoming another manual nobody reads.

## Proposed implementation and verification stages

1. Confirm the choices above; prototype one static page and one animated gesture with safe data.
2. Build the isolated pager and EN/PL content, including static/accessibility alternatives.
3. Integrate local acknowledgement, Settings replay and startup/external-open sequencing.
4. Test lifecycle, dismissal/replay, real tutorial controls and demonstration isolation. Use
   negative probes so tests fail if acknowledgement or event isolation is broken; directly
   exercise gestures rather than only calling the callback beneath them.
5. Run the full application tests and lint, then build a minified test-signed release APK in
   `dist/`. On a phone check first launch, upgrade, Skip/Finish/replay, Back/recreation, incoming
   file/link, ongoing playback, EN/PL, large text, TalkBack and disabled animations. Animation
   clarity and whether the flow actually teaches users require user feedback, not compilation.

Current verification is documentation-only: the agent inspected related code and records and
checked this plan's consistency and local links. No application tests or APK build are required
for recording the TODO; tutorial behaviour remains **unchecked**.
