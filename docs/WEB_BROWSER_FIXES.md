# WEB browser findings: C126–C129

Selected: **2026-10-31**. Branch: `fix/web-browser-polish`, from visual-tooling branch head
`97669fd`. The explicit repair request follows the
[real-browser visual review](WEB_VISUAL_REVIEW.md). Keep the tooling branch as the parent so the
same production-page capture path proves the fixes. Do not merge or publish without acceptance.

## Scope and sequence

1. **C126:** preserve the native Window receiver for History's default debounce/cancel timers.
   Retain the 200 ms policy, latest-query ownership, paging, playback context and injectable test
   clocks. Cover rapid typing, clearing and leaving during debounce through actual browser input.
2. **C127:** give Search scope controls bounded themed icons and readable text, including
   catalogue/platform choices, selected/disabled states and keyboard focus. Keep scope semantics,
   result ordering and recent-query suggestions unchanged.
3. **C128:** share Browse's Back styling with Random's destination header without changing either
   navigation action or introducing another dice control.
4. **C129:** cover the scrolling Settings sheet's top inset with its sticky header. Retain initial
   spacing, Close reachability, rounded clipping and desktop/phone layouts.

Each fix is a separate commit with its corresponding regression guard. Reproduce failure before
the correction, exercise the production path, then compare actual rendered images. UI tests must
check rendered state or hit/paint coverage, not just CSS strings. Run the five-profile matrix,
inspect its overview/original images and run the complete project gate/lint before handoff.
No APK, release-version change, engine rebuild, AMP/slider integration or Bluetooth claim is part
of this scope. Record exact source and checked/unchecked states at each checkpoint.

The baseline defects and original images remain recorded in
[STATUS](STATUS.md#c126--web-history-search-throws-when-using-the-native-browser-timer).
Implementation and verification results follow below as each stage completes.

## C126 focused checkpoint — 2026-10-31

Default timer adapters now call the global timer owner explicitly; injected clocks and the
200 ms/latest-query policy are unchanged. A new host regression fails before correction and all
**31** History filter/ownership cases pass afterward. The actual Chromium phone-input regression
also fails before correction (rapid edits/clear/departure produce native receiver errors) and
passes after correction: one Evening match, eight restored rows after clearing, cancellation on
departure and no page errors. The eighteen-view phone capture exits 0. This is focused host and
headless-browser evidence from the working tree, not a full gate, phone test or publication.

## C127 focused checkpoint — 2026-10-31

Search and Random now share basic chip/disabled/focus styling. Search category tiles use the
existing action palette, 24 px icons above labels and a common top alignment even when captions
wrap. Catalogue/platform sub-options retain compact 16 px icons and their existing pressed and
availability semantics. Three real-browser appearance/state guards fail before correction and
pass afterward, alongside both C126 cases. The original phone Search PNG has been inspected;
the complete viewport/theme matrix and full gate remain pending.

## C128 focused checkpoint — 2026-10-31

The shared destination-header Back rule now also covers Random. Its toolbar uses the same 16 px
side inset as Browse. The actual browser style/hit-target comparison fails before correction and
passes after it, together with all five C126/C127 browser checks. Navigation callbacks, captions
and the single dice are unchanged. The complete matrix/full gate remain pending.
