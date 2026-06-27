import { ref } from 'vue'

/**
 * 戰情室進場 boot 流程 —— 綁真實 ready 訊號,不是假進度條。
 *
 * progress 真的跟著三個里程碑走:
 *   initScene  完成 → 0.40   (Three.js 場景與相機就緒)
 *   connectWs  完成 → 0.70   (WebSocket 建連)
 *   firstData  完成 → 1.00   (首批爐況資料抵達)
 *
 * MIN_SHOW 確保動畫不會一閃而過(就算後端秒回也至少演完)。
 */
export function useDigitalTwinBoot(options = {}) {
  const MIN_SHOW = options.minShow ?? 1800 // 動畫最短展示時間 (ms)

  const phase = ref('booting')        // 'booting' | 'ready' | 'entered' | 'error'
  const progress = ref(0)             // 0..1 真實進度
  const stage = ref(0)                // 0..3 已完成的里程碑數
  const statusLabel = ref('初始化系統')
  const error = ref(null)

  const STEPS = [
    { label: '初始化爐體場景',                  weight: 0.40 },
    { label: '建立即時資料連線',                weight: 0.30 },
    { label: '載入爐況', weight: 0.30 },
  ]

  const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

  /**
   * @param {Object} tasks
   * @param {() => Promise<any>} tasks.initScene  建立 Three.js 場景
   * @param {() => Promise<any>} tasks.connectWs  建立 WebSocket 連線
   * @param {() => Promise<any>} tasks.firstData  等首批爐況資料
   */
  async function boot(tasks) {
    const order = [
      { fn: tasks.initScene, step: STEPS[0] },
      { fn: tasks.connectWs, step: STEPS[1] },
      { fn: tasks.firstData, step: STEPS[2] },
    ]
    const startedAt = performance.now()
    let acc = 0

    try {
      for (let i = 0; i < order.length; i++) {
        statusLabel.value = order[i].step.label
        await order[i].fn()                 // ← 真的等這件事完成
        acc += order[i].step.weight
        progress.value = Math.min(acc, 1)
        stage.value = i + 1
      }

      // 補滿最短展示時間,避免動畫一閃而過
      const elapsed = performance.now() - startedAt
      if (elapsed < MIN_SHOW) await sleep(MIN_SHOW - elapsed)

      statusLabel.value = '系統就緒'
      phase.value = 'ready'
    } catch (e) {
      error.value = e
      statusLabel.value = '啟動失敗,請重試'
      phase.value = 'error'
    }
  }

  function enter() {
    phase.value = 'entered'
  }

  function retry(tasks) {
    error.value = null
    progress.value = 0
    stage.value = 0
    phase.value = 'booting'
    return boot(tasks)
  }

  return { phase, progress, stage, statusLabel, error, boot, enter, retry }
}
