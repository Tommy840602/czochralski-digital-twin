import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import axios from 'axios'

export const useFurnaceStore = defineStore('furnace', () => {
  const furnaces    = ref([])
  const liveData    = ref({})
  const selected    = ref(null)
  const loading     = ref(false)
  const wsConnected = ref(false)
  const alarms      = ref([])

  const furnaceIds = computed(() => furnaces.value.map(f => f.furnaceId))
  const selectedFurnace = computed(() => furnaces.value.find(f => f.furnaceId === selected.value) ?? null)
  const selectedLive = computed(() => selected.value ? (liveData.value[selected.value] ?? null) : null)
  const activeFurnaces = computed(() => furnaces.value.filter(f => f.status !== 'offline'))

  async function loadFurnaces() {
    loading.value = true
    try {
      const { data } = await axios.get('/api/furnaces')
      furnaces.value = data
      if (!selected.value && data.length > 0) selected.value = data[0].furnaceId
    } catch (e) {
      console.error('[furnaceStore] loadFurnaces failed:', e)
    } finally {
      loading.value = false
    }
  }

  function updateLive(furnaceId, data) {
    liveData.value = { ...liveData.value, [furnaceId]: { ...data, _updatedAt: Date.now() } }
  }

  function updateAllLive(dataList) {
    const map = {}
    for (const d of dataList) map[d.furnaceId] = { ...d, _updatedAt: Date.now() }
    liveData.value = { ...liveData.value, ...map }
  }

  function selectFurnace(id) { selected.value = id }

  function addAlarm(alarm) {
    alarms.value = [{ ...alarm, _clientTs: Date.now() }, ...alarms.value].slice(0, 50)
  }

  async function autoRegister(furnaceId) {
    if (furnaces.value.some(f => f.furnaceId === furnaceId)) return
    try {
      await axios.post(`/api/furnaces/${furnaceId}/register`)
      await loadFurnaces()
    } catch (e) { console.warn('[furnaceStore] autoRegister failed:', e) }
  }

  return {
    furnaces, liveData, selected, loading, wsConnected, alarms,
    furnaceIds, selectedFurnace, selectedLive, activeFurnaces,
    loadFurnaces, updateLive, updateAllLive, selectFurnace, addAlarm, autoRegister
  }
})
