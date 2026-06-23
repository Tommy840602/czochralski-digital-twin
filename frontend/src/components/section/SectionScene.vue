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
const liveRef = { current: {} }

watch(() => props.live, v => { liveRef.current = v || {} }, { immediate: true, deep: true })

// ────────────────────────────────────────
function buildSection() {
  const root = new THREE.Group()

  clipPlane = new THREE.Plane(new THREE.Vector3(-1, 0, 0), 0)

  // ★ 共用：可被裁切的金屬材質。renderOrder=1 確保畫在溫度場之上
  const matCut = (params) => {
    const m = new THREE.MeshStandardMaterial({
      ...params,
      side: THREE.DoubleSide,
      clippingPlanes: [clipPlane],
      clipShadows: true,
    })
    return m
  }

  // 1. 外殼
  const shell = new THREE.Mesh(
    new THREE.CylinderGeometry(1.5, 1.55, 4.0, 64, 1, true),
    matCut({ color: 0x6b7785, metalness: 0.85, roughness: 0.35 })
  )
  shell.position.y = 2.0
  shell.renderOrder = 1
  root.add(shell)

  // 2. 隔熱層
  const insulation = new THREE.Mesh(
    new THREE.CylinderGeometry(1.35, 1.4, 3.6, 64, 1, true),
    matCut({ color: 0x2a1f18, metalness: 0.1, roughness: 0.95 })
  )
  insulation.position.y = 2.0
  insulation.renderOrder = 1
  root.add(insulation)

  // 3. 加熱器
  const heater = new THREE.Mesh(
    new THREE.CylinderGeometry(1.15, 1.15, 1.4, 48, 1, true),
    matCut({
      color: 0x1a1a1a, metalness: 0.4, roughness: 0.6,
      emissive: new THREE.Color(0xff3300), emissiveIntensity: 0.6,
    })
  )
  heater.position.y = 1.5
  heater.renderOrder = 1
  parts.heater = heater
  root.add(heater)

  // 4. 石英坩堝
  const cruciblePot = new THREE.Mesh(
    new THREE.CylinderGeometry(0.95, 0.85, 1.0, 48, 1, true),
    matCut({
      color: 0xddd8c8, metalness: 0.0, roughness: 0.35,
      transparent: true, opacity: 0.55,
    })
  )
  cruciblePot.position.y = 1.3
  cruciblePot.renderOrder = 1
  root.add(cruciblePot)

  const crucibleBottom = new THREE.Mesh(
    new THREE.SphereGeometry(0.85, 32, 16, 0, Math.PI * 2, Math.PI * 0.55, Math.PI * 0.45),
    matCut({ color: 0xddd8c8, roughness: 0.35, transparent: true, opacity: 0.55 })
  )
  crucibleBottom.position.y = 0.85
  crucibleBottom.renderOrder = 1
  root.add(crucibleBottom)

  // 5. 熔湯
  const melt = new THREE.Mesh(
    new THREE.CylinderGeometry(0.78, 0.78, 0.5, 48),
    matCut({
      color: 0xff7a30, metalness: 0.7, roughness: 0.15,
      emissive: new THREE.Color(0xff5500), emissiveIntensity: 0.9,
    })
  )
  melt.position.y = 1.05
  melt.renderOrder = 1
  parts.melt = melt
  root.add(melt)

  const meltTop = new THREE.Mesh(
    new THREE.CircleGeometry(0.78, 48),
    new THREE.MeshStandardMaterial({
      color: 0xffb070, emissive: new THREE.Color(0xff7a30), emissiveIntensity: 1.0,
      side: THREE.DoubleSide, clippingPlanes: [clipPlane],
    })
  )
  meltTop.rotation.x = -Math.PI / 2
  meltTop.position.y = 1.3
  meltTop.renderOrder = 1
  root.add(meltTop)

  // 6. 晶棒
  const ingotGroup = new THREE.Group()
  ingotGroup.name = 'ingot_group'

  const ingotMat = matCut({ color: 0x8a96a4, metalness: 0.9, roughness: 0.18 })

  const ingotBody = new THREE.Mesh(
    new THREE.CylinderGeometry(0.22, 0.22, 1.0, 32),
    ingotMat
  )
  ingotBody.position.y = 1.8
  ingotBody.renderOrder = 1
  parts.ingotBody = ingotBody
  ingotGroup.add(ingotBody)

  const ingotBase = new THREE.Mesh(
    new THREE.ConeGeometry(0.22, 0.35, 32),
    ingotMat
  )
  ingotBase.position.y = 1.13
  ingotBase.rotation.z = Math.PI
  ingotBase.renderOrder = 1
  parts.ingotBase = ingotBase
  ingotGroup.add(ingotBase)

  const ingotTop = new THREE.Mesh(
    new THREE.ConeGeometry(0.22, 0.3, 32),
    ingotMat
  )
  ingotTop.position.y = 2.45
  ingotTop.renderOrder = 1
  parts.ingotTop = ingotTop
  ingotGroup.add(ingotTop)

  parts.ingotGroup = ingotGroup
  root.add(ingotGroup)

  // seed
  const seedMat = matCut({ color: 0xc0d0e0, metalness: 0.9, roughness: 0.12 })
  const seed = new THREE.Mesh(
    new THREE.ConeGeometry(0.06, 0.18, 24),
    seedMat
  )
  seed.position.y = 1.45
  seed.rotation.z = Math.PI
  seed.renderOrder = 1
  parts.seed = seed
  root.add(seed)

  // 7. 拉桿
  const wire = new THREE.Mesh(
    new THREE.CylinderGeometry(0.015, 0.015, 2.5, 8),
    matCut({ color: 0xc5d2e0, metalness: 1.0, roughness: 0.05 })
  )
  wire.position.y = 3.85
  wire.renderOrder = 1
  root.add(wire)

  // 8. 頂蓋
  const lid = new THREE.Mesh(
    new THREE.CylinderGeometry(1.5, 1.5, 0.15, 48),
    matCut({ color: 0x4a5562, metalness: 0.8, roughness: 0.4 })
  )
  lid.position.y = 4.07
  lid.renderOrder = 1
  root.add(lid)

  // 9. 底座
  const base = new THREE.Mesh(
    new THREE.CylinderGeometry(1.55, 1.65, 0.2, 48),
    matCut({ color: 0x2a3038, metalness: 0.6, roughness: 0.6 })
  )
  base.position.y = -0.0
  base.renderOrder = 1
  root.add(base)

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
    map: thermalTexture,
    transparent: true,
    opacity: 0.65,         // ★ 拉回 0.65,在剖面上要看得清楚
    depthWrite: false,
    depthTest: true,        // ★ 啟用深度測試,讓前方 mesh 正確遮擋
    side: THREE.DoubleSide,
  })

  // 大小覆蓋整個剖面範圍(寬度=爐體直徑、高度=爐體高度)
  const geo = new THREE.PlaneGeometry(3.4, 4.4)
  const mesh = new THREE.Mesh(geo, mat)

  // ★ 放在 X=0(剖面切口),法向 +X(面朝鏡頭那一側)
  // 微微往 -X 偏 0.005,避免 z-fighting
  mesh.position.set(-0.005, 2.0, 0)
  mesh.rotation.y = Math.PI / 2   // 平面從 XY 旋成 YZ
  mesh.renderOrder = -1            // ★ 最早畫,被前方所有 mesh 蓋過

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
  if (parts.seed)       parts.seed.visible       =  hideIngot

  if (!hideIngot && parts.ingotBody) {
    const targetH = Math.max(0.1, Math.min(2.5, body / 200))
    const targetR = Math.max(0.4, Math.min(1.5, diam / 100))
    parts.ingotBody.scale.y += (targetH - parts.ingotBody.scale.y) * 0.06
    parts.ingotBody.scale.x += (targetR - parts.ingotBody.scale.x) * 0.06
    parts.ingotBody.scale.z = parts.ingotBody.scale.x
    parts.ingotBody.position.y = 1.3 + (parts.ingotBody.scale.y * 0.5)
  }

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
  // ★ 啟用 sortObjects 讓 renderOrder 正確生效
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
  key.position.set(6, 8, 4)
  scene.add(key)
  const rim = new THREE.DirectionalLight(0x88aaff, 0.6)
  rim.position.set(-5, 3, -4)
  scene.add(rim)

  const innerGlow = new THREE.PointLight(0xff5520, 3.5, 6, 1.5)
  innerGlow.position.set(0, 1.5, 0)
  scene.add(innerGlow)
  parts.innerGlow = innerGlow

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
.section-scene {
  width: 100%;
  height: 100%;
  position: relative;
  overflow: hidden;
}
.section-scene :deep(canvas) {
  display: block;
  width: 100% !important;
  height: 100% !important;
}
</style>
