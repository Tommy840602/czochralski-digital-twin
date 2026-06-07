/**
 * App.jsx
 * 主應用程式根元件
 * 佈局：上半 Three.js 孿生場景，下半卡片儀表板
 */
import { useState } from 'react';
import FurnaceViewer from './components/furnace/FurnaceViewer.jsx';
import Dashboard     from './components/dashboard/Dashboard.jsx';

export default function App() {
  const [alarms, setAlarms] = useState([]);

  const handleAlarm = (alarm) => {
    setAlarms(prev => [{ ...alarm, _clientTs: Date.now() }, ...prev].slice(0, 50));
  };

  return (
    <div style={{ display:'flex', flexDirection:'column', height:'100vh', background:'#080b10', overflow:'hidden' }}>
      {/* ── 上半：Three.js 數位孿生 3D 場景 ── */}
      <div style={{ flex:'0 0 45%', padding:'12px 12px 6px' }}>
        <FurnaceViewer height="100%" onAlarm={handleAlarm} />
      </div>

      {/* ── 下半：即時數據卡片儀表板 ── */}
      <div style={{ flex:'1 1 auto', overflowY:'auto' }}>
        <Dashboard externalAlarms={alarms} />
      </div>
    </div>
  );
}
