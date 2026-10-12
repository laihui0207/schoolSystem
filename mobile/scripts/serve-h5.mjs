// 本地预览 H5 构建产物：node scripts/serve-h5.mjs [port]  （默认 4173，目录 dist/build/h5）
import { createServer } from 'node:http'
import { readFile, stat } from 'node:fs/promises'
import { dirname, extname, join, resolve, sep } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..', 'dist', 'build', 'h5')
const port = Number(process.argv[2] ?? 4173)
const types = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript',
  '.css': 'text/css',
  '.png': 'image/png',
  '.json': 'application/json',
  '.svg': 'image/svg+xml',
  '.woff2': 'font/woff2',
  '.ttf': 'font/ttf'
}

const safeJoin = (pathname) => {
  const target = resolve(root, '.' + decodeURIComponent(pathname))
  return target === root || target.startsWith(root + sep) ? target : null
}

createServer(async (req, res) => {
  const url = new URL(req.url ?? '/', 'http://localhost')
  const file = safeJoin(url.pathname === '/' ? '/index.html' : url.pathname)
  if (!file) {
    res.writeHead(403)
    res.end('forbidden')
    return
  }
  try {
    const s = await stat(file)
    const target = s.isDirectory() ? join(file, 'index.html') : file
    const body = await readFile(target)
    res.writeHead(200, { 'Content-Type': types[extname(target)] ?? 'application/octet-stream' })
    res.end(body)
  } catch {
    res.writeHead(404)
    res.end('not found')
  }
}).listen(port, () => process.stdout.write(`serving ${root} on http://localhost:${port}\n`))
