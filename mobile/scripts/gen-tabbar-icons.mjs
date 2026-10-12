// 生成 tabBar 图标（纯 Node，无第三方依赖）：81x81 PNG，简单几何图形。
// 用法：node scripts/gen-tabbar-icons.mjs   （已生成的文件已提交，一般不需要重跑）
import { deflateSync } from 'node:zlib'
import { mkdirSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const SIZE = 81
const OUT_DIR = join(dirname(fileURLToPath(import.meta.url)), '..', 'src', 'static', 'tabbar')
const GRAY = [0x8a, 0x8f, 0x99]
const BLUE = [0x2f, 0x6f, 0xed]

const crcTable = Array.from({ length: 256 }, (_, n) => {
  let c = n
  for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1
  return c >>> 0
})

const crc32 = (buf) => {
  let c = 0xffffffff
  for (const b of buf) c = crcTable[(c ^ b) & 0xff] ^ (c >>> 8)
  return (c ^ 0xffffffff) >>> 0
}

const chunk = (type, data) => {
  const len = Buffer.alloc(4)
  len.writeUInt32BE(data.length)
  const typeBuf = Buffer.from(type, 'ascii')
  const crc = Buffer.alloc(4)
  crc.writeUInt32BE(crc32(Buffer.concat([typeBuf, data])))
  return Buffer.concat([len, typeBuf, data, crc])
}

const encodePng = (pixels) => {
  const raw = Buffer.alloc((SIZE * 4 + 1) * SIZE)
  for (let y = 0; y < SIZE; y++) {
    raw[y * (SIZE * 4 + 1)] = 0
    for (let x = 0; x < SIZE; x++) {
      const [r, g, b, a] = pixels[y * SIZE + x]
      const o = y * (SIZE * 4 + 1) + 1 + x * 4
      raw[o] = r
      raw[o + 1] = g
      raw[o + 2] = b
      raw[o + 3] = a
    }
  }
  const ihdr = Buffer.alloc(13)
  ihdr.writeUInt32BE(SIZE, 0)
  ihdr.writeUInt32BE(SIZE, 4)
  ihdr[8] = 8
  ihdr[9] = 6
  ihdr[10] = 0
  ihdr[11] = 0
  ihdr[12] = 0
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr),
    chunk('IDAT', deflateSync(raw)),
    chunk('IEND', Buffer.alloc(0))
  ])
}

/** shape(x, y) → 0..1 覆盖率；用 2x2 超采样做简单抗锯齿 */
const render = (shape, color) => {
  const pixels = new Array(SIZE * SIZE)
  for (let y = 0; y < SIZE; y++) {
    for (let x = 0; x < SIZE; x++) {
      let hits = 0
      for (const dy of [0.25, 0.75]) for (const dx of [0.25, 0.75]) if (shape(x + dx, y + dy)) hits++
      pixels[y * SIZE + x] = [...color, Math.round((hits / 4) * 255)]
    }
  }
  return pixels
}

const inRect = (x, y, x0, y0, x1, y1) => x >= x0 && x <= x1 && y >= y0 && y <= y1
const inRing = (x, y, cx, cy, rOuter, rInner) => {
  const d = Math.hypot(x - cx, y - cy)
  return d <= rOuter && d >= rInner
}
const inCircle = (x, y, cx, cy, r) => Math.hypot(x - cx, y - cy) <= r
const stroke = 6

// 首页：房子轮廓（屋顶三角 + 方形墙体，描边）
const home = (x, y) => {
  const roof = (px, py) => py >= 14 + Math.abs(px - 40.5) * 0.75 && py <= 40
  const inRoofOuter = roof(x, y)
  const inRoofInner = y >= 14 + stroke + Math.abs(x - 40.5) * 0.75 && y <= 40
  const wallOuter = inRect(x, y, 16, 36, 65, 68)
  const wallInner = inRect(x, y, 16 + stroke, 36, 65 - stroke, 68 - stroke)
  const door = inRect(x, y, 34, 48, 47, 68)
  return (inRoofOuter && !inRoofInner) || (wallOuter && !wallInner) || door
}

// 成绩：柱状图三根柱 + 底线
const score = (x, y) =>
  inRect(x, y, 14, 44, 26, 66) || inRect(x, y, 34, 26, 46, 66) || inRect(x, y, 54, 14, 66, 66) || inRect(x, y, 10, 66, 70, 71)

// 消息：铃铛（半圆 + 梯形身 + 小圆铃舌）
const message = (x, y) => {
  const body = y >= 22 && y <= 56 && Math.abs(x - 40.5) <= 14 + (y - 22) * 0.35
  const dome = inCircle(x, y, 40.5, 26, 14) && y <= 26
  const bodyInner = y >= 22 + stroke && y <= 56 - stroke && Math.abs(x - 40.5) <= 14 - stroke + (y - 22) * 0.35
  const domeInner = inCircle(x, y, 40.5, 26, 14 - stroke) && y <= 26
  const bar = inRect(x, y, 16, 56, 65, 61)
  const clapper = inCircle(x, y, 40.5, 67, 5)
  return ((body || dome) && !(bodyInner || domeInner)) || bar || clapper
}

// 我的：头像（圆环头 + 肩部弧线）
const mine = (x, y) => {
  const head = inRing(x, y, 40.5, 28, 15, 15 - stroke)
  const shoulderOuter = inCircle(x, y, 40.5, 78, 32) && y >= 50 && y <= 70
  const shoulderInner = inCircle(x, y, 40.5, 78, 32 - stroke) && y >= 50 + stroke && y <= 70
  return head || (shoulderOuter && !shoulderInner)
}

mkdirSync(OUT_DIR, { recursive: true })
const icons = { home, score, message, mine }
for (const [name, shape] of Object.entries(icons)) {
  writeFileSync(join(OUT_DIR, `${name}.png`), encodePng(render(shape, GRAY)))
  writeFileSync(join(OUT_DIR, `${name}-active.png`), encodePng(render(shape, BLUE)))
}
process.stdout.write(`generated ${Object.keys(icons).length * 2} icons in ${OUT_DIR}\n`)
