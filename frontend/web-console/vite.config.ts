import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import Components from 'unplugin-vue-components/vite';
import AutoImport from 'unplugin-auto-import/vite';
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers';

export default defineConfig({
  plugins: [
    vue(),
    AutoImport({
      resolvers: [ElementPlusResolver()]
    }),
    Components({
      resolvers: [ElementPlusResolver()]
    })
  ],
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          vue: ['vue', 'vue-router', 'pinia', '@tanstack/vue-query'],
          http: ['axios']
        }
      }
    }
  },
  server: {
    host: '127.0.0.1',
    port: 5173,
    proxy: process.env.VITE_API_PROXY_TARGET
      ? {
          '/api/v1': {
            target: process.env.VITE_API_PROXY_TARGET,
            changeOrigin: true
          }
        }
      : undefined
  }
});
