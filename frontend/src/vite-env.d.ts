/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_ASGARDEO_BASE_URL?: string;
  readonly VITE_ASGARDEO_CLIENT_ID?: string;
  readonly VITE_API_BASE_URL?: string;
  readonly VITE_APP_BASE_URL?: string;
  readonly VITE_WAREHOUSE_ID?: string;
  readonly VITE_GATEWAY_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
