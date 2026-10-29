import { defineConfig } from 'vite';
import preact from '@preact/preset-vite';

export default defineConfig({
  plugins: [preact()],
  build: {
    target: 'es2022',
    // Geen inline scripts of data-URL's: de CSP in public/_headers staat alleen eigen bestanden toe.
    assetsInlineLimit: 0,
  },
  test: {
    include: ['src/**/*.test.ts'],
  },
});
