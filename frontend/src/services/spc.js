import api from './api'

export const spcService = {
  async getBaselines(furnaceId) {
    const res = await api.get(`/spc/baseline`, { params: { furnaceId } })
    return res.data
  },

  async getBaseline(furnaceId, paramName) {
    const res = await api.get(`/spc/baseline/one`, { params: { furnaceId, paramName } })
    return res.data
  },

  async getParams() {
    const res = await api.get(`/spc/params`)
    return res.data
  },

  async getRecentViolations(minutes = 60) {
    const res = await api.get(`/spc/violation/recent`, { params: { minutes } })
    return res.data
  },

  async getViolationsByFurnace(furnaceId, minutes = 60) {
    const res = await api.get(`/spc/violation/byFurnace`, {
      params: { furnaceId, minutes }
    })
    return res.data
  },

  async getStatistics(minutes = 1440, furnaceId = null) {
    const params = { minutes }
    if (furnaceId) params.furnaceId = furnaceId
    const res = await api.get(`/spc/violation/statistics`, { params })
    return res.data
  },

  /** 重算單一爐子的 baseline（所有參數） */
  async rebuildFurnaceBaseline(furnaceId) {
    const res = await api.post(`/spc/baseline/rebuild/furnace`, null, { params: { furnaceId } })
    return res.data
  },

  /** 查詢某爐子目前是否有計算正在進行中 */
  async checkFurnaceRebuildStatus(furnaceId) {
    const res = await api.get(`/spc/baseline/rebuild/status`, { params: { furnaceId } })
    return res.data.inProgress
  },

  /** 調整某爐某參數的 σ 寬鬆度倍數 */
  async adjustSigmaMultiplier(furnaceId, paramName, multiplier) {
    const res = await api.patch(`/spc/baseline/sigma-multiplier`, null, {
      params: { furnaceId, paramName, multiplier }
    })
    return res.data
  },

  async getTimeseries(furnaceId, paramName, minutes = 60) {
    const res = await api.get(`/spc/timeseries`, {
      params: { furnaceId, paramName, minutes }
    })
    return res.data
  },

  async getStatistics(minutes = 1440, furnaceId = null, paramName = null) {
    const params = { minutes }
    if (furnaceId) params.furnaceId = furnaceId
    if (paramName) params.paramName = paramName
    const res = await api.get(`/spc/violation/statistics`, { params })
    return res.data
  },
}

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

export const SPC_PARAMS = [
  { key: 'heaterTemp', label: 'Heater Temp', unit: '°C' },
  { key: 'diameter', label: 'Diameter', unit: 'mm' },
  { key: 'grMean', label: 'Growth Rate', unit: 'mm/m' },
  { key: 'heaterPowerSv', label: 'Heater Power', unit: 'kW' },
  { key: 'seedLift', label: 'Seed Lift', unit: 'mm' },
  { key: 'bodyLength', label: 'Body Length', unit: 'mm' }
]

export const FURNACES = ['D1', 'D3', 'DB', 'F7', 'FA']
