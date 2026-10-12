// Live verification for feature batch 1: region stats, PDF/stat export, audit log, config/dict wiring, roster import/export.
// Usage: node frontend/tests/live-verify-batch1.mjs   (backend on :8080, dev profile = captcha off, seeded DB)
// Run from the repo root or frontend/ (xlsx is resolved from frontend/node_modules).
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const here = path.dirname(fileURLToPath(import.meta.url))
const XLSX = createRequire(path.join(here, '..', 'package.json'))('xlsx')

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
  return { http: res.status, code: json?.code, msg: json?.message, data: json?.data, ctype, size: buf.length, buf, disposition: res.headers.get('content-disposition') ?? '' }
}
const ok = r => r.http === 200 && (r.code === 200 || r.code === 0)
function check(label, pass, detail = '') {
  results.push({ label, pass })
  console.log(`${pass ? 'PASS' : 'FAIL'}  ${label}${detail ? '  :: ' + detail : ''}`)
  return pass
}
async function must(label, p) { const r = await p; check(label, ok(r), ok(r) ? '' : `http=${r.http} code=${r.code} ${r.msg ?? ''}`); return r.data }
async function mustFail(label, p, expectHttp) { const r = await p; check(label, !ok(r) && (expectHttp ? r.http === expectHttp : true), `http=${r.http} code=${r.code} ${r.msg ?? ''}`); return r }
const login = async (username, password) => (await call('POST', '/auth/login', { body: { username, password, captcha: '', captchaKey: '' } })).data
function xlsxForm(rows, header, fieldName = 'file', filename = 'import.xlsx', extra = {}) {
  const ws = XLSX.utils.aoa_to_sheet([header, ...rows])
  const wb = XLSX.utils.book_new(); XLSX.utils.book_append_sheet(wb, ws, 'Sheet1')
  const buf = XLSX.write(wb, { type: 'buffer', bookType: 'xlsx' })
  const fd = new FormData()
  fd.append(fieldName, new Blob([buf], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' }), filename)
  for (const [k, v] of Object.entries(extra)) fd.append(k, String(v))
  return fd
}
const readXlsx = buf => { const wb = XLSX.read(buf, { type: 'buffer' }); return Object.fromEntries(wb.SheetNames.map(n => [n, XLSX.utils.sheet_to_json(wb.Sheets[n], { header: 1 })])) }

console.log('==== 0. setup: admin login + fresh school/class/exam ====')
const adminLogin = await login('admin', 'admin123')
const A = { token: adminLogin.accessToken }
const school = await must('create school', call('POST', '/schools', { ...A, body: { schoolName: `批次一校${S}`, schoolCode: `B1${S}`, schoolType: '小学', regionCode: `R-${S}`, address: 'x', contactPerson: 'y', contactPhone: '' } }))
const sid = school.id
const term = await must('create term', call('POST', '/terms', { ...A, body: { schoolId: sid, year: '2026-2027', term: 1, startDate: '2026-09-01', endDate: '2027-01-31' } }))
const grade = await must('create grade', call('POST', '/grades', { ...A, body: { schoolId: sid, gradeName: '四年级', gradeCode: 4 } }))
const subjects = []
for (const [name, code, full, pass] of [['语文', 'YW', 100, 60], ['数学', 'SX', 150, 90]]) {
  subjects.push(await must(`create subject ${name}`, call('POST', '/subjects', { ...A, body: { schoolId: sid, subjectName: name, subjectCode: `${code}${S}`, fullScore: full, passScore: pass } })))
}
const teacher = await must('create teacher', call('POST', '/teachers', { ...A, body: { teacherNo: `T${S}`, schoolId: sid, name: '王老师', gender: 2, subjectId: subjects[0].id } }))
const teacherUser = await must('create teacher user', call('POST', '/users', { ...A, body: { username: `tch${S}`, password: 'Tch@123456', realName: '王老师', role: 'teacher', schoolId: sid, status: 1 } }))
await must('bind teacher user', call('PUT', `/teachers/${teacher.id}`, { ...A, body: { name: '王老师', gender: 2, subjectId: subjects[0].id, userId: teacherUser.id } }))
const cls = await must('create class', call('POST', '/classes', { ...A, body: { schoolId: sid, gradeId: grade.id, className: '四(1)班', classCode: 1, termId: term.id, headTeacherId: teacher.id } }))
for (const s of subjects) await must(`assign teacher ${s.subjectName}`, call('POST', `/teachers/${teacher.id}/classes`, { ...A, query: { classId: cls.id, subjectId: s.id } }))
const stuUser = await must('create student user', call('POST', '/users', { ...A, body: { username: `stu${S}`, password: 'Stu@123456', realName: '甲同学', role: 'student', schoolId: sid, status: 1 } }))
const students = []
for (const [i, name] of ['甲同学', '乙同学'].entries()) {
  const st = await must(`create student ${name}`, call('POST', '/students', { ...A, body: { studentNo: `${S}${i + 1}`, schoolId: sid, name, gender: 1, birthDate: '2015-01-01', enrollmentDate: '2023-09-01', userId: i === 0 ? stuUser.id : undefined } }))
  students.push(st); await must(`assign ${name}`, call('POST', `/students/${st.id}/assign-class`, { ...A, query: { classId: cls.id } }))
}
const exam = await must('create exam (期中)', call('POST', '/exams', { ...A, body: { schoolId: sid, termId: term.id, examName: `批次一期中${S}`, examType: 2, examDate: '2026-11-05', gradeIds: [grade.id], subjects: subjects.map((s, i) => ({ subjectId: s.id, fullScore: s.fullScore, sortOrder: i + 1 })) } }))
const eid = exam.id
await must('start-input', call('POST', `/exams/${eid}/start-input`, A))
const scores = { 0: [88, 120], 1: [55, 70] }
for (const [si, s] of subjects.entries()) await must(`batch-input ${s.subjectName}`, call('POST', '/scores/batch-input', { ...A, body: { examId: eid, classId: cls.id, subjectId: s.id, scores: students.map((st, i) => ({ studentId: st.id, score: scores[i][si], isAbsent: 0 })) } }))
await must('submit-review', call('POST', `/exams/${eid}/submit-review`, A))
const allRows = (await must('list scores', call('GET', '/scores', { ...A, query: { examId: eid, pageSize: 50 } }))).records
const rejectTarget = allRows.find(r => r.studentId === students[1].id && r.subjectId === subjects[0].id)

console.log('==== C. audit: review with reviewer/rejectReason + operation log ====')
await must('reject one score with reason', call('POST', '/scores/review', { ...A, body: { scoreIds: [rejectTarget.id], result: 2, rejectReason: 'live 驳回原因' } }))
const rejected = await must('GET rejected score', call('GET', `/scores/${rejectTarget.id}`, A))
check('rejected score has reviewerId/reviewerName/rejectReason, remark untouched', rejected.status === 2 && rejected.reviewerId === adminLogin.userInfo.id && !!rejected.reviewerName && rejected.rejectReason === 'live 驳回原因' && !(rejected.remark ?? '').includes('【驳回】'), JSON.stringify({ s: rejected.status, rid: rejected.reviewerId, rn: rejected.reviewerName, rr: rejected.rejectReason, remark: rejected.remark }))
const hist = await must('review-history', call('GET', '/scores/review-history', { ...A, query: { examId: eid, classId: cls.id, subjectId: subjects[0].id } }))
check('review-history exposes reviewer & reason', Array.isArray(hist) && hist.length >= 1 && hist[0].reviewerName && hist[0].rejectReason === 'live 驳回原因', JSON.stringify(hist[0]))
await mustFail('publish refused: pending + rejected', call('POST', `/exams/${eid}/publish`, A))
await must('re-approve all via triplets', call('POST', '/scores/review', { ...A, body: { examId: eid, classId: cls.id, subjectId: subjects[0].id, result: 1 } }).then(async r => { const r2 = await call('POST', '/scores/review', { ...A, body: { examId: eid, classId: cls.id, subjectId: subjects[1].id, result: 1 } }); return ok(r) ? r2 : r }))
const reapproved = await must('GET re-approved score', call('GET', `/scores/${rejectTarget.id}`, A))
check('re-approval clears rejectReason', reapproved.status === 1 && !reapproved.rejectReason, JSON.stringify({ s: reapproved.status, rr: reapproved.rejectReason }))
await must('publish', call('POST', `/exams/${eid}/publish`, A))
const logs = await must('operation-logs filter 审核成绩', call('GET', '/operation-logs', { ...A, query: { operation: '审核成绩', pageSize: 20 } }))
const rejectLog = (logs?.records ?? []).find(r => String(r.params).includes('live 驳回原因'))
check('operation log recorded the reject call with its params', !!rejectLog && rejectLog.operation.includes('审核成绩') && rejectLog.method.startsWith('POST') && rejectLog.method.includes('/scores/review') && rejectLog.status === 1, JSON.stringify({ total: logs?.total, found: !!rejectLog, op: rejectLog?.operation, method: rejectLog?.method }))
await call('POST', '/auth/login', { body: { username: 'no_such_user_xyz', password: 'wrongpass', captcha: '', captchaKey: '' } })
const failLogs = await must('operation-logs for failed login', call('GET', '/operation-logs', { ...A, query: { username: 'no_such_user_xyz', pageSize: 5 } }))
const fl = failLogs?.records?.[0]
check('failed login logged, password masked', !!fl && fl.status === 0 && fl.userId == null && String(fl.params).includes('***') && !String(fl.params).includes('wrongpass'), JSON.stringify({ s: fl?.status, uid: fl?.userId, params: String(fl?.params).slice(0, 120) }))
const one = await must('operation-logs/{id}', call('GET', `/operation-logs/${logs.records[0].id}`, A))
check('log detail has statusName', ['成功', '失败'].includes(one?.statusName), JSON.stringify(one?.statusName))
await mustFail('operation-logs/999999999 → 7001', call('GET', '/operation-logs/999999999', A))

console.log('==== D. config/dict wiring ====')
const batch = await must('dict/data/batch', call('GET', '/dict/data/batch', { ...A, query: { types: 'gender,student_status,relationship,region,exam_type' } }))
check('V17 dict types seeded', batch?.gender?.length === 2 && batch?.student_status?.length === 3 && batch?.relationship?.length === 3 && batch?.region?.length === 2 && batch?.exam_type?.length === 5, JSON.stringify(Object.fromEntries(Object.entries(batch ?? {}).map(([k, v]) => [k, v.length]))))
const dedup = await must('dict/data/batch dedup', call('GET', '/dict/data/batch', { ...A, query: { types: 'gender, no_such_type ,gender' } }))
check('batch dedups & keeps unknown as []', Object.keys(dedup ?? {}).length === 2 && Array.isArray(dedup?.no_such_type) && dedup.no_such_type.length === 0, JSON.stringify(Object.keys(dedup ?? {})))
const examsPage = await must('exams list', call('GET', '/exams', { ...A, query: { pageNum: 1, pageSize: 50, schoolId: sid } }))
const ourExam = (examsPage?.records ?? []).find(e => e.id === eid)
check('examTypeName comes from dict (期中考试)', ourExam?.examTypeName === '期中考试', JSON.stringify(ourExam?.examTypeName))
const configs = await must('configs list', call('GET', '/configs', A))
const maint = configs.find(c => c.configKey === 'system.maintenance'); const approval = configs.find(c => c.configKey === 'score.change.approval')
const setCfg = (c, v) => call('PUT', `/configs/${c.id}`, { ...A, body: { ...c, configValue: v } })
await must('enable maintenance', setCfg(maint, 'true'))
const mLogin = await call('POST', '/auth/login', { body: { username: `stu${S}`, password: 'Stu@123456', captcha: '', captchaKey: '' } })
check('maintenance blocks student login (9002)', mLogin.code === 9002 && String(mLogin.msg).includes('维护'), `code=${mLogin.code} ${mLogin.msg}`)
const aLogin = await call('POST', '/auth/login', { body: { username: 'admin', password: 'admin123', captcha: '', captchaKey: '' } })
check('maintenance still lets admin in', ok(aLogin) && !!aLogin.data?.accessToken)
await must('disable maintenance', setCfg(maint, 'false'))
const sLogin2 = await login(`stu${S}`, 'Stu@123456')
check('student login restored immediately (cache evicted)', !!sLogin2?.accessToken)
await must('turn off change approval', setCfg(approval, 'false'))
const directTarget = allRows.find(r => r.studentId === students[0].id && r.subjectId === subjects[0].id)
const direct = await must('admin apply w/o approval → direct write', call('POST', '/score-changes/apply', { ...A, body: { scoreId: directTarget.id, newScore: 90, changeReason: '免审批直改' } }))
check('direct change status=1 and score written', direct?.status === 1, JSON.stringify({ status: direct?.status }))
const after = await must('GET changed score', call('GET', `/scores/${directTarget.id}`, A))
check('score now 90', Number(after?.score) === 90, String(after?.score))
await must('turn approval back on', setCfg(approval, 'true'))
const pending = await must('admin apply with approval → pending', call('POST', '/score-changes/apply', { ...A, body: { scoreId: directTarget.id, newScore: 91, changeReason: '需审批' } }))
check('pending change status=0', pending?.status === 0, JSON.stringify({ status: pending?.status }))
await mustFail('teacher cannot create dict type (403)', call('POST', '/dict/types', { token: (await login(`tch${S}`, 'Tch@123456')).accessToken, body: { dictName: 't', dictType: `zz_${S}`, status: 0 } }), 403)

console.log('==== A. region stats ====')
const regions = await must('stats/regions', call('GET', '/stats/regions', A))
check('our region listed', (regions ?? []).some(r => r.regionCode === `R-${S}` && r.schoolCount >= 1), JSON.stringify(regions?.slice(0, 3)))
const region = await must('stats/region (our exam)', call('GET', '/stats/region', { ...A, query: { regionCode: `R-${S}`, year: '2026-2027', term: '1', examType: '2' } }))
const ours = (region?.schools ?? []).find(s => s.schoolId === sid)
check('region stat picks our published exam', ours?.examId === eid && ours.rank === 1 && ours.studentCount === 2 && Number(ours.averageScore) > 0, JSON.stringify(ours))
check('region subjects aligned by name', (region?.subjects ?? []).some(s => s.subjectName === '语文' && s.schools?.some(x => x.schoolId === sid)), JSON.stringify((region?.subjects ?? []).map(s => s.subjectName)))
const none = await must('stats/region no exam year', call('GET', '/stats/region', { ...A, query: { regionCode: `R-${S}`, year: '1999-2000', term: '1', examType: '2' } }))
check('no comparable exam → school listed without rank', (none?.schools ?? []).length >= 1 && none.schools.every(s => s.rank == null && s.examId == null), JSON.stringify(none?.schools?.[0]))
await mustFail('stats/region missing params → 400', call('GET', '/stats/region', { ...A, query: { regionCode: `R-${S}` } }), 400)
await mustFail('stats/region term=3 → 400', call('GET', '/stats/region', { ...A, query: { regionCode: `R-${S}`, year: '2026-2027', term: '3', examType: '2' } }), 400)
await mustFail('stats/regions as student → 403', call('GET', '/stats/regions', { token: sLogin2.accessToken }), 403)
await mustFail('regionCode > 20 chars → 400', call('PUT', `/schools/${sid}`, { ...A, body: { regionCode: '123456789012345678901' } }), 400)
const cstat = await must('stats/class (K1 ratios)', call('GET', '/stats/class', { ...A, query: { examId: eid, classId: cls.id } }))
check('class stat rates numeric', typeof cstat?.passRate === 'number' && typeof cstat?.excellentRate === 'number', JSON.stringify({ p: cstat?.passRate, e: cstat?.excellentRate }))

console.log('==== B. PDF + stat report ====')
const pdf = await call('GET', '/pdf/score-report', { ...A, query: { examId: eid, studentId: students[0].id } })
check('student PDF: pdf content-type, %PDF magic, >1KB', pdf.http === 200 && pdf.ctype === 'application/pdf' && pdf.buf.subarray(0, 4).toString() === '%PDF' && pdf.size > 1000 && pdf.disposition.includes("filename*=utf-8''"), `http=${pdf.http} ctype=${pdf.ctype} size=${pdf.size}`)
const cpdf = await call('GET', '/pdf/class-reports', { ...A, query: { examId: eid, classId: cls.id } })
const pageCount = (cpdf.buf.toString('latin1').match(/\/Type\s*\/Page[^s]/g) ?? []).length
check('class PDF: one page per student (2)', cpdf.http === 200 && cpdf.ctype === 'application/pdf' && pageCount === 2, `http=${cpdf.http} pages=${pageCount} size=${cpdf.size}`)
const spdf = await call('GET', '/pdf/score-report', { token: sLogin2.accessToken, query: { examId: eid, studentId: students[0].id } })
check('student downloads own PDF', spdf.http === 200 && spdf.ctype === 'application/pdf' && spdf.size > 1000, `http=${spdf.http} ctype=${spdf.ctype}`)
await mustFail('student downloads other PDF → 1101', call('GET', '/pdf/score-report', { token: sLogin2.accessToken, query: { examId: eid, studentId: students[1].id } }))
await mustFail('student class PDF → 403', call('GET', '/pdf/class-reports', { token: sLogin2.accessToken, query: { examId: eid, classId: cls.id } }), 403)
const tTok = (await login(`tch${S}`, 'Tch@123456')).accessToken
const tpdf = await call('GET', '/pdf/class-reports', { token: tTok, query: { examId: eid, classId: cls.id } })
check('teacher class PDF in scope', tpdf.http === 200 && tpdf.ctype === 'application/pdf', `http=${tpdf.http} ctype=${tpdf.ctype} code=${tpdf.code}`)
const rep = await call('GET', '/export/stat-report', { ...A, query: { examId: eid, classId: cls.id } })
const sheets = rep.ctype.includes('spreadsheet') ? readXlsx(rep.buf) : {}
check('stat report xlsx: 3 sheets 概览/科目统计/分数段', rep.http === 200 && Object.keys(sheets).length === 3 && '概览' in sheets && '科目统计' in sheets && '分数段' in sheets, `ctype=${rep.ctype} sheets=${Object.keys(sheets)}`)
check('概览 has exactly 1 data row for the class', (sheets['概览']?.length ?? 0) === 2, `rows=${sheets['概览']?.length}`)
const repAll = await call('GET', '/export/stat-report', { ...A, query: { examId: eid } })
const sheetsAll = repAll.ctype.includes('spreadsheet') ? readXlsx(repAll.buf) : {}
check('school-wide report ends with 全校合计', (sheetsAll['概览'] ?? []).some(r => String(r[0]).includes('合计')), JSON.stringify((sheetsAll['概览'] ?? []).map(r => r[0])))
await mustFail('student stat-report → 1101', call('GET', '/export/stat-report', { token: sLogin2.accessToken, query: { examId: eid, classId: cls.id } }))
await mustFail('teacher stat-report w/o classId → 1101', call('GET', '/export/stat-report', { token: tTok, query: { examId: eid } }))
await mustFail('stat-report exam 999999999 → 6001', call('GET', '/export/stat-report', { ...A, query: { examId: 999999999, classId: cls.id } }))

console.log('==== E. roster import/export ====')
const stTpl = await call('GET', '/students/template', A)
const stHeader = stTpl.ctype.includes('spreadsheet') ? readXlsx(stTpl.buf)[Object.keys(readXlsx(stTpl.buf))[0]][0] : []
check('student template header', stHeader.join(',') === '学号*,姓名*,性别(男/女),出生日期,入学日期,班级名称,是否开通账号(是/否)', stHeader.join(','))
const tTpl = await call('GET', '/teachers/template', A)
const tHeader = tTpl.ctype.includes('spreadsheet') ? readXlsx(tTpl.buf)[Object.keys(readXlsx(tTpl.buf))[0]][0] : []
check('teacher template header', tHeader.join(',') === '工号*,姓名*,性别(男/女),主教科目名称,是否开通账号(是/否)', tHeader.join(','))
await mustFail('teacher cannot download student template (403)', call('GET', '/students/template', { token: tTok }), 403)
await mustFail('student cannot export students (403)', call('GET', '/students/export', { token: sLogin2.accessToken }), 403)
const imp = await must('import 2 students (1 with account)', call('POST', '/students/import', { ...A, form: xlsxForm([[`LIVE${S}A`, '导入甲', '男', '2015-03-04', '2023-09-01', '四(1)班', '是'], [`LIVE${S}B`, '导入乙', '女', '2015-05-06', '2023-09-01', '', '否']], stHeader, 'file', 'students.xlsx', { schoolId: sid }) }))
check('import result 2/0 + 1 account', imp?.successCount === 2 && imp?.failCount === 0 && imp?.createdAccounts?.length === 1 && imp.createdAccounts[0].username === `LIVE${S}A` && imp.createdAccounts[0].initialPassword === '123456', JSON.stringify(imp))
const importedLogin = await login(`LIVE${S}A`, '123456')
check('imported student can log in with 123456 and is bound', !!importedLogin?.accessToken && importedLogin.userInfo.role === 'student' && importedLogin.userInfo.studentId != null, JSON.stringify(importedLogin?.userInfo))
const impA = (await must('find imported student', call('GET', '/students', { ...A, query: { studentNo: `LIVE${S}A`, schoolId: sid } }))).records?.[0]
check('imported student placed in 四(1)班', impA?.classes?.some(c => c.className === '四(1)班'), JSON.stringify(impA?.classes))
const dup = await must('import duplicate studentNo in file', call('POST', '/students/import', { ...A, form: xlsxForm([[`DUP${S}`, '甲', '男', '', '', '', '否'], [`DUP${S}`, '乙', '男', '', '', '', '否']], stHeader, 'file', 'dup.xlsx', { schoolId: sid }) }))
check('duplicate rejected, nothing written', dup?.successCount === 0 && dup?.failCount >= 1 && String(dup?.failures?.[0]?.reason).includes('重复'), JSON.stringify(dup))
const nocls = await must('import with unknown class', call('POST', '/students/import', { ...A, form: xlsxForm([[`NOCLS${S}`, '丙', '男', '', '', '不存在的班级XYZ', '否']], stHeader, 'file', 'nocls.xlsx', { schoolId: sid }) }))
check('unknown class reported', nocls?.failCount === 1 && String(nocls?.failures?.[0]?.reason).includes('不存在'), JSON.stringify(nocls?.failures))
const exists = await must('import existing studentNo', call('POST', '/students/import', { ...A, form: xlsxForm([[students[0].studentNo, '重复', '男', '', '', '', '否']], stHeader, 'file', 'exists.xlsx', { schoolId: sid }) }))
check('existing studentNo reported', exists?.failCount === 1 && String(exists?.failures?.[0]?.reason).includes('已存在'), JSON.stringify(exists?.failures))
await mustFail('import without schoolId as super admin → 400', call('POST', '/students/import', { ...A, form: xlsxForm([[`X${S}`, 'x', '男', '', '', '', '否']], stHeader) }))
await mustFail('import header-only → error', call('POST', '/students/import', { ...A, form: xlsxForm([], stHeader, 'file', 'empty.xlsx', { schoolId: sid }) }))
const txt = new FormData(); txt.append('file', new Blob(['hello'], { type: 'text/plain' }), 'a.txt'); txt.append('schoolId', String(sid))
await mustFail('import .txt → 8002', call('POST', '/students/import', { ...A, form: txt }))
const exp = await call('GET', '/students/export', { ...A, query: { schoolId: sid, classId: cls.id } })
const expSheet = exp.ctype.includes('spreadsheet') ? Object.values(readXlsx(exp.buf))[0] : []
const classTotal = (await must('count students in class', call('GET', '/students', { ...A, query: { classId: cls.id, pageSize: 1000 } }))).total
check('student export rows = class size, header ok', exp.http === 200 && expSheet[0]?.join(',') === '学号,姓名,性别,出生日期,入学日期,年级,班级,状态,账号用户名,学校' && expSheet.length - 1 === classTotal, `rows=${expSheet.length - 1} total=${classTotal} header=${expSheet[0]?.join(',')}`)
await mustFail('export with no match → business error', call('GET', '/students/export', { ...A, query: { studentNo: 'NO_SUCH_STUDENT_ZZZ' } }))
const timp = await must('import 2 teachers (1 with account)', call('POST', '/teachers/import', { ...A, form: xlsxForm([[`LIVET${S}`, '师一', '女', '语文', '是'], [`LIVET${S}B`, '师二', '男', '数学', '否']], tHeader, 'file', 'teachers.xlsx', { schoolId: sid }) }))
check('teacher import 2/0 + 1 account', timp?.successCount === 2 && timp?.createdAccounts?.length === 1 && timp.createdAccounts[0].username === `LIVET${S}`, JSON.stringify(timp))
// teacherNo filter is a LIKE match, so LIVET<S>B also comes back; pick the exact one
const t1 = (await must('find imported teacher', call('GET', '/teachers', { ...A, query: { teacherNo: `LIVET${S}`, schoolId: sid } }))).records?.find(t => t.teacherNo === `LIVET${S}`)
check('imported teacher subject resolved by name', t1?.subjectName === '语文', JSON.stringify({ subjectName: t1?.subjectName, subjectId: t1?.subjectId }))
const nosub = await must('import teacher with unknown subject', call('POST', '/teachers/import', { ...A, form: xlsxForm([[`NOSUB${S}`, '丁', '男', '不存在的科目QQQ', '否']], tHeader, 'file', 'nosub.xlsx', { schoolId: sid }) }))
check('unknown subject reported', nosub?.failCount === 1 && String(nosub?.failures?.[0]?.reason).includes('不存在'), JSON.stringify(nosub?.failures))
const texp = await call('GET', '/teachers/export', { ...A, query: { schoolId: sid } })
const texpSheet = texp.ctype.includes('spreadsheet') ? Object.values(readXlsx(texp.buf))[0] : []
check('teacher export header', texp.http === 200 && texpSheet[0]?.join(',') === '工号,姓名,性别,主教科目,状态,账号用户名,学校', texpSheet[0]?.join(','))
const byId = await must('GET /students/{id} still routes', call('GET', `/students/${students[0].id}`, A))
check('literal /template & /export do not shadow /{id}', byId?.id === students[0].id)

const failed = results.filter(r => !r.pass)
console.log(`\n==== SUMMARY: ${results.length - failed.length}/${results.length} passed ====`)
for (const f of failed) console.log('  FAILED:', f.label)
process.exit(failed.length ? 1 : 0)
