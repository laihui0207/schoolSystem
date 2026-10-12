// Live end-to-end verification of the remediation work against a running backend.
// Usage: node frontend/tests/live-verify.mjs   (backend on :8080 with context-path /api/v1, dev profile = captcha off)
const BASE = process.env.BASE ?? 'http://localhost:8080/api/v1'
const S = Date.now().toString().slice(-6)
const results = []

async function call(method, path, { token, body, query, form } = {}) {
  const url = BASE + path + (query ? '?' + new URLSearchParams(query) : '')
  const headers = {}
  if (token) headers.Authorization = `Bearer ${token}`
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  const res = await fetch(url, { method, headers, body: body === undefined ? form : JSON.stringify(body) })
  const ctype = res.headers.get('content-type') ?? ''
  const buf = Buffer.from(await res.arrayBuffer())
  let json = null
  if (ctype.includes('json')) { try { json = JSON.parse(buf.toString('utf8')) } catch { /* ignore */ } }
  return { http: res.status, code: json?.code, msg: json?.message, data: json?.data, ctype, size: buf.length }
}
const ok = r => r.http === 200 && (r.code === 200 || r.code === 0)
function check(label, pass, detail = '') {
  results.push({ label, pass })
  console.log(`${pass ? 'PASS' : 'FAIL'}  ${label}${detail ? '  :: ' + detail : ''}`)
  return pass
}
async function must(label, p) {
  const r = await p
  check(label, ok(r), ok(r) ? '' : `http=${r.http} code=${r.code} ${r.msg ?? ''}`)
  return r.data
}
async function mustFail(label, p, expectHttp) {
  const r = await p
  const denied = !ok(r) && (expectHttp ? r.http === expectHttp : true)
  check(label, denied, `http=${r.http} code=${r.code} ${r.msg ?? ''}`)
}

console.log('==== 1. admin login ====')
const login = await must('admin login', call('POST', '/auth/login', { body: { username: 'admin', password: 'admin123', captcha: '', captchaKey: '' } }))
const T = login?.accessToken
check('login returns refreshToken', !!login?.refreshToken)
check('userInfo.role = super_admin', login?.userInfo?.role === 'super_admin', JSON.stringify(login?.userInfo))
if (!T) { console.error('cannot continue'); process.exit(1) }
const A = { token: T }

console.log('==== 2. base data ====')
const school = await must('create school', call('POST', '/schools', { ...A, body: { schoolName: `验证小学${S}`, schoolCode: `VF${S}`, schoolType: '小学', province: '广东省', city: '深圳市', district: '南山区', address: '验证路1号', contactPerson: '校长', contactPhone: '' } }))
const sid = school?.id
const term = await must('create term', call('POST', '/terms', { ...A, body: { schoolId: sid, year: '2026-2027', term: 1, startDate: '2026-09-01', endDate: '2027-01-31' } }))
const grade = await must('create grade', call('POST', '/grades', { ...A, body: { schoolId: sid, gradeName: '三年级', gradeCode: 3 } }))
const subjects = []
for (const [name, code, full, pass] of [['语文', 'YW', 100, 60], ['数学', 'SX', 150, 90], ['英语', 'YY', 100, 60]]) {
  const s = await must(`create subject ${name}`, call('POST', '/subjects', { ...A, body: { schoolId: sid, subjectName: name, subjectCode: `${code}${S}`, fullScore: full, passScore: pass } }))
  if (s?.id) subjects.push(s)
}
const teacher = await must('create teacher', call('POST', '/teachers', { ...A, body: { teacherNo: `T${S}`, schoolId: sid, name: '李老师', gender: 1, subjectId: subjects[0]?.id } }))
const cls = await must('create class (head teacher)', call('POST', '/classes', { ...A, body: { schoolId: sid, gradeId: grade?.id, className: '三(1)班', classCode: 1, termId: term?.id, headTeacherId: teacher?.id } }))
for (const s of subjects) await must(`assign teacher to class/${s.subjectName}`, call('POST', `/teachers/${teacher?.id}/classes`, { ...A, query: { classId: cls?.id, subjectId: s.id } }))
const stuUser = await must('create student user', call('POST', '/users', { ...A, body: { username: `stu${S}`, password: 'Stu@123456', realName: '学生甲', role: 'student', schoolId: sid, status: 1 } }))
const students = []
for (const [i, name] of ['学生甲', '学生乙', '学生丙'].entries()) {
  const st = await must(`create student ${name}`, call('POST', '/students', { ...A, body: { studentNo: `${S}0${i + 1}`, schoolId: sid, name, gender: 1, birthDate: '2016-05-01', enrollmentDate: '2024-09-01', userId: i === 0 ? stuUser?.id : undefined } }))
  if (st?.id) { students.push(st); await must(`assign ${name} to class`, call('POST', `/students/${st.id}/assign-class`, { ...A, query: { classId: cls?.id } })) }
}

console.log('==== 3. exam lifecycle ====')
const examBody = { schoolId: sid, termId: term?.id, examName: `验证期中${S}`, examType: 2, examDate: '2026-10-10', gradeIds: [grade?.id], subjects: subjects.map((s, i) => ({ subjectId: s.id, fullScore: s.fullScore, sortOrder: i + 1 })) }
const exam = await must('create exam', call('POST', '/exams', { ...A, body: examBody }))
const eid = exam?.id
const detail0 = await must('exam detail', call('GET', `/exams/${eid}`, A))
check('exam detail gradeNames filled', Array.isArray(detail0?.gradeNames) && detail0.gradeNames.includes('三年级'), JSON.stringify(detail0?.gradeNames))
await must('update exam keeping same subjects (V14 regression)', call('PUT', `/exams/${eid}`, { ...A, body: { examName: examBody.examName, examType: 2, examDate: '2026-10-11', gradeIds: [grade?.id], subjects: examBody.subjects } }))
await mustFail('PATCH status=3 directly is refused', call('PATCH', `/exams/${eid}/status`, { ...A, query: { status: 3 } }))
await must('start-input', call('POST', `/exams/${eid}/start-input`, A))

const scoreTable = { // studentIndex -> [语文, 数学, 英语]
  0: [92, 141, 88], 1: [75, 99, 61], 2: [58, null, 70] // 学生丙 数学缺考
}
for (const [si, s] of subjects.entries()) {
  const r = await must(`batch-input ${s.subjectName}`, call('POST', '/scores/batch-input', { ...A, body: { examId: eid, classId: cls?.id, subjectId: s.id, scores: students.map((st, i) => ({ studentId: st.id, score: scoreTable[i][si], isAbsent: scoreTable[i][si] === null ? 1 : 0, remark: `r${i}` })) } }))
  check(`batch-input ${s.subjectName} result 3/0`, r?.successCount === 3 && r?.failCount === 0, JSON.stringify(r))
}
await mustFail('batch-input score > fullScore refused per row', call('POST', '/scores/batch-input', { ...A, body: { examId: eid, classId: cls?.id, subjectId: subjects[0].id, scores: [{ studentId: students[0].id, score: 101, isAbsent: 0 }] } }).then(r => ({ ...r, http: r.data?.failCount === 1 ? 400 : r.http, code: r.data?.failCount === 1 ? 0 : r.code })))
const progress = await must('exam progress', call('GET', `/exams/${eid}/progress`, A))
const pItems = progress?.items ?? progress ?? []
check('progress 3/3 per subject (no cartesian product)', Array.isArray(pItems) && pItems.length >= 3 && pItems.every(it => Number(it.totalCount) === 3 && Number(it.enteredCount) === 3), JSON.stringify(pItems.slice(0, 3)))
await mustFail('publish while pending is refused', call('POST', `/exams/${eid}/publish`, A))
await must('submit-review', call('POST', `/exams/${eid}/submit-review`, A))
for (const s of subjects) await must(`review triplet ${s.subjectName} as super admin`, call('POST', '/scores/review', { ...A, body: { examId: eid, classId: cls?.id, subjectId: s.id, result: 1 } }))
await must('publish', call('POST', `/exams/${eid}/publish`, A))

console.log('==== 4. post-publish data ====')
const page = await must('list scores', call('GET', '/scores', { ...A, query: { examId: eid, classId: cls?.id, pageSize: 50 } }))
const rows = page?.records ?? []
check('9 score rows', rows.length === 9, `got ${rows.length}`)
const valid = rows.filter(r => r.isAbsent !== 1 && r.score != null)
check('every valid row has subject rank_in_class', valid.every(r => r.rankInClass != null), JSON.stringify(valid.map(r => [r.studentId, r.subjectId, r.rankInClass])))
check('every row has total_rank_in_class', rows.every(r => r.totalRankInClass != null), JSON.stringify(rows.map(r => [r.studentId, r.totalRankInClass])))
const top = rows.find(r => r.studentId === students[0].id)
check('学生甲 total rank 1', top?.totalRankInClass === 1 && top?.totalRankInGrade === 1 && top?.totalRankInSchool === 1, JSON.stringify([top?.totalRankInClass, top?.totalRankInGrade, top?.totalRankInSchool]))
const yw = rows.filter(r => r.subjectId === subjects[0].id).sort((a, b) => a.rankInClass - b.rankInClass)
check('语文 subject ranks are 1,2,3 by score', yw.map(r => r.rankInClass).join(',') === '1,2,3' && yw[0].score == 92, yw.map(r => `${r.score}#${r.rankInClass}`).join(' '))
check('remark persisted', rows.some(r => r.remark === 'r0'), JSON.stringify(rows.slice(0, 2).map(r => r.remark)))
await must('stats/grade without gradeId', call('GET', '/stats/grade', { ...A, query: { examId: eid } }))
await must('stats/subject', call('GET', '/stats/subject', { ...A, query: { examId: eid, subjectId: subjects[1].id } }))
const cs = await must('stats/class', call('GET', '/stats/class', { ...A, query: { examId: eid, classId: cls?.id } }))
check('class passRate within 0..100', cs && Number(cs.passRate) >= 0 && Number(cs.passRate) <= 100, JSON.stringify({ passRate: cs?.passRate, excellentRate: cs?.excellentRate, averageScore: cs?.averageScore }))
const ps = await must('stats/personal 学生甲', call('GET', '/stats/personal', { ...A, query: { examId: eid, studentId: students[0].id } }))
check('personal stat has studentName & rankInClass', ps?.studentName === '学生甲' && ps?.rankInClass === 1, JSON.stringify({ name: ps?.studentName, rank: ps?.rankInClass, total: ps?.totalScore }))
const xlsx = await call('GET', '/export/scores', { ...A, query: { examId: eid, classId: cls?.id } })
check('export xlsx as admin', xlsx.http === 200 && xlsx.ctype.includes('spreadsheet') && xlsx.size > 2000, `http=${xlsx.http} ctype=${xlsx.ctype} size=${xlsx.size}`)
await must('score-changes list as admin', call('GET', '/score-changes', { ...A, query: { pageSize: 5 } }))

console.log('==== 5. student perspective ====')
const sl = await must('student login', call('POST', '/auth/login', { body: { username: `stu${S}`, password: 'Stu@123456', captcha: '', captchaKey: '' } }))
const ST = { token: sl?.accessToken }
check('student userInfo.studentId bound', sl?.userInfo?.studentId === students[0].id && sl?.userInfo?.role === 'student', JSON.stringify(sl?.userInfo))
const sp = await must('student lists own scores', call('GET', '/scores', { ...ST, query: { examId: eid, pageSize: 50 } }))
check('student sees only own rows', (sp?.records ?? []).length === 3 && (sp?.records ?? []).every(r => r.studentId === students[0].id), `rows=${(sp?.records ?? []).length}`)
await must('student personal stat (self)', call('GET', '/stats/personal', { ...ST, query: { examId: eid, studentId: students[0].id } }))
await mustFail('student personal stat (other) denied', call('GET', '/stats/personal', { ...ST, query: { examId: eid, studentId: students[1].id } }))
await mustFail('student GET /scores/{otherId} denied', call('GET', `/scores/${rows.find(r => r.studentId === students[1].id)?.id}`, ST))
await mustFail('student stats/class denied (403)', call('GET', '/stats/class', { ...ST, query: { examId: eid, classId: cls?.id } }), 403)
await mustFail('student export denied', call('GET', '/export/scores', { ...ST, query: { examId: eid } }))
await mustFail('student teacher-assign denied (403)', call('POST', `/teachers/${teacher?.id}/classes`, { ...ST, query: { classId: cls?.id, subjectId: subjects[0].id } }), 403)
await mustFail('student school update denied (403)', call('PUT', `/schools/${sid}`, { ...ST, body: { schoolName: 'x' } }), 403)
await must('student trend (self)', call('GET', `/stats/student/${students[0].id}/trend`, ST))
const notif = await must('student notifications', call('GET', '/notifications', { ...ST, query: { pageSize: 20 } }))
check('student received publish notification (type 1)', (notif?.records ?? []).some(n => Number(n.type) === 1 && Number(n.relatedId) === eid), JSON.stringify((notif?.records ?? []).map(n => [n.type, n.title])))
const unread = await must('student unread-count', call('GET', '/notifications/unread-count', ST))
check('unread-count >= 1', Number(unread) >= 1, String(unread))

console.log('==== 6. refresh rotation & logout ====')
const rf = await must('refresh with student refreshToken', call('POST', '/auth/refresh', { query: { refreshToken: sl?.refreshToken } }))
await mustFail('old refreshToken rejected after rotation', call('POST', '/auth/refresh', { query: { refreshToken: sl?.refreshToken } }))
const ST2 = { token: rf?.accessToken }
await must('new access token works', call('GET', '/notifications/unread-count', ST2))
await must('logout', call('POST', '/auth/logout', ST2))
await mustFail('access token dead after logout (401)', call('GET', '/notifications/unread-count', ST2), 401)
await mustFail('rotated refreshToken dead after logout', call('POST', '/auth/refresh', { query: { refreshToken: rf?.refreshToken } }))

const failed = results.filter(r => !r.pass)
console.log(`\n==== SUMMARY: ${results.length - failed.length}/${results.length} passed ====`)
for (const f of failed) console.log('  FAILED:', f.label)
process.exit(failed.length ? 1 : 0)
