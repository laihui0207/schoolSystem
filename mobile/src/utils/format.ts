/** 分数格式化：null/undefined → '—'，整数不带小数，其余保留 1 位 */
export const fmtScore = (v: number | null | undefined): string => {
  if (v === null || v === undefined || Number.isNaN(Number(v))) return '—'
  const n = Number(v)
  return Number.isInteger(n) ? String(n) : n.toFixed(1)
}

/** 名次：null → '—'，否则 '第 N 名' */
export const fmtRank = (v: number | null | undefined): string =>
  v === null || v === undefined ? '—' : `第 ${v} 名`

/**
 * 日期：'yyyy-MM-dd HH:mm:ss' / 'yyyy-MM-ddTHH:mm:ss' / 'yyyy-MM-dd' → 日期或 日期+HH:mm
 * ponytail: 实测 ScoreResponse.recordedAt 以 ISO 'T' 分隔返回，而通知 createdAt 是空格分隔，两种都要兼容
 */
export const fmtDate = (v: string | null | undefined, withTime = false): string => {
  if (!v) return ''
  const [date = '', time = ''] = v.split(/[ T]/)
  return withTime && time ? `${date} ${time.slice(0, 5)}` : date
}

/** 得分率百分比字符串 */
export const fmtPercent = (score: number | undefined, full: number | undefined): string => {
  if (score === undefined || full === undefined || full <= 0) return '—'
  return `${Math.round((score / full) * 100)}%`
}
