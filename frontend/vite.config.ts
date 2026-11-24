/// <reference types="vitest/config" />
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    // Forward API calls to the Spring Boot backend, so the browser sees one origin (no CORS setup needed).
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  test: {
    // Unit and component tests (Vitest). End-to-end tests live in e2e/ and run with Playwright.
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
    restoreMocks: true,
  },
})
