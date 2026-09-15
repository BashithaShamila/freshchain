// Placeholder for local `npm run dev`. In the container this file is rewritten
// by docker-entrypoint.sh from environment variables. When the values are empty
// the app falls back to Vite's own VITE_* variables, so dev needs no edits here.
window.__FRESHCHAIN_CONFIG__ = {
  asgardeoBaseUrl: "",
  asgardeoClientId: "",
  apiBaseUrl: "",
  appBaseUrl: "",
};
