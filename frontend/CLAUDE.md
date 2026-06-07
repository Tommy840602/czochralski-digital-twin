# Frontend — 子模組說明

- **只用 .js / .jsx，禁止任何 .ts / .tsx**
- React 18 + Vite 5 + Three.js r165
- 字體：IBM Plex Mono（數值）+ IBM Plex Sans（說明文字）
- 配色主題：深色工業風（bg:#080b10, accent-ok:#40c88c, accent-ng:#f04a4a）

## Three.js 元件架構

```
FurnaceViewer.jsx      ← 頁面掛這個，含 WS 訂閱
  ├── FurnaceScene.jsx  ← Three.js renderer、場景、動畫 loop
  │     ├── GLTFLoader  src/assets/models/furnace.glb × 2
  │     ├── OrbitControls
  │     ├── UnrealBloomPass（後處理發光）
  │     └── 爐體材質動態更新（heaterTemp → 顏色/emissive）
  ├── FurnaceOverlay.jsx ← HTML 疊加數字標籤（absolute定位）
  └── useFurnaceWebSocket.js 封裝在 FurnaceViewer 內
```

## GLB 模型命名規範
Mesh 名稱要含以下關鍵字 Three.js 才能正確識別：
- `heater` / `body` / `crucible` → 發光材質，隨溫度變色
- `crystal` / `ingot` / `pull`   → 隨 diameter + bodyLength 縮放

## 溫度→顏色映射
- < 1200°C → 藍白（冷機）
- 1200~1300 → 橙
- 1300~1380 → 深紅
- > 1380°C  → 亮紅 + Bloom 爆炸

## Event=6 (NG) 視覺效果
- 爐體 emissive → 紅色閃爍（ngPulse sin 函式）
- PointLight intensity 劇烈脈衝
- Bloom strength 提升到 1.2+
- 爐體 group 輕微震動（position jitter）
- FurnaceOverlay 卡片變紅邊框 + NG badge

## WebSocket 路由
- STOMP /topic/furnace/C1 → FurnaceScene dataRef.current.C1
- STOMP /topic/furnace/C2 → FurnaceScene dataRef.current.C2
- STOMP /topic/alarms     → AlarmPanel
- API base URL：http://localhost:8085（api-gateway，vite proxy）

## 佔位模型（GLB 不存在時）
FurnaceScene 內建 buildPlaceholderFurnace()，用 Three.js 幾何體
組合一個簡單爐形，GLB 載入失敗時自動使用，不影響數據動畫。
