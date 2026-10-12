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

/** 内置配置项的业务说明（后端 V8 种子的 5 个 key） */
export interface BuiltinConfigMeta {
  /** 这个值在业务里怎么用 */
  description: string
  /** 生效范围：影响哪些功能 */
  scope: string
  /** 合法取值提示 */
  format: string
}

/**
 * ponytail: sys_config 没有 description/scope 列，这份说明只能放前端；
 * key 与后端 ScoreRuleServiceImpl / ScoreWarningServiceImpl 读取的常量一一对应，改动要同步。
 * 后端 ScoreRuleService 对这些键有 60 秒本地缓存，但管理页保存时会立即清缓存，新值即时生效。
 */
export const BUILTIN_CONFIG_META: Readonly<Record<string, BuiltinConfigMeta>> = {
  'score.warning.threshold': {
    description: '单科较上一次考试下降超过该分值时，发布成绩后自动给学生发成绩预警',
    scope: '成绩预警（发布成绩时）',
    format: '分值，如 20'
  },
  'score.pass.line': {
    description: '及格线百分比。统计模块按「值 / 100 × 科目满分」折算各科及格线',
    scope: '统计分析、成绩预警、AI 助手',
    format: '0-100 的百分数，如 60'
  },
  'score.excellent.line': {
    description: '优秀线百分比。统计模块按「值 / 100 × 科目满分」折算各科优秀线',
    scope: '统计分析、AI 助手',
    format: '0-100 的百分数，如 85'
  },
  'score.change.approval': {
    description: '是否启用成绩修改审批。关闭后管理员提交的修改申请免审批直接生效并重算排名；教师申请仍需审批',
    scope: '成绩变更申请',
    format: 'true / false'
  },
  'system.maintenance': {
    description: '系统维护模式。开启后仅超级管理员和校管理员可登录，其他用户登录时提示"系统维护中"',
    scope: '登录',
    format: 'true / false'
  }
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
