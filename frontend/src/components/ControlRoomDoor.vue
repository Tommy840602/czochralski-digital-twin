<script setup>
import { ref, computed, watch, onMounted } from 'vue'

/**
 * 戰情室大門進場布幕(賽博霓虹)。
 * 用法:把真正的儀表板放進 default slot,門會疊在上面;
 *       boot 完成(phase='ready')後門全開、淡出、emit('enter')。
 */
const props = defineProps({
  progress:    { type: Number,  default: 0 },   // 0..1 真實進度
  stage:       { type: Number,  default: 0 },   // 0..3 完成的里程碑數
  statusLabel: { type: String,  default: '初始化系統' },
  phase:       { type: String,  default: 'booting' }, // booting|ready|entered|error
  error:       { type: [Object, Error, null], default: null },
})
const emit = defineEmits(['enter', 'retry'])

const reduce = ref(false)
const openVal = ref(0)        // 門開幅度 0..1
const showOnline = ref(false) // SYSTEM ONLINE 閃光
const gone = ref(false)       // 布幕淡出後卸載

const charge = computed(() => props.progress)

// 門開幅度跟著真實進度,但 boot 階段封頂在 0.62,把最戲劇化的全開留給「就緒」那一刻
watch(() => props.progress, (p) => {
  if (props.phase === 'booting') openVal.value = Math.min(p, 1) * 0.62
}, { immediate: true })

// 就緒:閃 SYSTEM ONLINE → 門全開 → 淡出 → 通知父層進場
watch(() => props.phase, async (ph) => {
  if (ph !== 'ready') return
  const wait = (ms) => new Promise((r) => setTimeout(r, ms))
  if (!reduce.value) { showOnline.value = true; await wait(500) }
  openVal.value = 1
  await wait(reduce.value ? 60 : 850)
  gone.value = true
  await wait(reduce.value ? 0 : 900) // 等淡出 transition 跑完
  emit('enter')
})

const lampClass = (i) => {
  if (props.stage > i) return 'lamp done'
  if (props.stage === i && props.phase === 'booting') return 'lamp charging'
  return 'lamp'
}

onMounted(() => {
  reduce.value = window.matchMedia('(prefers-reduced-motion: reduce)').matches
})
</script>

<template>
  <div class="twin-shell">
    <!-- 真正的內容(儀表板 / Three.js 場景),門關著時就在背後 boot -->
    <slot />

    <!-- 門布幕 -->
    <div
      v-if="!gone"
      class="cr-overlay"
      :class="{ gone: phase === 'entered', reduce }"
      :style="{ '--open': openVal, '--charge': charge }"
      role="status"
      :aria-label="`系統載入中 ${Math.round(charge * 100)}%`"
    >
      <div class="seam-glow" aria-hidden="true"></div>
      <div class="seam-blip" aria-hidden="true"></div>

      <span class="bracket tl"></span><span class="bracket tr"></span>
      <span class="bracket bl"></span><span class="bracket br"></span>

      <!-- 左門 -->
      <div class="door door-l" aria-hidden="true">
        <div class="seam-edge"></div>
        <div class="title-half">CZOCHRALSKI</div>
        <svg class="seal" viewBox="0 0 280 280">
          <g class="ring-rot"><circle cx="140" cy="140" r="118" fill="none" stroke="#0e5c6b" stroke-width="1.5" stroke-dasharray="3 9"/></g>
          <g class="ring-rot rev">
            <circle cx="140" cy="140" r="100" fill="none" stroke="#0e5c6b" stroke-width="1"/>
            <circle cx="140" cy="22" r="3" fill="#2df0ff"/><circle cx="140" cy="258" r="3" fill="#2df0ff"/>
            <circle cx="22" cy="140" r="3" fill="#2df0ff"/><circle cx="258" cy="140" r="3" fill="#2df0ff"/>
          </g>
          <polygon points="140,30 226,90 226,190 140,250 54,190 54,90" fill="none" stroke="#2df0ff" stroke-width="2" opacity=".85"/>
          <circle cx="140" cy="140" r="62" fill="rgba(45,240,255,.06)"/>
          <path d="M140,72 L150,118 L168,168 L168,206 Q168,220 150,220 L130,220 Q112,220 112,206 L112,168 L130,118 Z"
                fill="#2df0ff" stroke="#d8fdff" stroke-width="1.5" class="ingot"/>
        </svg>
      </div>
DOCKE
      <!-- 右門 -->
      <div class="door door-r" aria-hidden="true">
        <div class="seam-edge"></div>
        <div class="title-half">WAR ROOM</div>
        <svg class="seal" viewBox="0 0 280 280">
          <g class="ring-rot"><circle cx="140" cy="140" r="118" fill="none" stroke="#0e5c6b" stroke-width="1.5" stroke-dasharray="3 9"/></g>
          <g class="ring-rot rev">
            <circle cx="140" cy="140" r="100" fill="none" stroke="#0e5c6b" stroke-width="1"/>
            <circle cx="140" cy="22" r="3" fill="#2df0ff"/><circle cx="140" cy="258" r="3" fill="#2df0ff"/>
            <circle cx="22" cy="140" r="3" fill="#2df0ff"/><circle cx="258" cy="140" r="3" fill="#2df0ff"/>
          </g>
          <polygon points="140,30 226,90 226,190 140,250 54,190 54,90" fill="none" stroke="#2df0ff" stroke-width="2" opacity=".85"/>
          <circle cx="140" cy="140" r="62" fill="rgba(45,240,255,.06)"/>
          <path d="M140,72 L150,118 L168,168 L168,206 Q168,220 150,220 L130,220 Q112,220 112,206 L112,168 L130,118 Z"
                fill="#2df0ff" stroke="#d8fdff" stroke-width="1.5" class="ingot"/>
        </svg>
      </div>

      <div class="online" :class="{ show: showOnline }" aria-hidden="true">SYSTEM ONLINE</div>

      <!-- HUD readout -->
      <div class="hud">
        <div class="status">
          <span>{{ statusLabel }}</span><span class="caret">_</span>
        </div>
        <div class="lamps">
          <div :class="lampClass(0)"><span class="dot"></span>場景就緒</div>
          <div :class="lampClass(1)"><span class="dot"></span>連線建立</div>
          <div :class="lampClass(2)"><span class="dot"></span>資料載入</div>
        </div>
        <div class="bar"><i></i></div>
        <div class="pct">{{ Math.round(charge * 100) }}%</div>

        <button v-if="phase === 'error'" class="retry-btn" @click="emit('retry')">↻ 重新啟動</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.twin-shell { position: relative; width: 100%; height: 100%; }

.cr-overlay {
  position: fixed; inset: 0; z-index: 9000;
  font-family: 'Chakra Petch', system-ui, sans-serif;
  transition: opacity .9s ease;
  --void: #03060a; --metal-1: #0a1820; --metal-2: #0f2733;
  --edge: #1c4456; --cyan: #2df0ff; --cyan-soft: #8ff7ff;
  --cyan-core: #d8fdff; --cyan-dim: #0e5c6b; --cyan-deep: #073846;
}
.cr-overlay.gone { opacity: 0; pointer-events: none; }

.seam-glow {
  position: absolute; left: 50%; top: 0; bottom: 0; width: 4px; transform: translateX(-50%);
  background: linear-gradient(180deg, transparent, var(--cyan-core) 20%, var(--cyan) 50%, var(--cyan-core) 80%, transparent);
  box-shadow: 0 0 24px 7px var(--cyan), 0 0 70px 26px rgba(45,240,255,.45);
  opacity: calc(.15 + var(--charge) * .85); z-index: 1;
}
.seam-blip {
  position: absolute; left: 50%; width: 14px; height: 80px; transform: translate(-50%,-50%);
  background: radial-gradient(ellipse at center, var(--cyan-core), transparent 70%);
  opacity: calc(var(--charge) * .9); animation: cr-blip 2.6s ease-in-out infinite; z-index: 1;
}
@keyframes cr-blip { 0%,100% { top: 8%; } 50% { top: 92%; } }

.door {
  position: absolute; top: 0; bottom: 0; width: 50.4%; z-index: 3; overflow: visible;
  background:
    repeating-linear-gradient(90deg, transparent 0 46px, rgba(45,240,255,.03) 46px 47px),
    linear-gradient(180deg, var(--metal-2) 0%, var(--metal-1) 55%, #07131a 100%);
  transition: transform 850ms cubic-bezier(.72,.02,.2,1);
}
.reduce .door { transition: transform 250ms linear; }
.door::after {
  content: ''; position: absolute; inset: 0; pointer-events: none;
  background:
    linear-gradient(0deg, transparent calc(28% - 1px), rgba(0,0,0,.5) 28%, transparent calc(28% + 2px)),
    linear-gradient(0deg, transparent calc(72% - 1px), rgba(0,0,0,.5) 72%, transparent calc(72% + 2px));
}
.door-l { left: 0;  transform: translateX(calc(var(--open) * -101%)); }
.door-r { right: 0; transform: translateX(calc(var(--open) *  101%)); }

.seam-edge { position: absolute; top: 0; bottom: 0; width: 16px;
  background: repeating-linear-gradient(-45deg, var(--cyan-deep) 0 11px, #050f16 11px 24px);
  box-shadow: inset 0 0 12px rgba(0,0,0,.6); }
.seam-edge::after { content: ''; position: absolute; top: 0; bottom: 0; width: 2px;
  background: var(--cyan); box-shadow: 0 0 10px 2px var(--cyan), 0 0 26px 6px rgba(45,240,255,.5);
  opacity: calc(.4 + var(--charge) * .6); }
.door-l .seam-edge { right: 0; } .door-l .seam-edge::after { right: 0; }
.door-r .seam-edge { left: 0; }  .door-r .seam-edge::after { left: 0; }

.title-half { position: absolute; top: 38px; font-weight: 600;
  font-size: clamp(16px, 2.4vw, 30px); letter-spacing: .34em;
  color: var(--cyan); text-shadow: 0 0 18px rgba(45,240,255,.55); white-space: nowrap; }
.door-l .title-half { right: 28px; }
.door-r .title-half { left: 28px; }

.seal { position: absolute; top: 50%; width: 280px; height: 280px; transform: translateY(-50%); }
.door-l .seal { right: -140px; clip-path: inset(0 50% 0 0); }
.door-r .seal { left:  -140px; clip-path: inset(0 0 0 50%); }
.ring-rot { transform-origin: 140px 140px; animation: cr-spin 26s linear infinite; }
.ring-rot.rev { animation: cr-spin 18s linear infinite reverse; }
@keyframes cr-spin { to { transform: rotate(360deg); } }
.reduce .ring-rot { animation: none; }
.ingot { filter: drop-shadow(0 0 8px #2df0ff); }

.hud { position: absolute; left: 50%; bottom: 8%; transform: translateX(-50%);
  width: min(560px, 78vw); z-index: 5; text-align: center;
  font-family: 'Share Tech Mono', ui-monospace, monospace; }
.status { font-size: 16px; letter-spacing: .12em; color: var(--cyan-soft);
  min-height: 22px; text-shadow: 0 0 12px rgba(45,240,255,.5); }
.caret { animation: cr-cursor 1s step-end infinite; color: var(--cyan); }
@keyframes cr-cursor { 50% { opacity: 0; } }
.lamps { display: flex; justify-content: center; gap: 28px; margin: 16px 0 14px; }
.lamp { display: flex; align-items: center; gap: 9px; font-size: 12px;
  letter-spacing: .08em; color: var(--cyan-dim); transition: color .4s; }
.lamp .dot { width: 9px; height: 9px; border-radius: 50%; background: #0c2630;
  box-shadow: inset 0 0 4px #000; transition: all .4s; }
.lamp.charging { color: var(--cyan-soft); }
.lamp.charging .dot { background: var(--cyan); box-shadow: 0 0 8px 2px var(--cyan);
  animation: cr-pulse 1s ease-in-out infinite; }
.lamp.done { color: var(--cyan); }
.lamp.done .dot { background: var(--cyan-core); box-shadow: 0 0 10px 3px var(--cyan); }
@keyframes cr-pulse { 50% { opacity: .35; } }
.reduce .lamp.charging .dot { animation: none; }

.bar { height: 3px; width: 100%; background: rgba(45,240,255,.12); border-radius: 2px; overflow: hidden; }
.bar > i { display: block; height: 100%; width: calc(var(--charge) * 100%);
  background: linear-gradient(90deg, var(--cyan-dim), var(--cyan), var(--cyan-core));
  box-shadow: 0 0 10px var(--cyan); transition: width .7s cubic-bezier(.72,.02,.2,1); }
.pct { font-size: 12px; color: var(--cyan-dim); margin-top: 6px; letter-spacing: .15em; }

.retry-btn { margin-top: 16px; font-family: inherit; font-size: 13px; letter-spacing: .1em;
  color: var(--cyan); background: rgba(6,16,24,.8); border: 1px solid var(--edge);
  border-radius: 6px; padding: 8px 18px; cursor: pointer; }
.retry-btn:hover { background: rgba(15,39,51,.9); box-shadow: 0 0 14px rgba(45,240,255,.35); }

.bracket { position: absolute; width: 46px; height: 46px; border: 2px solid var(--cyan-dim); z-index: 6; opacity: .7; }
.bracket.tl { top: 26px; left: 26px; border-right: 0; border-bottom: 0; }
.bracket.tr { top: 26px; right: 26px; border-left: 0; border-bottom: 0; }
.bracket.bl { bottom: 26px; left: 26px; border-right: 0; border-top: 0; }
.bracket.br { bottom: 26px; right: 26px; border-left: 0; border-top: 0; }

.online { position: absolute; left: 50%; top: 50%; transform: translate(-50%,-50%); z-index: 4;
  font-weight: 700; font-size: 40px; letter-spacing: .4em; color: var(--cyan-core);
  text-shadow: 0 0 30px var(--cyan); opacity: 0; pointer-events: none; }
.online.show { animation: cr-flash 1.1s ease forwards; }
@keyframes cr-flash {
  0% { opacity: 0; transform: translate(-50%,-50%) scale(.85); }
  25% { opacity: 1; } 70% { opacity: 1; }
  100% { opacity: 0; transform: translate(-50%,-50%) scale(1.04); }
}
</style>
