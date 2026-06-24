import { onMounted, onUnmounted } from 'vue'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { useFurnaceStore } from '@/stores/furnaceStore.js'

const WS_URL = import.meta.env.VITE_WS_URL ?? '/ws'

/**
 * useFurnaceWebSocket — STOMP WebSocket Composable（彈性多爐版）
 *
 * 訂閱策略：
 *   /topic/furnaces/all     → 所有爐子快照（每 2s）
 *   /topic/furnace/{id}     → 各爐子個別 topic（隨 furnaces store 動態訂閱）
 *   /topic/alarms           → 告警事件
 *
 * 彈性設計：
 *   store.furnaces 有幾台就訂幾個 topic，
 *   新爐子出現時呼叫 resubscribe() 自動補訂。
 */
export function useFurnaceWebSocket() {
  const store = useFurnaceStore()
  let client = null
  const subscriptions = {}   // { furnaceId: StompSubscription }

  function subscribe(furnaceId) {
    if (!client?.connected || subscriptions[furnaceId]) return
    subscriptions[furnaceId] = client.subscribe(
      `/topic/furnace/${furnaceId}`,
      msg => {
        try {
          const data = JSON.parse(msg.body)
          store.updateLive(furnaceId, data)
          // 若出現未知爐子，自動注冊
          if (!store.furnaceIds.includes(furnaceId)) {
            store.autoRegister(furnaceId)
          }
        } catch (e) {
          console.error(`[ws] parse error furnace=${furnaceId}`, e)
        }
      }
    )
  }

  function resubscribe() {
    for (const id of store.furnaceIds) {
      subscribe(id)
    }
  }

  function connect() {
    client = new Client({
      webSocketFactory: () => new SockJS(WS_URL),
      reconnectDelay: 5000,

      onConnect: () => {
        store.wsConnected = true
        console.log('[ws] 已連線')

        // 1. 全部爐子快照（單一 topic，省連線數）
        client.subscribe('/topic/furnaces/all', msg => {
          try {
            const list = JSON.parse(msg.body)
            store.updateAllLive(list)
          } catch (e) {
            console.error('[ws] all parse/update error', e)            // ← 別吞錯
          }
        })

        // 2. 各爐子個別 topic
        resubscribe()

        // 3. 告警
        client.subscribe('/topic/alarms', msg => {
          try {
            const alarm = JSON.parse(msg.body)
            store.addAlarm(alarm)
          } catch {}
        })
      },

      onDisconnect: () => {
        store.wsConnected = false
        Object.keys(subscriptions).forEach(k => delete subscriptions[k])
        console.log('[ws] 已斷線')
      },

      onStompError: frame => {
        console.error('[ws] STOMP error', frame)
      }
    })

    client.activate()
  }

  onMounted(connect)
  onUnmounted(() => client?.deactivate())

  return { resubscribe }
}
