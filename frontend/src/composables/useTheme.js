import { ref } from 'vue'

// 主題：'dark'（預設）| 'light'，記在 localStorage，套在 <html data-theme>
const STORAGE_KEY = 'twin_theme'
const theme = ref('dark')

/** 套用主題到 <html>，並寫入 localStorage */
function apply(next) {
  theme.value = next
  const root = document.documentElement
  if (next === 'light') {
    root.setAttribute('data-theme', 'light')
  } else {
    root.removeAttribute('data-theme')
  }
  try {
    localStorage.setItem(STORAGE_KEY, next)
  } catch { /* 隱私模式等情況忽略 */ }
}

/** 在 app 掛載前呼叫一次：讀取記憶的主題（無則預設深色），避免閃爍 */
export function initTheme() {
  let saved = 'dark'
  try {
    const v = localStorage.getItem(STORAGE_KEY)
    if (v === 'light' || v === 'dark') saved = v
  } catch { /* ignore */ }
  apply(saved)
}

export function useTheme() {
  const toggle = () => apply(theme.value === 'light' ? 'dark' : 'light')
  const setTheme = (t) => apply(t === 'light' ? 'light' : 'dark')
  return { theme, toggle, setTheme, isLight: () => theme.value === 'light' }
}
