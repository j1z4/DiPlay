import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// A relative base and hash routes let the build run from any folder, including GitHub Pages.
export default defineConfig({
  base: "./",
  plugins: [react()],
  build: {
    chunkSizeWarningLimit: 800,
    rollupOptions: {
      output: {
        // Libraries change less often than the site, so they cache across releases. One vendor
        // chunk: splitting React from Chakra creates a load-order cycle between the chunks.
        manualChunks: (id) => (id.includes("node_modules") ? "vendor" : undefined),
      },
    },
  },
});
