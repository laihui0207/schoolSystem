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

// 2. Term — spans today (2026-09-12) so /terms/current has something to find
const term = await step('create term', 'POST', '/terms', {
  body: {
    schoolId: ids.schoolId, year: '2026-2027', term: 1,
    startDate: '2026-09-01', endDate: '2027-01-31'
  }
})
ids.termId = term?.id

// 3. Grade
const grade = await step('create grade', 'POST', '/grades', {
  body: { schoolId: ids.schoolId, gradeName: '三年级', gradeCode: 3 }
})
ids.gradeId = grade?.id

// 4. Subjects
ids.subjectIds = []
for (const [name, code] of [['语文', 'CHINESE'], ['数学', 'MATH'], ['英语', 'ENGLISH']]) {
  const s = await step(`create subject ${name}`, 'POST', '/subjects', {
    body: {
      subjectName: name, subjectCode: `${code}${S}`,
      fullScore: 100, passScore: 60, schoolId: ids.schoolId
    }
  })
  if (s?.id) ids.subjectIds.push(s.id)
}

// 5. Teacher
const teacher = await step('create teacher', 'POST', '/teachers', {
  body: {
    teacherNo: `T${S}`, schoolId: ids.schoolId, name: '李老师',
    gender: 2, subjectId: ids.subjectIds[0]
  }
})
ids.teacherId = teacher?.id

// 6. Class (needs school + grade + term)
const klass = await step('create class', 'POST', '/classes', {
  body: {
    schoolId: ids.schoolId, gradeId: ids.gradeId, className: '三年级1班',
    classCode: 1, termId: ids.termId, headTeacherId: ids.teacherId
  }
})
ids.classId = klass?.id

// 7. Students + class assignment (stats need several students to be meaningful)
ids.studentIds = []
const names = ['王小明', '李小红', '张小刚', '刘小美', '陈小强']
for (let i = 0; i < names.length; i++) {
  const st = await step(`create student ${names[i]}`, 'POST', '/students', {
    body: {
      studentNo: `S${S}${i}`, schoolId: ids.schoolId, name: names[i],
      gender: i % 2 === 0 ? 1 : 2, birthDate: '2017-05-10', enrollmentDate: '2023-09-01'
    }
  })
  if (st?.id) {
    ids.studentIds.push(st.id)
    await step(`assign ${names[i]} to class`, 'POST', `/students/${st.id}/assign-class`,
      { query: { classId: ids.classId } })
  }
}

// 8. Exam (needs school + term + grades + subjects)
const exam = await step('create exam', 'POST', '/exams', {
  body: {
    schoolId: ids.schoolId, termId: ids.termId, examName: `期中考试${S}`,
    examType: 1, examDate: '2026-11-10',
    gradeIds: [ids.gradeId],
    subjects: ids.subjectIds.map((sid, i) => ({ subjectId: sid, fullScore: 100, sortOrder: i + 1 }))
  }
})
ids.examId = exam?.id

// 9. Exam status flow: publish -> start score input
if (ids.examId) {
  await step('publish exam', 'POST', `/exams/${ids.examId}/publish`)
  await step('start score input', 'POST', `/exams/${ids.examId}/start-input`)
}

// 10. Batch score input (the core business path)
if (ids.examId && ids.studentIds.length && ids.subjectIds.length) {
  const scores = []
  ids.studentIds.forEach((stId, si) => {
    ids.subjectIds.forEach((subId, bi) => {
      scores.push({
        studentId: stId, subjectId: subId, classId: ids.classId,
        schoolId: ids.schoolId,
        score: 60 + ((si * 7 + bi * 11) % 40), // spread 60-99 for real stats
        isAbsent: 0
      })
    })
  })
  await step('batch input scores', 'POST', '/scores/batch-input',
    { body: { examId: ids.examId, scores } })
  await step('calculate rankings', 'POST', '/scores/calculate-rankings',
    { query: { examId: ids.examId } })
}

// 11. Dict + config + notification (independent modules)
const dt = await step('create dict type', 'POST', '/dict/types', {
  body: { dictName: `测试字典${S}`, dictType: `test_dict_${S}`, status: 1, remark: '功能测试' }
})
ids.dictTypeId = dt?.id
if (ids.dictTypeId) {
  const dd = await step('create dict data', 'POST', '/dict/data', {
    body: {
      dictType: `test_dict_${S}`, dictLabel: '选项A', dictValue: 'A',
      dictSort: 1, status: 1
    }
  })
  ids.dictDataId = dd?.id
}

const cfg = await step('create config', 'POST', '/configs', {
  body: {
    configName: `测试配置${S}`, configKey: `test.key.${S}`,
    configValue: 'test-value', configType: 1, remark: '功能测试'
  }
})
ids.configId = cfg?.id

const notif = await step('create notification', 'POST', '/notifications', {
  body: {
    title: `测试通知${S}`, content: '这是一条功能测试通知',
    type: 1, receiverId: 4, senderId: 4
  }
})
ids.notificationId = notif?.id

// 12. User (system module)
const user = await step('create user', 'POST', '/users', {
  body: {
    username: `testuser${S}`, password: 'Test@123456', realName: '测试用户',
    phone: '13900139001', userType: 2, schoolId: ids.schoolId, status: 1
  }
})
ids.userId = user?.id

console.log('\n======== SEED SUMMARY ========')
const failed = log.filter(l => !l.ok)
console.log(`total writes: ${log.length}, ok: ${log.length - failed.length}, failed: ${failed.length}`)
if (failed.length) {
  console.log('\nFAILED WRITES:')
  for (const f of failed) console.log(`  ${f.method} ${f.path} :: http=${f.http} code=${f.code} ${f.msg || ''}`)
}
const fs = await import('node:fs')
fs.writeFileSync('/tmp/ids.json', JSON.stringify(ids, null, 2))
fs.writeFileSync('/tmp/seed-log.json', JSON.stringify(log, null, 2))
console.log('\nids -> /tmp/ids.json')
console.log(JSON.stringify(ids))

