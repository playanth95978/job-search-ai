// Show an error message if the app loading takes more than 4 seconds.
//
// Kept in its own file rather than inline so the Content-Security-Policy can drop
// script-src 'unsafe-inline'. See content/js/theme-init.js for the same reasoning.
window.onload = function () {
  setTimeout(function () {
    var errorElm = document.getElementById('jhipster-error');
    if (errorElm && errorElm.style) {
      errorElm.style.display = 'block';
    }
  }, 4000);
};
