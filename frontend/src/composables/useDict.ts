import { ref, type Ref } from 'vue'
import { getDictDataBatch, getDictDataList, type DictData } from '@/api/dict'

/** 下拉/展示用的字典选项 */
export interface DictOption {
  label: string
  value: string | number
}

export interface UseDictOptions {
  /**
   * 把 dict_value（后端一律是字符串）转成数字。
   * 缺省时若默认选项里有数字值则自动开启——业务枚举（考试类型、性别、学籍状态）在前端都是 number。
   */
  numeric?: boolean
}

export interface DictHandle {
  /** 选项列表：加载前/失败/字典为空时为调用方传入的默认选项 */
  options: Ref<DictOption[]>
  loading: Ref<boolean>
  /** 按值取标签；找不到返回 fallbackText（缺省为值本身的字符串） */
  label: (value: string | number | null | undefined, fallbackText?: string) => string
  /** 忽略缓存重新拉取 */
  reload: () => Promise<void>
}

interface CacheEntry {
  options: Ref<DictOption[]>
  loading: Ref<boolean>
  /** 后端原始行，numeric 变化时可重新换算 */
  rows: DictData[]
  loaded: boolean
  pending: Promise<void> | null
  fallback: ReadonlyArray<DictOption>
  numeric: boolean
}

// ponytail: 模块级缓存——同一字典在列表页、弹窗、详情页各自调用 useDict 时共享同一个 Ref，
// 整个会话只请求一次；不放 Pinia 是因为它只是只读的参照数据，不需要持久化也不需要跨组件写。
const cache = new Map<string, CacheEntry>()

const toOptions = (rows: DictData[], numeric: boolean): DictOption[] =>
  rows.map((row) => {
    const asNumber = Number(row.dictValue)
    const convertible = numeric && row.dictValue !== '' && !Number.isNaN(asNumber)
    return { label: row.dictLabel, value: convertible ? asNumber : row.dictValue }
  })

/** 用原始行 + 默认选项重新计算 options：字典为空视为"没配"，回落默认表 */
const applyRows = (entry: CacheEntry) => {
  const converted = toOptions(entry.rows, entry.numeric)
  entry.options.value = converted.length > 0 ? converted : [...entry.fallback]
}

const getOrCreate = (type: string, fallback: ReadonlyArray<DictOption>, numeric: boolean): CacheEntry => {
  const existing = cache.get(type)
  if (existing) {
    // 后来的调用方给了更完整的默认表或要求数字值时，以它为准重新换算
    const fallbackChanged = fallback.length > 0 && existing.fallback.length === 0
    const numericChanged = numeric && !existing.numeric
    if (fallbackChanged) existing.fallback = fallback
    if (numericChanged) existing.numeric = true
    if (fallbackChanged || numericChanged) applyRows(existing)
    return existing
  }
  const entry: CacheEntry = {
    options: ref<DictOption[]>([...fallback]),
    loading: ref(false),
    rows: [],
    loaded: false,
    pending: null,
    fallback,
    numeric
  }
  cache.set(type, entry)
  return entry
}

const load = (type: string, entry: CacheEntry): Promise<void> => {
  if (entry.pending) return entry.pending
  entry.loading.value = true
  entry.pending = getDictDataList(type)
    .then((rows) => {
      entry.rows = Array.isArray(rows) ? rows : []
      entry.loaded = true
      applyRows(entry)
    })
    .catch(() => {
      // ponytail: 字典接口失败不能拖垮业务页，回落默认选项；HTTP 层提示由 request 拦截器统一弹，这里不重复
      entry.rows = []
      applyRows(entry)
    })
    .finally(() => {
      entry.loading.value = false
      entry.pending = null
    })
  return entry.pending
}

/**
 * 读取一种字典：useDict('exam_type', 默认选项)
 *
 * - 首次调用触发 GET /dict/data/{type}，后续调用复用缓存
 * - 请求失败或字典为空时 options 为传入的默认选项
 */
export function useDict(
  type: string,
  fallback: ReadonlyArray<DictOption> = [],
  opts: UseDictOptions = {}
): DictHandle {
  const numeric = opts.numeric ?? fallback.some((o) => typeof o.value === 'number')
  const entry = getOrCreate(type, fallback, numeric)
  if (!entry.loaded && !entry.pending) {
    void load(type, entry)
  }

  const label: DictHandle['label'] = (value, fallbackText) => {
    if (value === null || value === undefined || value === '') return fallbackText ?? ''
    const key = String(value)
    const hit = entry.options.value.find((o) => String(o.value) === key)
    return hit?.label ?? fallbackText ?? key
  }

  const reload = () => {
    entry.loaded = false
    return load(type, entry)
  }

  return { options: entry.options, loading: entry.loading, label, reload }
}

/**
 * 一次预热多种字典（走 GET /dict/data/batch），供需要多个下拉的表单页在 onMounted 调一次。
 * 之后对这些类型调用 useDict 不再发请求。失败时静默，各 useDict 会各自按需加载并回落。
 */
export async function preloadDicts(types: string[], numericTypes: string[] = []): Promise<void> {
  const missing = types.filter((t) => {
    const entry = cache.get(t)
    return !entry || (!entry.loaded && !entry.pending)
  })
  if (missing.length === 0) return
  try {
    const grouped = await getDictDataBatch(missing)
    missing.forEach((t) => {
      const entry = getOrCreate(t, [], numericTypes.includes(t))
      entry.rows = Array.isArray(grouped?.[t]) ? grouped[t] : []
      entry.loaded = true
      applyRows(entry)
    })
  } catch {
    // 预热失败不是错误：业务页会按需单独加载
  }
}

/** 测试/登出时清空缓存 */
export function clearDictCache(): void {
  cache.clear()
}
