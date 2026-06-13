import React, { useState, useEffect } from 'react';
import { useTheme } from '../../theme-system.jsx';

/**
 * Dashboard.jsx
 * 長晶爐即時監控主介面
 * - 接收 App.jsx 傳來的 furnaceData、externalAlarms、wsConnected
 * - 每張爐況卡片可折疊
 * - 保留 Sparkline 趨勢圖表（支持主題色）
 * - 主題系統集成（深色/淺色）
 */

const FURNACES = ['C1', 'C2'];

export default function Dashboard({
  furnaceData = {},
  externalAlarms = [],
  wsConnected = false,
}) {
  const { bg, text, border, accent } = useTheme();
  const [collapsed, setCollapsed] = useState({ C1: false, C2: false });

  const toggleCard = (id) => {
    setCollapsed(prev => ({ ...prev, [id]: !prev[id] }));
  };

  return (
    <div style={{
      fontFamily: "'IBM Plex Sans', sans-serif",
      background: bg.primary,
      color: text.primary,
      minHeight: '100%',
      display: 'flex',
      flexDirection: 'column',
      transition: 'background 0.3s ease, color 0.3s ease',
    }}>
      {/* 工作区 */}
      <div style={{
        padding: 16,
        paddingBottom: 72,
        display: 'flex',
        flexDirection: 'column',
        gap: 12,
        flex: 1,
      }}>
        {FURNACES.map(id => (
          <FurnaceCard
            key={id}
            id={id}
            data={furnaceData[id] || {}}
            collapsed={collapsed[id]}
            onToggle={() => toggleCard(id)}
          />
        ))}
        <AlarmPanel alarms={externalAlarms} />
      </div>

      {/* 底部状态栏 */}
      <StatusBar wsConnected={wsConnected} />
    </div>
  );
}

// ==========================================
// 🔥 炉子卡片
// ==========================================
function FurnaceCard({ id, data, collapsed, onToggle }) {
  const { bg, text, border, accent } = useTheme();

  // 关键：字符串比较
  const isNg = String(data.event) !== '1';

  const displayDiameter = parseFloat(data.diameter) || 0;
  const displayTemp = parseFloat(data.heaterTemp) || 0;
  const displayGR = parseFloat(data.grMean) || 0;
  const displayBody = parseFloat(data.bodyLength) || 0;

  return (
    <div style={{
      background: bg.secondary,
      border: `1px solid ${isNg ? accent.ng + '30' : accent.ok + '30'}`,
      borderRadius: 10,
      overflow: 'hidden',
      transition: 'all 0.2s ease',
      boxShadow: isNg
        ? `0 0 20px ${accent.ng}15`
        : `0 0 20px ${accent.ok}12`,
    }}>
      {/* 卡片头 */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 10,
          padding: '10px 14px',
          cursor: 'pointer',
          background: bg.tertiary,
          borderBottom: `1px solid ${border}`,
        }}
        onClick={onToggle}
      >
        {/* 状态指示点 */}
        <div style={{
          width: 8,
          height: 8,
          borderRadius: '50%',
          background: isNg ? accent.ng : accent.ok,
          boxShadow: `0 0 6px ${isNg ? accent.ng : accent.ok}`,
          flexShrink: 0,
        }} />

        {/* 炉号 */}
        <span style={{
          fontFamily: "'IBM Plex Mono', sans-serif",
          fontSize: 13,
          fontWeight: 600,
          color: text.primary,
        }}>
          爐 {id}
        </span>

        {/* 样品号 */}
        <span style={{
          fontFamily: "'IBM Plex Mono', sans-serif",
          fontSize: 11,
          color: text.muted,
        }}>
          {data.ingotNo || '—'}
        </span>

        {/* Mode 徽章 */}
        <span style={{
          fontFamily: "'IBM Plex Mono', sans-serif",
          fontSize: 10,
          fontWeight: 500,
          padding: '2px 7px',
          borderRadius: 4,
          background: accent.info + '15',
          color: accent.info,
          border: `1px solid ${accent.info}30`,
        }}>
          {data.operationMode || '—'}
        </span>

        {/* OK/NG 徽章 */}
        <span style={{
          fontFamily: "'IBM Plex Mono', sans-serif",
          fontSize: 10,
          fontWeight: 600,
          padding: '2px 7px',
          borderRadius: 4,
          background: isNg ? accent.ng + '15' : accent.ok + '15',
          color: isNg ? accent.ng : accent.ok,
          border: `1px solid ${isNg ? accent.ng : accent.ok}30`,
        }}>
          {isNg ? 'NG' : 'OK'}
        </span>

        {/* 折疊時顯示迷你數值 */}
        {collapsed && (
          <div style={{
            display: 'flex',
            gap: 16,
            marginLeft: 8,
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
              <span style={{
                fontFamily: "'IBM Plex Mono', sans-serif",
                fontSize: 10,
                color: text.muted,
              }}>
                Ø
              </span>
              <span style={{
                fontFamily: "'IBM Plex Mono', sans-serif",
                fontSize: 12,
                fontWeight: 600,
                color: accent.ok,
              }}>
                {displayDiameter.toFixed(1)}mm
              </span>
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
              <span style={{
                fontFamily: "'IBM Plex Mono', sans-serif",
                fontSize: 10,
                color: text.muted,
              }}>
                Temp
              </span>
              <span style={{
                fontFamily: "'IBM Plex Mono', sans-serif",
                fontSize: 12,
                fontWeight: 600,
                color: accent.warn,
              }}>
                {displayTemp.toFixed(0)}°C
              </span>
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
              <span style={{
                fontFamily: "'IBM Plex Mono', sans-serif",
                fontSize: 10,
                color: text.muted,
              }}>
                GR
              </span>
              <span style={{
                fontFamily: "'IBM Plex Mono', sans-serif",
                fontSize: 12,
                fontWeight: 600,
                color: accent.info,
              }}>
                {displayGR.toFixed(3)}
              </span>
            </div>
          </div>
        )}

        {/* 展开按钮 */}
        <button
          style={{
            marginLeft: 'auto',
            width: 22,
            height: 22,
            borderRadius: 4,
            border: `1px solid ${border}`,
            background: 'transparent',
            color: text.muted,
            fontSize: 14,
            cursor: 'pointer',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            transition: 'all 0.2s ease',
          }}
          onMouseOver={(e) => {
            e.target.style.borderColor = accent.ok;
            e.target.style.color = accent.ok;
          }}
          onMouseOut={(e) => {
            e.target.style.borderColor = border;
            e.target.style.color = text.muted;
          }}
        >
          {collapsed ? '+' : '−'}
        </button>
      </div>

      {/* 卡片内容 */}
      {!collapsed && (
        <div style={{
          padding: 14,
          background: bg.secondary,
        }}>
          {/* KPI 网格 */}
          <div style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fill, minmax(130px, 1fr))',
            gap: 8,
            marginBottom: 14,
          }}>
            <KpiBox
              label="Diameter"
              value={displayDiameter.toFixed(2)}
              unit="mm"
              delta={`target ${(parseFloat(data.diameterTarget) || 0).toFixed(2)}mm`}
              pct={(displayDiameter / 200) * 100}
              isNg={isNg}
            />
            <KpiBox
              label="Heater Temp"
              value={displayTemp.toFixed(1)}
              unit="°C"
              delta={`power ${(parseFloat(data.heaterPowerSv) || 0).toFixed(1)}kW`}
              pct={(displayTemp - 1200) / 300 * 100}
              isNg={isNg}
            />
            <KpiBox
              label="GR Mean"
              value={displayGR.toFixed(3)}
              unit="mm/m"
              delta="—"
              pct={displayGR / 3 * 100}
              isNg={isNg}
            />
            <KpiBox
              label="Body Length"
              value={displayBody.toFixed(1)}
              unit="mm"
              delta="—"
              pct={displayBody / 500 * 100}
              isNg={isNg}
            />
          </div>

          {/* Sparkline 趨勢圖 */}
          <SparklineRow
            diameterHistory={data._history?.diameter || []}
            tempHistory={data._history?.heaterTemp || []}
            isNg={isNg}
          />
        </div>
      )}
    </div>
  );
}

// ==========================================
// 📊 KPI 框
// ==========================================
function KpiBox({ label, value, unit, delta, pct, isNg }) {
  const { bg, text, border, accent } = useTheme();

  return (
    <div style={{
      background: bg.tertiary,
      border: `1px solid ${border}`,
      borderRadius: 7,
      padding: '10px 12px',
      position: 'relative',
      overflow: 'hidden',
    }}>
      <div style={{
        fontFamily: "'IBM Plex Mono', sans-serif",
        fontSize: 10,
        color: text.muted,
        marginBottom: 5,
        textTransform: 'uppercase',
        letterSpacing: '0.04em',
      }}>
        {label}
      </div>
      <div style={{
        fontFamily: "'IBM Plex Mono', sans-serif",
        fontSize: 20,
        fontWeight: 600,
        color: text.primary,
        lineHeight: 1,
      }}>
        {value ?? '—'}
        <span style={{
          fontFamily: "'IBM Plex Mono', sans-serif",
          fontSize: 11,
          color: text.secondary,
          marginLeft: 3,
        }}>
          {unit}
        </span>
      </div>
      <div style={{
        fontFamily: "'IBM Plex Mono', sans-serif",
        fontSize: 10,
        marginTop: 4,
        color: text.muted,
      }}>
        {delta}
      </div>
      {/* 进度条 */}
      <div style={{
        position: 'absolute',
        bottom: 0,
        left: 0,
        height: 2,
        width: Math.max(0, Math.min(100, pct || 0)) + '%',
        background: isNg ? accent.ng : accent.ok,
        transition: 'width 0.4s ease',
        opacity: 0.7,
      }} />
    </div>
  );
}

// ==========================================
// 📈 Sparkline 趨勢圖
// ==========================================
function Sparkline({ data, color, unit = '', textColor = "rgba(128, 128, 128, 0.6)" }) {
  if (!data || data.length < 2) {
    return <svg style={{ width: '100%', height: 200 }} />;
  }

  const W = 200, H = 200, padX = 20, padY = 14;
  const raw = data.filter(v => v != null && !isNaN(v));

  if (raw.length < 2) {
    return <svg style={{ width: '100%', height: 200 }} />;
  }

  const dataMin = Math.min(...raw);
  const dataMax = Math.max(...raw);
  const spread = dataMax - dataMin;
  const padding = spread < 0.1 ? dataMax * 0.02 : spread * 0.3;
  const min = dataMin - padding;
  const max = dataMax + padding;
  const range = max - min || 1;

  // 计算所有点的坐标
  const pointCoords = raw.map((v, i) => {
    const x = padX + (i / (raw.length - 1)) * (W - padX * 2);
    const y = padY + (1 - (v - min) / range) * (H - padY * 2);
    return { x, y, val: v };
  });

  const pts = pointCoords.map(p => `${p.x},${p.y}`).join(' ');

  // Y 轴标签（上、中、下）
  const yTop = padY;
  const yMid = padY + (H - padY * 2) / 2;
  const yBot = H - padY;

  const maxLabel = parseFloat(max).toFixed(1);
  const midLabel = parseFloat((max + min) / 2).toFixed(1);
  const minLabel = parseFloat(min).toFixed(1);

  return (
    <svg
      viewBox={`0 0 ${W} ${H}`}
      preserveAspectRatio="none"
      style={{ width: '100%', height: 200, display: 'block' }}
    >
      {/* Y 轴 */}
      <line
        x1={padX - 2}
        y1={yTop}
        x2={padX - 2}
        y2={yBot}
        stroke={textColor}
        strokeWidth="0.8"
        opacity="0.3"
      />

      {/* X 轴 */}
      <line
        x1={padX}
        y1={yBot}
        x2={W - 6}
        y2={yBot}
        stroke={textColor}
        strokeWidth="0.8"
        opacity="0.3"
      />

      {/* Y 轴网格线和标签 */}
      <line x1={padX - 2} y1={yTop} x2={W - 6} y2={yTop} stroke={textColor} strokeWidth="0.5" opacity="0.1" />
      <text x={padX - 6} y={yTop + 3} fill={textColor} fontSize="7" textAnchor="end">
        {maxLabel}
      </text>

      <line x1={padX - 2} y1={yMid} x2={W - 6} y2={yMid} stroke={textColor} strokeWidth="0.5" opacity="0.1" />
      <text x={padX - 6} y={yMid + 3} fill={textColor} fontSize="7" textAnchor="end">
        {midLabel}
      </text>

      <line x1={padX - 2} y1={yBot} x2={W - 6} y2={yBot} stroke={textColor} strokeWidth="0.5" opacity="0.1" />
      <text x={padX - 6} y={yBot + 3} fill={textColor} fontSize="7" textAnchor="end">
        {minLabel}
      </text>

      {/* 趋势线 */}
      <polyline
        points={pts}
        fill="none"
        stroke={color}
        strokeWidth="2"
        strokeLinejoin="round"
      />

      {/* 数据点（最多显示5个，填色圆点） */}
      {pointCoords.map((p, i) => {
        // 均匀分布显示最多 5 个点
        const step = Math.max(1, Math.floor(pointCoords.length / 5));
        const shouldShow = i % step === 0 || i === pointCoords.length - 1;
        return shouldShow ? (
          <circle
            key={i}
            cx={p.x}
            cy={p.y}
            r="2.5"
            fill={color}
          />
        ) : null;
      })}

      {/* 最后一个值的标签 */}
      {pointCoords.length > 0 && (
        <text
          x={pointCoords[pointCoords.length - 1].x - 2}
          y={pointCoords[pointCoords.length - 1].y - 6}
          fill={color}
          fontSize="7"
          textAnchor="end"
          fontWeight="600"
        >
          {parseFloat(raw[raw.length - 1]).toFixed(1)}
          {unit}
        </text>
      )}
    </svg>
  );
}

function SparklineRow({ diameterHistory, tempHistory, isNg }) {
  const { bg, text, border, accent } = useTheme();

  return (
    <div style={{
      display: 'grid',
      gridTemplateColumns: '1fr 1fr',
      gap: 8,
    }}>
      <div style={{
        background: bg.tertiary,
        border: `1px solid ${border}`,
        borderRadius: 7,
        padding: '14px 16px',
        minHeight: 240,
      }}>
        <div style={{
          fontFamily: "'IBM Plex Mono', sans-serif",
          fontSize: 10,
          color: text.muted,
          marginBottom: 6,
          textTransform: 'uppercase',
          letterSpacing: '0.04em',
        }}>
          Diameter
        </div>
        {/* ✅ 傳入 textColor */}
        <Sparkline
          data={diameterHistory}
          color="#4a8cf0"
          unit="mm"
          textColor={text.muted}
        />
      </div>

      <div style={{
        background: bg.tertiary,
        border: `1px solid ${border}`,
        borderRadius: 7,
        padding: '14px 16px',
        minHeight: 240,
      }}>
        <div style={{
          fontFamily: "'IBM Plex Mono', sans-serif",
          fontSize: 10,
          color: text.muted,
          marginBottom: 6,
          textTransform: 'uppercase',
          letterSpacing: '0.04em',
        }}>
          Heater Temp
        </div>
        {/* ✅ 傳入 textColor */}
        <Sparkline
          data={tempHistory}
          color="#f04a4a"
          unit="°C"
          textColor={text.muted}
        />
      </div>
    </div>
  );
}

// ==========================================
// ⚡ 警报面板
// ==========================================
function AlarmPanel({ alarms }) {
  const { bg, text, border, accent } = useTheme();

  return (
    <div style={{
      background: bg.secondary,
      border: `1px solid ${accent.ng}30`,
      borderRadius: 10,
      overflow: 'hidden',
    }}>
      <div style={{
        display: 'flex',
        alignItems: 'center',
        gap: 8,
        padding: '10px 14px',
        background: accent.ng + '10',
        borderBottom: `1px solid ${accent.ng}20`,
      }}>
        <span style={{ fontSize: 13 }}>⚡</span>
        <span style={{
          fontFamily: "'IBM Plex Mono', sans-serif",
          fontSize: 12,
          fontWeight: 600,
          color: accent.ng,
        }}>
          ALARM MESSAGES
        </span>
        <span style={{
          marginLeft: 'auto',
          fontFamily: "'IBM Plex Mono', sans-serif",
          fontSize: 11,
          background: accent.ng + '20',
          color: accent.ng,
          borderRadius: 10,
          padding: '1px 7px',
        }}>
          {alarms.length}
        </span>
      </div>

      <div style={{ padding: 8, display: 'flex', flexDirection: 'column', gap: 5 }}>
        {alarms.length === 0 ? (
          <span style={{
            fontFamily: "'IBM Plex Mono', sans-serif",
            fontSize: 11,
            color: text.muted,
            padding: 8,
          }}>
            等待告警事件...
          </span>
        ) : (
          alarms.slice(0, 5).map((a, i) => (
            <div
              key={i}
              style={{
                display: 'flex',
                alignItems: 'flex-start',
                gap: 10,
                padding: '8px 10px',
                borderRadius: 6,
                border: `1px solid ${accent.ng}20`,
                background: accent.ng + '05',
              }}
            >
              <span style={{
                fontFamily: "'IBM Plex Mono', sans-serif",
                fontSize: 10,
                fontWeight: 600,
                padding: '1px 6px',
                borderRadius: 3,
                background: accent.ng + '20',
                color: accent.ng,
                flexShrink: 0,
                marginTop: 1,
              }}>
                {a.severity || 'WARN'}
              </span>
              <span style={{
                color: text.secondary,
                fontFamily: "'IBM Plex Mono', sans-serif",
                fontSize: 11,
                flex: 1,
              }}>
                [{a.furnaceId || '?'}] {a.message || a.title || 'Unknown alarm'}
              </span>
              <span style={{
                fontFamily: "'IBM Plex Mono', sans-serif",
                fontSize: 10,
                color: text.muted,
                flexShrink: 0,
              }}>
                {a._clientTs ? new Date(a._clientTs).toTimeString().slice(0, 8) : '—'}
              </span>
            </div>
          ))
        )}
      </div>
    </div>
  );
}

// ==========================================
// 📊 底部状态栏
// ==========================================
function StatusBar({ wsConnected }) {
  const { bg, text, border, accent } = useTheme();
  const [clock, setClock] = useState('');

  useEffect(() => {
    const t = setInterval(() => {
      setClock(new Date().toLocaleTimeString('zh-TW'));
    }, 1000);
    return () => clearInterval(t);
  }, []);

  return (
    <div style={{
      display: 'flex',
      alignItems: 'center',
      gap: 10,
      padding: '7px 20px',
      borderTop: `1px solid ${border}`,
      background: bg.secondary,
      fontFamily: "'IBM Plex Mono', sans-serif",
      fontSize: 11,
      color: text.secondary,
      position: 'fixed',
      bottom: 0,
      left: 0,
      right: 0,
      zIndex: 200,
      transition: 'all 0.3s ease',
    }}>
      <span style={{ color: text.muted }}>STATUS</span>
      <span style={{ color: wsConnected ? accent.ok : accent.ng }}>
        {wsConnected ? '✓ LIVE' : '✗ OFFLINE'}
      </span>
      <span style={{ marginLeft: 'auto', color: text.muted }}>
        {clock}
      </span>
    </div>
  );
}
