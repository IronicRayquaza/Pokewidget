import { defineConfig } from 'vite';
// Adjust this plugin import based on your actual framework (e.g., @vitejs/plugin-vue for Vue, @sveltejs/vite-plugin-svelte for Svelte)
import react from '@vitejs/plugin-react'; // Assuming React based on common usage
import path from 'path';

export default defineConfig({
  plugins: [
    // Ensure your framework plugin is listed here.
    // For example:
    react(),
    // Add other plugins if you have them
  ],
  resolve: {
    // This 'alias' configuration is crucial for resolving modules imported
    // using path aliases (e.g., '@/') that are defined in your tsconfig.json.
    // If the build is crashing without a clear error, especially on a new feature branch
    // that uses aliased imports, this is a very common fix.
    //
    // Make sure this alias matches your 'paths' configuration in tsconfig.json.
    // If you have other aliases (e.g., '@components', '@utils'), ensure they are also included here.
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  build: {
    // Optional: You can add or adjust other build options here.
    // For instance, if you get 'chunk size warning' messages, you might increase the limit:
    // chunkSizeWarningLimit: 1000, // 1000 KB (1 MB)
  },
  // Include any other existing top-level configurations (e.g., `server`, `css`, `define`) here
});
