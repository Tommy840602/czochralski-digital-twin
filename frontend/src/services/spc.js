import api from './api'

export const spcService = {
  async getBaselines(furnaceId) {
    const res = await api.get(`/spc/baseline`, { params: { furnaceId } })
    return res.data
  },

  async getBaseline(furnaceId, paramName, operationMode) {
    const res = await api.get(`/spc/baseline/one`, {
      params: { furnaceId, paramName, operationMode }
    })
    return res.data
  },

  /** 該爐近 7 天出現過的製程階段（baseline 以此為單位建立） */
  async getModes(furnaceId) {
    const res = await api.get(`/spc/modes`, { params: { furnaceId } })
    return res.data
  },

  /** 爐子「當下」的製程階段（取最新一筆原始讀值，與數位孿生同源） */
  async getCurrentMode(furnaceId) {
    const res = await api.get(`/spc/current-mode`, { params: { furnaceId } })
    return res.data?.mode ?? ''
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

  /** 調整某爐某參數在某製程階段的 σ 寬鬆度倍數 */
  async adjustSigmaMultiplier(furnaceId, paramName, operationMode, multiplier) {
    const res = await api.patch(`/spc/baseline/sigma-multiplier`, null, {
      params: { furnaceId, paramName, operationMode, multiplier }
    })
    return res.data
  },

  /**
   * 管制圖資料點：最近 N 個「一分鐘子群平均」的即時資料（不切階段）。
   * 每個點自帶它所屬階段的管制界，前端畫成隨階段跳動的階梯線。
   */
  async getTimeseries(furnaceId, paramName, points = 120) {
    const res = await api.get(`/spc/timeseries`, {
      params: { furnaceId, paramName, points }
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
