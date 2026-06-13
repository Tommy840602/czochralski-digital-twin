import React, { useState } from 'react';
import { useTheme } from './theme-system.jsx';
import FurnaceViewer from './components/furnace/FurnaceViewer.jsx';
import Dashboard from './components/dashboard/Dashboard.jsx';
import { useFurnaceWebSocket } from './hooks/useFurnaceWebSocket.js';

export default function App() {
  // ✅ 正确：从 hook 获取数据
  const { furnaceData, wsConnected, alarms: wsAlarms } = useFurnaceWebSocket();

  // ✅ 正确：用不同的名字管理告警
  const [allAlarms, setAllAlarms] = useState([]);

  // 获取主题
  const { bg, text, border, theme, toggleTheme } = useTheme();

  // 新告警事件处理
  const handleAlarm = (alarm) => {
    setAllAlarms(prev => [{ ...alarm, _clientTs: Date.now() }, ...prev].slice(0, 50));
  };

  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      minHeight: '100vh',
      background: bg.primary,
      color: text.primary,
      transition: 'background 0.3s ease, color 0.3s ease',
    }}>
      {/* 🌓 顶部栏：主题切换 */}
      <div style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        padding: '8px 16px',
        background: bg.secondary,
        borderBottom: `1px solid ${border}`,
        flexShrink: 0,
      }}>
        <div style={{
          fontFamily: "'IBM Plex Mono', sans-serif",
          fontSize: 12,
          fontWeight: 500,
          color: text.muted,
          letterSpacing: '0.05em',
        }}>
          {theme === 'dark' ? '🌙 DARK MODE' : '☀️ LIGHT MODE'}
        </div>
        <button
          onClick={toggleTheme}
          style={{
            padding: '6px 12px',
            borderRadius: 6,
            border: `1px solid ${border}`,
            background: 'transparent',
            color: text.primary,
            cursor: 'pointer',
            fontSize: 12,
            fontFamily: "'IBM Plex Mono', sans-serif",
            fontWeight: 600,
            transition: 'all 0.2s ease',
            display: 'flex',
            alignItems: 'center',
            gap: 6,
          }}
          onMouseOver={(e) => {
            e.target.style.borderColor = theme === 'dark' ? '#40c88c' : '#2d8659';
            e.target.style.background = theme === 'dark' ? 'rgba(64,200,140,0.1)' : 'rgba(45,134,89,0.1)';
          }}
          onMouseOut={(e) => {
            e.target.style.borderColor = border;
            e.target.style.background = 'transparent';
          }}
        >
          {theme === 'dark' ? '☀️ Switch to Light' : '🌙 Switch to Dark'}
        </button>
      </div>

      {/* 📺 FurnaceViewer：3D 场景 */}
      <div style={{
        height: '80vh',
        padding: '12px 12px 6px',
        borderRadius: 10,
        overflow: 'hidden',
      }}>
        <FurnaceViewer height="100%" onAlarm={handleAlarm} />
      </div>

      {/* 📊 Dashboard：仪表板 */}
      <div>
        <Dashboard
          furnaceData={furnaceData}
          externalAlarms={allAlarms}
          wsConnected={wsConnected}
        />
      </div>
    </div>
  );
}
