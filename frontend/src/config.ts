/**
 * Application configuration.
 *
 * Values come from the runtime `config.js` the container writes at start-up, so
 * one image serves every environment. Falling back to Vite's build-time
 * variables keeps `npm run dev` working without a container.
 */
declare global {
  interface Window {
    __FRESHCHAIN_CONFIG__?: Partial<RuntimeConfig>;
  }
}

interface RuntimeConfig {
  asgardeoBaseUrl: string;
  asgardeoClientId: string;
  apiBaseUrl: string;
  appBaseUrl: string;
}

const runtime = window.__FRESHCHAIN_CONFIG__ ?? {};

function setting(fromRuntime: string | undefined, fromEnv: string | undefined, fallback = ""): string {
  return (fromRuntime && fromRuntime.length > 0 ? fromRuntime : fromEnv) ?? fallback;
}

/** e.g. https://api.asgardeo.io/t/your-org */
export const ASGARDEO_BASE_URL = setting(
  runtime.asgardeoBaseUrl,
  import.meta.env.VITE_ASGARDEO_BASE_URL,
);

export const ASGARDEO_CLIENT_ID = setting(
  runtime.asgardeoClientId,
  import.meta.env.VITE_ASGARDEO_CLIENT_ID,
);

/** Empty means same-origin, which is how the container serves it: nginx proxies /api. */
export const API_BASE_URL = setting(runtime.apiBaseUrl, import.meta.env.VITE_API_BASE_URL, "");

/** Where Asgardeo sends the browser back after sign-in. */
export const APP_BASE_URL = setting(
  runtime.appBaseUrl,
  import.meta.env.VITE_APP_BASE_URL,
  window.location.origin,
);

export const IS_CONFIGURED = ASGARDEO_BASE_URL.length > 0 && ASGARDEO_CLIENT_ID.length > 0;

export const WAREHOUSE_ID = setting(undefined, import.meta.env.VITE_WAREHOUSE_ID, "WH-COL-01");
