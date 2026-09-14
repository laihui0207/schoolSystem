// Temporary functional-test seeder. Creates a full dependency chain through the
// REAL API so every write path is exercised. Delete after testing.
const BASE = 'http://localhost:8080/api/v1'
const TOKEN = process.env.TOKEN
if (!TOKEN) { console.error('no TOKEN env'); process.exit(1) }

const S = Date.now().toString().slice(-6) // unique suffix, avoids unique-key clashes
const ids = {}
const log = []

async function call(method, path, { body, query } = {}) {
  const url = BASE + path + (query ? '?' + new URLSearchParams(query) : '')
  const res = await fetch(url, {
    method,
    headers: { Authorization: `Bearer ${TOKEN}`, 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body)
  })
  const text = await res.text()
  let json = null
  try { json = JSON.parse(text) } catch { /* non-JSON (blob/template) */ }
  return { http: res.status, code: json?.code, msg: json?.message, data: json?.data, raw: text }
}

// step(): run one seeded write, record outcome, return created id if any
async function step(label, method, path, opts = {}) {
  const r = await call(method, path, opts)
  const ok = r.http === 200 && (r.code === 200 || r.code === 0)
  log.push({ label, method, path, http: r.http, code: r.code, ok, msg: r.msg })
  const mark = ok ? 'OK  ' : 'FAIL'
  console.log(`${mark} ${method} ${path} -> http=${r.http} code=${r.code}${ok ? '' : ' :: ' + (r.msg || r.raw?.slice(0, 160))}`)
  if (!ok) return null
  return r.data
}

console.log('======== SEEDING DEPENDENCY CHAIN ========')

// 1. School (the pre-existing one has status=0/disabled, so make a live one)
const school = await step('create school', 'POST', '/schools', {
  body: {
    schoolName: `测试小学${S}`, schoolCode: `TS${S}`, schoolType: '小学',
    province: '广东省', city: '深圳市', district: '南山区', address: '测试路1号',
    contactPerson: '张校长', contactPhone: '13800138001'
  }
})
ids.schoolId = school?.id
if (!ids.schoolId) { console.error('cannot continue without school'); process.exit(1) }
