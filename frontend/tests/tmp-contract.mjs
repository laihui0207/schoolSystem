// Reproduce frontend payloads EXACTLY as the .vue views send them, to prove
// which forms 400 out. Node is used because Git Bash mangles UTF-8 in curl.
const BASE = 'http://localhost:8080/api/v1'
const TOKEN = process.env.TOKEN
const fs = await import('node:fs')
const ids = JSON.parse(fs.readFileSync('/tmp/ids.json', 'utf8'))

async function post(label, path, body) {
  const res = await fetch(BASE + path, {
    method: 'POST',
    headers: { Authorization: `Bearer ${TOKEN}`, 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  })
  const j = await res.json().catch(() => ({}))
  const ok = res.status === 200 && (j.code === 200 || j.code === 0)
  console.log(`${ok ? 'OK  ' : 'FAIL'} ${label} -> http=${res.status} code=${j.code} ${ok ? '' : ':: ' + j.message}`)
  return ok
}

console.log('=== class/index.vue defaultForm() shape (no classCode, no termId, teacherId not headTeacherId) ===')
await post('POST /classes as frontend sends', '/classes', {
  className: '三年级2班',
  gradeId: ids.gradeId,
  schoolId: ids.schoolId,
  teacherId: ids.teacherId,
  maxStudents: 50,
  description: '前端原样载荷'
})

console.log('\n=== same, but with the fields the backend actually requires ===')
await post('POST /classes corrected', '/classes', {
  className: '三年级3班',
  gradeId: ids.gradeId,
  schoolId: ids.schoolId,
  classCode: 3,
  termId: ids.termId,
  headTeacherId: ids.teacherId
})

console.log('\n=== malformed JSON -> should be 400, currently 500 ===')
const bad = await fetch(BASE + '/classes', {
  method: 'POST',
  headers: { Authorization: `Bearer ${TOKEN}`, 'Content-Type': 'application/json' },
  body: '{"className": "x", oops}'
})
const badJson = await bad.json().catch(() => ({}))
console.log(`  malformed JSON -> http=${bad.status} code=${badJson.code} msg=${badJson.message}`)
