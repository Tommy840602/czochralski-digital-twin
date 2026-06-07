/**
 * FurnaceScene.jsx
 * Three.js 主場景 — 載入兩台長晶爐 .glb，
 * 依 WebSocket 即時數據動態更新材質顏色、發光強度、晶棒縮放
 *
 * 依賴：three r165, @stomp/stompjs, sockjs-client
 * 使用：<FurnaceScene furnaceData={{ C1: {...}, C2: {...} }} />
 */
import { useEffect, useRef } from 'react';
import * as THREE from 'three';
import { GLTFLoader }      from 'three/examples/jsm/loaders/GLTFLoader.js';
import { OrbitControls }   from 'three/examples/jsm/controls/OrbitControls.js';
import { EffectComposer }  from 'three/examples/jsm/postprocessing/EffectComposer.js';
import { RenderPass }      from 'three/examples/jsm/postprocessing/RenderPass.js';
import { UnrealBloomPass } from 'three/examples/jsm/postprocessing/UnrealBloomPass.js';

const MODEL_PATH  = '/models/furnace.glb';
const FURNACE_GAP = 6;
const TEMP_MIN    = 1200;
const TEMP_MAX    = 1450;

function tempToColor(temp) {
  const t = Math.max(0, Math.min(1, (temp - TEMP_MIN) / (TEMP_MAX - TEMP_MIN)));
  if (t < 0.4)  return new THREE.Color().lerpColors(new THREE.Color(0x1a2840), new THREE.Color(0xaa3300), t / 0.4);
  if (t < 0.75) return new THREE.Color().lerpColors(new THREE.Color(0x8b2200), new THREE.Color(0xff4400), (t - 0.4) / 0.35);
  return new THREE.Color().lerpColors(new THREE.Color(0xff4400), new THREE.Color(0xff1100), (t - 0.75) / 0.25);
}

function ngPulse(t) { return 0.5 + 0.5 * Math.sin(t * 4); }

export default function FurnaceScene({ furnaceData = {} }) {
  const mountRef = useRef(null);
  const dataRef  = useRef(furnaceData);
  useEffect(() => { dataRef.current = furnaceData; }, [furnaceData]);

  useEffect(() => {
    const el = mountRef.current;
    if (!el) return;

    // Renderer
    const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true });
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    renderer.setSize(el.clientWidth, el.clientHeight);
    renderer.shadowMap.enabled = true;
    renderer.shadowMap.type = THREE.PCFSoftShadowMap;
    renderer.toneMapping = THREE.NoToneMapping;
    el.appendChild(renderer.domElement);

    // Scene
    const scene = new THREE.Scene();
    scene.background = new THREE.Color(0x080b10);
    scene.fog = new THREE.FogExp2(0x080b10, 0.04);

    // Camera
    const camera = new THREE.PerspectiveCamera(50, el.clientWidth / el.clientHeight, 0.1, 200);
    camera.position.set(0, 6, 14);
    camera.lookAt(0, 2, 0);

    // Controls
    const controls = new OrbitControls(camera, renderer.domElement);
    controls.enableDamping = true;
    controls.dampingFactor = 0.08;
    controls.minDistance   = 4;
    controls.maxDistance   = 30;
    controls.maxPolarAngle = Math.PI / 2 + 0.2;
    controls.target.set(0, 2, 0);

    // Lights
    scene.add(new THREE.AmbientLight(0xaabbcc, 4.0));
    const dirLight = new THREE.DirectionalLight(0xffffff, 4.0);
    dirLight.position.set(5, 15, 8);
    dirLight.castShadow = true;
    dirLight.shadow.mapSize.set(2048, 2048);
    Object.assign(dirLight.shadow.camera, { near:0.5, far:50, left:-15, right:15, top:15, bottom:-15 });
    scene.add(dirLight);

    const furnaceLights = ['C1','C2'].map((_, i) => {
      const l = new THREE.PointLight(0xffffff, 3, 10, 2);
      l.position.set((i === 0 ? -1 : 1) * FURNACE_GAP / 2, 1.5, 0);
      scene.add(l);
      return l;
    });

    // Floor + Grid
    const floor = new THREE.Mesh(
      new THREE.PlaneGeometry(40, 40),
      new THREE.MeshStandardMaterial({ color:0x0a0e14, metalness:0.3, roughness:0.8 })
    );
    floor.rotation.x = -Math.PI / 2;
    floor.receiveShadow = true;
    scene.add(floor);
    const grid = new THREE.GridHelper(30, 30, 0x1a2a3a, 0x111820);
    grid.position.y = 0.01;
    scene.add(grid);

    // Dust particles
    const pCount = 200;
    const pGeo   = new THREE.BufferGeometry();
    const pPos   = new Float32Array(pCount * 3);
    for (let i = 0; i < pCount; i++) {
      pPos[i*3]   = (Math.random()-0.5) * 20;
      pPos[i*3+1] = Math.random() * 8;
      pPos[i*3+2] = (Math.random()-0.5) * 10;
    }
    pGeo.setAttribute('position', new THREE.BufferAttribute(pPos, 3));
    scene.add(new THREE.Points(pGeo, new THREE.PointsMaterial({ color:0x334455, size:0.03, transparent:true, opacity:0.6 })));

    // Bloom
    const composer = new EffectComposer(renderer);
    composer.addPass(new RenderPass(scene, camera));
    const bloomPass = new UnrealBloomPass(new THREE.Vector2(el.clientWidth, el.clientHeight), 0.2, 0.3, 1.5);
    composer.addPass(bloomPass);

    // Furnace objects
    const furnaces = {};
    const loader   = new GLTFLoader();

    ['C1','C2'].forEach((id, idx) => {
      const offsetX = (idx === 0 ? -1 : 1) * FURNACE_GAP / 2;
      const ph      = buildPlaceholderFurnace();
      ph.position.set(offsetX, 0, 0);
      scene.add(ph);

      furnaces[id] = { group:null, heaterMeshes:[], crystalMesh:null, glowRing:null, statusLight:furnaceLights[idx], placeholder:ph, loaded:false };

      loader.load(MODEL_PATH, (gltf) => {
        const group = gltf.scene;
        group.position.set(offsetX, 0, 0);
        group.traverse((child) => {
          if (!child.isMesh) return;
          child.castShadow = child.receiveShadow = true;
          const name = child.name.toLowerCase();
          if (name.includes('heater') || name.includes('body') || name.includes('crucible')) {
            child.material = child.material.clone();
            child.material.emissive = new THREE.Color(0xff4400);
            child.material.emissiveIntensity = 0;
            furnaces[id].heaterMeshes.push(child);
          }
          if (name.includes('crystal') || name.includes('ingot') || name.includes('pull')) {
            furnaces[id].crystalMesh = child;
          }
        });
        scene.remove(ph);
        scene.add(group);
        furnaces[id].group  = group;
        furnaces[id].loaded = true;
      }, undefined, () => {
        // GLB 不存在時佔位模型繼續使用
        furnaces[id].group        = ph;
        furnaces[id].heaterMeshes = collectPlaceholderMeshes(ph);
        furnaces[id].crystalMesh  = findCrystalMesh(ph);
        furnaces[id].glowRing    = findGlowRing(ph);
        furnaces[id].loaded       = true;
      });
    });

    // Labels
    ['C1','C2'].forEach((id, idx) => {
      const sp = makeLabelSprite(id);
      sp.position.set((idx===0?-1:1) * FURNACE_GAP / 2, 5.5, 0);
      sp.scale.set(2, 0.6, 1);
      scene.add(sp);
    });

    const clock = new THREE.Clock();

    const onResize = () => {
      const w = el.clientWidth, h = el.clientHeight;
      camera.aspect = w / h;
      camera.updateProjectionMatrix();
      renderer.setSize(w, h);
      composer.setSize(w, h);
    };
    window.addEventListener('resize', onResize);

    let animId;
    const animate = () => {
      animId = requestAnimationFrame(animate);
      const t  = clock.getElapsedTime();
      const data = dataRef.current;
      controls.update();

      // 粒子飄動
      const pa = pGeo.attributes.position.array;
      for (let i = 0; i < pCount; i++) {
        pa[i*3+1] += 0.003;
        if (pa[i*3+1] > 8) pa[i*3+1] = 0;
      }
      pGeo.attributes.position.needsUpdate = true;

      // 爐況更新
      ['C1','C2'].forEach((id) => {
        const f = furnaces[id];
        if (!f?.loaded) return;
        const d = data[id];
        if (!d) return;

        const isNg   = d.event === 6;
        const temp   = d.heaterTemp  ?? 1300;
        const diam   = d.diameter    ?? 105;
        const body   = d.bodyLength  ?? 0;
        const color  = tempToColor(temp);
        const ei     = isNg ? ngPulse(t)*1.0 : Math.max(0,(temp-1200)/250)*0.6;

        f.heaterMeshes.forEach((mesh) => {
          if (!mesh.material) return;
          // 爐體本身不發光，保持鋼鐵質感
          mesh.material.emissive.set(0x000000);
          mesh.material.emissiveIntensity = 0;
        });
        // 更新底部發光環顏色 = 溫度色
        // 每幀從 group 找 glow_ring
        const gr = findGlowRing(f.group || f.placeholder);
        if (gr && gr.material) {
          const glowColor = isNg
            ? new THREE.Color(0xff0020)
            : color.clone().multiplyScalar(Math.max(0.3, (temp-1200)/250));
          gr.material.color.copy(glowColor);
          gr.visible = true;
        }

        f.statusLight.color.set(isNg ? 0xff0022 : 0xffffff);
        f.statusLight.intensity = isNg ? 3+ngPulse(t)*3 : 2.0;

        // 熔湯波紋動畫
        if (f.group || f.placeholder) {
          const src = f.group || f.placeholder;
          src.traverse((child) => {
            if (child.name === 'melt_pool' && child.material) {
              child.material.emissiveIntensity = 0.7 + Math.sin(t * 2) * 0.2;
            }
            if (child.name === 'ripple1') {
              const s = 0.8 + Math.sin(t * 2) * 0.2;
              child.scale.set(s, 1, s);
              child.material.opacity = 0.3 + Math.sin(t * 2) * 0.3;
            }
            if (child.name === 'ripple2') {
              const s = 0.8 + Math.sin(t * 2 + Math.PI) * 0.2;
              child.scale.set(s, 1, s);
              child.material.opacity = 0.2 + Math.sin(t * 2 + Math.PI) * 0.2;
            }
            if (child.name === 'seed_wire') {
              // 鋼琴線隨溫度微微搖擺
              child.rotation.z = Math.sin(t * 1.5) * 0.008;
              child.rotation.x = Math.cos(t * 1.2) * 0.005;
            }
          });
        }
        if (f.crystalMesh) {
          const sy = Math.max(0.01, Math.min(1, body/400));
          const sx = Math.max(0.01, Math.min(1.5, diam/120));
          f.crystalMesh.scale.y += (sy - f.crystalMesh.scale.y) * 0.05;
          f.crystalMesh.scale.x += (sx - f.crystalMesh.scale.x) * 0.05;
          f.crystalMesh.scale.z  = f.crystalMesh.scale.x;
        }

        if (isNg && f.group) {
          f.group.position.x += (Math.random()-0.5)*0.003;
          f.group.position.z += (Math.random()-0.5)*0.003;
        }
      });

      const maxTemp = Math.max(data.C1?.heaterTemp??1200, data.C2?.heaterTemp??1200);
      bloomPass.strength = 0.2 + ((maxTemp-1200)/250)*0.3;
      if (data.C1?.event===6 || data.C2?.event===6)
        bloomPass.strength = Math.max(bloomPass.strength, 0.5+ngPulse(t)*0.3);

      composer.render();
    };
    animate();

    return () => {
      window.removeEventListener('resize', onResize);
      cancelAnimationFrame(animId);
      controls.dispose();
      renderer.dispose();
      composer.dispose();
      if (el.contains(renderer.domElement)) el.removeChild(renderer.domElement);
    };
  }, []);

  return (
    <div ref={mountRef} style={{
      width:'100%', height:'100%', minHeight:360,
      background:'#080b10', borderRadius:10, overflow:'hidden',
    }} />
  );
}

// ── 工具函式 ──────────────────────────────────────────────────────────────────
function buildPlaceholderFurnace() {
  const g = new THREE.Group();

  const metalMat = (color, rough=0.35) => new THREE.MeshStandardMaterial({
    color, metalness:0.6, roughness:rough
  });
  const emissiveMat = new THREE.MeshStandardMaterial({
    color:0x2e3540, metalness:0.8, roughness:0.25,
    emissive: new THREE.Color(0x000000), emissiveIntensity:0,
    transparent:true, opacity:0.75,
  });


  // 底座平台
  const base = new THREE.Mesh(new THREE.CylinderGeometry(1.3,1.4,0.25,32), metalMat(0x1a1a1a));
  base.position.y = 0.125;
  base.castShadow = base.receiveShadow = true;
  g.add(base);

  // 爐體主體（較矮、較寬）
  const body = new THREE.Mesh(new THREE.CylinderGeometry(1.1,1.25,2.8,48), emissiveMat);
  body.position.y = 1.65;
  body.castShadow = true;
  body.name = 'heater_body';
  g.add(body);

  // 腰部收縮環
  const waist = new THREE.Mesh(new THREE.CylinderGeometry(0.95,1.1,0.3,32), metalMat(0x445060,0.35));
  waist.position.y = 2.95;
  g.add(waist);

  // 上蓋
  const top = new THREE.Mesh(new THREE.CylinderGeometry(0.7,0.95,0.5,32), metalMat(0x2a3040,0.3));
  top.position.y = 3.35;
  top.castShadow = true;
  g.add(top);

  // 拉晶頸管（細）
  const neck = new THREE.Mesh(new THREE.CylinderGeometry(0.18,0.22,1.4,16), metalMat(0x1e2a38,0.35));
  neck.position.y = 4.35;
  neck.castShadow = true;
  g.add(neck);

  // 熱場發光環（爐體腰部縫隙）
  const glowMesh = new THREE.Mesh(
    new THREE.TorusGeometry(1.13, 0.04, 16, 64),
    new THREE.MeshBasicMaterial({ color:0x331100 })
  );
  glowMesh.rotation.x = Math.PI / 2;
  glowMesh.position.y = 1.4;
  glowMesh.name = 'glow_ring';
  g.add(glowMesh);

  // 鋼琴線（seed wire，從管頂垂下）
  const wireMat = new THREE.MeshStandardMaterial({
    color:0xccddee, metalness:1.0, roughness:0.05,
  });
  const wire = new THREE.Mesh(
    new THREE.CylinderGeometry(0.012, 0.012, 3.5, 8),
    wireMat
  );
  wire.position.y = 3.55;
  wire.name = 'seed_wire';
  g.add(wire);

  // 熔湯主體（坩堝內矽熔液）
  const meltMat2 = new THREE.MeshStandardMaterial({
    color:0xff6600,
    emissive: new THREE.Color(0xff4400),
    emissiveIntensity:0.8,
    roughness:0.15,
    metalness:0.7,
  });
  const melt = new THREE.Mesh(
    new THREE.CylinderGeometry(0.78, 0.78, 0.06, 64),
    meltMat2
  );
  melt.position.y = 1.0;
  melt.name = 'melt_pool';
  g.add(melt);

  // 熔湯波紋環（同心圓，動畫用）
  const rippleMat1 = new THREE.MeshBasicMaterial({ color:0xff6600, transparent:true, opacity:0.6 });
  const rippleMat2 = new THREE.MeshBasicMaterial({ color:0xff8800, transparent:true, opacity:0.4 });
  const ripple1 = new THREE.Mesh(new THREE.TorusGeometry(0.25, 0.02, 8, 32), rippleMat1);
  ripple1.rotation.x = Math.PI/2; ripple1.position.y = 1.03; ripple1.name = 'ripple1';
  g.add(ripple1);
  const ripple2 = new THREE.Mesh(new THREE.TorusGeometry(0.5, 0.02, 8, 32), rippleMat2);
  ripple2.rotation.x = Math.PI/2; ripple2.position.y = 1.03; ripple2.name = 'ripple2';
  g.add(ripple2);



  // 晶棒（從拉晶管頂端往上生長）
  const crystalMat = new THREE.MeshStandardMaterial({
    color:0xc8e0f0, metalness:0.2, roughness:0.05,
    transparent:true, opacity:0.88,
  });
  const crystal = new THREE.Mesh(new THREE.CylinderGeometry(0.12,0.14,0.01,24), crystalMat);
  crystal.position.y = 1.05;  // 從熔湯表面往上長
  crystal.name = 'crystal_ingot';
  g.add(crystal);

  // 管線裝飾（左右各一）
  [-1, 1].forEach(side => {
    const pipe = new THREE.Mesh(
      new THREE.CylinderGeometry(0.05,0.05,0.9,8),
      metalMat(0x2a3a4a)
    );
    pipe.rotation.z = Math.PI/2;
    pipe.position.set(side * 1.25, 1.2, 0);
    g.add(pipe);

    // 管線端蓋
    const cap = new THREE.Mesh(new THREE.CylinderGeometry(0.08,0.08,0.05,8), metalMat(0x334455));
    cap.rotation.z = Math.PI/2;
    cap.position.set(side * 1.72, 1.2, 0);
    g.add(cap);
  });

  // 前後管線
  [-1, 1].forEach(side => {
    const pipe = new THREE.Mesh(
      new THREE.CylinderGeometry(0.04,0.04,0.6,8),
      metalMat(0x2a3a4a)
    );
    pipe.rotation.x = Math.PI/2;
    pipe.position.set(0, 0.7, side * 1.3);
    g.add(pipe);
  });

  return g;
}

function collectPlaceholderMeshes(group) {
  const res = [];
  group.traverse(c => { if (c.isMesh && c.name.includes('heater')) res.push(c); });
  return res;
}

function findCrystalMesh(group) {
  let found = null;
  group.traverse(c => { if (c.isMesh && c.name.includes('crystal')) found = c; });
  return found;
}

function findGlowRing(group) {
  let found = null;
  group.traverse(c => { if (c.isMesh && c.name.includes('glow')) found = c; });
  return found;
}

function makeLabelSprite(text) {
  const canvas = document.createElement('canvas');
  canvas.width = 256; canvas.height = 64;
  const ctx = canvas.getContext('2d');
  ctx.fillStyle   = 'rgba(10,14,20,0.75)';
  ctx.strokeStyle = 'rgba(64,200,140,0.6)';
  ctx.lineWidth   = 2;
  ctx.beginPath();
  ctx.roundRect(4, 4, 248, 56, 8);
  ctx.fill(); ctx.stroke();
  ctx.fillStyle    = '#40c88c';
  ctx.font         = 'bold 28px monospace';
  ctx.textAlign    = 'center';
  ctx.textBaseline = 'middle';
  ctx.fillText('爐 ' + text, 128, 32);
  const mat = new THREE.SpriteMaterial({ map: new THREE.CanvasTexture(canvas), transparent:true, depthWrite:false });
  return new THREE.Sprite(mat);
}
