import axios from 'axios'

function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}

function authHeaders() {
  const token = localStorage.getItem('token')
  return token ? { Authorization: `Bearer ${token}` } : {}
}

export async function exportScores(params: { examId: number; classId?: number }) {
  const res = await axios.get('/api/export/scores', {
    params,
    responseType: 'blob',
    headers: authHeaders()
  })
  downloadBlob(res.data, `成绩导出_${Date.now()}.xlsx`)
}

export async function downloadScoreTemplate() {
  const res = await axios.get('/api/export/score-template', {
    responseType: 'blob',
    headers: authHeaders()
  })
  downloadBlob(res.data, '成绩导入模板.xlsx')
}

export async function importScores(params: { examId: number; classId: number; schoolId: number }, file: File) {
  const form = new FormData()
  form.append('examId', String(params.examId))
  form.append('classId', String(params.classId))
  form.append('schoolId', String(params.schoolId))
  form.append('file', file)
  const res = await axios.post('/api/export/import-scores', form, {
    headers: { ...authHeaders(), 'Content-Type': 'multipart/form-data' }
  })
  return res.data
}
