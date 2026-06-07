/**
 * useFurnaceWebSocket.js
 * STOMP WebSocket hook — 訂閱兩台爐即時數據 + Alarm channel
 *
 * 回傳：
 *   { furnaceData, alarms, wsConnected, msgCount }
 *
 * furnaceData 格式（對應 Spring Boot TwinStateService 推送）：
 * {
 *   furnaceId, event, operationMode, ingotNo,
 *   diameter, diameterTarget,
 *   heaterTemp, heaterPowerSv,
 *   grMean, bodyLength, residualWeight,
 * }
 */
import { useState, useEffect, useRef, useCallback } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

const WS_URL      = import.meta.env.VITE_WS_URL || 'http://localhost:8085/ws';
const HISTORY_MAX = 60;  // 保留最近 60 筆 sparkline 數據

function initFurnace(id) {
  return {
    furnaceId: id, event: 1,
    operationMode:'—', ingotNo:'—',
    diameter:null, diameterTarget:null,
    heaterTemp:null, heaterPowerSv:null,
    grMean:null, bodyLength:0, residualWeight:null,
    _ts: null,
    _history: { diameter:[], heaterTemp:[], grMean:[] },
  };
}

export function useFurnaceWebSocket() {
  const [wsConnected, setWsConnected] = useState(false);
  const [msgCount,    setMsgCount]    = useState(0);
  const [alarms,      setAlarms]      = useState([]);
  const [furnaceData, setFurnaceData] = useState({
    C1: initFurnace('C1'),
    C2: initFurnace('C2'),
  });

  const handleMsg = useCallback((id, raw) => {
    setFurnaceData(prev => {
      const old = prev[id];
      // 追加 history
      const appendHistory = (arr, val) =>
        val != null ? [...arr, val].slice(-HISTORY_MAX) : arr;

      return {
        ...prev,
        [id]: {
          ...raw,
          _ts: Date.now(),
          _history: {
            diameter:   appendHistory(old._history.diameter,  raw.diameter),
            heaterTemp: appendHistory(old._history.heaterTemp, raw.heaterTemp),
            grMean:     appendHistory(old._history.grMean,    raw.grMean),
          },
        },
      };
    });
    setMsgCount(n => n + 1);
  }, []);

  useEffect(() => {
    const client = new Client({
      webSocketFactory: () => new SockJS(WS_URL),
      reconnectDelay: 5000,
      onConnect: () => {
        setWsConnected(true);
        ['C1','C2'].forEach(id => {
          client.subscribe(`/topic/furnace/${id}`, msg => {
            handleMsg(id, JSON.parse(msg.body));
          });
        });
        client.subscribe('/topic/alarms', msg => {
          const alarm = JSON.parse(msg.body);
          setAlarms(prev => [{ ...alarm, _clientTs: Date.now() }, ...prev].slice(0, 50));
        });
      },
      onDisconnect: () => setWsConnected(false),
    });
    client.activate();
    return () => client.deactivate();
  }, [handleMsg]);

  return { furnaceData, alarms, wsConnected, msgCount };
}
