// Smoke test every backend endpoint enumerated in task #1.
// Green = 2xx, Yellow = 4xx with expected validation message, Red = 500 or unexpected.
import fs from 'node:fs'

const BASE = 'http://localhost:8080/api'
const TOKEN = process.env.TOKEN
const ids = JSON.parse(fs.readFileSync('/tmp/ids.json', 'utf8'))

const req = (method, url, body) =>
  fetch(BASE + url, {
    method,
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${TOKEN}`
    },
    body: body ? JSON.stringify(body) : undefined
  })

const test = async (name, method, url, body, expectStatus) => {
  try {
    const r = await req(method, url, body)
    const text = await r.text()
    let data
    try { data = JSON.parse(text) } catch {}

    const ok = expectStatus ? r.status === expectStatus : r.status >= 200 && r.status < 300
    const icon = ok ? '✓' : r.status >= 500 ? '✗' : '~'
    console.log(`${icon} ${method.padEnd(6)} ${url.padEnd(50)} ${r.status}`)
    if (!ok && data?.message) console.log(`   → ${data.message.slice(0, 120)}`)
    return { name, status: r.status, ok, message: data?.message }
  } catch (e) {
    console.log(`✗ ${method.padEnd(6)} ${url.padEnd(50)} ERROR: ${e.message.slice(0, 60)}`)
    return { name, status: 0, ok: false, error: e.message }
  }
}

console.log('\n=== AUTH ===')
await test('captcha', 'GET', '/auth/captcha')
await test('logout', 'POST', '/auth/logout')

console.log('\n=== SCHOOL ===')
await test('school-list', 'GET', `/schools?pageNum=1&pageSize=10`)
await test('school-detail', 'GET', `/schools/${ids.schoolId}`)

console.log('\n=== TERM ===')
await test('term-list', 'GET', `/terms?pageNum=1&pageSize=10`)
await test('term-detail', 'GET', `/terms/${ids.termId}`)
await test('term-create-400', 'POST', '/terms', {}, 400)

console.log('\n=== GRADE ===')
await test('grade-list', 'GET', `/grades?pageNum=1&pageSize=10`)
await test('grade-detail', 'GET', `/grades/${ids.gradeId}`)
await test('grades-by-school', 'GET', `/grades/school/${ids.schoolId}`)

console.log('\n=== CLASS ===')
await test('class-list', 'GET', `/classes?pageNum=1&pageSize=10`)
await test('class-detail', 'GET', `/classes/${ids.classId}`)

console.log('\n=== SUBJECT ===')
await test('subject-list', 'GET', `/subjects?pageNum=1&pageSize=10`)
await test('subject-detail', 'GET', `/subjects/${ids.subjectIds[0]}`)

console.log('\n=== TEACHER ===')
await test('teacher-list', 'GET', `/teachers?pageNum=1&pageSize=10`)

console.log('\n=== STUDENT ===')
await test('student-list', 'GET', `/students?pageNum=1&pageSize=10`)
if (ids.studentId) await test('student-detail', 'GET', `/students/${ids.studentId}`)

console.log('\n=== EXAM ===')
await test('exam-list', 'GET', `/exams?pageNum=1&pageSize=10`)
await test('exam-detail', 'GET', `/exams/${ids.examId}`)
await test('exam-progress', 'GET', `/exams/${ids.examId}/progress`)

console.log('\n=== SCORE ===')
await test('score-list', 'GET', `/scores?examId=${ids.examId}&pageNum=1&pageSize=10`)
await test('score-review-list', 'GET', `/scores/review-list?examId=${ids.examId}&pageNum=1&pageSize=10`)

console.log('\n=== STAT ===')
await test('stat-personal', 'GET', `/stats/personal?examId=${ids.examId}`)
await test('stat-class', 'GET', `/stats/class?examId=${ids.examId}&classId=${ids.classId}`)
await test('stat-grade', 'GET', `/stats/grade?examId=${ids.examId}`)
await test('stat-subject', 'GET', `/stats/subject?examId=${ids.examId}&subjectId=${ids.subjectIds[0]}`)

console.log('\n=== USER ===')
await test('user-list', 'GET', `/users?pageNum=1&pageSize=10`)

console.log('\n=== CONFIG ===')
await test('config-list', 'GET', `/configs`)

console.log('\n=== DICT ===')
await test('dict-type-list', 'GET', `/dict/types`)
await test('dict-data-list', 'GET', `/dict/data/exam_type`)

console.log('\n=== GHOST ENDPOINTS (should 404 or 400) ===')
await test('score-class-list', 'GET', `/scores/class-list?examId=${ids.examId}&classId=${ids.classId}&subjectId=${ids.subjectIds[0]}`, null, 400)
await test('score-statistics', 'GET', `/scores/statistics?examId=${ids.examId}&classId=${ids.classId}&subjectId=${ids.subjectIds[0]}`, null, 400)
await test('score-distribution', 'GET', `/scores/distribution?examId=${ids.examId}&classId=${ids.classId}&subjectId=${ids.subjectIds[0]}`, null, 400)
await test('score-review-detail', 'GET', `/scores/review-detail?examId=${ids.examId}&classId=${ids.classId}&subjectId=${ids.subjectIds[0]}`, null, 400)
await test('score-review-history', 'GET', `/scores/review-history?examId=${ids.examId}&classId=${ids.classId}&subjectId=${ids.subjectIds[0]}`, null, 400)

console.log('\n✓ = 2xx, ~ = expected 4xx, ✗ = 5xx or unexpected')
