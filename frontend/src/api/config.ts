import request from '@/utils/request'

/**
 * 对应后端 SysConfig 实体
 * configType: 0=非内置, 1=系统内置（不可删除）
 * 后端无 description 字段，备注用 remark
 */
export interface SystemConfig {
  id?: number
  configName: string
  configKey: string
  configValue: string
  configType?: number
  remark?: string
  createdAt?: string
}

/** @deprecated use SystemConfig */
export type SysConfig = SystemConfig

export interface ConfigQuery {
  configName?: string
  configKey?: string
}

/**
 * 根据 key 获取配置值
 * 后端 GET /configs/key/{configKey} 返回 Result<String>
 */
export const getConfigByKey = (configKey: string) => {
  return request<string>({
    url: `/configs/key/${configKey}`,
    method: 'get'
  })
}

/**
 * ponytail: GET /configs 返回 List<SysConfig>，直接是数组，不分页
 */
export const getConfigList = () => {
  return request<SystemConfig[]>({
    url: '/configs',
    method: 'get'
  })
}

export const createConfig = (data: SystemConfig) => {
  return request({
    url: '/configs',
    method: 'post',
    data
  })
}

export const updateConfig = (id: number, data: SystemConfig) => {
  return request({
    url: `/configs/${id}`,
    method: 'put',
    data
  })
}

export const deleteConfig = (id: number) => {
  return request({
    url: `/configs/${id}`,
    method: 'delete'
  })
}
