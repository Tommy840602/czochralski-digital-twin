export default function FurnaceOverlay({ furnaceData = {}, wsConnected = false }) {
  return (
    <div style={{
      position:'absolute', inset:0, pointerEvents:'none',
      display:'flex', alignItems:'flex-end', justifyContent:'space-between', padding:'12px 16px',
    }}>
      {['C1','C2'].map(id => (
        <FurnaceTag key={id} id={id} data={furnaceData[id]} />
      ))}
      <div style={{
        position:'absolute', top:10, right:12,
        display:'flex', alignItems:'center', gap:6,
        fontFamily:'IBM Plex Mono', fontSize:10,
        color: wsConnected ? '#40c88c' : '#f04a4a',
        background:'rgba(8,11,16,0.8)',
        border:`1px solid ${wsConnected?'rgba(64,200,140,0.3)':'rgba(240,74,74,0.3)'}`,
        borderRadius:5, padding:'3px 8px',
      }}>
        <div style={{ width:6, height:6, borderRadius:'50%', background: wsConnected ? '#40c88c' : '#f04a4a' }} />
        {wsConnected ? 'LIVE' : 'OFFLINE'}
      </div>
      <div style={{
        position:'absolute', bottom:10, left:'50%', transform:'translateX(-50%)',
        fontFamily:'IBM Plex Mono', fontSize:9, color:'rgba(232,237,245,0.2)',
        background:'rgba(8,11,16,0.5)', borderRadius:4, padding:'2px 8px',
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
  if (!data) return null;
  const isNg = String(data.event) === '6';
  const accentColor = isNg ? '#f04a4a' : '#40c88c';
  const borderColor = isNg ? 'rgba(240,74,74,0.5)' : 'rgba(64,200,140,0.3)';

  return (
    <div style={{
      background:'rgba(8,11,16,0.82)', border:`1px solid ${borderColor}`,
      borderRadius:8, padding:'8px 12px', minWidth:130,
      backdropFilter:'blur(6px)',
      boxShadow: isNg ? '0 0 16px rgba(240,74,74,0.15)' : '0 0 16px rgba(64,200,140,0.08)',
    }}>
      <div style={{ display:'flex', alignItems:'center', gap:6, marginBottom:6 }}>
        <div style={{ width:7, height:7, borderRadius:'50%', background:accentColor, boxShadow:`0 0 5px ${accentColor}` }} />
        <span style={{ fontFamily:'IBM Plex Mono', fontSize:12, fontWeight:600, color:'#e8edf5' }}>爐 {id}</span>
        <span style={{
          fontFamily:'IBM Plex Mono', fontSize:9, fontWeight:600,
          padding:'1px 5px', borderRadius:3,
          background: isNg ? 'rgba(240,74,74,0.15)' : 'rgba(64,200,140,0.12)',
          color: accentColor, border:`1px solid ${borderColor}`,
        }}>{isNg ? 'NG' : 'OK'}</span>
      </div>
      <div style={{ display:'flex', flexDirection:'column', gap:3 }}>
        <KpiRow label="Mode"  val={data.operationMode || '—'}          color="rgba(74,140,240,0.9)" />
        <KpiRow label="Ø"     val={fmt(data.diameter,1) + 'mm'}        color={accentColor} />
        <KpiRow label="Temp"  val={fmt(data.heaterTemp,0) + '°C'}      color={isNg?'#f04a4a':'#f0a840'} />
        <KpiRow label="GR"    val={fmt(data.grMean,3)}                  color="#4a8cf0" />
      </div>
    </div>
  );
}

function KpiRow({ label, val, color }) {
  return (
    <div style={{ display:'flex', justifyContent:'space-between', gap:12 }}>
      <span style={{ fontFamily:'IBM Plex Mono', fontSize:9, color:'rgba(232,237,245,0.3)', letterSpacing:'0.04em' }}>{label}</span>
      <span style={{ fontFamily:'IBM Plex Mono', fontSize:11, fontWeight:600, color }}>{val}</span>
    </div>
  );
}
