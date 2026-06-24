<template>
  <div ref="mountEl" class="section-scene"></div>
</template>

<script setup>
import { ref, watch, onMounted, onBeforeUnmount } from 'vue'
import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import { createThermalCanvas, paintThermal } from './thermalTexture.js'

const props = defineProps({
  furnaceId: { type: String, default: null },
  live: { type: Object, default: () => ({}) },
})

const mountEl = ref(null)
let renderer, scene, camera, controls, animId
let clipPlane
let thermalCanvas, thermalTexture, thermalMesh
let parts = {}
let lastThermalPaint = 0
let thermalPhase = 0
// 動畫狀態（緩動用，非 reactive）
let bodyH = 0.1, bodyR = 0.4, pull = 0
const liveRef = { current: {} }

// 幾何常數
const MELT_Y     = 1.3      // 熔湯面 / 生長前緣
const LID_Y      = 4.07     // 頂蓋
const SHOULDER_H = 0.3      // 肩(晶冠)高
const NECK_H     = 0.25     // 細頸高
const SEED_H     = 0.18     // 晶種高
const WIRE_BASE  = 1.0      // 鋼琴線基準長(用 scale.y 拉伸)

watch(() => props.live, v => { liveRef.current = v || {} }, { immediate: true, deep: true })

// ────────────────────────────────────────
function buildSection() {
  const root = new THREE.Group()

  clipPlane = new THREE.Plane(new THREE.Vector3(-1, 0, 0), 0)

  const matCut = (params) => new THREE.MeshStandardMaterial({
    ...params, side: THREE.DoubleSide, clippingPlanes: [clipPlane], clipShadows: true,
  })

  // 1. 外殼
  const shell = new THREE.Mesh(
    new THREE.CylinderGeometry(1.5, 1.55, 4.0, 64, 1, true),
    matCut({ color: 0x6b7785, metalness: 0.85, roughness: 0.35 })
  )
  shell.position.y = 2.0; shell.renderOrder = 1; root.add(shell)

  // 2. 隔熱層
  const insulation = new THREE.Mesh(
    new THREE.CylinderGeometry(1.35, 1.4, 3.6, 64, 1, true),
    matCut({ color: 0x2a1f18, metalness: 0.1, roughness: 0.95 })
  )
  insulation.position.y = 2.0; insulation.renderOrder = 1; root.add(insulation)

  // 3. 加熱器
  const heater = new THREE.Mesh(
    new THREE.CylinderGeometry(1.15, 1.15, 1.4, 48, 1, true),
    matCut({ color: 0x1a1a1a, metalness: 0.4, roughness: 0.6,
      emissive: new THREE.Color(0xff3300), emissiveIntensity: 0.6 })
  )
  heater.position.y = 1.5; heater.renderOrder = 1; parts.heater = heater; root.add(heater)

  // 4. 石英坩堝
  const cruciblePot = new THREE.Mesh(
    new THREE.CylinderGeometry(0.95, 0.85, 1.0, 48, 1, true),
    matCut({ color: 0xddd8c8, metalness: 0.0, roughness: 0.35, transparent: true, opacity: 0.55 })
  )
  cruciblePot.position.y = 1.3; cruciblePot.renderOrder = 1; root.add(cruciblePot)

  const crucibleBottom = new THREE.Mesh(
    new THREE.SphereGeometry(0.85, 32, 16, 0, Math.PI * 2, Math.PI * 0.55, Math.PI * 0.45),
    matCut({ color: 0xddd8c8, roughness: 0.35, transparent: true, opacity: 0.55 })
  )
  crucibleBottom.position.y = 0.85; crucibleBottom.renderOrder = 1; root.add(crucibleBottom)

  // 5. 熔湯
  const melt = new THREE.Mesh(
    new THREE.CylinderGeometry(0.78, 0.78, 0.5, 48),
    matCut({ color: 0xff7a30, metalness: 0.7, roughness: 0.15,
      emissive: new THREE.Color(0xff5500), emissiveIntensity: 0.9 })
  )
  melt.position.y = 1.05; melt.renderOrder = 1; parts.melt = melt; root.add(melt)

  const meltTop = new THREE.Mesh(
    new THREE.CircleGeometry(0.78, 48),
    new THREE.MeshStandardMaterial({
      color: 0xffb070, emissive: new THREE.Color(0xff7a30), emissiveIntensity: 1.0,
      side: THREE.DoubleSide, clippingPlanes: [clipPlane],
    })
  )
  meltTop.rotation.x = -Math.PI / 2; meltTop.position.y = MELT_Y; meltTop.renderOrder = 1; root.add(meltTop)

  // 6. 晶棒（整組，由下而上：本體 → 肩 → 細頸）
  const ingotGroup = new THREE.Group()
  ingotGroup.name = 'ingot_group'
  const ingotMat = matCut({ color: 0x8a96a4, metalness: 0.9, roughness: 0.18 })

  // 本體（圓柱，基準高 1.0，靠 scale.y 拉長；底錨在熔湯面）
  const ingotBody = new THREE.Mesh(new THREE.CylinderGeometry(0.22, 0.22, 1.0, 32), ingotMat)
  ingotBody.renderOrder = 1; parts.ingotBody = ingotBody; ingotGroup.add(ingotBody)

  // 肩 / 晶冠（圓錐：apex 朝上=細、base 朝下=粗，接在本體頂；寬度跟本體）
  const shoulder = new THREE.Mesh(new THREE.ConeGeometry(0.22, SHOULDER_H, 32), ingotMat)
  shoulder.renderOrder = 1; parts.shoulder = shoulder; ingotGroup.add(shoulder)

  // 細頸
  const neck = new THREE.Mesh(new THREE.CylinderGeometry(0.05, 0.05, NECK_H, 24), ingotMat)
  neck.renderOrder = 1; parts.neck = neck; ingotGroup.add(neck)

  parts.ingotGroup = ingotGroup; root.add(ingotGroup)

  // 晶種（小錐，apex 朝下，貼在頸頂；全程顯示）
  const seedMat = matCut({ color: 0xc0d0e0, metalness: 0.9, roughness: 0.12 })
  const seed = new THREE.Mesh(new THREE.ConeGeometry(0.06, SEED_H, 24), seedMat)
  seed.rotation.z = Math.PI; seed.renderOrder = 1; parts.seed = seed; root.add(seed)

  // 7. 拉桿 / 鋼琴線（動態長度，接晶種頂 → 頂蓋）
  const wire = new THREE.Mesh(
    new THREE.CylinderGeometry(0.015, 0.015, WIRE_BASE, 8),
    matCut({ color: 0xc5d2e0, metalness: 1.0, roughness: 0.05 })
  )
  wire.renderOrder = 1; parts.wire = wire; root.add(wire)

  // 8. 頂蓋
  const lid = new THREE.Mesh(
    new THREE.CylinderGeometry(1.5, 1.5, 0.15, 48),
    matCut({ color: 0x4a5562, metalness: 0.8, roughness: 0.4 })
  )
  lid.position.y = LID_Y; lid.renderOrder = 1; root.add(lid)

  // 9. 底座
  const base = new THREE.Mesh(
    new THREE.CylinderGeometry(1.55, 1.65, 0.2, 48),
    matCut({ color: 0x2a3038, metalness: 0.6, roughness: 0.6 })
  )
  base.position.y = -0.0; base.renderOrder = 1; root.add(base)

  return root
}

// ★ 溫度場：貼到剖面平面(X=0)上,法向 +X
function buildThermalBackdrop() {
  thermalCanvas = createThermalCanvas()
  paintThermal(thermalCanvas, { heaterTemp: 1200, phase: 0 })
  thermalTexture = new THREE.CanvasTexture(thermalCanvas)
  thermalTexture.colorSpace = THREE.SRGBColorSpace
  thermalTexture.minFilter = THREE.LinearFilter
  thermalTexture.magFilter = THREE.LinearFilter

  const mat = new THREE.MeshBasicMaterial({
    map: thermalTexture, transparent: true, opacity: 0.65,
    depthWrite: false, depthTest: true, side: THREE.DoubleSide,
  })
  const geo = new THREE.PlaneGeometry(3.4, 4.4)
  const mesh = new THREE.Mesh(geo, mat)
  mesh.position.set(-0.005, 2.0, 0)
  mesh.rotation.y = Math.PI / 2
  mesh.renderOrder = -1
  return mesh
}

// ────────────────────────────────────────
function animate() {
  animId = requestAnimationFrame(animate)
  const t = performance.now() / 1000
  const live = liveRef.current
  const temp = parseFloat(live?.heaterTemp) || 0
  const body = parseFloat(live?.bodyLength) || 0
  const diam = parseFloat(live?.diameter)   || 0
  const seedLift = parseFloat(live?.seedLift) || 0
  const mode = String(live?.operationMode || '').toUpperCase()

  if (parts.heater?.material) {
    const k = Math.max(0, Math.min(1, (temp - 900) / 500))
    parts.heater.material.emissiveIntensity = 0.4 + k * 1.6
    parts.heater.material.emissive.setHSL(0.04 - 0.02 * k, 0.95, 0.3 + 0.15 * k)
  }
  if (parts.melt?.material) {
    const k = Math.max(0, Math.min(1, (temp - 900) / 500))
    parts.melt.material.emissiveIntensity = 0.7 + Math.sin(t * 2) * 0.15 + k * 0.3
  }

  const hideIngot = (body < 5) || mode === 'MELT' || mode === 'STABILIZE'
  if (parts.ingotGroup) parts.ingotGroup.visible = !hideIngot

  // ── seedLift → 緩動拉升偏移（clamp，讓晶種/鋼琴線可見上移）──
  const liftTarget = Math.max(0, Math.min(0.6, seedLift * 0.25))
  pull += (liftTarget - pull) * 0.05

  let topAnchor   // 細頸頂端 y（晶種掛這上面）
  if (!hideIngot && parts.ingotBody) {
    const targetH = Math.max(0.1, Math.min(2.4, body / 200))
    const targetR = Math.max(0.4, Math.min(1.4, diam / 100))
    bodyH += (targetH - bodyH) * 0.06
    bodyR += (targetR - bodyR) * 0.06

    // 本體：底錨在熔湯面，往上長
    parts.ingotBody.scale.set(bodyR, bodyH, bodyR)
    parts.ingotBody.position.y = MELT_Y + bodyH * 0.5
    const bodyTopY = MELT_Y + bodyH

    // 肩(晶冠)：接本體頂，寬度跟本體
    parts.shoulder.scale.set(bodyR, 1, bodyR)
    parts.shoulder.position.y = bodyTopY + SHOULDER_H * 0.5
    const shoulderTopY = bodyTopY + SHOULDER_H

    // 細頸
    parts.neck.position.y = shoulderTopY + NECK_H * 0.5
    topAnchor = shoulderTopY + NECK_H
  } else {
    // 拉晶前：晶種懸在熔湯上方
    topAnchor = MELT_Y + 0.15
  }
  topAnchor += pull

  // 晶種（apex 朝下，貼頸頂）
  parts.seed.position.y = topAnchor + SEED_H * 0.5
  const seedTopY = topAnchor + SEED_H

  // 鋼琴線：晶種頂 → 頂蓋，動態長度（晶種越高線越短 = 被拉上去）
  const wireLen = Math.max(0.1, LID_Y - seedTopY)
  parts.wire.scale.y = wireLen / WIRE_BASE
  parts.wire.position.y = seedTopY + wireLen * 0.5

  if (t - lastThermalPaint > 0.2) {
    lastThermalPaint = t
    thermalPhase += 0.08
    if (thermalCanvas && thermalTexture) {
      paintThermal(thermalCanvas, { heaterTemp: temp, phase: thermalPhase })
      thermalTexture.needsUpdate = true
    }
  }

  controls.update()
  renderer.render(scene, camera)
}

// ────────────────────────────────────────
onMounted(() => {
  const el = mountEl.value

  renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true })
  renderer.setPixelRatio(Math.min(devicePixelRatio, 2))
  renderer.setSize(el.clientWidth, el.clientHeight)
  renderer.localClippingEnabled = true
  renderer.toneMapping = THREE.NoToneMapping
  renderer.sortObjects = true
  el.appendChild(renderer.domElement)

  scene = new THREE.Scene()
  scene.background = null

  camera = new THREE.PerspectiveCamera(38, el.clientWidth / el.clientHeight, 0.1, 100)
  camera.position.set(0.4, 2.4, 8.5)

  controls = new OrbitControls(camera, renderer.domElement)
  controls.enableDamping = true
  controls.dampingFactor = 0.08
  controls.minDistance = 4
  controls.maxDistance = 14
  controls.maxPolarAngle = Math.PI / 2 + 0.15
  controls.target.set(0, 2.0, 0)
  controls.update()

  scene.add(new THREE.AmbientLight(0xaabbcc, 1.8))
  const key = new THREE.DirectionalLight(0xffffff, 1.6)
  key.position.set(6, 8, 4); scene.add(key)
  const rim = new THREE.DirectionalLight(0x88aaff, 0.6)
  rim.position.set(-5, 3, -4); scene.add(rim)

  const innerGlow = new THREE.PointLight(0xff5520, 3.5, 6, 1.5)
  innerGlow.position.set(0, 1.5, 0); scene.add(innerGlow); parts.innerGlow = innerGlow

  scene.add(buildSection())
  thermalMesh = buildThermalBackdrop()
  scene.add(thermalMesh)

  const onResize = () => {
    if (!el) return
    const w = el.clientWidth, h = el.clientHeight
    if (w === 0 || h === 0) return
    camera.aspect = w / h
    camera.updateProjectionMatrix()
    renderer.setSize(w, h)
  }
  window.addEventListener('resize', onResize)
  setTimeout(onResize, 250)

  animate()

  onBeforeUnmount(() => {
    cancelAnimationFrame(animId)
    window.removeEventListener('resize', onResize)
    controls?.dispose()
    renderer?.dispose()
    thermalTexture?.dispose()
    scene?.traverse(obj => {
      if (obj.isMesh) {
        obj.geometry?.dispose()
        if (Array.isArray(obj.material)) obj.material.forEach(m => m.dispose())
        else obj.material?.dispose()
      }
    })
    if (el && renderer && el.contains(renderer.domElement)) {
      el.removeChild(renderer.domElement)
    }
  })
})
</script>

<style scoped>
.section-scene { width: 100%; height: 100%; position: relative; overflow: hidden; }
.section-scene :deep(canvas) { display: block; width: 100% !important; height: 100% !important; }
</style>
