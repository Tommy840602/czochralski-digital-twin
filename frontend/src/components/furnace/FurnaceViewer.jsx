/**
 * FurnaceViewer.jsx
 * 整合容器：Three.js 場景 + HTML Overlay + WebSocket 訂閱
 * 這是頁面上實際掛的元件，App.jsx 只需 <FurnaceViewer />
 *
 * 內部負責：
 *   1. STOMP WebSocket 訂閱 /topic/furnace/C1 和 /topic/furnace/C2
 *   2. 將 furnaceData 傳給 FurnaceScene（Three.js 動畫更新）
 *   3. 將 furnaceData 傳給 FurnaceOverlay（HTML 數字標籤）
 */
import { useState, useEffect, useRef } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import FurnaceScene   from './FurnaceScene.jsx';
import FurnaceOverlay from './FurnaceOverlay.jsx';

const WS_URL = import.meta.env.VITE_WS_URL || 'http://localhost:8091/ws';

export default function FurnaceViewer({ height = '480px', onAlarm }) {
  const [wsConnected,  setWsConnected]  = useState(false);
  const [furnaceData,  setFurnaceData]  = useState({ C1: defaultData('C1'), C2: defaultData('C2') });
  const stompRef = useRef(null);

  useEffect(() => {
    const client = new Client({
      webSocketFactory: () => new SockJS(WS_URL),
      reconnectDelay: 5000,
      onConnect: () => {
        setWsConnected(true);
        // 訂閱兩台爐
        ['C1','C2'].forEach(id => {
          client.subscribe(`/topic/furnace/${id}`, (msg) => {
            const data = JSON.parse(msg.body);
            setFurnaceData(prev => ({ ...prev, [id]: { ...data, _ts: Date.now() } }));
            // NG 通知父層
            if (data.event === 6) onAlarm?.({ furnaceId: id, ...data });
          });
        });
        // 訂閱 Alarm（Flink CEP 觸發）
        client.subscribe('/topic/alarms', (msg) => {
          onAlarm?.(JSON.parse(msg.body));
        });
      },
      onDisconnect: () => setWsConnected(false),
      onStompError: (f) => console.error('[FurnaceViewer] STOMP error:', f),
    });
    client.activate();
    stompRef.current = client;
    return () => client.deactivate();
  }, []);

  return (
    <div style={{ position:'relative', width:'100%', height, borderRadius:10, overflow:'hidden' }}>
      {/* Three.js 場景 */}
      <FurnaceScene furnaceData={furnaceData} />

      {/* HTML 數據 Overlay */}
      <FurnaceOverlay furnaceData={furnaceData} wsConnected={wsConnected} />
    </div>
  );
}

function defaultData(id) {
  return {
    furnaceId: id, event: 1,
    operationMode: '—', ingotNo: '—',
    diameter: null, diameterTarget: null,
    heaterTemp: null, heaterPowerSv: null,
    grMean: null, bodyLength: 0,
    residualWeight: null, _ts: null,
  };
}
