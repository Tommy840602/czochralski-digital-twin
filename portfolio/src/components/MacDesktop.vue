<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue';
import { profile, projects } from '../data/projects.js';

const baseUrl = import.meta.env.BASE_URL || '/';
const query = ref('');
const searchInput = ref(null);
const timeLabel = ref('');
const highlightedSlug = ref(projects[0]?.slug ?? '');

let timerId;

const basePath = computed(() => baseUrl.replace(/\/$/, ''));
const projectLinks = computed(() =>
  projects.map((project) => ({
    ...project,
    url: `${basePath.value}${project.href}`,
    searchable: [
      project.title,
      project.subtitle,
      project.short,
      project.role,
      project.period,
      ...project.stack,
      ...project.highlights
    ]
      .join(' ')
      .toLowerCase()
  }))
);

const filteredProjects = computed(() => {
  const keyword = query.value.trim().toLowerCase();

  if (!keyword) {
    return projectLinks.value;
  }

  return projectLinks.value.filter((project) => project.searchable.includes(keyword));
});

const activeProject = computed(() => {
  const preferred = filteredProjects.value.find((project) => project.slug === highlightedSlug.value);
  return preferred ?? filteredProjects.value[0] ?? projectLinks.value[0];
});

const quickLinks = computed(() => [
  {
    key: 'about',
    title: 'About Me',
    subtitle: profile.role,
    url: `${basePath.value}/about/`,
    badge: 'ME'
  },
  {
    key: 'github',
    title: 'GitHub Repository',
    subtitle: 'Source code and deployment workflow',
    url: profile.github,
    badge: 'GH',
    external: true
  }
]);

function localAsset(name) {
  return `${basePath.value}/${name}`;
}

function refreshTime() {
  timeLabel.value = new Intl.DateTimeFormat('zh-TW', {
    weekday: 'short',
    hour: '2-digit',
    minute: '2-digit'
  }).format(new Date());
}

function setHighlight(project) {
  highlightedSlug.value = project.slug;
}

function openHighlighted() {
  if (activeProject.value?.url) {
    window.location.href = activeProject.value.url;
  }
}

function handleKeydown(event) {
  if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
    event.preventDefault();
    const list = filteredProjects.value;
    if (!list.length) return;

    const currentIndex = list.findIndex((project) => project.slug === highlightedSlug.value);
    const fallbackIndex = currentIndex === -1 ? 0 : currentIndex;
    const offset = event.key === 'ArrowDown' ? 1 : -1;
    const nextIndex = (fallbackIndex + offset + list.length) % list.length;
    highlightedSlug.value = list[nextIndex].slug;
  }

  if (event.key === 'Enter') {
    event.preventDefault();
    openHighlighted();
  }
}

onMounted(async () => {
  refreshTime();
  timerId = window.setInterval(refreshTime, 30000);
  await nextTick();
  searchInput.value?.focus();
});

onUnmounted(() => {
  window.clearInterval(timerId);
});
</script>

<template>
  <main
    class="spotlight-page"
    :style="{ '--wallpaper-image': `url(${localAsset('desktop-wallpaper.png')})` }"
  >
    <div class="wallpaper-blur" aria-hidden="true"></div>

    <header class="menu-bar">
      <div class="menu-left">
        <span class="system-mark" aria-hidden="true"></span>
        <strong>{{ profile.name }}</strong>
        <a :href="`${basePath}/about/`">About</a>
        <a :href="profile.github" target="_blank" rel="noreferrer">GitHub</a>
      </div>
      <div class="menu-right">
        <span>{{ profile.location }}</span>
        <span>{{ timeLabel }}</span>
      </div>
    </header>

    <section class="spotlight-shell" aria-label="Portfolio search">
      <div class="spotlight-panel">
        <label class="search-row" for="portfolio-search">
          <span class="search-icon" aria-hidden="true"></span>
          <input
            id="portfolio-search"
            ref="searchInput"
            v-model="query"
            type="search"
            autocomplete="off"
            spellcheck="false"
            placeholder="Search projects"
            @keydown="handleKeydown"
          />
        </label>

        <div class="spotlight-content">
          <div class="result-list">
            <p class="section-label">Top Hit</p>
            <a
              v-if="activeProject"
              class="top-hit"
              :href="activeProject.url"
              :style="{ '--accent': activeProject.accent }"
            >
              <span class="result-icon">{{ activeProject.icon }}</span>
              <span>
                <strong>{{ activeProject.title }}</strong>
                <small>{{ activeProject.subtitle }}</small>
              </span>
            </a>

            <p class="section-label">Projects</p>
            <a
              v-for="project in filteredProjects"
              :key="project.slug"
              class="result-row"
              :class="{ active: activeProject?.slug === project.slug }"
              :href="project.url"
              :style="{ '--accent': project.accent }"
              @mouseenter="setHighlight(project)"
              @focus="setHighlight(project)"
            >
              <span class="result-icon">{{ project.icon }}</span>
              <span class="result-copy">
                <strong>{{ project.title }}</strong>
                <small>{{ project.short }}</small>
              </span>
              <span class="result-kind">Project</span>
            </a>

            <div v-if="!filteredProjects.length" class="empty-state">
              <strong>No results</strong>
              <small>{{ query }}</small>
            </div>

            <p class="section-label">Quick Links</p>
            <a
              v-for="link in quickLinks"
              :key="link.key"
              class="result-row utility"
              :href="link.url"
              :target="link.external ? '_blank' : undefined"
              :rel="link.external ? 'noreferrer' : undefined"
            >
              <span class="result-icon">{{ link.badge }}</span>
              <span class="result-copy">
                <strong>{{ link.title }}</strong>
                <small>{{ link.subtitle }}</small>
              </span>
            </a>
          </div>

          <article v-if="activeProject" class="preview-card" :style="{ '--accent': activeProject.accent }">
            <span class="preview-mark">{{ activeProject.icon }}</span>
            <p class="eyebrow">{{ activeProject.period }} / {{ activeProject.role }}</p>
            <h1>{{ activeProject.title }}</h1>
            <p>{{ activeProject.short }}</p>
            <div class="preview-stack">
              <span v-for="item in activeProject.stack.slice(0, 7)" :key="item">{{ item }}</span>
            </div>
            <a class="open-button" :href="activeProject.url">Open Project</a>
          </article>
        </div>
      </div>
    </section>
  </main>
</template>

<style scoped>
.spotlight-page {
  position: relative;
  min-height: 100vh;
  overflow: hidden;
  color: #f7fbff;
  background:
    linear-gradient(180deg, rgba(20, 32, 48, 0.12), rgba(4, 16, 32, 0.38)),
    var(--wallpaper-image) center / cover no-repeat,
    linear-gradient(145deg, #6bc5ff, #195fa9 58%, #0a2f70);
}

.wallpaper-blur {
  position: absolute;
  inset: -1.5rem;
  background:
    radial-gradient(circle at 50% 18%, rgba(255, 255, 255, 0.22), transparent 18rem),
    linear-gradient(180deg, rgba(10, 20, 32, 0.05), rgba(5, 12, 24, 0.42));
  backdrop-filter: blur(7px) saturate(1.12);
  pointer-events: none;
}

.menu-bar {
  position: relative;
  z-index: 4;
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 1.72rem;
  padding: 0 0.72rem;
  color: #0b1b2d;
  font-size: 0.78rem;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.9), rgba(220, 232, 240, 0.76));
  border-bottom: 1px solid rgba(40, 70, 95, 0.22);
  box-shadow: 0 1px 14px rgba(10, 45, 80, 0.16);
  backdrop-filter: blur(20px);
}

.menu-left,
.menu-right {
  display: flex;
  align-items: center;
  gap: 0.82rem;
  min-width: 0;
}

.menu-left a,
.menu-right span {
  color: inherit;
  text-decoration: none;
  white-space: nowrap;
}

.system-mark {
  width: 0.9rem;
  height: 0.9rem;
  border-radius: 50%;
  background:
    radial-gradient(circle at 35% 25%, #fff, transparent 32%),
    linear-gradient(135deg, #5aa9ff, #0f65b8 52%, #083a77);
  box-shadow: inset 0 1px 1px rgba(255, 255, 255, 0.78), 0 0 0 1px rgba(10, 48, 90, 0.28);
}

.spotlight-shell {
  position: relative;
  z-index: 2;
  display: grid;
  place-items: start center;
  min-height: calc(100vh - 1.72rem);
  padding: clamp(3.4rem, 10vh, 5.4rem) 1rem 2rem;
}

.spotlight-panel {
  width: min(52rem, calc(100vw - 2rem));
  max-height: calc(100vh - 4.5rem);
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.3);
  border-radius: 1.15rem;
  background:
    linear-gradient(180deg, rgba(238, 244, 250, 0.82), rgba(214, 226, 238, 0.72)),
    rgba(226, 236, 246, 0.72);
  box-shadow: 0 34px 90px rgba(5, 20, 36, 0.38), inset 0 1px 0 rgba(255, 255, 255, 0.82);
  backdrop-filter: blur(34px) saturate(1.25);
}

.search-row {
  display: grid;
  grid-template-columns: 2rem 1fr;
  gap: 0.55rem;
  align-items: center;
  min-height: 4.15rem;
  padding: 0 1.25rem;
  border-bottom: 1px solid rgba(47, 76, 100, 0.16);
}

.search-icon {
  position: relative;
  width: 1.34rem;
  height: 1.34rem;
  border: 3px solid rgba(50, 70, 88, 0.68);
  border-radius: 50%;
}

.search-icon::after {
  position: absolute;
  right: -0.42rem;
  bottom: -0.35rem;
  width: 0.68rem;
  height: 0.18rem;
  content: '';
  border-radius: 999px;
  background: rgba(50, 70, 88, 0.68);
  transform: rotate(45deg);
}

.search-row input {
  width: 100%;
  min-width: 0;
  color: #172636;
  font: inherit;
  font-size: clamp(1.7rem, 3.7vw, 2.6rem);
  font-weight: 650;
  letter-spacing: 0;
  background: transparent;
  border: 0;
  outline: 0;
}

.search-row input::placeholder {
  color: rgba(48, 67, 86, 0.62);
}

.spotlight-content {
  display: grid;
  grid-template-columns: minmax(21rem, 1fr) minmax(16rem, 22rem);
  height: min(32rem, calc(100vh - 6rem));
  min-height: 24rem;
}

.result-list {
  padding: 0.85rem;
  border-right: 1px solid rgba(47, 76, 100, 0.14);
  overflow: auto;
}

.section-label {
  margin: 0.65rem 0 0.35rem;
  padding: 0 0.55rem;
  color: rgba(38, 58, 76, 0.62);
  font-size: 0.72rem;
  font-weight: 900;
  text-transform: uppercase;
}

.section-label:first-child {
  margin-top: 0;
}

.top-hit,
.result-row {
  display: grid;
  grid-template-columns: 2.8rem minmax(0, 1fr) auto;
  gap: 0.75rem;
  align-items: center;
  min-height: 3.65rem;
  padding: 0.52rem 0.62rem;
  color: #172636;
  text-decoration: none;
  border-radius: 0.72rem;
}

.top-hit {
  grid-template-columns: 3.15rem minmax(0, 1fr);
  min-height: 4.05rem;
  background: rgba(255, 255, 255, 0.46);
}

.result-row:hover,
.result-row:focus-visible,
.result-row.active,
.top-hit:hover,
.top-hit:focus-visible {
  color: #fff;
  background: linear-gradient(180deg, rgba(66, 151, 237, 0.95), rgba(20, 105, 207, 0.96));
}

.result-icon {
  display: grid;
  place-items: center;
  width: 2.45rem;
  height: 2.45rem;
  color: #fff;
  font-size: 0.72rem;
  font-weight: 900;
  border-radius: 0.62rem;
  background:
    radial-gradient(circle at 35% 20%, rgba(255, 255, 255, 0.75), transparent 30%),
    linear-gradient(145deg, var(--accent, #5b8def), #0a477f);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.55), 0 8px 18px rgba(20, 55, 86, 0.18);
}

.result-copy,
.top-hit span:last-child {
  min-width: 0;
}

.result-copy strong,
.result-copy small,
.top-hit strong,
.top-hit small {
  display: block;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
}

.result-copy strong,
.top-hit strong {
  font-size: 0.95rem;
  line-height: 1.2;
  white-space: nowrap;
}

.result-copy small,
.top-hit small {
  margin-top: 0.16rem;
  color: rgba(34, 52, 70, 0.72);
  font-size: 0.78rem;
  line-height: 1.3;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 1;
  white-space: normal;
}

.result-row:hover small,
.result-row:focus-visible small,
.result-row.active small,
.top-hit:hover small,
.top-hit:focus-visible small {
  color: rgba(255, 255, 255, 0.82);
}

.result-kind {
  color: rgba(34, 52, 70, 0.55);
  font-size: 0.72rem;
  font-weight: 800;
}

.result-row:hover .result-kind,
.result-row:focus-visible .result-kind,
.result-row.active .result-kind {
  color: rgba(255, 255, 255, 0.82);
}

.result-row.utility {
  grid-template-columns: 2.8rem minmax(0, 1fr);
  min-height: 3.3rem;
}

.empty-state {
  display: grid;
  gap: 0.2rem;
  margin: 0.35rem 0;
  padding: 0.85rem 0.62rem;
  color: #31485f;
}

.empty-state small {
  color: rgba(49, 72, 95, 0.68);
}

.preview-card {
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
  padding: clamp(1.1rem, 3vw, 1.7rem);
  color: #172636;
  background:
    radial-gradient(circle at 50% 0%, rgba(255, 255, 255, 0.58), transparent 14rem),
    rgba(255, 255, 255, 0.36);
  overflow: auto;
}

.preview-mark {
  display: grid;
  place-items: center;
  width: 4rem;
  height: 4rem;
  color: #fff;
  font-weight: 900;
  border-radius: 1rem;
  background:
    radial-gradient(circle at 35% 20%, rgba(255, 255, 255, 0.72), transparent 30%),
    linear-gradient(145deg, var(--accent), #0c477d);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.62), 0 18px 34px rgba(20, 55, 86, 0.22);
}

.eyebrow {
  margin: 0;
  color: #426b91;
  font-size: 0.72rem;
  font-weight: 900;
  letter-spacing: 0;
  text-transform: uppercase;
}

.preview-card h1 {
  margin: 0;
  color: #12263b;
  font-size: clamp(1.85rem, 3.3vw, 2.9rem);
  line-height: 1;
  letter-spacing: 0;
}

.preview-card p {
  margin: 0;
  color: #41586d;
  font-size: 0.95rem;
  line-height: 1.7;
}

.preview-stack {
  display: flex;
  flex-wrap: wrap;
  gap: 0.42rem;
}

.preview-stack span {
  padding: 0.33rem 0.52rem;
  color: #23465f;
  font-size: 0.72rem;
  font-weight: 900;
  background: rgba(255, 255, 255, 0.66);
  border: 1px solid rgba(65, 120, 160, 0.18);
  border-radius: 999px;
}

.open-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: fit-content;
  min-height: 2.5rem;
  margin-top: auto;
  padding: 0 0.95rem;
  color: #fff;
  font-weight: 900;
  text-decoration: none;
  background: linear-gradient(180deg, #44a2ef, #116bc5);
  border: 1px solid rgba(20, 73, 128, 0.28);
  border-radius: 0.7rem;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.52), 0 10px 20px rgba(17, 107, 197, 0.2);
}

@media (max-width: 820px) {
  .spotlight-page {
    overflow: auto;
  }

  .menu-left {
    gap: 0.58rem;
  }

  .menu-left a:nth-of-type(2),
  .menu-right span:first-child {
    display: none;
  }

  .spotlight-shell {
    place-items: start center;
    padding-top: 3.5rem;
  }

  .spotlight-panel {
    border-radius: 0.95rem;
    max-height: none;
    overflow: visible;
  }

  .search-row {
    min-height: 4rem;
    grid-template-columns: 1.6rem 1fr;
    padding: 0 1rem;
  }

  .search-row input {
    font-size: clamp(1.45rem, 8vw, 2rem);
  }

  .spotlight-content {
    grid-template-columns: 1fr;
    height: auto;
    min-height: 0;
  }

  .result-list {
    border-right: 0;
  }

  .preview-card {
    border-top: 1px solid rgba(47, 76, 100, 0.14);
  }

  .result-kind {
    display: none;
  }

  .top-hit,
  .result-row {
    grid-template-columns: 2.8rem minmax(0, 1fr);
  }
}
</style>
