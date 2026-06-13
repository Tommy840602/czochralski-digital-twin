/**
 * useFurnaceModel.js
 * 載入單台爐子的 GLB 到指定 Three.js Scene，並標記發光/晶棒 mesh。
 * 由 FurnaceScene 內部使用，也可獨立呼叫做單爐細節檢視。
 *
 * loadFurnaceModel(scene, {
 *   position  : [x,y,z]      預設 [0,0,0]
 *   furnaceId : 'C1'|'C2'
 *   modelPath : string       預設 '/models/furnace.glb'
 *   onLoaded  : (furnaceObj) => void
 * }) => dispose()  // 呼叫以從場景移除
 */
import * as THREE from 'three';
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js';

export function loadFurnaceModel(scene, {
  position = [0, 0, 0],
  furnaceId = 'C1',
  modelPath = '/models/furnace.glb',
  onLoaded,
} = {}) {
  if (!scene) return () => {};

  const loader = new GLTFLoader();
  let group = null;

  loader.load(
    modelPath,
    (gltf) => {
      group = gltf.scene;
      group.position.set(...position);
      const furnaceObj = { group, heaterMeshes: [], crystalMesh: null };

      group.traverse((child) => {
        if (!child.isMesh) return;
        child.castShadow = child.receiveShadow = true;
        const n = child.name.toLowerCase();
        if (n.includes('heater') || n.includes('body') || n.includes('crucible')) {
          child.material = child.material.clone();
          child.material.emissive = new THREE.Color(0xff4400);
          child.material.emissiveIntensity = 0;
          furnaceObj.heaterMeshes.push(child);
        }
        if (n.includes('crystal') || n.includes('ingot')) furnaceObj.crystalMesh = child;
      });

      scene.add(group);
      onLoaded?.(furnaceObj);
    },
    undefined,
    (err) => console.error(`[FurnaceModel ${furnaceId}] GLB 載入失敗:`, err)
  );

  return () => {
    if (group && scene) scene.remove(group);
  };
}
