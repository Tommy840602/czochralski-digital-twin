<template>
  <div ref="mountEl" style="width:100%;height:100%;background:#080b10;display:block;position:absolute;inset:0;"></div>
</template>

<script setup>
import { ref, watch, onMounted, onUnmounted } from "vue"
import * as THREE from "three"
import { OrbitControls } from "three/examples/jsm/controls/OrbitControls.js"

const props = defineProps({
  furnaceData: { type: Object, default: () => ({}) },
  furnaceIds:  { type: Array,  default: () => [] }
})

const mountEl = ref(null)
let renderer, scene, camera, controls, animId
const furnaces = {}
const labelSprites = {}
const dataRef = { current: {} }

watch(() => props.furnaceData, val => { dataRef.current = val }, { deep: true })

watch(() => props.furnaceIds, ids => {
  if (!scene || ids.length === 0) return
  ids.forEach((id, idx) => {
    if (furnaces[id]) return
    const GAP = 5
    const offsetX = (idx - (ids.length - 1) / 2) * GAP
    const group = buildFurnace()
    group.position.set(offsetX, 0, 0)
    scene.add(group)

    const light = new THREE.PointLight(0xffffff, 3, 10, 2)
    light.position.set(offsetX, 1.5, 0)
    scene.add(light)

    const { sprite, canvas, ctx } = makeLabel(id, false)
    sprite.position.set(offsetX, 5.6, 0)
    sprite.scale.set(2.2, 0.65, 1)
    scene.add(sprite)
    labelSprites[id] = { sprite, canvas, ctx }

    let crystalMesh = null, glowRing = null
    group.traverse(c => {
      if (c.name === "crystal_ingot") crystalMesh = c
      if (c.name === "glow_ring") glowRing = c
    })
    furnaces[id] = { group, crystalMesh, glowRing, light }
  })
}, { immediate: false })

function tempToColor(temp) {
  if (temp < 200)  return new THREE.Color(0x110500)
  if (temp < 800)  return new THREE.Color().lerpColors(new THREE.Color(0x330800), new THREE.Color(0x882200), (temp-200)/600)
  if (temp < 1200) return new THREE.Color().lerpColors(new THREE.Color(0x882200), new THREE.Color(0xff3300), (temp-800)/400)
  if (temp < 1350) return new THREE.Color().lerpColors(new THREE.Color(0xff3300), new THREE.Color(0xff6600), (temp-1200)/150)
  return new THREE.Color().lerpColors(new THREE.Color(0xff6600), new THREE.Color(0xffaa00), Math.min(1,(temp-1350)/150))
}

function buildFurnace() {
  const g = new THREE.Group()
  const metal = (c, r=0.35) => new THREE.MeshStandardMaterial({ color: c, metalness: 0.65, roughness: r })

  // 底座
  const base = new THREE.Mesh(new THREE.CylinderGeometry(1.3,1.4,0.25,32), metal(0x1a1a1a))
  base.position.y = 0.125; base.receiveShadow = true; g.add(base)

  // 爐體
  const body = new THREE.Mesh(
    new THREE.CylinderGeometry(1.1,1.25,2.8,48),
    new THREE.MeshStandardMaterial({ color:0x2e3540, metalness:0.8, roughness:0.25, transparent:true, opacity:0.8 })
  )
  body.position.y = 1.65; body.castShadow = true; body.name = "heater_body"; g.add(body)

  // 腰環
  const waist = new THREE.Mesh(new THREE.CylinderGeometry(0.95,1.1,0.3,32), metal(0x445060,0.35))
  waist.position.y = 2.95; g.add(waist)

  // 上蓋
  const top = new THREE.Mesh(new THREE.CylinderGeometry(0.7,0.95,0.5,32), metal(0x2a3040,0.3))
  top.position.y = 3.35; top.castShadow = true; g.add(top)

  // 頸管
  const neck = new THREE.Mesh(new THREE.CylinderGeometry(0.18,0.22,1.4,16), metal(0x1e2a38,0.35))
  neck.position.y = 4.35; neck.castShadow = true; g.add(neck)

  // 發光環
  const glowRing = new THREE.Mesh(
    new THREE.TorusGeometry(1.13,0.04,16,64),
    new THREE.MeshBasicMaterial({ color: 0xff2200 })
  )
  glowRing.rotation.x = Math.PI/2; glowRing.position.y = 1.4; glowRing.name = "glow_ring"; g.add(glowRing)

  // 鋼琴線
  const wire = new THREE.Mesh(
    new THREE.CylinderGeometry(0.012,0.012,3.5,8),
    new THREE.MeshStandardMaterial({ color:0xccddee, metalness:1.0, roughness:0.05 })
  )
  wire.position.y = 3.55; wire.name = "seed_wire"; g.add(wire)

  // 熔湯
  const melt = new THREE.Mesh(
    new THREE.CylinderGeometry(0.78,0.78,0.06,64),
    new THREE.MeshStandardMaterial({ color:0xff6600, emissive:new THREE.Color(0xff4400), emissiveIntensity:0.8, roughness:0.15, metalness:0.7 })
  )
  melt.position.y = 1.0; melt.name = "melt_pool"; g.add(melt)

  // 熔湯波紋
  const r1 = new THREE.Mesh(new THREE.TorusGeometry(0.25,0.02,8,32), new THREE.MeshBasicMaterial({ color:0xff6600, transparent:true, opacity:0.6 }))
  r1.rotation.x = Math.PI/2; r1.position.y = 1.03; r1.name = "ripple1"; g.add(r1)
  const r2 = new THREE.Mesh(new THREE.TorusGeometry(0.5,0.02,8,32), new THREE.MeshBasicMaterial({ color:0xff8800, transparent:true, opacity:0.4 }))
  r2.rotation.x = Math.PI/2; r2.position.y = 1.03; r2.name = "ripple2"; g.add(r2)

  // 晶棒（沿鋼琴線 x=0,z=0 從熔湯往上長）
  const crystalGroup = new THREE.Group()
  crystalGroup.name = "crystal_ingot"
  crystalGroup.position.set(0, 1.5, 0)
  crystalGroup.scale.set(0.1, 0.1, 0.1)

  const crystalMat = new THREE.MeshStandardMaterial({
    color: 0x778899,
    metalness: 0.85,
    roughness: 0.12
  })

  const seedCone = new THREE.Mesh(new THREE.ConeGeometry(0.18, 0.4, 32), crystalMat)
  seedCone.position.y = 0.7
  crystalGroup.add(seedCone)

  const crystalBody = new THREE.Mesh(new THREE.CylinderGeometry(0.18, 0.18, 1.0, 48), crystalMat)
  crystalGroup.add(crystalBody)

  const tailCone = new THREE.Mesh(new THREE.ConeGeometry(0.18, 0.3, 32), crystalMat)
  tailCone.rotation.z = Math.PI
  tailCone.position.y = -0.65
  crystalGroup.add(tailCone)

  g.add(crystalGroup)

  // 管線（左右）
  ;[-1,1].forEach(side => {
    const pipe = new THREE.Mesh(new THREE.CylinderGeometry(0.05,0.05,0.9,8), metal(0x2a3a4a))
    pipe.rotation.z = Math.PI/2; pipe.position.set(side*1.25, 1.2, 0); g.add(pipe)
    const cap = new THREE.Mesh(new THREE.CylinderGeometry(0.08,0.08,0.05,8), metal(0x334455))
    cap.rotation.z = Math.PI/2; cap.position.set(side*1.72, 1.2, 0); g.add(cap)
  })

  return g
}

function makeLabel(id, isNg) {
  const canvas = document.createElement("canvas")
  canvas.width = 256; canvas.height = 64
  const ctx = canvas.getContext("2d")
  drawLabel(ctx, canvas, id, isNg)
  const mat = new THREE.SpriteMaterial({ map: new THREE.CanvasTexture(canvas), transparent: true, depthWrite: false })
  const sprite = new THREE.Sprite(mat)
  return { sprite, canvas, ctx }
}

function drawLabel(ctx, canvas, id, isNg) {
  const accent = isNg ? "#f04a4a" : "#40c88c"
  const border = isNg ? "rgba(240,74,74,0.7)" : "rgba(64,200,140,0.6)"
  ctx.clearRect(0, 0, 256, 64)
  ctx.fillStyle = "rgba(10,14,20,0.88)"
  ctx.strokeStyle = border
  ctx.lineWidth = isNg ? 3 : 2
  ctx.beginPath()
  ctx.roundRect(4, 4, 248, 56, 8)
  ctx.fill(); ctx.stroke()
  ctx.fillStyle = accent
  ctx.font = "bold 28px monospace"
  ctx.textAlign = "center"
  ctx.textBaseline = "middle"
  ctx.fillText("爐 " + id, 128, 32)
}

window._furnaces = furnaces
function animate() {
  animId = requestAnimationFrame(animate)
  const data = dataRef.current
  const t = performance.now() / 1000

  // 自動同步新爐子
  if (Object.keys(data).length > 0 && Object.keys(furnaces).length === 0) {
    const ids = Object.keys(data)
    ids.forEach((id, idx) => {
      const GAP = 5
      const offsetX = (idx - (ids.length - 1) / 2) * GAP
      const group = buildFurnace()
      group.position.set(offsetX, 0, 0)
      scene.add(group)
      const light = new THREE.PointLight(0xffffff, 3, 10, 2)
      light.position.set(offsetX, 1.5, 0)
      scene.add(light)
      const { sprite, canvas, ctx } = makeLabel(id, false)
      sprite.position.set(offsetX, 5.6, 0)
      sprite.scale.set(2.2, 0.65, 1)
      scene.add(sprite)
      labelSprites[id] = { sprite, canvas, ctx }
      let crystalMesh = null, glowRing = null
      group.traverse(c => {
        if (c.name === "crystal_ingot") crystalMesh = c
        if (c.isMesh && c.name === "glow_ring") glowRing = c
      })
      if (!crystalMesh) console.warn("crystal_ingot not found for", id)
      furnaces[id] = { group, crystalMesh, glowRing, light }
    })
  }

  Object.entries(furnaces).forEach(([id, f]) => {
    const d = data[id]
    const temp = parseFloat(d?.heaterTemp) || 0
    const diam = parseFloat(d?.diameter) || 0
    const body = parseFloat(d?.bodyLength) || 0

    // 發光環顏色
    if (f.glowRing?.material) {
      const color = temp > 200 ? tempToColor(temp) : new THREE.Color(0x110300)
      f.glowRing.material.color.copy(color)
      f.glowRing.material.needsUpdate = true
    }

    // PointLight 強度
    if (f.light) f.light.intensity = temp > 200 ? 3 + (temp/1400)*8 : 0.2

    // 波紋動畫
    f.group.traverse(c => {
      if (c.name === "ripple1") { const s = 0.8+Math.sin(t*2)*0.2; c.scale.set(s,1,s); c.material.opacity = 0.3+Math.sin(t*2)*0.3 }
      if (c.name === "ripple2") { const s = 0.8+Math.sin(t*2+Math.PI)*0.2; c.scale.set(s,1,s); c.material.opacity = 0.2+Math.sin(t*2+Math.PI)*0.2 }
      if (c.name === "seed_wire") { c.rotation.z = Math.sin(t*1.5)*0.008; c.rotation.x = Math.cos(t*1.2)*0.005 }
      if (c.name === "melt_pool" && c.material) c.material.emissiveIntensity = 0.7+Math.sin(t*2)*0.2
    })

    // 晶棒
    if (f.crystalMesh) {
      const sy = Math.max(0.01, Math.min(5.0, body/100))
      const sx = Math.max(0.01, Math.min(1.5, diam/120))
      // diameter 150mm → x scale 0.75 (爐子比例)
      const targetX = Math.max(0.15, Math.min(1.0, diam / 150))
      // bodyLength → y scale，500mm = scale 5
      const targetY = Math.max(0.15, Math.min(4.0, body / 300))
      f.crystalMesh.scale.x += (targetX - f.crystalMesh.scale.x) * 0.08
      f.crystalMesh.scale.z  =  f.crystalMesh.scale.x
      f.crystalMesh.scale.y += (targetY - f.crystalMesh.scale.y) * 0.08
      // 晶棒底部在熔湯(y=1.0)，中心往上
      f.crystalMesh.position.y = 1.0 + f.crystalMesh.scale.y * 0.85
    }

    // 標籤
    const lb = labelSprites[id]
    if (lb) {
      drawLabel(lb.ctx, lb.canvas, id, false)
      lb.sprite.material.map.needsUpdate = true
    }
  })

  controls.update()
  renderer.render(scene, camera)
}

onMounted(() => {
  const el = mountEl.value
  renderer = new THREE.WebGLRenderer({ antialias: true, alpha: false })
  renderer.setPixelRatio(Math.min(devicePixelRatio, 2))
  renderer.setSize(el.clientWidth, el.clientHeight)
  renderer.shadowMap.enabled = true
  renderer.toneMapping = THREE.NoToneMapping
  el.appendChild(renderer.domElement)

  scene = new THREE.Scene()
  scene.background = new THREE.Color(0x080b10)
  scene.fog = new THREE.FogExp2(0x080b10, 0.035)

  camera = new THREE.PerspectiveCamera(70, el.clientWidth/el.clientHeight, 0.1, 200)
  camera.position.set(0, 4, 16)

  controls = new OrbitControls(camera, renderer.domElement)
  controls.enableDamping = true
  controls.dampingFactor = 0.08
  controls.minDistance = 4
  controls.maxDistance = 30
  controls.maxPolarAngle = Math.PI/2 + 0.2
  controls.target.set(0, 3.5, 0)
  controls.update()

  scene.add(new THREE.AmbientLight(0xaabbcc, 8.0))
  const dir = new THREE.DirectionalLight(0xffffff, 8.0)
  dir.position.set(5,15,8); dir.castShadow = true; scene.add(dir)

  const floor = new THREE.Mesh(
    new THREE.PlaneGeometry(40,40),
    new THREE.MeshStandardMaterial({ color:0x080b10, metalness:0.3, roughness:0.8 })
  )
  floor.rotation.x = -Math.PI/2; floor.receiveShadow = true; scene.add(floor)
  const grid = new THREE.GridHelper(30, 30, 0x1a2840, 0x1a2840)
  grid.position.y = 0.01; scene.add(grid)

  // 粒子
  const pCount = 200
  const pGeo = new THREE.BufferGeometry()
  const pPos = new Float32Array(pCount * 3)
  for (let i = 0; i < pCount; i++) {
    pPos[i*3]   = (Math.random()-0.5)*20
    pPos[i*3+1] = Math.random()*8
    pPos[i*3+2] = (Math.random()-0.5)*10
  }
  pGeo.setAttribute("position", new THREE.BufferAttribute(pPos, 3))
  scene.add(new THREE.Points(pGeo, new THREE.PointsMaterial({ color:0x334455, size:0.03, transparent:true, opacity:0.6 })))

  // 若已有 furnaceIds 立即建模
  if (props.furnaceIds.length > 0) {
    props.furnaceIds.forEach((id, idx) => {
      const GAP = 5
      const offsetX = (idx - (props.furnaceIds.length-1)/2) * GAP
      const group = buildFurnace()
      group.position.set(offsetX, 0, 0)
      scene.add(group)
      const light = new THREE.PointLight(0xffffff, 3, 10, 2)
      light.position.set(offsetX, 1.5, 0)
      scene.add(light)
      const { sprite, canvas, ctx } = makeLabel(id, false)
      sprite.position.set(offsetX, 5.6, 0)
      sprite.scale.set(2.2, 0.65, 1)
      scene.add(sprite)
      labelSprites[id] = { sprite, canvas, ctx }
      let crystalMesh = null, glowRing = null
      group.traverse(c => {
        if (c.name === "crystal_ingot") crystalMesh = c
        if (c.isMesh && c.name === "glow_ring") glowRing = c
      })
      if (!crystalMesh) console.warn("crystal_ingot not found for", id)
      furnaces[id] = { group, crystalMesh, glowRing, light }
    })
  }

  const onResize = () => {
    const w = el.clientWidth, h = el.clientHeight
    camera.aspect = w/h; camera.updateProjectionMatrix()
    renderer.setSize(w, h)
  }
  window.addEventListener("resize", onResize)
  // 強制用 furnaceIds 建立初始模型
  if (props.furnaceIds.length > 0) {
    props.furnaceIds.forEach((id, idx) => {
      if (furnaces[id]) return
      const GAP = 5
      const offsetX = (idx - (props.furnaceIds.length - 1) / 2) * GAP
      const group = buildFurnace()
      group.position.set(offsetX, 0, 0)
      scene.add(group)
      const light = new THREE.PointLight(0xffffff, 3, 10, 2)
      light.position.set(offsetX, 1.5, 0)
      scene.add(light)
      const { sprite, canvas, ctx } = makeLabel(id, false)
      sprite.position.set(offsetX, 5.6, 0)
      sprite.scale.set(2.2, 0.65, 1)
      scene.add(sprite)
      labelSprites[id] = { sprite, canvas, ctx }
      let crystalMesh = null, glowRing = null
      group.traverse(c => {
        if (c.name === "crystal_ingot") crystalMesh = c
        if (c.isMesh && c.name === "glow_ring") glowRing = c
      })
      if (!crystalMesh) console.warn("crystal_ingot not found for", id)
      furnaces[id] = { group, crystalMesh, glowRing, light }
    })
  }
  // 強制 resize 確保 canvas 填滿
  setTimeout(() => {
    const w = el.clientWidth, h = el.clientHeight
    camera.aspect = w / h
    camera.updateProjectionMatrix()
    renderer.setSize(w, h)
  }, 100)
  animate()

  onUnmounted(() => {
    cancelAnimationFrame(animId)
    window.removeEventListener("resize", onResize)
    controls.dispose(); renderer.dispose()
    if (el.contains(renderer.domElement)) el.removeChild(renderer.domElement)
  })
})
</script>
