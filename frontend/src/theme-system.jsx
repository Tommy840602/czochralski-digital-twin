import React, { createContext, useContext, useState, useEffect } from 'react';

// 🎨 为你的项目量身定制的两套主题
const THEMES = {
  dark: {
    name: 'Dark',
    // 背景色
    bg: {
      primary: '#080b10',      // 主背景
      secondary: '#0f1218',    // 次背景（卡片）
      tertiary: '#161b24',     // 第三层（内容框）
    },
    // 文本色
    text: {
      primary: '#e8edf5',      // 主文本
      secondary: 'rgba(232,237,245,0.6)',
      muted: 'rgba(232,237,245,0.3)',
      dim: 'rgba(232,237,245,0.15)',
    },
    // 边框和装饰
    border: 'rgba(255,255,255,0.07)',
    borderLight: 'rgba(255,255,255,0.03)',
    // 强调色
    accent: {
      ok: '#40c88c',           // OK（绿）
      ng: '#f04a4a',           // NG（红）
      warn: '#f0a840',         // 警告（橙）
      info: '#4a8cf0',         // 信息（蓝）
      chart: '#4a8cf0',        // 图表
    },
    // Three.js 场景颜色
    scene: {
      bg: 0x080b10,
      grid: 0x1a2a3a,
      fog: 0x080b10,
    },
  },

  light: {
    name: 'Light',
    bg: {
      primary: '#f8f9fa',      // 主背景
      secondary: '#ffffff',    // 次背景（卡片）
      tertiary: '#f1f3f5',     // 第三层（内容框）
    },
    text: {
      primary: '#1a1a1a',      // 主文本
      secondary: 'rgba(26,26,26,0.7)',
      muted: 'rgba(26,26,26,0.4)',
      dim: 'rgba(26,26,26,0.2)',
    },
    border: 'rgba(0,0,0,0.08)',
    borderLight: 'rgba(0,0,0,0.03)',
    accent: {
      ok: '#2d8659',           // OK（深绿）
      ng: '#d32f2f',           // NG（深红）
      warn: '#f57c00',         // 警告（深橙）
      info: '#1565c0',         // 信息（深蓝）
      chart: '#1565c0',
    },
    scene: {
      bg: 0xfafbfc,
      grid: 0xe8eef5,
      fog: 0xfafbfc,
    },
  },
};

// 🎯 ThemeContext
const ThemeContext = createContext();

export function ThemeProvider({ children }) {
  const [theme, setTheme] = useState(() => {
    const saved = localStorage.getItem('furnace-theme');
    if (saved) return saved;
    const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
    return prefersDark ? 'dark' : 'light';
  });

  useEffect(() => {
    localStorage.setItem('furnace-theme', theme);
    document.documentElement.setAttribute('data-theme', theme);
  }, [theme]);

  const toggleTheme = () => {
    setTheme(prev => prev === 'dark' ? 'light' : 'dark');
  };

  const currentTheme = THEMES[theme];

  return (
    <ThemeContext.Provider value={{ theme, toggleTheme, ...currentTheme }}>
      {children}
    </ThemeContext.Provider>
  );
}

export function useTheme() {
  const context = useContext(ThemeContext);
  if (!context) {
    throw new Error('useTheme must be used within ThemeProvider');
  }
  return context;
}