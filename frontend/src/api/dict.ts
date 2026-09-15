import request from '@/utils/request'

export interface DictType {
  id?: number
  dictType: string
  dictName: string
  remark?: string
}

export interface DictData {
  id?: number
  dictType: string
  dictLabel: string
  dictValue: string
  sort?: number
  remark?: string
}

/**
 * 查询所有字典类型
 */
export const getDictTypeList = () => {
  return request({
    url: '/dict/type',
    method: 'get'
  })
}

/**
 * 根据字典类型查询字典数据
 */
export const getDictDataByType = (dictType: string) => {
  return request({
    url: `/dict/data/${dictType}`,
    method: 'get'
  })
}

/**
 * 创建字典类型
 */
export const createDictType = (data: DictType) => {
  return request({
    url: '/dict/type',
    method: 'post',
    data
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
 * 更新字典类型
 */
export const updateDictType = (id: number, data: DictType) => {
  return request({
    url: `/dict/type/${id}`,
    method: 'put',
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
 * 删除字典类型
 */
export const deleteDictType = (id: number) => {
  return request({
    url: `/dict/type/${id}`,
    method: 'delete'
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
