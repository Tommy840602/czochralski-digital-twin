import React from 'react';
import { useTheme } from '../../theme-system.jsx';
import FurnaceScene from './FurnaceScene.jsx';
import FurnaceOverlay from './FurnaceOverlay.jsx';
import { useFurnaceWebSocket } from '../../hooks/useFurnaceWebSocket.js';  // 👈 使用 hook

export default function FurnaceViewer({ height = '480px', onAlarm }) {
  // 👈 用 hook 替代 useState + useEffect + Client 的复杂逻辑
  const { furnaceData, wsConnected, alarms } = useFurnaceWebSocket();
  const { bg, text, border, accent } = useTheme();

  // 当有新报警时通知父组件
  React.useEffect(() => {
    if (alarms.length > 0) {
      const latest = alarms[0];
      onAlarm?.(latest);
    }
  }, [alarms, onAlarm]);

  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      width: '100%',
      height: '100%',
      background: bg.primary,  // 👈 使用主题
      transition: 'background 0.3s ease',
    }}>

      {/* 🎨 顶部栏 */}
      <div style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        padding: '14px 20px',
        borderBottom: `1px solid ${border}`,  // 👈 使用主题
        background: bg.secondary,  // 👈 使用主题
        flexShrink: 0,
        transition: 'background 0.3s ease, border-color 0.3s ease',
      }}>
        {/* Logo + 标题 */}
        <div style={{
          display: 'flex',
          alignItems: 'center',
          gap: 12,
        }}>
          <div style={{
            width: 32,
            height: 32,
            borderRadius: 6,
            background: `linear-gradient(135deg, ${accent.ok}, ${accent.info})`,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontFamily: "'IBM Plex Mono', sans-serif",
            fontSize: 13,
            fontWeight: 600,
            color: bg.primary,  // 👈 对比度
            transition: 'all 0.3s ease',
          }}>
            CT
          </div>
          <div>
            <div style={{
              fontFamily: "'IBM Plex Mono', sans-serif",
              fontSize: 13,
              fontWeight: 500,
              letterSpacing: '0.05em',
              color: text.primary,  // 👈 使用主题
            }}>
              CZOCHRALSKI DIGITAL TWIN
            </div>
            <div style={{
              fontFamily: "'IBM Plex Mono', sans-serif",
              fontSize: 11,
              color: text.muted,  // 👈 使用主题
              marginTop: 1,
            }}>
              長晶爐即時監控系統 v1.0
            </div>
          </div>
        </div>

        {/* WS 连接指示 */}
        <div style={{
          display: 'flex',
          alignItems: 'center',
          gap: 6,
          fontFamily: "'IBM Plex Mono', sans-serif",
          fontSize: 11,
          color: text.secondary,  // 👈 使用主题
          padding: '4px 10px',
          borderRadius: 5,
          background: wsConnected ? accent.ok + '15' : accent.ng + '15',  // 👈 使用主题强调色
          border: `1px solid ${wsConnected ? accent.ok + '30' : accent.ng + '30'}`,  // 👈 使用主题强调色
          transition: 'all 0.3s ease',
        }}>
          <div style={{
            width: 7,
            height: 7,
            borderRadius: '50%',
            background: wsConnected ? accent.ok : accent.ng,  // 👈 使用主题强调色
            boxShadow: `0 0 4px ${wsConnected ? accent.ok : accent.ng}`,
            animation: wsConnected ? 'pulse 2s infinite' : 'none',
          }} />
          <span>{wsConnected ? 'WS 已連線' : 'WS 已斷線'}</span>
        </div>
      </div>

      {/* 📺 Three.js 场景 */}
      <div style={{
        position: 'relative',
        flex: '1 1 0',
        minHeight: 0,
        borderRadius: 10,
        overflow: 'hidden',
        transition: 'background 0.3s ease',
      }}>
        <FurnaceScene furnaceData={furnaceData} />
        <FurnaceOverlay furnaceData={furnaceData} wsConnected={wsConnected} />
      </div>

      {/* 脉冲动画 */}
      <style>{`
        @keyframes pulse {
          0%, 100% { opacity: 1; }
          50% { opacity: 0.6; }
        }
      `}</style>
    </div>
  );
}
