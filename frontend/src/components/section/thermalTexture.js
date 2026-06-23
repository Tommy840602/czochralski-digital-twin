// 溫度場背景紋理 v2:更像「場」,不像「光柱」
//   - 範圍更廣、層次更平緩
//   - 中心溫度不要那麼極端
//   - 多層 noise 讓它看起來像連續流場
//   - 配色稍微壓低飽和度,符合工程剖面美學

const W = 512
const H = 768

export function createThermalCanvas() {
  const canvas = (typeof OffscreenCanvas !== 'undefined')
    ? new OffscreenCanvas(W, H)
    : Object.assign(document.createElement('canvas'), { width: W, height: H })
  return canvas
}

function hsl(h, s, l) {
  s /= 100; l /= 100
  const k = n => (n + h / 30) % 12
  const a = s * Math.min(l, 1 - l)
  const f = n => l - a * Math.max(-1, Math.min(k(n) - 3, Math.min(9 - k(n), 1)))
  return [
    Math.round(255 * f(0)),
    Math.round(255 * f(8)),
    Math.round(255 * f(4)),
  ]
}

// 溫度 → 色:深藍 → 青 → 綠 → 黃 → 橙 → 深紅(壓低飽和)
function tempToRGB(t) {
  t = Math.max(0, Math.min(1, t))
  const h = 240 - 240 * t           // 240 藍 → 0 紅
  const s = 55 + 20 * (1 - Math.abs(t - 0.5) * 2)  // 中段最飽和,兩端較灰
  const l = 28 + 18 * t             // 越熱越亮
  return hsl(h, s, l)
}

// 多層 fbm-ish noise(便宜的偽 Perlin,2 階)
function noise2(x, y, t = 0) {
  const a = Math.sin(x * 0.020 + y * 0.013 + t) * 0.5
  const b = Math.sin(x * 0.045 - y * 0.038 + t * 0.7) * 0.3
  const c = Math.sin(x * 0.09 + y * 0.07 + t * 1.4) * 0.2
  return a + b + c   // -1..1 區間
}

export function paintThermal(canvas, params) {
  const ctx = canvas.getContext('2d')
  const { heaterTemp = 1200, phase = 0 } = params || {}

  // 強度映射:900°C → 0、1500°C → 1,範圍更廣讓低溫場也看得見
  const intensity = Math.max(0, Math.min(1, (heaterTemp - 850) / 650))

  // 加熱器中心(畫面中下方)
  const cx = 0.5
  const cy = 0.60

  const img = ctx.createImageData(W, H)
  const data = img.data

  for (let py = 0; py < H; py++) {
    for (let px = 0; px < W; px++) {
      const nx = px / W
      const ny = py / H

      const dx = nx - cx
      const dy = ny - cy
      const r  = Math.sqrt(dx * dx + dy * dy)

      // 高斯衰減,sigma 大 → 場分佈廣;從 0.32 改成 0.5
      const sigma = 0.5
      const radial = Math.exp(-(r * r) / (2 * sigma * sigma))

      // 浮力上升:熱往上飄,但減弱效果(避免上方形成火焰柱)
      const above = Math.max(0, cy - ny)
      const buoyancy = 1 + above * 0.7

      // 整體溫度(0..1)
      let t = radial * buoyancy

      // 邊界平緩冷卻
      const edgeX = Math.min(nx, 1 - nx) * 2
      t *= 0.5 + 0.5 * edgeX

      // 加 fbm noise 製造「流動感」
      // noise 強度跟著溫度增大,冷區噪音很少、熱區較劇烈(模擬湍流)
      t += noise2(px, py, phase) * 0.06 * (0.3 + intensity * 0.7)

      // 整體強度由 heaterTemp 控制,但低溫也有底色
      t = 0.05 + t * (0.35 + intensity * 0.6)

      const [r8, g8, b8] = tempToRGB(t)
      const i = (py * W + px) * 4
      data[i]   = r8
      data[i+1] = g8
      data[i+2] = b8
      data[i+3] = 255
    }
  }

  ctx.putImageData(img, 0, 0)
}
