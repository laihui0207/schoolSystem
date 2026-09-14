// Re-check only the ambiguous routes from the first sweep, this time with the
// query params those pages actually require. Delete after testing.
import { chromium } from '@playwright/test'
import fs from 'node:fs'

const ORIGIN = 'http://localhost:3000'
const TOKEN = process.env.TOKEN
const ids = JSON.parse(fs.readFileSync('/tmp/ids.json', 'utf8'))
const q = `examId=${ids.examId}&classId=${ids.classId}&subjectId=${ids.subjectIds[0]}`

const CASES = [
  ['/login', 'login form should render inputs'],
  ['/stat/subject-compare', 'was white-screen from missing export'],
  [`/score/input-table?${q}`, 'needs examId+classId+subjectId'],
  [`/score/review-detail?${q}`, 'needs examId+classId+subjectId'],
  [`/score/input`, 'exam/class/subject picker'],
]

const browser = await chromium.launch()
const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } })
await ctx.addInitScript(t => localStorage.setItem('token', t), TOKEN)

for (const [route, why] of CASES) {
  const page = await ctx.newPage()
  const pageErrors = [], apiFails = [], consoleErrors = []
  page.on('pageerror', e => pageErrors.push(String(e.message).slice(0, 160)))
  page.on('console', m => { if (m.type() === 'error') consoleErrors.push(m.text().slice(0, 160)) })
  page.on('response', r => {
    if (r.url().includes('/api/') && r.status() >= 400) apiFails.push(`${r.status()} ${r.url().replace(ORIGIN, '')}`)
  })

  let text = '', inputs = 0, landed = '', toasts = []
  try {
    await page.goto(ORIGIN + route, { waitUntil: 'networkidle', timeout: 25000 })
    await page.waitForTimeout(1500)
    landed = new URL(page.url()).pathname
    text = (await page.locator('body').innerText().catch(() => '')).replace(/\s+/g, ' ').trim()
    inputs = await page.locator('input, .el-select, table').count().catch(() => 0)
    toasts = await page.locator('.el-message--error').allInnerTexts().catch(() => [])
  } catch (e) {
    pageErrors.push('NAV_FAIL: ' + String(e.message).slice(0, 120))
  }

  const ok = !pageErrors.length && !apiFails.length && !toasts.length && text.length > 20
  console.log(`\n${ok ? 'OK     ' : 'PROBLEM'} ${route}`)
  console.log(`        (${why}) landed=${landed} interactive=${inputs} textLen=${text.length}`)
  if (text) console.log(`        text: ${text.slice(0, 130)}`)
  if (pageErrors.length) console.log('        UNCAUGHT: ' + pageErrors.join(' | '))
  if (apiFails.length) console.log('        API FAIL: ' + [...new Set(apiFails)].join(' | '))
  if (toasts.length) console.log('        TOAST: ' + toasts.join(' | '))
  if (consoleErrors.length) console.log('        CONSOLE: ' + [...new Set(consoleErrors)].slice(0, 2).join(' | '))
  await page.close()
}
await browser.close()
