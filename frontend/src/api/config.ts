import request from '@/utils/request'

export interface SystemConfig {
  id?: number
  configName: string
  configKey: string
  configValue: string
  configType?: number
  remark?: string
}

/** @deprecated use SystemConfig */
export type SysConfig = SystemConfig

/**
 * 根据 key 获取配置值
 */
export const getConfigByKey = (configKey: string) => {
  return request({
    url: `/config/key/${configKey}`,
    method: 'get'
  })
}

/**
 * 查询所有配置
 */
export const getConfigList = () => {
  return request({
    url: '/config',
    method: 'get'
  })
}

/**
 * 创建配置
 */
export const createConfig = (data: SystemConfig) => {
  return request({
    url: '/config',
    method: 'post',
    data
  })
}

/**
 * 更新配置
 */
export const updateConfig = (id: number, data: SystemConfig) => {
  return request({
    url: `/config/${id}`,
    method: 'put',
    data
  })
}

/**
 * 删除配置
 */
export const deleteConfig = (id: number) => {
  return request({
    url: `/config/${id}`,
    method: 'delete'
  })
}
