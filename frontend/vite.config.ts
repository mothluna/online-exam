import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import { existsSync, readdirSync } from 'node:fs'
import { fileURLToPath, URL } from 'node:url'

/**
 * 枚举 element-plus 所有组件的 style/css 入口。
 *
 * 为什么需要：按需引入后，各 el-* 组件的样式是在"第一次打开某个页面"时才由解析器注入的。
 * 不预声明的话 Vite 会在开发中途发现新依赖并触发二次优化，而二次优化要先删掉旧的 deps 目录
 * （按需引入后有 50+ 个 style 模块），在本机安全策略下会被判为批量删除、直接中断 dev 进程。
 *
 * 不能用 `element-plus/es/components/*​/style/css` 这种 glob：Vite 的 include 用 fast-glob 相对项目根
 * 展开，默认不进入 node_modules。所以这里直接读目录生成，新增组件时自动覆盖，不需要手工维护清单。
 */
function elementPlusStyleEntries(): string[] {
  const componentsDir = fileURLToPath(
    new URL('./node_modules/element-plus/es/components', import.meta.url),
  )
  if (!existsSync(componentsDir)) {
    return []
  }
  return readdirSync(componentsDir, { withFileTypes: true })
    .filter((entry) => entry.isDirectory() && existsSync(`${componentsDir}/${entry.name}/style/css.mjs`))
    .map((entry) => `element-plus/es/components/${entry.name}/style/css`)
}

export default defineConfig({
  plugins: [
    vue(),
    // 模板里的 el-* 组件按需解析并自动引入对应样式，不再全量注册
    Components({
      resolvers: [ElementPlusResolver()],
      dts: 'src/components.d.ts',
    }),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  optimizeDeps: {
    include: elementPlusStyleEntries(),
  },
  server: {
    port: 5173,
    // 开发期代理到后端，避免跨域；生产由 Nginx 转发同样的 /api 前缀
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  // 预览构建产物时同样需要代理，否则 /api 会打到 4173 自己身上
  preview: {
    port: 4173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    chunkSizeWarningLimit: 1500,
  },
})
