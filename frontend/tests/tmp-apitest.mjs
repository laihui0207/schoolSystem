// Temporary comprehensive API functional test. Exercises every endpoint with
// real seeded IDs. Delete after testing.
const BASE = 'http://localhost:8080/api/v1'
const TOKEN = process.env.TOKEN
const fs = await import('node:fs')
const ids = JSON.parse(fs.readFileSync('/tmp/ids.json', 'utf8'))
const S = Date.now().toString().slice(-6)

const results = []

async function t(label, method, path, { body, query, expectBlob } = {}) {
  const url = BASE + path + (query ? '?' + new URLSearchParams(query) : '')
  let http = 0, code, msg, data, raw = ''
  try {
    const res = await fetch(url, {
      method,
      headers: { Authorization: `Bearer ${TOKEN}`, 'Content-Type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body)
    })
    http = res.status
    const ct = res.headers.get('content-type') || ''
    if (expectBlob) {
      const buf = Buffer.from(await res.arrayBuffer())
      raw = `<${buf.length} bytes, ${ct}>`
      // a real file download: non-JSON content-type and non-trivial size
      const ok = http === 200 && !ct.includes('application/json') && buf.length > 100
      results.push({ label, method, path, http, ok, msg: raw })
      console.log(`${ok ? 'OK  ' : 'FAIL'} ${method} ${path} -> ${http} ${raw}`)
      return null
    }
    raw = await res.text()
    try { const j = JSON.parse(raw); code = j.code; msg = j.message; data = j.data } catch {}
  } catch (e) {
    msg = 'FETCH_ERR ' + e.message
  }
  const ok = http === 200 && (code === 200 || code === 0)
  results.push({ label, method, path, http, code, ok, msg })
  console.log(`${ok ? 'OK  ' : 'FAIL'} ${method} ${path} -> http=${http} code=${code}${ok ? '' : ' :: ' + (msg || raw?.slice(0, 150))}`)
  return data
}

console.log('======== A. FIX PREVIOUSLY-FAILED WRITES ========')
await t('create notification', 'POST', '/notifications', {
  body: { userId: 4, title: `测试通知${S}`, content: '功能测试通知', type: 3 }
})
const newUser = await t('create user', 'POST', '/users', {
  body: {
    username: `testuser${S}`, password: 'Test@123456', realName: '测试用户',
    role: 'teacher', schoolId: ids.schoolId, phone: '13900139001', status: 1
  }
})
ids.userId = newUser?.id
await t('create dict data', 'POST', '/dict/data', {
  body: { dictType: `test_dict_${S}`, dictLabel: '选项A', dictValue: 'A', dictSort: 1, status: 1 }
})
