import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // Dev-server convenience so `npm run dev` talks to the stack in Docker
    // without CORS. The container image proxies /api through nginx instead.
    proxy: {
      "/api": {
        target: process.env.VITE_GATEWAY_URL ?? "http://localhost:18080",
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: "dist",
    sourcemap: true,
  },
});
