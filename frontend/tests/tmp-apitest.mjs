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

console.log('\n======== B. EXAM STATUS FLOW (correct order) ========')
// seed already did start-input; exam is in 录入中
await t('submit exam for review', 'POST', `/exams/${ids.examId}/submit-review`)
await t('publish exam', 'POST', `/exams/${ids.examId}/publish`)

console.log('\n======== C. DETAIL GETs (real ids) ========')
await t('school detail', 'GET', `/schools/${ids.schoolId}`)
await t('term detail', 'GET', `/terms/${ids.termId}`)
await t('term current', 'GET', '/terms/current', { query: { schoolId: ids.schoolId } })
await t('terms by school', 'GET', `/terms/school/${ids.schoolId}`)
await t('grade detail', 'GET', `/grades/${ids.gradeId}`)
await t('grades by school', 'GET', `/grades/school/${ids.schoolId}`)
await t('class detail', 'GET', `/classes/${ids.classId}`)
await t('subject detail', 'GET', `/subjects/${ids.subjectIds[0]}`)
await t('subjects by school', 'GET', `/subjects/school/${ids.schoolId}`)
await t('teacher detail', 'GET', `/teachers/${ids.teacherId}`)
await t('student detail', 'GET', `/students/${ids.studentIds[0]}`)
await t('students by class', 'GET', `/students/by-class/${ids.classId}`)
await t('exam detail', 'GET', `/exams/${ids.examId}`)
await t('user detail', 'GET', '/users/4')
await t('config by key', 'GET', '/configs/key/system.name')
await t('dict data by type', 'GET', `/dict/data/test_dict_${S}`)
await t('notif unread count', 'GET', '/notifications/user/4/unread-count')

console.log('\n======== D. SCORES ========')
const scorePage = await t('score list filtered', 'GET', '/scores', {
  query: { examId: ids.examId, classId: ids.classId, pageNum: 1, pageSize: 100 }
})
const firstScoreId = scorePage?.records?.[0]?.id
console.log(`   (score rows returned: ${scorePage?.records?.length ?? 'n/a'}, total=${scorePage?.total ?? 'n/a'})`)
if (firstScoreId) await t('score detail', 'GET', `/scores/${firstScoreId}`)
else console.log('SKIP score detail — no score rows came back')
await t('student exam scores', 'GET', `/scores/student/${ids.studentIds[0]}/exam/${ids.examId}`)

console.log('\n======== E. STATISTICS ========')
await t('personal stat', 'GET', '/stats/personal', { query: { examId: ids.examId, studentId: ids.studentIds[0] } })
await t('class stat', 'GET', '/stats/class', { query: { examId: ids.examId, classId: ids.classId } })
await t('grade stat', 'GET', '/stats/grade', { query: { examId: ids.examId, gradeId: ids.gradeId } })
await t('subject stat', 'GET', '/stats/subject', { query: { examId: ids.examId, subjectId: ids.subjectIds[0] } })
await t('student trend', 'GET', `/stats/student/${ids.studentIds[0]}/trend`, { query: { termId: ids.termId } })

console.log('\n======== F. WARNINGS ========')
await t('check exam warnings', 'POST', '/warnings/check-exam', { query: { examId: ids.examId } })
await t('score trend analysis', 'GET', `/warnings/trend/${ids.studentIds[0]}`)

console.log('\n======== G. EXPORT / PDF (binary) ========')
await t('score template xlsx', 'GET', '/export/score-template', { expectBlob: true })
await t('export scores xlsx', 'GET', '/export/scores', { query: { examId: ids.examId, classId: ids.classId }, expectBlob: true })
await t('pdf score report', 'GET', '/pdf/score-report', { query: { examId: ids.examId, studentId: ids.studentIds[0] }, expectBlob: true })
await t('pdf class reports', 'GET', '/pdf/class-reports', { query: { examId: ids.examId, classId: ids.classId }, expectBlob: true })

console.log('\n======== H. UPDATES (PUT/PATCH) ========')
await t('update school', 'PUT', `/schools/${ids.schoolId}`, {
  body: { schoolName: `测试小学改${S}`, schoolCode: `TS${S}`, contactPhone: '13800138002' }
})
await t('school status', 'PATCH', `/schools/${ids.schoolId}/status`, { query: { status: 1 } })
await t('update term', 'PUT', `/terms/${ids.termId}`, {
  body: { year: '2026-2027', term: 1, startDate: '2026-09-01', endDate: '2027-02-01' }
})
await t('update grade', 'PUT', `/grades/${ids.gradeId}`, { body: { gradeName: '三年级改', gradeCode: 3 } })
await t('update subject', 'PUT', `/subjects/${ids.subjectIds[0]}`, {
  body: { subjectName: '语文改', fullScore: 120, passScore: 72 }
})
await t('update teacher', 'PUT', `/teachers/${ids.teacherId}`, { body: { name: '李老师改', gender: 2 } })
await t('teacher status', 'PATCH', `/teachers/${ids.teacherId}/status`, { query: { status: 1 } })
await t('teacher subject', 'PATCH', `/teachers/${ids.teacherId}/subject`, { query: { subjectId: ids.subjectIds[1] } })
await t('update student', 'PUT', `/students/${ids.studentIds[0]}`, { body: { name: '王小明改', gender: 1 } })
await t('student status', 'PATCH', `/students/${ids.studentIds[0]}/status`, { query: { status: 1 } })
await t('class head teacher', 'PATCH', `/classes/${ids.classId}/head-teacher`, { query: { teacherId: ids.teacherId } })
if (ids.userId) {
  await t('update user', 'PUT', `/users/${ids.userId}`, { body: { realName: '测试用户改', phone: '13900139002' } })
  await t('user status', 'PATCH', `/users/${ids.userId}/status`, { query: { status: 1 } })
  await t('reset password', 'PATCH', `/users/${ids.userId}/password`, { body: { password: 'NewPass@123' } })
}
await t('notif batch read', 'PATCH', '/notifications/batch-read', { body: [1] })
await t('notif read all', 'PATCH', '/notifications/user/4/read-all')

console.log('\n======== I. DELETES (throwaway entities only) ========')
// Build disposable rows so deletes never touch the chain the frontend needs.
const dSub = await t('mk disposable subject', 'POST', '/subjects', {
  body: { subjectName: '待删科目', subjectCode: `DEL${S}`, fullScore: 100, passScore: 60, schoolId: ids.schoolId }
})
if (dSub?.id) await t('delete subject', 'DELETE', `/subjects/${dSub.id}`)
const dStu = await t('mk disposable student', 'POST', '/students', {
  body: { studentNo: `DELS${S}`, schoolId: ids.schoolId, name: '待删学生', gender: 1, enrollmentDate: '2023-09-01' }
})
if (dStu?.id) await t('delete student', 'DELETE', `/students/${dStu.id}`)
const dTea = await t('mk disposable teacher', 'POST', '/teachers', {
  body: { teacherNo: `DELT${S}`, schoolId: ids.schoolId, name: '待删教师', gender: 1 }
})
if (dTea?.id) await t('delete teacher', 'DELETE', `/teachers/${dTea.id}`)

console.log('\n======== J. AUTH ========')
await t('refresh token', 'POST', '/auth/refresh', { query: { refreshToken: 'invalid-token-on-purpose' } })

console.log('\n\n================ SUMMARY ================')
const failed = results.filter(r => !r.ok)
console.log(`total: ${results.length}  passed: ${results.length - failed.length}  FAILED: ${failed.length}`)
if (failed.length) {
  console.log('\n---- FAILURES ----')
  for (const f of failed) console.log(`  [${f.method} ${f.path}] http=${f.http} code=${f.code} :: ${f.msg}`)
}
fs.writeFileSync('/tmp/apitest-results.json', JSON.stringify(results, null, 2))
fs.writeFileSync('/tmp/ids.json', JSON.stringify(ids, null, 2))
