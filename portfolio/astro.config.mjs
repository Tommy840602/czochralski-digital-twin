import { defineConfig } from 'astro/config';
import vue from '@astrojs/vue';

const repositoryName = process.env.GITHUB_REPOSITORY?.split('/')[1] ?? 'czochralski-digital-twin';
const isGitHubPages = process.env.GITHUB_ACTIONS === 'true';

export default defineConfig({
  site: 'https://Tommy840602.github.io',
  base: isGitHubPages ? `/${repositoryName}` : '/',
  output: 'static',
  integrations: [vue()]
});
