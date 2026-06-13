import React, { useEffect, useRef } from 'react';
import { useTheme } from '../../theme-system.jsx';  // 👈 新增

export default function FurnaceOverlay({ furnaceData = {}, wsConnected = false }) {
  const { bg, text, accent, border } = useTheme();  // 👈 新增

  return (
    <div style={{
      position: 'absolute',
      inset: 0,
      pointerEvents: 'none',
      display: 'flex',
      alignItems: 'flex-end',
      justifyContent: 'space-between',
      padding: '12px 16px',
    }}>
      {['C1', 'C2'].map(id => (
        <FurnaceTag key={id} id={id} data={furnaceData[id]} />
      ))}

      {/* WS 连接状态 */}
      <div style={{
        position: 'absolute',
        top: 10,
        right: 12,
        display: 'flex',
        alignItems: 'center',
        gap: 6,
        fontFamily: "'IBM Plex Mono', sans-serif",
        fontSize: 10,
        color: wsConnected ? accent.ok : accent.ng,
        background: bg.primary + 'cc',  // 👈 使用主题背景
        border: `1px solid ${wsConnected ? accent.ok + '50' : accent.ng + '50'}`,  // 👈 使用主题边框
        borderRadius: 5,
        padding: '3px 8px',
        backdropFilter: 'blur(4px)',
      }}>
        <div style={{
          width: 6,
          height: 6,
          borderRadius: '50%',
          background: wsConnected ? accent.ok : accent.ng,
          boxShadow: `0 0 4px ${wsConnected ? accent.ok : accent.ng}`,
        }} />
        {wsConnected ? 'LIVE' : 'OFFLINE'}
      </div>

      {/* 操作提示 */}
      <div style={{
        position: 'absolute',
        bottom: 10,
        left: '50%',
        transform: 'translateX(-50%)',
        fontFamily: "'IBM Plex Mono', sans-serif",
        fontSize: 9,
        color: text.dim,  // 👈 使用主题文本
        background: bg.primary + '80',  // 👈 使用主题背景
        borderRadius: 4,
        padding: '2px 8px',
      }}>
        拖曳旋轉 · 滾輪縮放 · 右鍵平移
      </div>
    </div>
  );
}

function fmt(val, decimals) {
  const n = parseFloat(val);
  return isNaN(n) ? '—' : n.toFixed(decimals);
}

function FurnaceTag({ id, data }) {
  const { bg, text, accent, border } = useTheme();  // 👈 新增

  if (!data) return null;

  // 改用字符串比较（如前所述）
  const isNg = String(data.event) !== '1';
  const statusColor = isNg ? accent.ng : accent.ok;

  return (
    <div style={{
      background: bg.primary + 'd0',  // 👈 使用主题
      border: `1px solid ${statusColor}50`,  // 👈 使用主题强调色
      borderRadius: 8,
      padding: '8px 12px',
      minWidth: 130,
      backdropFilter: 'blur(6px)',
      boxShadow: `0 0 16px ${statusColor}20`,  // 👈 使用主题强调色
      transition: 'all 0.3s ease',
    }}>
      {/* 炉号 + 状态 */}
      <div style={{
        display: 'flex',
        alignItems: 'center',
        gap: 6,
        marginBottom: 6,
      }}>
        <div style={{
          width: 7,
          height: 7,
          borderRadius: '50%',
          background: statusColor,
          boxShadow: `0 0 5px ${statusColor}`,
          flexShrink: 0,
        }} />
        <span style={{
          fontFamily: "'IBM Plex Mono', sans-serif",
          fontSize: 12,
          fontWeight: 600,
          color: text.primary,  // 👈 使用主题
        }}>
          爐 {id}
        </span>
        <span style={{
          fontFamily: "'IBM Plex Mono', sans-serif",
          fontSize: 9,
          fontWeight: 600,
          padding: '1px 5px',
          borderRadius: 3,
          background: statusColor + '25',  // 👈 使用主题强调色
          color: statusColor,
          border: `1px solid ${statusColor}40`,  // 👈 使用主题强调色
        }}>
          {isNg ? 'NG' : 'OK'}
        </span>
      </div>

      {/* KPI 数据 */}
      <div style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 3,
      }}>
        <KpiRow
          label="Mode"
          val={data.operationMode || '—'}
          color={accent.info}
        />
        <KpiRow
          label="Ø"
          val={fmt(data.diameter, 1) + 'mm'}
          color={statusColor}
        />
        <KpiRow
          label="Temp"
          val={fmt(data.heaterTemp, 0) + '°C'}
          color={isNg ? accent.ng : accent.warn}
        />
        <KpiRow
          label="GR"
          val={fmt(data.grMean, 3)}
          color={accent.info}
        />
      </div>
    </div>
  );
}

function KpiRow({ label, val, color }) {
  const { text } = useTheme();  // 👈 新增

  return (
    <div style={{
      display: 'flex',
      justifyContent: 'space-between',
      gap: 12,
    }}>
      <span style={{
        fontFamily: "'IBM Plex Mono', sans-serif",
        fontSize: 9,
        color: text.muted,  // 👈 使用主题
        letterSpacing: '0.04em',
      }}>
        {label}
      </span>
      <span style={{
        fontFamily: "'IBM Plex Mono', sans-serif",
        fontSize: 11,
        fontWeight: 600,
        color,
      }}>
        {val}
      </span>
    </div>
  );
}
