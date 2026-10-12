import request from '@/utils/request'

export interface DictType {
  id?: number
  dictName: string
  dictType: string
  status: 0 | 1  // 0-正常 1-停用
  remark?: string
  createdAt?: string
}

export interface DictData {
  id?: number
  dictType: string
  dictLabel: string
  dictValue: string
  dictSort: number
  status: 0 | 1  // 0-正常 1-停用
  remark?: string
  createdAt?: string
}

/**
 * 获取字典类型列表
 */
export const getDictTypeList = () => {
  return request({
    url: '/dict/types',
    method: 'get'
  })
}

/**
 * 创建字典类型
 */
export const createDictType = (data: DictType) => {
  return request({
    url: '/dict/types',
    method: 'post',
    data
  })
}

/**
 * 更新字典类型
 */
export const updateDictType = (id: number, data: DictType) => {
  return request({
    url: `/dict/types/${id}`,
    method: 'put',
    data
  })
}

/**
 * 删除字典类型
 */
export const deleteDictType = (id: number) => {
  return request({
    url: `/dict/types/${id}`,
    method: 'delete'
  })
}

/**
 * 根据字典类型获取字典数据列表（仅 status=0，按 dictSort 升序）
 */
export const getDictDataList = (dictType: string) => {
  return request<DictData[]>({
    url: `/dict/data/${dictType}`,
    method: 'get'
  })
}

/**
 * 一次获取多种字典数据：GET /dict/data/batch?types=a,b,c
 * 返回 { [dictType]: DictData[] }；请求过的类型没有数据时也会出现（空数组）。
 * 后端单次最多接受 20 种类型。
 */
export const getDictDataBatch = (types: string[]) => {
  return request<Record<string, DictData[]>>({
    url: '/dict/data/batch',
    method: 'get',
    params: { types: types.join(',') }
  })
}

/**
 * 创建字典数据
 */
export const createDictData = (data: DictData) => {
  return request({
    url: '/dict/data',
    method: 'post',
    data
  })
}

/**
 * 更新字典数据
 */
export const updateDictData = (id: number, data: DictData) => {
  return request({
    url: `/dict/data/${id}`,
    method: 'put',
    data
  })
}

/**
 * 删除字典数据
 */
export const deleteDictData = (id: number) => {
  return request({
    url: `/dict/data/${id}`,
    method: 'delete'
  })
}

