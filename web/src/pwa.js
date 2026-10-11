// SPDX-FileCopyrightText: 2026 Przunk
// SPDX-License-Identifier: GPL-3.0-or-later

// Keep the system bar in step with Settings and the system theme, including the first frame.
const themeColour = document.querySelector('meta[name="theme-color"]');
function syncThemeColour() {
  themeColour.content = getComputedStyle(document.body).backgroundColor;
}
syncThemeColour();
new MutationObserver(syncThemeColour).observe(document.documentElement, {
  attributes: true, attributeFilter: ['data-theme'],
});
matchMedia('(prefers-color-scheme: dark)').addEventListener('change', syncThemeColour);

// The worker belongs above src/, because the engine and QR library are its siblings.
// Relative URLs also keep this installation inside /Protracktor/ on GitHub Pages.
if (isSecureContext && 'serviceWorker' in navigator) {
  const register = () => navigator.serviceWorker.register(new URL('../sw.js', import.meta.url), {
    scope: new URL('../', import.meta.url).href,
    updateViaCache: 'none',
  }).catch((error) => console.warn('Offline startup is unavailable:', error));
  if (document.readyState === 'complete') register();
  else addEventListener('load', register, { once: true });
}
