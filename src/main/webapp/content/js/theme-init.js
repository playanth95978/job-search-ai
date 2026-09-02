// Set the theme before first paint to avoid a flash of the wrong theme (FOUC).
//
// Kept in its own file, and loaded from <head> without defer/async, so it still runs before the
// first paint while letting the Content-Security-Policy drop script-src 'unsafe-inline'. Inlining
// it would force the policy to allow every inline script on every page.
(function () {
  try {
    var t = localStorage.getItem('jsa.theme');
    if (t !== 'light' && t !== 'dark') {
      t = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
    }
    document.documentElement.setAttribute('data-bs-theme', t);
  } catch (e) {
    /* storage unavailable (private mode, blocked cookies): keep the default theme */
  }
})();
