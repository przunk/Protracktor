# Code review — Protracktor Android app, November 2026

**Status: in progress.** This document is filled in stage by stage; the coverage ledger (§10) says
what has been read so far. Until the summary (§2) is written, nothing here is a conclusion about
the whole code base.

## 1. Header

| | |
| --- | --- |
| Reviewed branch | `develop` |
| Reviewed commit | `af2606123d0d6fc304eb3362d6823970233a86c7` (`af260612`), 2026-11-15 |
| Tree at start | clean; no untracked files; one worktree; level with `origin/develop` |
| `versionCode` at that commit | 1602 (commit count) |
| Review started | 2026-11-15 |
| Reviewer | the agent (Claude), unattended, against `GOAL.md` |
| Method | every in-scope own file read in full once; cross-cutting concepts compared side by side; reading, not execution, unless a finding says otherwise |

Every `file:line` reference below is valid for the reviewed commit.

### Why this review exists

In priority order:

1. I suspect the code could have been written considerably better: several agents wrote it at
   different times and with different depth. I want to know where it repeats itself, where it is
   brittle, and where *n* things of one kind are handled *n* separate ways instead of through one
   mechanism with only the specific parts supplied per case.
2. I do not know this code well enough to trust it. It has to be robust and secure, and must not
   crash for trivial reasons, particularly where it meets native code.
3. It has been developed for months by AI only, across many context compactions. I expect traces.

### Scope

**In:** `app/src/main` (254 Kotlin files, 36 690 lines; manifest; resources that carry behaviour);
own native code that is compiled into the APK; the boundary with third-party code; build
configuration that shapes the APK (`app/build.gradle.kts`, `app/proguard-rules.pro`,
`gradle/libs.versions.toml`); the database schema and migrations; tests under `app/src/test` as a
secondary subject (266 files, about 32 000 lines).

**Own native code, as settled at the start** from `app/notices/components.tsv` and the CMake files:

| in scope (own, in the APK) | lines |
| --- | --- |
| `native/engine/engine.cpp`, `engine.h` | 2 970 + 208 |
| `native/engine/player_oboe.cpp` | 914 |
| `native/engine/lha_jni.cpp`, `log.h`, `loudness.h`, `loudness_analysis.h`, `stereo_mix.h` | 323 |
| `native/lhasa/lha_extract.cpp`, `lha_extract.h` | 178 |
| `native/backends/asap/asap_measure.c`, `.h` | 227 |
| `native/backends/zxtune/lha_zxtune.cpp`, `uint16_char_traits.h` | 175 |
| `native/backends/minimp3/minimp3.c` | 16 |
| `native/patches/libopenmpt/0001-read-c1-text-as-cp437.patch` | 25 |
| `native/CMakeLists.txt`, `native/*/CMakeLists.txt`, generated config headers (build wiring only) | — |

**Out:** `web/` and `native/engine/player_wasm.cpp` (the website's engine host); `scripts/`;
`native/probe/` (host-side probes, not linked into the APK); `native/vendor/` and everything else
`components.tsv` lists as third-party, except at the boundary; `store/`, `user/`, `dist/`,
`artwork/`.

### Legend

**Severity of a bug**

| level | means |
| --- | --- |
| **Critical** | loss or corruption of user data; a vulnerability reachable through remote content or another app; a crash at start-up or on the main playback path with ordinary input |
| **High** | a crash, freeze or ANR reachable in normal use or through a plausibly malformed file; wrong behaviour in a core function; a leak that degrades a session; a broken migration path |
| **Medium** | wrong behaviour in a secondary function or under uncommon conditions; an error swallowed silently; an inconsistency the app recovers from |
| **Low** | cosmetic; an edge case of negligible effect; a latent defect not reachable today, with the reason |

**Likelihood:** *common* · *plausible* · *rare* — how readily the trigger occurs.

**Evidence**

| state | means |
| --- | --- |
| **verified** | it was run, and the finding says what was run and what it showed |
| **partially verified** | a property was confirmed, not the behaviour |
| **unverified** | concluded from reading alone |

Anything about the screen, audio output or behaviour on a device is unverified by definition:
this workshop has no emulator and no device.

**Bug or observation.** A bug has a concrete input or state that produces a wrong outcome in the
code as it is. Fragility that has not broken yet, and anything whose mechanism could not be
established, is an observation.

**Identifiers:** `RV-B###` bugs, `RV-O###` observations, `RV-S###` suggestions, `RV-I###` ideas.
The prefix `RV-` was checked against the identifiers the project documents already use (A, B, C,
D, E, F, Q, R, S, W, MP, CP, CC, CX, ID) and collides with none.

## 2. Summary

*Written last.*

## 3. Architecture as built

*Stage 1.*

## 4. Bugs

### 4.1 Critical

### 4.2 High

### 4.3 Medium

### 4.4 Low

## 5. Observations

### 5.1 Duplication

### 5.2 Brittleness

### 5.3 Inconsistency

### 5.4 Dead code

### 5.5 Complexity

### 5.6 Traces of lost context

### 5.7 Documentation drift

### 5.8 Performance

## 6. Suggestions

### 6.1 Architectural

### 6.2 Local

## 7. Ideas

### 7.1 Features

### 7.2 Other

## 8. Tests

*Stage 4.*

## 9. Questions

## 10. Coverage ledger

Status is one of: **not read** · **read — findings** · **read — nothing found** · **read in part**
(with what was skipped and why) · **out of scope**. "Nothing found" is written only for a file that
was read in full.

### 10.1 By area

| area | files | lines | status |
| --- | --- | --- | --- |
| native engine and JNI (`native/engine`, `native/lhasa`, own backend glue) | 15 | 5 036 | not read |
| `engine/` (Kotlin side of the native boundary) | 5 | 598 | not read |
| `player/` | 104 | 13 816 | not read |
| `data/` | 25 | 3 652 | not read |
| `net/` | 16 | 2 016 | not read |
| `alarms/` | 34 | 3 163 | not read |
| `permissions/` | 2 | 169 | not read |
| `ui/` | 64 | 12 864 | not read |
| app root (`MainActivity`, theme, locale) | 4 | 412 | not read |
| manifest, resources, build configuration, schemas | — | — | not read |
| tests (`app/src/test`), secondary | 266 | 32 067 | not read |

### 10.2 By file

| file | lines | status | findings |
| --- | --- | --- | --- |
| `native/engine/engine.cpp` | 2970 | not read | |
| `native/engine/engine.h` | 208 | not read | |
| `native/engine/player_oboe.cpp` | 914 | not read | |
| `native/engine/lha_jni.cpp` | 72 | not read | |
| `native/engine/log.h` | 27 | not read | |
| `native/engine/loudness.h` | 158 | not read | |
| `native/engine/loudness_analysis.h` | 31 | not read | |
| `native/engine/stereo_mix.h` | 35 | not read | |
| `native/lhasa/lha_extract.cpp` | 143 | not read | |
| `native/lhasa/lha_extract.h` | 35 | not read | |
| `native/backends/asap/asap_measure.c` | 180 | not read | |
| `native/backends/asap/asap_measure.h` | 47 | not read | |
| `native/backends/zxtune/lha_zxtune.cpp` | 79 | not read | |
| `native/backends/zxtune/uint16_char_traits.h` | 96 | not read | |
| `native/backends/minimp3/minimp3.c` | 16 | not read | |
| `native/patches/libopenmpt/0001-read-c1-text-as-cp437.patch` | 25 | not read | |
| `engine/DescribeBlock.kt` | 50 | not read | |
| `engine/NativeData.kt` | 177 | not read | |
| `engine/NativeEngine.kt` | 305 | not read | |
| `engine/NativeLifetime.kt` | 51 | not read | |
| `engine/OutputRecovery.kt` | 15 | not read | |
| `player/ActiveQueue.kt` | 103 | not read | |
| `player/AndroidCatalogueStorage.kt` | 29 | not read | |
| `player/AndroidDownloadText.kt` | 67 | not read | |
| `player/AndroidFolderAccess.kt` | 18 | not read | |
| `player/AndroidMaintenanceFiles.kt` | 23 | not read | |
| `player/AndroidMaintenanceText.kt` | 32 | not read | |
| `player/AndroidPlaylistFiles.kt` | 35 | not read | |
| `player/AndroidPlaylistText.kt` | 30 | not read | |
| `player/AndroidTrackFileAccess.kt` | 60 | not read | |
| `player/AudioExport.kt` | 63 | not read | |
| `player/AudioFocus.kt` | 116 | not read | |
| `player/BarLength.kt` | 26 | not read | |
| `player/BrowseCache.kt` | 93 | not read | |
| `player/BrowseFilter.kt` | 78 | not read | |
| `player/BrowseNavigation.kt` | 78 | not read | |
| `player/BrowseReadOwner.kt` | 39 | not read | |
| `player/BrowseState.kt` | 266 | not read | |
| `player/BrowseWorkflow.kt` | 337 | not read | |
| `player/CatalogueBrowseState.kt` | 109 | not read | |
| `player/CatalogueBrowser.kt` | 203 | not read | |
| `player/CatalogueTracks.kt` | 32 | not read | |
| `player/Companions.kt` | 50 | not read | |
| `player/DownloadKeys.kt` | 38 | not read | |
| `player/DownloadPlan.kt` | 82 | not read | |
| `player/DownloadSizes.kt` | 46 | not read | |
| `player/DownloadText.kt` | 33 | not read | |
| `player/FallbackLength.kt` | 65 | not read | |
| `player/FloatingControls.kt` | 63 | not read | |
| `player/FloatingControlsWindow.kt` | 367 | not read | |
| `player/FloatingPanelDrag.kt` | 40 | not read | |
| `player/FloatingPanelPosition.kt` | 49 | not read | |
| `player/FolderPrefetch.kt` | 118 | not read | |
| `player/HistoryBrowser.kt` | 90 | not read | |
| `player/HistoryFilter.kt` | 31 | not read | |
| `player/HistoryPages.kt` | 32 | not read | |
| `player/HistoryPresentation.kt` | 25 | not read | |
| `player/HistoryPresentationReads.kt` | 49 | not read | |
| `player/HistoryReads.kt` | 106 | not read | |
| `player/HistoryRecording.kt` | 9 | not read | |
| `player/HistoryWrites.kt` | 80 | not read | |
| `player/IncrementalScan.kt` | 47 | not read | |
| `player/LengthSource.kt` | 73 | not read | |
| `player/LibraryDownloads.kt` | 424 | not read | |
| `player/LibraryMaintenance.kt` | 206 | not read | |
| `player/LibraryMaintenanceState.kt` | 87 | not read | |
| `player/LibrarySearch.kt` | 122 | not read | |
| `player/ListeningSession.kt` | 57 | not read | |
| `player/LocalFolderBrowser.kt` | 125 | not read | |
| `player/LocalTree.kt` | 53 | not read | |
| `player/LoudnessPreparation.kt` | 49 | not read | |
| `player/LoudnessProfiles.kt` | 80 | not read | |
| `player/M4aWriter.kt` | 108 | not read | |
| `player/MediaScanner.kt` | 193 | not read | |
| `player/Message.kt` | 19 | not read | |
| `player/NowPlayingText.kt` | 21 | not read | |
| `player/OnPhone.kt` | 50 | not read | |
| `player/OpenFailure.kt` | 113 | not read | |
| `player/PcmExport.kt` | 74 | not read | |
| `player/PickedFiles.kt` | 27 | not read | |
| `player/Platforms.kt` | 310 | not read | |
| `player/PlayFromEnd.kt` | 41 | not read | |
| `player/PlayQueue.kt` | 352 | not read | |
| `player/PlaybackController.kt` | 3825 | not read | |
| `player/PlaybackMediaSession.kt` | 21 | not read | |
| `player/PlaybackMetadata.kt` | 22 | not read | |
| `player/PlaybackPlan.kt` | 32 | not read | |
| `player/PlaybackPresentation.kt` | 111 | not read | |
| `player/PlaybackReadAhead.kt` | 91 | not read | |
| `player/PlaybackSchedule.kt` | 138 | not read | |
| `player/PlaybackService.kt` | 292 | not read | |
| `player/PlayerUiState.kt` | 206 | not read | |
| `player/PlayerViewModel.kt` | 259 | not read | |
| `player/PlaylistHeart.kt` | 70 | not read | |
| `player/PlaylistHeartActions.kt` | 73 | not read | |
| `player/PlaylistSearch.kt` | 19 | not read | |
| `player/PlaylistSession.kt` | 309 | not read | |
| `player/PlaylistSessionState.kt` | 142 | not read | |
| `player/PlaylistTransfer.kt` | 162 | not read | |
| `player/QueueLink.kt` | 278 | not read | |
| `player/QueueSourceNavigation.kt` | 44 | not read | |
| `player/RandomHistory.kt` | 96 | not read | |
| `player/RandomScope.kt` | 74 | not read | |
| `player/RecentSearches.kt` | 41 | not read | |
| `player/ReleaseYear.kt` | 70 | not read | |
| `player/ReplayDownloads.kt` | 98 | not read | |
| `player/SavedPlace.kt` | 38 | not read | |
| `player/SavedPlaces.kt` | 126 | not read | |
| `player/ScanBatch.kt` | 115 | not read | |
| `player/ScanNotification.kt` | 82 | not read | |
| `player/SearchResults.kt` | 85 | not read | |
| `player/SearchScope.kt` | 60 | not read | |
| `player/SeekProgress.kt` | 26 | not read | |
| `player/SelectionShare.kt` | 47 | not read | |
| `player/SessionSource.kt` | 57 | not read | |
| `player/StereoMix.kt` | 15 | not read | |
| `player/StoreDownloadSources.kt` | 54 | not read | |
| `player/SubsongAdvance.kt` | 55 | not read | |
| `player/SubsongNormalization.kt` | 64 | not read | |
| `player/SupportedFormats.kt` | 260 | not read | |
| `player/TrackArtwork.kt` | 155 | not read | |
| `player/TrackArtworkBitmaps.kt` | 76 | not read | |
| `player/TrackEditing.kt` | 41 | not read | |
| `player/TrackFiles.kt` | 137 | not read | |
| `player/TunesFound.kt` | 9 | not read | |
| `data/CatalogueStore.kt` | 644 | not read | |
| `data/DatabaseAccess.kt` | 37 | not read | |
| `data/DatabasePreparation.kt` | 41 | not read | |
| `data/FavouriteStore.kt` | 89 | not read | |
| `data/HistoryStore.kt` | 159 | not read | |
| `data/LearnedLengthStore.kt` | 53 | not read | |
| `data/LegalText.kt` | 76 | not read | |
| `data/LibraryIndexStore.kt` | 288 | not read | |
| `data/LibraryStore.kt` | 291 | not read | |
| `data/Md5.kt` | 19 | not read | |
| `data/ModlandFavourites.kt` | 65 | not read | |
| `data/MusicSources.kt` | 38 | not read | |
| `data/OpenSourceNotices.kt` | 60 | not read | |
| `data/PlaylistFile.kt` | 163 | not read | |
| `data/ProtracktorDatabase.kt` | 107 | not read | |
| `data/RecentSearchPreferences.kt` | 28 | not read | |
| `data/SavedPlacesStore.kt` | 43 | not read | |
| `data/SchemaSql.kt` | 683 | not read | |
| `data/SearchTerms.kt` | 141 | not read | |
| `data/SongDbLengthStore.kt` | 89 | not read | |
| `data/SongDbLengths.kt` | 152 | not read | |
| `data/SongDbMetadata.kt` | 99 | not read | |
| `data/SongLengthStore.kt` | 85 | not read | |
| `data/SongLengths.kt` | 85 | not read | |
| `data/TrackMetadataStore.kt` | 117 | not read | |
| `net/Amp.kt` | 148 | not read | |
| `net/AmpAccess.kt` | 35 | not read | |
| `net/AmpModuleIds.kt` | 115 | not read | |
| `net/AtomicFileWrite.kt` | 23 | not read | |
| `net/CacheBudget.kt` | 121 | not read | |
| `net/Catalogue.kt` | 291 | not read | |
| `net/CatalogueHttp.kt` | 9 | not read | |
| `net/CatalogueRetention.kt` | 11 | not read | |
| `net/ModArchive.kt` | 199 | not read | |
| `net/RemoteFileCache.kt` | 46 | not read | |
| `net/RemoteFiles.kt` | 282 | not read | |
| `net/Sc68Replays.kt` | 108 | not read | |
| `net/SharedFetches.kt` | 50 | not read | |
| `net/UadePlayers.kt` | 213 | not read | |
| `net/UnExoticA.kt` | 190 | not read | |
| `net/WebRemote.kt` | 175 | not read | |
| `alarms/AlarmCodec.kt` | 103 | not read | |
| `alarms/AlarmCoordinator.kt` | 148 | not read | |
| `alarms/AlarmFeatureLifecycle.kt` | 55 | not read | |
| `alarms/AlarmFileStore.kt` | 67 | not read | |
| `alarms/AlarmManagement.kt` | 536 | not read | |
| `alarms/AlarmManagementViewModel.kt` | 114 | not read | |
| `alarms/AlarmMetadata.kt` | 37 | not read | |
| `alarms/AlarmModels.kt` | 141 | not read | |
| `alarms/AlarmMusicPicker.kt` | 194 | not read | |
| `alarms/AlarmMusicPreparer.kt` | 96 | not read | |
| `alarms/AlarmMusicQueue.kt` | 73 | not read | |
| `alarms/AlarmMusicSource.kt` | 66 | not read | |
| `alarms/AlarmNotifications.kt` | 62 | not read | |
| `alarms/AlarmPlanner.kt` | 49 | not read | |
| `alarms/AlarmPlatform.kt` | 24 | not read | |
| `alarms/AlarmPlaybackObservation.kt` | 64 | not read | |
| `alarms/AlarmQuizFactory.kt` | 51 | not read | |
| `alarms/AlarmReadiness.kt` | 52 | not read | |
| `alarms/AlarmReceiver.kt` | 48 | not read | |
| `alarms/AlarmRepository.kt` | 62 | not read | |
| `alarms/AlarmResults.kt` | 15 | not read | |
| `alarms/AlarmRingingActivity.kt` | 49 | not read | |
| `alarms/AlarmRingingScreen.kt` | 202 | not read | |
| `alarms/AlarmRingingViewModel.kt` | 107 | not read | |
| `alarms/AlarmRules.kt` | 132 | not read | |
| `alarms/AlarmService.kt` | 192 | not read | |
| `alarms/AlarmSound.kt` | 96 | not read | |
| `alarms/AlarmSourcePolicy.kt` | 17 | not read | |
| `alarms/AlarmSummaries.kt` | 43 | not read | |
| `alarms/AlarmTimeLabels.kt` | 34 | not read | |
| `alarms/AlarmsActivity.kt` | 82 | not read | |
| `alarms/AndroidAlarmScheduler.kt` | 52 | not read | |
| `alarms/api/AlarmMusicSelection.kt` | 17 | not read | |
| `alarms/api/MusicAlarms.kt` | 83 | not read | |
| `permissions/AccessRequestCoordinator.kt` | 84 | not read | |
| `permissions/AppAccess.kt` | 85 | not read | |
| `ui/AddToPlaylistDialog.kt` | 151 | not read | |
| `ui/AlarmMenuItem.kt` | 15 | not read | |
| `ui/BrowseFilters.kt` | 164 | not read | |
| `ui/BrowseScreen.kt` | 2071 | not read | |
| `ui/BrowseScroll.kt` | 117 | not read | |
| `ui/DatabaseGate.kt` | 85 | not read | |
| `ui/DockCollapsed.kt` | 202 | not read | |
| `ui/DockEdgeSeek.kt` | 147 | not read | |
| `ui/DockMarquee.kt` | 29 | not read | |
| `ui/DownloadPicker.kt` | 211 | not read | |
| `ui/DraggableScrollbar.kt` | 160 | not read | |
| `ui/FeedbackRow.kt` | 84 | not read | |
| `ui/FloatingControlsAction.kt` | 105 | not read | |
| `ui/FloatingControlsPanel.kt` | 140 | not read | |
| `ui/FloatingDismissTarget.kt` | 74 | not read | |
| `ui/FloatingDragContainer.kt` | 90 | not read | |
| `ui/Haptics.kt` | 174 | not read | |
| `ui/HistoryScroll.kt` | 25 | not read | |
| `ui/HoldToConfirm.kt` | 157 | not read | |
| `ui/LabelledAction.kt` | 328 | not read | |
| `ui/LegalScreens.kt` | 234 | not read | |
| `ui/ListScrolling.kt` | 123 | not read | |
| `ui/ManualQueueReorder.kt` | 157 | not read | |
| `ui/MenuFocus.kt` | 70 | not read | |
| `ui/MessageSnackbar.kt` | 33 | not read | |
| `ui/NavSlots.kt` | 86 | not read | |
| `ui/NavSlotsSetting.kt` | 131 | not read | |
| `ui/NowPlaying.kt` | 278 | not read | |
| `ui/PlayedBefore.kt` | 38 | not read | |
| `ui/PlayerDock.kt` | 601 | not read | |
| `ui/PlayerIcons.kt` | 493 | not read | |
| `ui/PlaylistScreen.kt` | 785 | not read | |
| `ui/PlaylistSearchHeader.kt` | 156 | not read | |
| `ui/PlaylistSwitcher.kt` | 297 | not read | |
| `ui/PreparingDatabase.kt` | 75 | not read | |
| `ui/ProtracktorApp.kt` | 1092 | not read | |
| `ui/QueueActions.kt` | 20 | not read | |
| `ui/QueueCount.kt` | 52 | not read | |
| `ui/QueueHelp.kt` | 105 | not read | |
| `ui/QueueScreen.kt` | 161 | not read | |
| `ui/QueueSwipeIntent.kt` | 17 | not read | |
| `ui/QueueSwipeRow.kt` | 172 | not read | |
| `ui/RandomDice.kt` | 56 | not read | |
| `ui/RandomFilterState.kt` | 88 | not read | |
| `ui/RandomScreen.kt` | 220 | not read | |
| `ui/SavedPlacesScreen.kt` | 123 | not read | |
| `ui/ScannerScreen.kt` | 203 | not read | |
| `ui/ScrollingActionRow.kt` | 129 | not read | |
| `ui/SearchQueryField.kt` | 99 | not read | |
| `ui/SearchScopePanel.kt` | 260 | not read | |
| `ui/SeekBar.kt` | 271 | not read | |
| `ui/SettingChoice.kt` | 94 | not read | |
| `ui/SettingsScreen.kt` | 557 | not read | |
| `ui/ShareMenu.kt` | 120 | not read | |
| `ui/ShellNavigation.kt` | 170 | not read | |
| `ui/ShellState.kt` | 133 | not read | |
| `ui/StereoMixSetting.kt` | 39 | not read | |
| `ui/StorageSection.kt` | 210 | not read | |
| `ui/SubsongStrip.kt` | 144 | not read | |
| `ui/SwipeableSnackbar.kt` | 81 | not read | |
| `ui/TrackInfo.kt` | 64 | not read | |
| `ui/theme/LightColors.kt` | 41 | not read | |
| `ui/theme/SystemBars.kt` | 21 | not read | |
| `ui/theme/Theme.kt` | 36 | not read | |
| `AppLanguage.kt` | 17 | not read | |
| `AppLocale.kt` | 42 | not read | |
| `AppTheme.kt` | 220 | not read | |
| `MainActivity.kt` | 133 | not read | |
| `app/src/main/AndroidManifest.xml` | 437 | not read | |
| `app/build.gradle.kts` | 427 | not read | |
| `app/proguard-rules.pro` | 15 | not read | |
| `gradle/libs.versions.toml` | 49 | not read | |
| `app/src/main/res/xml/data_extraction_rules.xml` | 21 | not read | |
| `app/src/main/res/xml/file_paths.xml` | 11 | not read | |
| `app/src/main/res/xml/network_security_config.xml` | 21 | not read | |
| `app/src/main/res/values/strings.xml` | 773 | not read | |
| `app/src/main/res/values-pl/strings.xml` | 816 | not read | |

## 11. Dismissed suspicions

## 12. Working notes

*Open threads to follow in areas not read yet. Empty at completion.*
