// Temporary frontend page sweep. Visits every route with a seeded JWT and
// records console errors, uncaught exceptions, failed API calls, and whether
// the page rendered real content. Delete after testing.
import { chromium } from '@playwright/test'
import fs from 'node:fs'

const ORIGIN = 'http://localhost:3000'
const TOKEN = process.env.TOKEN
if (!TOKEN) { console.error('no TOKEN env'); process.exit(1) }

const ROUTES = [
  '/login', '/dashboard', '/school', '/term', '/grade', '/class', '/subject',
  '/teacher', '/student', '/exam', '/exam/detail/1', '/score', '/score/input',
  '/score/input-table', '/score/review-list', '/score/review-detail',
  '/stat', '/stat/exam-overview', '/stat/class-analysis', '/stat/student-trend',
  '/stat/subject-compare', '/system', '/system/user', '/system/dict', '/system/config'
]

const browser = await chromium.launch()
const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } })
// Seed the token so the router guard lets us through (guard only checks token).
await ctx.addInitScript(t => localStorage.setItem('token', t), TOKEN)

const report = []

for (const route of ROUTES) {
  const page = await ctx.newPage()
  const consoleErrors = []
  const pageErrors = []
  const apiFails = []

  page.on('console', m => {
    if (m.type() === 'error') consoleErrors.push(m.text().slice(0, 200))
  })
  page.on('pageerror', e => pageErrors.push(String(e.message).slice(0, 200)))
  page.on('response', r => {
    const u = r.url()
    if (u.includes('/api/') && r.status() >= 400) {
      apiFails.push(`${r.status()} ${u.replace(ORIGIN, '')}`)
    }
  })

  let landed = route
  let visibleText = ''
  let tableRows = 0
  let emptyBlocks = 0
  let elMessageErrors = []

  try {
    await page.goto(ORIGIN + route, { waitUntil: 'networkidle', timeout: 25000 })
    await page.waitForTimeout(1200) // let async loads settle
    landed = new URL(page.url()).pathname
    visibleText = (await page.locator('body').innerText().catch(() => '')).replace(/\s+/g, ' ').trim()
    tableRows = await page.locator('.el-table__body tbody tr').count().catch(() => 0)
    emptyBlocks = await page.locator('.el-empty, .el-table__empty-block').count().catch(() => 0)
    // Element Plus error toasts are how the app surfaces API failures to users
    elMessageErrors = await page.locator('.el-message--error').allInnerTexts().catch(() => [])
  } catch (e) {
    pageErrors.push('NAV_FAIL: ' + String(e.message).slice(0, 150))
  }

  const redirected = landed !== route && !(route === '/' )
  report.push({
    route, landed, redirected,
    consoleErrors, pageErrors, apiFails,
    elMessageErrors,
    tableRows, emptyBlocks,
    textLen: visibleText.length,
    textHead: visibleText.slice(0, 110)
  })
  await page.close()
}

await browser.close()
fs.writeFileSync('/tmp/pagesweep.json', JSON.stringify(report, null, 2))

// ---- print summary ----
const bad = r => r.pageErrors.length || r.apiFails.length || r.elMessageErrors.length || r.redirected || r.textLen < 40
console.log('='.repeat(78))
console.log('ROUTE SWEEP — 25 routes')
console.log('='.repeat(78))
for (const r of report) {
  const status = bad(r) ? 'PROBLEM' : 'OK     '
  console.log(`\n${status} ${r.route}${r.redirected ? `  [REDIRECTED -> ${r.landed}]` : ''}`)
  console.log(`        rows=${r.tableRows} empty=${r.emptyBlocks} textLen=${r.textLen}`)
  if (r.pageErrors.length) console.log('        UNCAUGHT: ' + r.pageErrors.join(' | '))
  if (r.apiFails.length) console.log('        API FAIL: ' + [...new Set(r.apiFails)].join(' | '))
  if (r.elMessageErrors.length) console.log('        USER-VISIBLE ERROR TOAST: ' + r.elMessageErrors.join(' | '))
  if (r.consoleErrors.length) console.log('        CONSOLE: ' + [...new Set(r.consoleErrors)].slice(0, 3).join(' | '))
}
const problems = report.filter(bad)
console.log('\n' + '='.repeat(78))
console.log(`TOTAL ${report.length} routes | clean ${report.length - problems.length} | PROBLEM ${problems.length}`)
console.log('problem routes: ' + problems.map(p => p.route).join(', '))
