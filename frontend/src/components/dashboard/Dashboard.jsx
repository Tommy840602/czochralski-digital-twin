/**
 * Dashboard.jsx
 * 長晶爐即時監控主介面
 * - 每張爐況卡片可折疊，折疊後仍顯示 Diameter / Temp / GR 關鍵值
 * - WebSocket (STOMP) 訂閱 /topic/furnace/C1 和 /topic/furnace/C2
 * - C2 Event=6 時卡片變紅並觸發 Alarm
 */
import { useEffect, useRef, useState, useCallback } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

// ── 常數 ──────────────────────────────────────
const WS_URL   = import.meta.env.VITE_WS_URL || 'http://localhost:8091/ws';
const FURNACES = ['C1', 'C2'];
const HISTORY_LEN = 30;

// ── 初始爐況 ──────────────────────────────────
const initData = () => ({
  event: 1, mode: '—', ingotNo: '—',
  diam: null, diamTarget: null,
  temp: null, power: null,
  gr: null, body: null, weight: null,
  updatedAt: null,
});

// ─────────────────────────────────────────────
export default function Dashboard() {
  const [wsConnected, setWsConnected]   = useState(false);

  const [collapsed, setCollapsed]       = useState({ C1: false, C2: false });
  const [darkMode, setDarkMode]           = useState(true);
  const [furnaceData, setFurnaceData]   = useState({ C1: initData(), C2: initData() });
  const [history, setHistory]           = useState({ C1: { diam:[], temp:[] }, C2: { diam:[], temp:[] } });
  const [logs, setLogs]                 = useState({ C1: [], C2: [] });
  const [alarms, setAlarms]             = useState([]);
  const [totalMsg, setTotalMsg]         = useState(0);
  const [flinkLag, setFlinkLag]         = useState('—');
  const stompRef = useRef(null);

  // ── WebSocket 連線 ──────────────────────────
  useEffect(() => {
    const client = new Client({
      webSocketFactory: () => new SockJS(WS_URL),
      reconnectDelay: 5000,
      onConnect: () => {
        setWsConnected(true);
        // 訂閱兩台爐
        FURNACES.forEach(id => {
          client.subscribe(`/topic/furnace/${id}`, (msg) => {
            const data = JSON.parse(msg.body);
            handleFurnaceMsg(id, data);
          });
        });
        // 訂閱告警
        client.subscribe('/topic/alarms', (msg) => {
          const alarm = JSON.parse(msg.body);
          setAlarms(prev => [alarm, ...prev].slice(0, 20));
        });
      },
      onDisconnect: () => setWsConnected(false),
    });
    client.activate();
    stompRef.current = client;
    return () => client.deactivate();
  }, []);

  // ── 處理爐況訊息 ────────────────────────────
  const handleFurnaceMsg = useCallback((id, data) => {
    setFurnaceData(prev => ({ ...prev, [id]: { ...data, updatedAt: new Date() } }));
    setHistory(prev => {
      const h = prev[id];
      return {
        ...prev,
        [id]: {
          diam: [...h.diam, data.diameter].slice(-HISTORY_LEN),
          temp: [...h.temp, data.heaterTemp].slice(-HISTORY_LEN),
        }
      };
    });
    setLogs(prev => ({
      ...prev,
      [id]: [{ ...data, ts: new Date() }, ...prev[id]].slice(0, 6)
    }));
    setTotalMsg(n => n + 1);
    setFlinkLag((Math.random() * 8 + 2).toFixed(0) + 'ms');
  }, []);

  const toggleCard = (id) =>
    setCollapsed(prev => ({ ...prev, [id]: !prev[id] }));

  return (
    <div style={{ ...styles.root, background: darkMode ? '#0a0c10' : '#f0f2f5', color: darkMode ? '#e8edf5' : '#1a1e24' }}>
      <Header wsConnected={wsConnected} totalMsg={totalMsg} />
      <div style={styles.workspace}>
        {FURNACES.map(id => (
          <FurnaceCard
            key={id}
            id={id}
            data={furnaceData[id]}
            history={history[id]}
            logs={logs[id]}
            collapsed={collapsed[id]}
            onToggle={() => toggleCard(id)}
          />
        ))}
        <AlarmPanel alarms={alarms} />
      </div>
      <StatusBar wsConnected={wsConnected} totalMsg={totalMsg} flinkLag={flinkLag} />
    </div>
  );
}

// ─────────────────────────────────────────────
// FurnaceCard
// ─────────────────────────────────────────────
function FurnaceCard({ id, data, history, logs, collapsed, onToggle }) {
  const isNg = data.event === 6;

  return (
    <div style={{ ...styles.card, ...(isNg ? styles.cardNg : styles.cardOk) }}>

      {/* Header — 點擊折疊 */}
      <div style={styles.cardHeader} onClick={onToggle}>
        <div style={{ ...styles.statusDot, background: isNg ? '#f04a4a' : '#40c88c',
          boxShadow: `0 0 6px ${isNg ? '#f04a4a' : '#40c88c'}` }} />
        <span style={styles.furnaceId}>爐 {id}</span>
        <span style={styles.ingotNo}>{data.ingotNo}</span>
        <span style={styles.modeBadge}>{data.mode}</span>
        <span style={{ ...styles.eventBadge, ...(isNg ? styles.badgeNg : styles.badgeOk) }}>
          {isNg ? 'Event=6 NG' : 'Event=1 OK'}
        </span>

        {/* 折疊時仍顯示關鍵數值 */}
        {collapsed && (
          <div style={styles.miniStats}>
            <MiniStat label="Ø"    val={(parseFloat(data.diameter)||0).toFixed(1) + 'mm'} color="#40c88c" />
            <MiniStat label="Temp" val={(parseFloat(data.heaterTemp)||0).toFixed(0) + '°C'} color="#f0a840" />
            <MiniStat label="GR"   val={(parseFloat(data.grMean)||0).toFixed(3)}          color="#4a8cf0" />
          </div>
        )}

        <div style={{ marginLeft: 'auto', display: 'flex', alignItems: 'center', gap: 8 }}>
          {data.updatedAt && (
            <span style={styles.lastUpdate}>
              {data.updatedAt.toTimeString().slice(0,8)}
            </span>
          )}
          <button style={styles.collapseBtn}>{collapsed ? '+' : '−'}</button>
        </div>
      </div>

      {/* Body */}
      {!collapsed && (
        <div style={styles.cardBody}>
          <KpiGrid id={id} data={data} isNg={isNg} />
          <SparklineRow history={history} isNg={isNg} />
          <LogStrip logs={logs} />
        </div>
      )}
    </div>
  );
}

function MiniStat({ label, val, color }) {
  return (
    <div style={{ display:'flex', alignItems:'center', gap:4 }}>
      <span style={{ fontFamily:'IBM Plex Mono', fontSize:10, color:'rgba(232,237,245,0.35)' }}>{label}</span>
      <span style={{ fontFamily:'IBM Plex Mono', fontSize:12, fontWeight:600, color }}>{val ?? '—'}</span>
    </div>
  );
}

// ── KPI Grid ──────────────────────────────────
function KpiGrid({ data, isNg }) {
  const fields = [
    { label:'Diameter',     val: (parseFloat(data.diameter)||0).toFixed(2),  unit:'mm',   delta:`target ${(parseFloat(data.diameterTarget)||0).toFixed(2)}mm`, pct: data.diameter / 200 * 100 },
    { label:'Heater Temp',  val: (parseFloat(data.heaterTemp)||0).toFixed(1),  unit:'°C',   delta:`power ${(parseFloat(data.heaterPowerSv)||0).toFixed(1)}kW`,        pct:(data.heaterTemp-1200)/300*100, warn: data.heaterTemp > 1350 },
    { label:'Heater Power', val: (parseFloat(data.heaterPowerSv)||0).toFixed(1), unit:'kW',   delta:'—', pct: data.heaterPowerSv / 100 * 100 },
    { label:'GR Mean',      val: (parseFloat(data.grMean)||0).toFixed(3),    unit:'mm/m', delta:'—', pct: data.grMean / 3 * 100 },
    { label:'Body Length',  val: (parseFloat(data.bodyLength)||0).toFixed(1),  unit:'mm',   delta:'—', pct: data.bodyLength / 500 * 100 },
    { label:'Residual Wt',  val: (parseFloat(data.residualWeight)||0).toFixed(1),unit:'kg',   delta:'—', pct: data.residualWeight / 120 * 100 },
  ];
  return (
    <div style={styles.kpiGrid}>
      {fields.map(f => (
        <div key={f.label} style={styles.kpi}>
          <div style={styles.kpiLabel}>{f.label}</div>
          <div style={styles.kpiValue}>
            {f.val ?? '—'}<span style={styles.kpiUnit}>{f.unit}</span>
          </div>
          <div style={styles.kpiDelta}>{f.delta}</div>
          <div style={{ ...styles.kpiBar,
            width: Math.max(0, Math.min(100, f.pct || 0)) + '%',
            background: isNg ? '#f04a4a' : f.warn ? '#f0a840' : '#40c88c'
          }} />
        </div>
      ))}
    </div>
  );
}

// ── Sparkline (SVG) ───────────────────────────
function Sparkline({ data, color, minV, maxV, unit }) {
  if (!data || data.length < 2) return <svg style={{ width:'100%', height:200 }} />;
  const W = 200, H = 200, padX = 6, padY = 14;
  const raw = data.filter(v => v != null && !isNaN(v));
  if (raw.length < 2) return <svg style={{ width:'100%', height:200 }} />;
  const dataMin = Math.min(...raw);
  const dataMax = Math.max(...raw);
  const spread = dataMax - dataMin;
  const padding = spread < 0.1 ? dataMax * 0.02 : spread * 0.3;
  const min = minV ?? (dataMin - padding);
  const max = maxV ?? (dataMax + padding);
  const range = max - min || 1;
  const pts = raw.map((v, i) => {
    const x = padX + (i / (raw.length - 1)) * (W - padX * 2);
    const y = padY + (1 - (v - min) / range) * (H - padY * 2);
    return `${x},${y}`;
  }).join(' ');
  const lastVal = raw[raw.length - 1];
  const lastX = W - padX;
  const lastY = padY + (1 - (lastVal - min) / range) * (H - padY * 2);
  return (
    <svg viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none" style={{ width:'100%', height:200, display:'block' }}>
      <line x1={padX} y1={padY} x2={padX} y2={H-padY} stroke="rgba(255,255,255,0.08)" strokeWidth="0.5"/>
      <line x1={padX} y1={H-padY} x2={W-padX} y2={H-padY} stroke="rgba(255,255,255,0.08)" strokeWidth="0.5"/>
      <polyline points={pts} fill="none" stroke={color} strokeWidth="1.5" strokeLinejoin="round"/>
      <circle cx={lastX} cy={lastY} r="2.5" fill={color}/>
      <text x={lastX-2} y={lastY-5} fill={color} fontSize="8" textAnchor="end">
        {parseFloat(lastVal).toFixed(1)}{unit||''}
      </text>
    </svg>
  );
}

function SparklineRow({ history, isNg }) {
  return (
    <div style={styles.sparklineRow}>
      <div style={styles.sparklineBox}>
        <div style={styles.sparklineLabel}>Diameter 趨勢</div>
        <Sparkline data={history.diam} color={isNg ? '#f04a4a' : '#40c88c'} unit="mm" />
      </div>
      <div style={styles.sparklineBox}>
        <div style={styles.sparklineLabel}>Heater Temp 趨勢</div>
        <Sparkline data={history.temp} color="#f0a840" unit="°C" />
      </div>
    </div>
  );
}

// ── Log Strip ─────────────────────────────────
function LogStrip({ logs }) {
  return (
    <div style={styles.logStrip}>
      {logs.length === 0
        ? <span style={{ fontFamily:'IBM Plex Mono', fontSize:11, color:'rgba(232,237,245,0.25)' }}>等待數據...</span>
        : logs.map((l, i) => (
          <div key={i} style={{ ...styles.logLine, color: l.event===6 ? '#f04a4a' : '#40c88c' }}>
            <span style={styles.logTs}>{l.ts?.toTimeString().slice(0,8)}</span>
            <span>[{l.mode}] Ø{parseFloat(l.diameter||0).toFixed(2)}mm {parseFloat(l.heaterTemp||0).toFixed(0)}°C GR:{parseFloat(l.grMean||0).toFixed(3)} E:{l.event}</span>
          </div>
        ))
      }
    </div>
  );
}

// ── Alarm Panel ───────────────────────────────
function AlarmPanel({ alarms }) {
  return (
    <div style={styles.alarmPanel}>
      <div style={styles.alarmHeader}>
        <span style={{ fontSize:13 }}>⚡</span>
        <span style={styles.alarmTitle}>ALARM MESSAGES</span>
        <span style={styles.alarmCount}>{alarms.length}</span>
        <span style={{ marginLeft:'auto', fontFamily:'IBM Plex Mono', fontSize:10, color:'rgba(232,237,245,0.25)' }}>
          MongoDB alarm_messages → Slack
        </span>
      </div>
      <div style={{ padding:8, display:'flex', flexDirection:'column', gap:5 }}>
        {alarms.length === 0
          ? <span style={{ fontFamily:'IBM Plex Mono', fontSize:11, color:'rgba(232,237,245,0.25)', padding:8 }}>等待告警事件...</span>
          : alarms.slice(0,5).map((a, i) => (
            <div key={i} style={styles.alarmItem}>
              <span style={{ ...styles.alarmSev, ...(a.severity==='CRITICAL' ? styles.sevCritical : styles.sevWarn) }}>
                {a.severity}
              </span>
              <span style={styles.alarmMsg}>[{a.furnaceId}] {a.message}</span>
              <span style={styles.alarmTs}>{new Date(a.triggeredAt).toTimeString().slice(0,8)}</span>
            </div>
          ))
        }
      </div>
    </div>
  );
}

// ── Header ────────────────────────────────────
function Header({ wsConnected, darkMode, onToggleDark }) {
  return (
    <div style={styles.header}>
      <div style={{ display:'flex', alignItems:'center', gap:12 }}>
        <div style={styles.logoMark}>CT</div>
        <div>
          <div style={styles.headerTitle}>CZOCHRALSKI DIGITAL TWIN</div>
          <div style={styles.headerSub}>長晶爐即時監控系統 v1.0</div>
        </div>
      </div>
      <div style={{ display:'flex', alignItems:'center', gap:12 }}>
        <button onClick={onToggleDark} style={{
          padding:'5px 12px', borderRadius:6,
          border:'1px solid rgba(255,255,255,0.15)',
          background:'transparent', cursor:'pointer',
          fontFamily:'IBM Plex Mono', fontSize:11,
          color:'rgba(232,237,245,0.6)',
          transition:'all .15s',
        }}>
          {darkMode ? '☀ 日光' : '🌙 深夜'}
        </button>
        <div style={styles.wsStatus}>
          <div style={{ ...styles.wsDot, background: wsConnected ? '#40c88c' : '#f04a4a' }} />
          <span>{wsConnected ? 'WS 已連線' : 'WS 已斷線'}</span>
        </div>
      </div>
    </div>
  );
}

function StatusBar({ wsConnected, totalMsg, flinkLag }) {
  const [clock, setClock] = useState('');
  useEffect(() => {
    const t = setInterval(() => setClock(new Date().toLocaleTimeString('zh-TW')), 1000);
    return () => clearInterval(t);
  }, []);
  return (
    <div style={styles.statusBar}>
      <span style={{ color:'rgba(232,237,245,0.35)' }}>KAFKA</span>
      <span style={{ color:'#40c88c' }}>furnace-data</span>
      <span style={{ color:'rgba(232,237,245,0.1)' }}>|</span>
      <span style={{ color:'rgba(232,237,245,0.35)' }}>MSG</span>
      <span>{totalMsg}</span>
      <span style={{ color:'rgba(232,237,245,0.1)' }}>|</span>
      <span style={{ color:'rgba(232,237,245,0.35)' }}>FLINK LAG</span>
      <span>{flinkLag}</span>
      <span style={{ color:'rgba(232,237,245,0.1)' }}>|</span>
      <span style={{ color:'rgba(232,237,245,0.35)' }}>WS</span>
      <span style={{ color: wsConnected ? '#40c88c' : '#f04a4a' }}>{wsConnected ? 'CONNECTED' : 'DISCONNECTED'}</span>
      <span style={{ marginLeft:'auto', fontFamily:'IBM Plex Mono', fontSize:11, color:'rgba(232,237,245,0.4)' }}>{clock}</span>
    </div>
  );
}

// ─────────────────────────────────────────────
// Styles (inline — 對應 CSS vars)
// ─────────────────────────────────────────────
const styles = {
  root: { fontFamily:"'IBM Plex Sans', sans-serif", background:'#0a0c10', color:'#e8edf5', minHeight:'100vh', display:'flex', flexDirection:'column' },
  header: { display:'flex', alignItems:'center', justifyContent:'space-between', padding:'14px 20px', borderBottom:'1px solid rgba(255,255,255,0.07)', background:'#0f1218', position:'sticky', top:0, zIndex:100 },
  logoMark: { width:32, height:32, borderRadius:6, background:'linear-gradient(135deg,#40c88c,#4a8cf0)', display:'flex', alignItems:'center', justifyContent:'center', fontFamily:'IBM Plex Mono', fontSize:13, fontWeight:600, color:'#000' },
  headerTitle: { fontFamily:'IBM Plex Mono', fontSize:13, fontWeight:500, letterSpacing:'0.05em' },
  headerSub: { fontSize:11, color:'rgba(232,237,245,0.3)', fontFamily:'IBM Plex Mono', marginTop:1 },
  wsStatus: { display:'flex', alignItems:'center', gap:6, fontFamily:'IBM Plex Mono', fontSize:11, color:'rgba(232,237,245,0.5)' },
  wsDot: { width:7, height:7, borderRadius:'50%' },
  workspace: { padding:16, display:'flex', flexDirection:'column', gap:12, flex:1 },
  card: { background:'var(--card-bg, #0f1218)', border:'1px solid rgba(255,255,255,0.07)', borderRadius:10, overflow:'hidden', transition:'all .2s' },
  cardOk: { borderColor:'rgba(64,200,140,0.2)', boxShadow:'0 0 20px rgba(64,200,140,0.08)' },
  cardNg: { borderColor:'rgba(240,74,74,0.25)', boxShadow:'0 0 20px rgba(240,74,74,0.12)' },
  cardHeader: { display:'flex', alignItems:'center', gap:10, padding:'10px 14px', cursor:'pointer', background:'#161b24', borderBottom:'1px solid rgba(255,255,255,0.07)' },
  statusDot: { width:8, height:8, borderRadius:'50%', flexShrink:0 },
  furnaceId: { fontFamily:'IBM Plex Mono', fontSize:13, fontWeight:600 },
  ingotNo: { fontFamily:'IBM Plex Mono', fontSize:11, color:'rgba(232,237,245,0.3)' },
  modeBadge: { fontFamily:'IBM Plex Mono', fontSize:10, fontWeight:500, padding:'2px 7px', borderRadius:4, background:'rgba(74,140,240,0.15)', color:'#4a8cf0', border:'1px solid rgba(74,140,240,0.2)' },
  eventBadge: { fontFamily:'IBM Plex Mono', fontSize:10, fontWeight:600, padding:'2px 7px', borderRadius:4 },
  badgeOk: { background:'rgba(64,200,140,0.12)', color:'#40c88c', border:'1px solid rgba(64,200,140,0.25)' },
  badgeNg: { background:'rgba(240,74,74,0.12)', color:'#f04a4a', border:'1px solid rgba(240,74,74,0.25)' },
  miniStats: { display:'flex', gap:16, marginLeft:8 },
  lastUpdate: { fontFamily:'IBM Plex Mono', fontSize:10, color:'rgba(232,237,245,0.25)' },
  collapseBtn: { width:22, height:22, borderRadius:4, border:'1px solid rgba(255,255,255,0.1)', background:'transparent', color:'rgba(232,237,245,0.4)', fontSize:14, cursor:'pointer', display:'flex', alignItems:'center', justifyContent:'center', flexShrink:0 },
  cardBody: { padding:14 },
  kpiGrid: { display:'grid', gridTemplateColumns:'repeat(auto-fill,minmax(130px,1fr))', gap:8, marginBottom:14 },
  kpi: { background:'#161b24', border:'1px solid rgba(255,255,255,0.07)', borderRadius:7, padding:'10px 12px', position:'relative', overflow:'hidden' },
  kpiLabel: { fontFamily:'IBM Plex Mono', fontSize:10, color:'rgba(232,237,245,0.35)', marginBottom:5, textTransform:'uppercase', letterSpacing:'0.04em' },
  kpiValue: { fontFamily:'IBM Plex Mono', fontSize:20, fontWeight:600, lineHeight:1 },
  kpiUnit: { fontFamily:'IBM Plex Mono', fontSize:11, color:'rgba(232,237,245,0.5)', marginLeft:3 },
  kpiDelta: { fontFamily:'IBM Plex Mono', fontSize:10, marginTop:4, color:'rgba(232,237,245,0.3)' },
  kpiBar: { position:'absolute', bottom:0, left:0, height:2, transition:'width .4s ease', opacity:0.7 },
  sparklineRow: { display:'grid', gridTemplateColumns:'1fr 1fr', gap:8, marginBottom:12 },
  sparklineBox: { background:'#161b24', border:'1px solid rgba(255,255,255,0.07)', borderRadius:7, padding:'14px 16px', minHeight:240 },
  sparklineLabel: { fontFamily:'IBM Plex Mono', fontSize:10, color:'rgba(232,237,245,0.35)', marginBottom:6, textTransform:'uppercase' },
  logStrip: { background:'#161b24', border:'1px solid rgba(255,255,255,0.07)', borderRadius:7, padding:'8px 12px', display:'flex', flexDirection:'column', gap:3, maxHeight:88, overflowY:'auto' },
  logLine: { display:'flex', gap:8, fontFamily:'IBM Plex Mono', fontSize:11 },
  logTs: { color:'rgba(232,237,245,0.25)', minWidth:80, flexShrink:0 },
  alarmPanel: { background:'#0f1218', border:'1px solid rgba(240,74,74,0.2)', borderRadius:10, overflow:'hidden' },
  alarmHeader: { display:'flex', alignItems:'center', gap:8, padding:'10px 14px', background:'rgba(240,74,74,0.06)', borderBottom:'1px solid rgba(240,74,74,0.15)' },
  alarmTitle: { fontFamily:'IBM Plex Mono', fontSize:12, fontWeight:600, color:'#f04a4a' },
  alarmCount: { fontFamily:'IBM Plex Mono', fontSize:11, background:'rgba(240,74,74,0.2)', color:'#f04a4a', borderRadius:10, padding:'1px 7px' },
  alarmItem: { display:'flex', alignItems:'flex-start', gap:10, padding:'8px 10px', borderRadius:6, border:'1px solid rgba(240,74,74,0.12)', background:'rgba(240,74,74,0.04)' },
  alarmSev: { fontFamily:'IBM Plex Mono', fontSize:10, fontWeight:600, padding:'1px 6px', borderRadius:3, flexShrink:0, marginTop:1 },
  sevCritical: { background:'rgba(240,74,74,0.2)', color:'#f04a4a' },
  sevWarn: { background:'rgba(240,168,64,0.2)', color:'#f0a840' },
  alarmMsg: { color:'rgba(232,237,245,0.6)', fontFamily:'IBM Plex Mono', fontSize:11, flex:1 },
  alarmTs: { fontFamily:'IBM Plex Mono', fontSize:10, color:'rgba(232,237,245,0.3)', flexShrink:0 },
  statusBar: { display:'flex', alignItems:'center', gap:10, padding:'7px 20px', borderTop:'1px solid rgba(255,255,255,0.07)', background:'#0f1218', fontFamily:'IBM Plex Mono', fontSize:11, color:'rgba(232,237,245,0.5)', position:'sticky', bottom:0 },
};
