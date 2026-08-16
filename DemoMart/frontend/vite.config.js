import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  // Relative asset URLs let the same build run at / locally and /demomart/ on EC2.
  base: './',
  plugins: [react()],
  server: {
    port: 5174,
    proxy: {
      '/api': 'http://localhost:8090'
    }
  }
})
