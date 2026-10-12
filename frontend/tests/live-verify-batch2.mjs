// Live verification for feature batch 2: parent role, RBAC, scheduled jobs, backup/restore, SMS register/login.
// Usage: node frontend/tests/live-verify-batch2.mjs   (backend on :8080, dev profile, seeded DB, Redis reachable via docker exec school-redis-6380)
import { execSync } from 'node:child_process'

const BASE = process.env.BASE ?? 'http://localhost:8080/api/v1'
const REDIS = process.env.REDIS_CONTAINER ?? 'school-redis-6380'
const S = Date.now().toString().slice(-6)
const results = []
const sleep = ms => new Promise(r => setTimeout(r, ms))

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
function check(label, pass, detail = '') { results.push({ label, pass }); console.log(`${pass ? 'PASS' : 'FAIL'}  ${label}${detail ? '  :: ' + detail : ''}`); return pass }
async function must(label, p) { const r = await p; check(label, ok(r), ok(r) ? '' : `http=${r.http} code=${r.code} ${r.msg ?? ''}`); return r.data }
async function mustFail(label, p, expectHttp, expectCode) {
  const r = await p
  const pass = !ok(r) && (expectHttp ? r.http === expectHttp : true) && (expectCode ? r.code === expectCode : true)
  check(label, pass, `http=${r.http} code=${r.code} ${r.msg ?? ''}`)
  return r
}
const login = async (username, password) => (await call('POST', '/auth/login', { body: { username, password, captcha: '', captchaKey: '' } })).data
const redisGet = key => { try { return execSync(`docker exec ${REDIS} redis-cli -a redis_pass_2024 --no-auth-warning GET "${key}"`, { encoding: 'utf8' }).trim().replace(/^"|"$/g, '') } catch { return '' } }
const smsCode = (scene, phone) => redisGet(`sms:code:${scene}:${phone}`)
async function pollJobLog(A, jobId, logId, tries = 15) {
  for (let i = 0; i < tries; i++) {
    await sleep(1000)
    const logs = (await call('GET', `/jobs/${jobId}/logs`, { ...A, query: { pageSize: 5 } })).data
    const row = (logs?.records ?? []).find(l => l.id === logId)
    if (row && row.status !== 2 && row.finishedAt) return row
  }
  return null
}
async function pollBackup(A, id, tries = 60) {
  for (let i = 0; i < tries; i++) {
    const r = (await call('GET', `/backups/${id}`, A)).data
    if (r && r.status !== 0) return r
    await sleep(3000)
  }
  return null
}

console.log('==== 0. setup: admin + school + class + student + published exam ====')
const adminLogin = await login('admin', 'admin123')
const A = { token: adminLogin.accessToken }
const school = await must('create school', call('POST', '/schools', { ...A, body: { schoolName: `批次二校${S}`, schoolCode: `B2${S}`, schoolType: '小学', address: 'x', contactPerson: 'y', contactPhone: '' } }))
const sid = school.id
const otherSchool = await must('create other school', call('POST', '/schools', { ...A, body: { schoolName: `批次二他校${S}`, schoolCode: `B2O${S}`, schoolType: '小学', address: 'x', contactPerson: 'y', contactPhone: '' } }))
const term = await must('create term', call('POST', '/terms', { ...A, body: { schoolId: sid, year: '2026-2027', term: 1, startDate: '2026-09-01', endDate: '2027-01-31' } }))
const grade = await must('create grade', call('POST', '/grades', { ...A, body: { schoolId: sid, gradeName: '五年级', gradeCode: 5 } }))
const subject = await must('create subject', call('POST', '/subjects', { ...A, body: { schoolId: sid, subjectName: '语文', subjectCode: `YW${S}`, fullScore: 100, passScore: 60 } }))
const schoolAdminUser = await must('create school admin user', call('POST', '/users', { ...A, body: { username: `sadm${S}`, password: 'Adm@123456', realName: '校管', role: 'admin', schoolId: sid, status: 1 } }))
const teacherUser = await must('create teacher user', call('POST', '/users', { ...A, body: { username: `tch${S}`, password: 'Tch@123456', realName: '王老师', role: 'teacher', schoolId: sid, status: 1 } }))
const teacher = await must('create teacher', call('POST', '/teachers', { ...A, body: { teacherNo: `T${S}`, schoolId: sid, name: '王老师', gender: 2, subjectId: subject.id, userId: teacherUser.id } }))
const cls = await must('create class', call('POST', '/classes', { ...A, body: { schoolId: sid, gradeId: grade.id, className: '五(1)班', classCode: 1, termId: term.id, headTeacherId: teacher.id } }))
await must('assign teacher', call('POST', `/teachers/${teacher.id}/classes`, { ...A, query: { classId: cls.id, subjectId: subject.id } }))
const stuUser = await must('create student user', call('POST', '/users', { ...A, body: { username: `stu${S}`, password: 'Stu@123456', realName: '甲同学', role: 'student', schoolId: sid, status: 1 } }))
const students = []
for (const [i, name] of ['甲同学', '乙同学'].entries()) {
  const st = await must(`create student ${name}`, call('POST', '/students', { ...A, body: { studentNo: `${S}${i + 1}`, schoolId: sid, name, gender: 1, birthDate: '2014-01-01', enrollmentDate: '2022-09-01', userId: i === 0 ? stuUser.id : undefined } }))
  students.push(st); await must(`assign ${name}`, call('POST', `/students/${st.id}/assign-class`, { ...A, query: { classId: cls.id } }))
}
const otherStudent = await must('create student in other school', call('POST', '/students', { ...A, body: { studentNo: `${S}9`, schoolId: otherSchool.id, name: '他校生', gender: 1 } }))
const exam = await must('create exam', call('POST', '/exams', { ...A, body: { schoolId: sid, termId: term.id, examName: `批次二期中${S}`, examType: 2, examDate: '2026-11-10', gradeIds: [grade.id], subjects: [{ subjectId: subject.id, fullScore: 100, sortOrder: 1 }] } }))
const eid = exam.id
await must('start-input', call('POST', `/exams/${eid}/start-input`, A))
await must('batch-input', call('POST', '/scores/batch-input', { ...A, body: { examId: eid, classId: cls.id, subjectId: subject.id, scores: students.map((st, i) => ({ studentId: st.id, score: 90 - i * 40, isAbsent: 0 })) } }))
await must('submit-review', call('POST', `/exams/${eid}/submit-review`, A))
await must('approve all', call('POST', '/scores/review', { ...A, body: { examId: eid, classId: cls.id, subjectId: subject.id, result: 1 } }))
const SA = { token: (await login(`sadm${S}`, 'Adm@123456')).accessToken }
const TT = { token: (await login(`tch${S}`, 'Tch@123456')).accessToken }
const ST = { token: (await login(`stu${S}`, 'Stu@123456')).accessToken }

console.log('==== A. parent role ====')
const parentUser = await must('create parent user', call('POST', '/users', { ...A, body: { username: `par${S}`, password: 'Par@123456', realName: '家长LV', role: 'parent', schoolId: sid, status: 1 } }))
check('parent user role/type', parentUser?.role === 'parent' && parentUser?.userType === 5, JSON.stringify({ role: parentUser?.role, t: parentUser?.userType }))
await mustFail('parent without schoolId → 400', call('POST', '/users', { ...A, body: { username: `parx${S}`, password: 'Par@123456', realName: 'x', role: 'parent' } }), undefined, 400)
const bind = await must('school admin binds child', call('POST', `/parents/${parentUser.id}/children`, { ...SA, query: { studentId: students[0].id, relationship: 'father' } }))
check('bind response', bind?.studentId === students[0].id && bind?.relationship === 'father' && bind?.relationshipName === '父亲' && !!bind?.className, JSON.stringify(bind))
await mustFail('bind duplicate → 7002', call('POST', `/parents/${parentUser.id}/children`, { ...SA, query: { studentId: students[0].id, relationship: 'father' } }), undefined, 7002)
await mustFail('bind bad relationship → 400', call('POST', `/parents/${parentUser.id}/children`, { ...SA, query: { studentId: students[1].id, relationship: 'uncle' } }), undefined, 400)
await mustFail('bind to non-parent user → 400', call('POST', `/parents/${teacherUser.id}/children`, { ...SA, query: { studentId: students[0].id } }), undefined, 400)
await mustFail('bind cross-school student → 400', call('POST', `/parents/${parentUser.id}/children`, { ...A, query: { studentId: otherStudent.id } }), undefined, 400)
const kids = await must('school admin lists children', call('GET', `/parents/${parentUser.id}/children`, SA))
check('one child listed', Array.isArray(kids) && kids.length === 1 && kids[0].studentId === students[0].id)
await mustFail('teacher lists children → 403', call('GET', `/parents/${parentUser.id}/children`, TT), 403)
await mustFail('student /parents/me/children → 1101', call('GET', '/parents/me/children', ST), undefined, 1101)
await mustFail('anonymous /parents/me/children → 401', call('GET', '/parents/me/children', {}), 401)
const pLogin = await login(`par${S}`, 'Par@123456')
const PT = { token: pLogin.accessToken }
check('parent login carries childStudentIds', pLogin?.userInfo?.userType === 5 && pLogin.userInfo.role === 'parent' && (pLogin.userInfo.childStudentIds ?? []).includes(students[0].id), JSON.stringify(pLogin?.userInfo))
const mine = await must('parent /me/children', call('GET', '/parents/me/children', PT))
check('parent sees own child', mine?.[0]?.studentId === students[0].id && !!mine[0].studentNo)
await mustFail('parent scores before publish (unpublished filtered)', call('GET', '/scores', { ...PT, query: { studentId: students[0].id, pageSize: 10 } }).then(r => ({ ...r, http: (r.data?.records?.length ?? 0) === 0 ? 400 : r.http, code: (r.data?.records?.length ?? 0) === 0 ? 0 : r.code })))
await must('publish exam', call('POST', `/exams/${eid}/publish`, SA))
const pScores = await must('parent scores of child', call('GET', '/scores', { ...PT, query: { studentId: students[0].id, pageSize: 10 } }))
check('parent only sees child rows', (pScores?.records ?? []).length === 1 && pScores.records[0].studentId === students[0].id, `rows=${pScores?.records?.length}`)
await mustFail('parent scores without studentId → 400', call('GET', '/scores', { ...PT, query: { pageSize: 10 } }), undefined, 400)
await mustFail('parent scores of other student → 1101', call('GET', '/scores', { ...PT, query: { studentId: students[1].id, pageSize: 10 } }), undefined, 1101)
const otherRow = (await must('admin lists scores', call('GET', '/scores', { ...A, query: { examId: eid, pageSize: 10 } }))).records.find(r => r.studentId === students[1].id)
await mustFail('parent GET other score by id → 1101', call('GET', `/scores/${otherRow.id}`, PT), undefined, 1101)
await must('parent GET child score by id', call('GET', `/scores/${pScores.records[0].id}`, PT))
await must('parent personal stat', call('GET', '/stats/personal', { ...PT, query: { examId: eid, studentId: students[0].id } }))
await mustFail('parent personal stat other → 1101', call('GET', '/stats/personal', { ...PT, query: { examId: eid, studentId: students[1].id } }), undefined, 1101)
await must('parent trend', call('GET', `/stats/student/${students[0].id}/trend`, PT))
await mustFail('parent trend other → 1101', call('GET', `/stats/student/${students[1].id}/trend`, PT), undefined, 1101)
await must('parent warning trend', call('GET', `/warnings/trend/${students[0].id}`, PT))
const ppdf = await call('GET', '/pdf/score-report', { ...PT, query: { examId: eid, studentId: students[0].id } })
check('parent downloads child PDF', ppdf.http === 200 && ppdf.ctype === 'application/pdf', `ctype=${ppdf.ctype} code=${ppdf.code}`)
await mustFail('parent other PDF → 1101', call('GET', '/pdf/score-report', { ...PT, query: { examId: eid, studentId: students[1].id } }), undefined, 1101)
await mustFail('parent class PDF → 1101', call('GET', '/pdf/class-reports', { ...PT, query: { examId: eid, classId: cls.id } }), undefined, 1101)
await mustFail('parent stats/class → 403', call('GET', '/stats/class', { ...PT, query: { examId: eid, classId: cls.id } }), 403)
await mustFail('parent export → denied', call('GET', '/export/scores', { ...PT, query: { examId: eid } }))
const pNotif = await must('parent notifications', call('GET', '/notifications', { ...PT, query: { pageSize: 20 } }))
check('parent got publish notification', (pNotif?.records ?? []).some(n => Number(n.type) === 1 && Number(n.relatedId) === eid), JSON.stringify((pNotif?.records ?? []).map(n => [n.type, n.relatedId])))
const sNotif = await must('student notifications', call('GET', '/notifications', { ...ST, query: { pageSize: 20 } }))
check('student also got publish notification', (sNotif?.records ?? []).some(n => Number(n.type) === 1 && Number(n.relatedId) === eid))
await must('unbind child', call('DELETE', `/parents/${parentUser.id}/children/${students[0].id}`, SA))
await mustFail('unbind again → 7001', call('DELETE', `/parents/${parentUser.id}/children/${students[0].id}`, SA), undefined, 7001)
await mustFail('parent scores after unbind → 1101 (no re-login)', call('GET', '/scores', { ...PT, query: { studentId: students[0].id, pageSize: 10 } }), undefined, 1101)
await must('rebind after unbind (partial unique index)', call('POST', `/parents/${parentUser.id}/children`, { ...SA, query: { studentId: students[0].id, relationship: 'mother' } }))
const bindLogs = await must('operation-logs 绑定家长学生', call('GET', '/operation-logs', { ...A, query: { operation: '绑定家长学生', pageSize: 5 } }))
check('bind audited', (bindLogs?.total ?? 0) >= 1 && bindLogs.records[0].operation.includes('绑定家长学生'))

console.log('==== B. RBAC ====')
await mustFail('anonymous /me/permissions → 401', call('GET', '/me/permissions', {}), 401)
const adminPerms = await must('admin /me/permissions', call('GET', '/me/permissions', A))
check('admin has all codes', Array.isArray(adminPerms) && ['sys:role:edit', 'score:review', 'stat:region'].every(c => adminPerms.includes(c)), `n=${adminPerms?.length}`)
const stuPerms = await must('student /me/permissions', call('GET', '/me/permissions', ST))
check('student perms scoped', stuPerms.includes('score:query') && stuPerms.includes('stat:personal') && !stuPerms.includes('score:input') && !stuPerms.includes('sys:role:list'), JSON.stringify(stuPerms))
const roles = await must('admin /roles', call('GET', '/roles', { ...A, query: { pageSize: 50 } }))
const builtin = ['ROLE_SUPER_ADMIN', 'ROLE_SCHOOL_ADMIN', 'ROLE_TEACHER', 'ROLE_STUDENT', 'ROLE_PARENT']
check('5 builtin roles flagged', builtin.every(c => (roles?.records ?? []).some(r => r.roleCode === c && r.isBuiltin === 1)), JSON.stringify((roles?.records ?? []).map(r => [r.roleCode, r.isBuiltin])))
const teacherRole = roles.records.find(r => r.roleCode === 'ROLE_TEACHER')
const tree = await must('permissions tree', call('GET', '/permissions/tree', A))
const flat = []; (function walk(ns) { for (const n of ns ?? []) { flat.push(n); walk(n.children) } })(tree)
const permId = code => flat.find(p => p.permissionCode === code)?.id
check('tree has sys:manage > sys:role > sys:role:edit', !!permId('sys:manage') && !!permId('sys:role:edit') && flat.find(p => p.permissionCode === 'sys:manage').children?.some(c => c.permissionCode === 'sys:role'), `nodes=${flat.length}`)
await mustFail('delete builtin role → 1101', call('DELETE', `/roles/${teacherRole.id}`, A), undefined, 1101)
await mustFail('change builtin roleCode → 1101', call('PUT', `/roles/${teacherRole.id}`, { ...A, body: { roleCode: 'teacher_v2' } }), undefined, 1101)
const upd = await must('update builtin description', call('PUT', `/roles/${teacherRole.id}`, { ...A, body: { description: '教师（live-check）' } }))
check('description updated, code kept', upd?.description === '教师（live-check）' && upd?.roleCode === 'ROLE_TEACHER')
await mustFail('ROLE_ prefix reserved → 400', call('POST', '/roles', { ...A, body: { roleCode: 'ROLE_QA', roleName: 'x' } }), undefined, 400)
await mustFail('bad roleCode pattern → 400', call('POST', '/roles', { ...A, body: { roleCode: 'bad code!', roleName: 'x' } }), 400)
const auditor = await must('create platform role', call('POST', '/roles', { ...A, body: { roleCode: `qa_auditor_${S}`, roleName: '审计员', description: 'live' } }))
check('platform role shape', auditor?.isBuiltin === 0 && auditor?.schoolId == null && auditor?.userCount === 0, JSON.stringify(auditor))
await must('assign 2 perms', call('PUT', `/roles/${auditor.id}/permissions`, { ...A, body: { permissionIds: [permId('score:query'), permId('stat:personal')] } }))
const rp = await must('read role perms', call('GET', `/roles/${auditor.id}/permissions`, A))
check('exactly those 2 codes', Array.isArray(rp) && rp.length === 2 && rp.map(p => p.permissionCode ?? p).sort().join(',') === 'score:query,stat:personal', JSON.stringify(rp))
await mustFail('invalid permission id → 400', call('PUT', `/roles/${auditor.id}/permissions`, { ...A, body: { permissionIds: [999999999] } }), undefined, 400)
await mustFail('attach builtin via user roles → 1101', call('PUT', `/users/${teacherUser.id}/roles`, { ...A, body: { roleCodes: ['ROLE_SCHOOL_ADMIN'] } }), undefined, 1101)
const ur = await must('attach custom role to teacher', call('PUT', `/users/${teacherUser.id}/roles`, { ...A, body: { roleCodes: [`qa_auditor_${S}`] } }))
// endpoint returns role objects, not bare codes
const urCodes = (ur ?? []).map(r => (typeof r === 'string' ? r : r.roleCode))
check('builtin kept + custom attached', urCodes.includes('ROLE_TEACHER') && urCodes.includes(`qa_auditor_${S}`), JSON.stringify(urCodes))
await mustFail('delete role with users → 7003', call('DELETE', `/roles/${auditor.id}`, A), undefined, 7003)
await must('detach custom role', call('PUT', `/users/${teacherUser.id}/roles`, { ...A, body: { roleCodes: [] } }))
await must('delete now-unused role', call('DELETE', `/roles/${auditor.id}`, A))
const saRoles = await must('school admin /roles', call('GET', '/roles', { ...SA, query: { pageSize: 100, schoolId: 1 } }))
check('school admin sees platform + own school only', (saRoles?.records ?? []).every(r => r.schoolId == null || r.schoolId === sid), JSON.stringify((saRoles?.records ?? []).map(r => r.schoolId)))
const schoolRole = await must('school admin creates role', call('POST', '/roles', { ...SA, body: { roleCode: `head_teacher_${S}`, roleName: '班主任', schoolId: 999999 } }))
check('forced to own school', schoolRole?.schoolId === sid && schoolRole?.isBuiltin === 0, JSON.stringify(schoolRole))
await mustFail('school admin assigns perm it lacks → 1101', call('PUT', `/roles/${schoolRole.id}/permissions`, { ...SA, body: { permissionIds: [permId('score:review'), permId('stat:region')] } }), undefined, 1101)
await must('school admin assigns owned perm', call('PUT', `/roles/${schoolRole.id}/permissions`, { ...SA, body: { permissionIds: [permId('score:review')] } }))
await mustFail('school admin edits platform role perms → 1101', call('PUT', `/roles/${teacherRole.id}/permissions`, { ...SA, body: { permissionIds: [] } }), undefined, 1101)
await must('school admin attaches school role to teacher', call('PUT', `/users/${teacherUser.id}/roles`, { ...SA, body: { roleCodes: [`head_teacher_${S}`] } }))
const tPerms = await must('teacher /me/permissions after attach', call('GET', '/me/permissions', TT))
check('teacher now has score:review', tPerms.includes('score:review'))
await mustFail('school admin reads cross-school user roles → 2001', call('GET', `/users/${otherStudent.userId ?? 1}/roles`, SA), undefined, 2001)
await mustFail('school admin creates permission → 403', call('POST', '/permissions', { ...SA, body: { permissionCode: 'qa:x', permissionName: 'x', resourceType: 3 } }), 403)
await mustFail('teacher /roles → 403', call('GET', '/roles', TT), 403)
await mustFail('teacher /permissions/tree → 403', call('GET', '/permissions/tree', TT), 403)
const perm = await must('admin creates permission', call('POST', '/permissions', { ...A, body: { permissionCode: `qa:live-${S}`, permissionName: 'live check', resourceType: 3, parentId: permId('sys:manage') } }))
await mustFail('duplicate permission → 7002', call('POST', '/permissions', { ...A, body: { permissionCode: `qa:live-${S}`, permissionName: 'live check', resourceType: 3 } }), undefined, 7002)
const moved = await must('move permission to root', call('PUT', `/permissions/${perm.id}`, { ...A, body: { parentId: 0, permissionName: 'live check 2' } }))
check('moved to root', moved?.parentId == null && moved?.permissionName === 'live check 2', JSON.stringify(moved))
await mustFail('self-parent → 400', call('PUT', `/permissions/${perm.id}`, { ...A, body: { parentId: perm.id } }), undefined, 400)
await mustFail('delete permission with children → 7003', call('DELETE', `/permissions/${permId('sys:manage')}`, A), undefined, 7003)
await must('delete test permission', call('DELETE', `/permissions/${perm.id}`, A))
const rbacLogs = await must('operation-logs 分配角色权限', call('GET', '/operation-logs', { ...A, query: { operation: '分配角色权限', pageSize: 5 } }))
check('rbac audited', (rbacLogs?.total ?? 0) >= 1 && (rbacLogs.records ?? []).some(r => r.method.startsWith('PUT') && r.method.includes('/roles/')))

console.log('==== C. scheduled jobs ====')
const jobs = await must('admin /jobs', call('GET', '/jobs', { ...A, query: { pageSize: 20 } }))
const byCode = c => (jobs?.records ?? []).find(j => j.jobCode === c)
check('3 seeded jobs disabled', ['cleanOperationLogs', 'scoreWarningScan', 'backupDatabase'].every(c => byCode(c) && byCode(c).status === 0), JSON.stringify((jobs?.records ?? []).map(j => [j.jobCode, j.status])))
await mustFail('school admin /jobs → 403', call('GET', '/jobs', SA), 403)
await mustFail('anonymous /jobs/handlers → 401', call('GET', '/jobs/handlers', {}), 401)
const handlers = await must('/jobs/handlers', call('GET', '/jobs/handlers', A))
check('handlers listed incl. backup', ['cleanOperationLogsHandler', 'scoreWarningScanHandler', 'backupJobHandler'].every(h => handlers.includes(h)), JSON.stringify(handlers))
const job = await must('create job', call('POST', '/jobs', { ...A, body: { jobName: '测试任务', jobCode: `liveTestJob${S}`, handlerBean: 'cleanOperationLogsHandler', cron: '0 0 4 * * ?', params: '{"retainDays":365}', status: 0, remark: 'live' } }))
await mustFail('duplicate jobCode → 7002', call('POST', '/jobs', { ...A, body: { jobName: '重复', jobCode: `liveTestJob${S}`, handlerBean: 'cleanOperationLogsHandler', cron: '0 0 4 * * ?', status: 0 } }), undefined, 7002)
await mustFail('bad cron → 402', call('POST', '/jobs', { ...A, body: { jobName: '坏cron', jobCode: `badCron${S}`, handlerBean: 'cleanOperationLogsHandler', cron: 'not a cron', status: 0 } }), undefined, 402)
await mustFail('bad handler → 402', call('POST', '/jobs', { ...A, body: { jobName: '坏handler', jobCode: `badHandler${S}`, handlerBean: 'noSuchHandler', cron: '0 0 4 * * ?', status: 0 } }), undefined, 402)
await mustFail('bad params json → 402', call('POST', '/jobs', { ...A, body: { jobName: '坏参数', jobCode: `badParams${S}`, handlerBean: 'cleanOperationLogsHandler', cron: '0 0 4 * * ?', params: '{bad', status: 0 } }), undefined, 402)
await must('enable job', call('PATCH', `/jobs/${job}/status`, { ...A, query: { status: 1 } }))
let j1 = (await call('GET', '/jobs', { ...A, query: { jobCode: `liveTestJob${S}` } })).data?.records?.[0]
check('enabled job has nextRunAt', j1?.status === 1 && !!j1?.nextRunAt, JSON.stringify({ s: j1?.status, next: j1?.nextRunAt }))
await must('disable job', call('PATCH', `/jobs/${job}/status`, { ...A, query: { status: 0 } }))
j1 = (await call('GET', '/jobs', { ...A, query: { jobCode: `liveTestJob${S}` } })).data?.records?.[0]
check('disabled job has no nextRunAt', j1?.status === 0 && !j1?.nextRunAt)
const logId = await must('run job now', call('POST', `/jobs/${job}/run`, A))
const logRow = await pollJobLog(A, job, logId)
check('run log success', logRow?.status === 1 && logRow?.statusName === '成功' && logRow?.triggeredBy === 'admin', JSON.stringify(logRow))
const scanLogId = await must('run scoreWarningScan now', call('POST', `/jobs/${byCode('scoreWarningScan').id}/run`, A))
const scanRow = await pollJobLog(A, byCode('scoreWarningScan').id, scanLogId, 30)
check('scan ran as system identity', scanRow?.status === 1 && /扫描 .* 起已发布考试 \d+ 场/.test(scanRow?.message ?? ''), JSON.stringify(scanRow))
const failed = await must('logs filter status=0', call('GET', `/jobs/${job}/logs`, { ...A, query: { status: 0, pageSize: 10 } }))
check('no failed logs for test job', (failed?.total ?? -1) === 0)
await must('update job', call('PUT', `/jobs/${job}`, { ...A, body: { jobName: '测试任务改', jobCode: `liveTestJob${S}b`, handlerBean: 'cleanOperationLogsHandler', cron: '0 30 4 * * ?', status: 1 } }))
j1 = (await call('GET', '/jobs', { ...A, query: { jobCode: `liveTestJob${S}b` } })).data?.records?.[0]
check('job updated & rescheduled', j1?.cron === '0 30 4 * * ?' && j1?.status === 1 && !!j1?.nextRunAt && !j1?.params, JSON.stringify(j1))
await must('delete job', call('DELETE', `/jobs/${job}`, A))
check('deleted job gone', ((await call('GET', '/jobs', { ...A, query: { jobCode: `liveTestJob${S}b` } })).data?.total ?? -1) === 0)
await mustFail('logs of deleted job → 7001', call('GET', `/jobs/${job}/logs`, A), undefined, 7001)
const jobLogs = await must('operation-logs 定时任务', call('GET', '/operation-logs', { ...A, query: { operation: '定时任务', pageSize: 20 } }))
check('job writes audited', (jobLogs?.records ?? []).some(r => r.operation.includes('新建定时任务')) && (jobLogs?.records ?? []).some(r => r.operation.includes('立即执行')))

console.log('==== D. backup / restore ====')
await mustFail('anonymous /backups → 401', call('GET', '/backups', {}), 401)
await mustFail('school admin /backups → 403', call('GET', '/backups', SA), 403)
await mustFail('teacher POST /backups → 403', call('POST', '/backups', TT), 403)
const started = await must('manual backup', call('POST', '/backups', A))
check('backup record initial state', started?.status === 0 && started?.backupType === 1 && started?.triggeredBy === 'admin', JSON.stringify(started))
const done = await pollBackup(A, started.id)
check('backup finished OK (auto → docker or sql)', done?.status === 1 && done?.fileSize > 0 && ['docker', 'pg_dump', 'sql'].includes(done?.backupMode), JSON.stringify(done))
const dl = await call('GET', `/backups/${started.id}/download`, A)
check('download size matches record', dl.http === 200 && dl.size === done?.fileSize && dl.disposition.includes('attachment'), `http=${dl.http} size=${dl.size} rec=${done?.fileSize}`)
await mustFail('restore without confirm → 400', call('POST', `/backups/${started.id}/restore`, { ...A, body: {} }), 400)
if (done?.backupMode !== 'sql') {
  await mustFail('restore of pg_dump artifact → needs pg_restore', call('POST', `/backups/${started.id}/restore`, { ...A, body: { confirm: 'RESTORE' } }))
}
const upBad = new FormData(); upBad.append('file', new Blob(['not gzip'], { type: 'text/plain' }), 'bad.sql.gz')
await mustFail('upload non-gzip → 8002', call('POST', '/backups/upload', { ...A, form: upBad }), undefined, 8002)
const backupJobLogId = await must('run backupDatabase job', call('POST', `/jobs/${byCode('backupDatabase').id}/run`, A))
const bjRow = await pollJobLog(A, byCode('backupDatabase').id, backupJobLogId, 90)
check('backup job log OK', bjRow?.status === 1 && String(bjRow?.message).startsWith('备份完成'), JSON.stringify(bjRow))
const jobBackups = await must('backups filter type=2', call('GET', '/backups', { ...A, query: { status: 1, backupType: 2 } }))
check('job backup recorded', (jobBackups?.records ?? []).length >= 1 && jobBackups.records.every(r => r.status === 1 && r.backupType === 2))
for (const b of [started, ...(jobBackups?.records ?? [])]) await call('DELETE', `/backups/${b.id}`, A)
await mustFail('deleted backup → 7001', call('GET', `/backups/${started.id}`, A), undefined, 7001)

console.log('==== E. SMS register / login ====')
const schools = await must('anonymous /auth/schools', call('GET', '/auth/schools', {}))
check('public schools: id+name only, includes ours', (schools ?? []).some(s => s.id === sid) && schools.every(s => !('contactPhone' in s) && !('address' in s)), `n=${schools?.length}`)
const phone = `139${S}01`
const send1 = await must('send register code', call('POST', '/auth/sms/send', { body: { phone, scene: 'register' } }))
check('send response', send1?.expiresIn === 300 && send1?.sendInterval === 60, JSON.stringify(send1))
let code = smsCode('register', phone)
check('code stored in redis (6 digits)', /^\d{6}$/.test(code), `code=${code}`)
await mustFail('resend within interval → 400', call('POST', '/auth/sms/send', { body: { phone, scene: 'register' } }), undefined, 400)
await mustFail('bad phone → 400', call('POST', '/auth/sms/send', { body: { phone: '12345', scene: 'register' } }), 400)
await mustFail('bad scene → 400', call('POST', '/auth/sms/send', { body: { phone: `139${S}09`, scene: 'hack' } }), 400)
await mustFail('register numeric-only password → 400', call('POST', '/auth/register', { body: { phone, code, password: '12345678', realName: '甲', role: 'student', schoolId: sid } }), 400)
await mustFail('register teacher role → 400', call('POST', '/auth/register', { body: { phone, code, password: 'abc12345', realName: '甲', role: 'teacher', schoolId: sid } }), 400)
await mustFail('register without school → 400', call('POST', '/auth/register', { body: { phone, code, password: 'abc12345', realName: '甲', role: 'student' } }), 400)
const unbound = await must('create unbound student for binding test', call('POST', '/students', { ...A, body: { studentNo: `SMS${S}`, schoolId: sid, name: '绑定测试', gender: 1 } }))
const reg = await must('register student with matching studentNo', call('POST', '/auth/register', { body: { phone, code, password: 'abc12345', realName: '绑定测试', role: 'student', schoolId: sid, studentNo: `SMS${S}` } }))
check('register logs in and binds profile', !!reg?.accessToken && reg?.userInfo?.username === phone && reg.userInfo.role === 'student' && reg.userInfo.studentId === unbound.id, JSON.stringify(reg?.userInfo))
// a fresh register code for the same phone is still inside the 60 s resend window; the duplicate-phone
// check must fire before any code is consumed, so send to a second scene-free attempt after the window
await sleep(61000)
await must('resend register code after interval', call('POST', '/auth/sms/send', { body: { phone, scene: 'register' } }))
await mustFail('register same phone again → 2005', call('POST', '/auth/register', { body: { phone, code: smsCode('register', phone), password: 'abc12345', realName: 'x', role: 'student', schoolId: sid } }), undefined, 2005)
const loginSend = await call('POST', '/auth/sms/send', { body: { phone, scene: 'login' } })
check('send login code (interval is per phone+scene or expired)', ok(loginSend) || loginSend.code === 400, `code=${loginSend.code}`)
if (!ok(loginSend)) { await sleep(61000); await must('send login code after interval', call('POST', '/auth/sms/send', { body: { phone, scene: 'login' } })) }
code = smsCode('login', phone)
for (let i = 1; i <= 4; i++) await mustFail(`wrong login code #${i} → 1005`, call('POST', '/auth/login/phone', { body: { phone, code: '000000' } }), undefined, 1005)
const pl = await must('phone login with right code', call('POST', '/auth/login/phone', { body: { phone, code } }))
check('phone login shape = password login', !!pl?.accessToken && !!pl?.refreshToken && pl.tokenType === 'Bearer' && pl.userInfo?.username === phone, JSON.stringify(pl?.userInfo))
const otherPhone = `139${S}02`
await must('send login code to unregistered', call('POST', '/auth/sms/send', { body: { phone: otherPhone, scene: 'login' } }))
await mustFail('phone login unregistered → 2001', call('POST', '/auth/login/phone', { body: { phone: otherPhone, code: smsCode('login', otherPhone) } }), undefined, 2001)
await must('password login works for self-registered', call('POST', '/auth/login', { body: { username: phone, password: 'abc12345', captcha: '', captchaKey: '' } }))
const parentPhone = `139${S}03`
await must('send register code (parent)', call('POST', '/auth/sms/send', { body: { phone: parentPhone, scene: 'register' } }))
const preg = await must('register parent', call('POST', '/auth/register', { body: { phone: parentPhone, code: smsCode('register', parentPhone), password: 'abc12345', realName: '家长注册', role: 'parent', schoolId: sid } }))
check('parent registered', preg?.userInfo?.role === 'parent' && preg.userInfo.userType === 5 && preg.userInfo.studentId == null, JSON.stringify(preg?.userInfo))
await mustFail('anonymous GET /users still 401', call('GET', '/users', {}), 401)
const smsLogs = await must('operation-logs 自助注册', call('GET', '/operation-logs', { ...A, query: { operation: '自助注册', pageSize: 5 } }))
check('register audited with password masked', (smsLogs?.records ?? []).some(r => String(r.params).includes('***') && String(r.params).includes(phone) && !String(r.params).includes('abc12345')), JSON.stringify(smsLogs?.records?.[0]?.params).slice(0, 160))

const failedChecks = results.filter(r => !r.pass)
console.log(`\n==== SUMMARY: ${results.length - failedChecks.length}/${results.length} passed ====`)
for (const f of failedChecks) console.log('  FAILED:', f.label)
process.exit(failedChecks.length ? 1 : 0)
