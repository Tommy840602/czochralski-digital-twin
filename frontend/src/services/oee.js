import api from './api'

export const oeeService = {
  async getOee(furnaceId, minutes = 1440) {
    const res = await api.get(`/oee`, { params: { furnaceId, minutes } })
    return res.data
  }
}

export const OEE_FURNACES = ['D1', 'D3', 'DB', 'F7', 'FA']
