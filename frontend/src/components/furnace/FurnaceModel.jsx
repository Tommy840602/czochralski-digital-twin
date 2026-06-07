/**
 * FurnaceModel.jsx
 * 單台爐子的 Three.js Group 管理器
 * 供 FurnaceScene 內部使用，也可獨立掛載做單爐細節檢視
 *
 * Props:
 *   scene        {THREE.Scene}
 *   position     {[x,y,z]}
 *   furnaceId    {'C1'|'C2'}
 *   modelPath    {string}   預設 '/models/furnace.glb'
 *   onLoaded     {fn}       (furnaceObj) => void
 */
import { useEffect, useRef } from 'react';
import * as THREE from 'three';
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js';

export default function FurnaceModel({ scene, position=[0,0,0], furnaceId='C1', modelPath='/models/furnace.glb', onLoaded }) {
  const groupRef = useRef(null);

  useEffect(() => {
    if (!scene) return;
    const loader = new GLTFLoader();
    let group;

    loader.load(
      modelPath,
      (gltf) => {
        group = gltf.scene;
        group.position.set(...position);
        const furnaceObj = { group, heaterMeshes:[], crystalMesh:null };

        group.traverse((child) => {
          if (!child.isMesh) return;
          child.castShadow = child.receiveShadow = true;
          const n = child.name.toLowerCase();
          if (n.includes('heater')||n.includes('body')||n.includes('crucible')) {
            child.material = child.material.clone();
            child.material.emissive = new THREE.Color(0xff4400);
            child.material.emissiveIntensity = 0;
            furnaceObj.heaterMeshes.push(child);
          }
          if (n.includes('crystal')||n.includes('ingot')) furnaceObj.crystalMesh = child;
        });

        scene.add(group);
        groupRef.current = group;
        onLoaded?.(furnaceObj);
      },
      undefined,
      (err) => console.error(`[FurnaceModel ${furnaceId}] GLB 載入失敗:`, err)
    );

    return () => {
      if (group && scene) scene.remove(group);
    };
  }, [scene, modelPath]);

  return null; // 純邏輯元件，無 DOM 輸出
}
