import api from './api'

export const spcService = {
  /** 拿指定爐所有參數的 baseline */
  async getBaselines(furnaceId) {
    const res = await api.get(`/spc/baseline`, { params: { furnaceId } })
    return res.data
  },

  /** 拿指定爐 + 參數的 baseline */
  async getBaseline(furnaceId, paramName) {
    const res = await api.get(`/spc/baseline/one`, { params: { furnaceId, paramName } })
    return res.data
  },

  /** 拿可用參數清單 */
  async getParams() {
    const res = await api.get(`/spc/params`)
    return res.data
  },

  /** 拿最近的 violations */
  async getRecentViolations(minutes = 60) {
    const res = await api.get(`/spc/violation/recent`, { params: { minutes } })
    return res.data
  },

  /** 拿某爐的 violations */
  async getViolationsByFurnace(furnaceId, minutes = 60) {
    const res = await api.get(`/spc/violation/byFurnace`, {
      params: { furnaceId, minutes }
    })
    return res.data
  },

  /** Rule 觸發統計 */
  async getStatistics(minutes = 1440, furnaceId = null) {
    const params = { minutes }
    if (furnaceId) params.furnaceId = furnaceId
    const res = await api.get(`/spc/violation/statistics`, { params })
    return res.data
  },

  /** 手動觸發 baseline 重算（ADMIN/ENGINEER）*/
  async rebuildBaseline() {
    const res = await api.post(`/spc/baseline/rebuild`)
    return res.data
  },

  /** 拿某爐某參數的最近 N 分鐘所有點 */
  async getTimeseries(furnaceId, paramName, minutes = 60) {
    const res = await api.get(`/spc/timeseries`, {
      params: { furnaceId, paramName, minutes }
    })
    return res.data
  },
}

/** 8 條 Western Electric Rules 對照表 */
export const SPC_RULES = {
  1: { name: '1 point outside 3σ', severity: 'CRITICAL', color: '#ff4d4f' },
  2: { name: '9 points on same side', severity: 'WARN', color: '#fa8c16' },
  3: { name: '6 points trending', severity: 'WARN', color: '#faad14' },
  4: { name: '14 points alternating', severity: 'WARN', color: '#fadb14' },
  5: { name: '2 of 3 beyond 2σ', severity: 'WARN', color: '#a0d911' },
  6: { name: '4 of 5 beyond 1σ', severity: 'WARN', color: '#52c41a' },
  7: { name: '15 points within 1σ', severity: 'WARN', color: '#13c2c2' },
  8: { name: '8 points beyond 1σ', severity: 'WARN', color: '#1890ff' }
}

/** 6 個 SPC 監測參數 */
export const SPC_PARAMS = [
  { key: 'heaterTemp', label: 'Heater Temp', unit: '°C' },
  { key: 'diameter', label: 'Diameter', unit: 'mm' },
  { key: 'grMean', label: 'Growth Rate', unit: 'mm/m' },
  { key: 'heaterPowerSv', label: 'Heater Power', unit: 'kW' },
  { key: 'seedLift', label: 'Seed Lift', unit: 'mm' },
  { key: 'bodyLength', label: 'Body Length', unit: 'mm' }
]

/** 5 台爐子 */
export const FURNACES = ['D1', 'D3', 'DB', 'F7', 'FA']
