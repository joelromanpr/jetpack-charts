import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';

export default defineConfig({
  site: 'https://joelromanpr.github.io',
  base: '/jetpack-charts',
  trailingSlash: 'always',
  integrations: [starlight({
    title: 'Jetpack Charts',
    description: 'Compose charts for markets, portfolios, and commerce. Built for live data.',
    logo: { src: './src/assets/mark.svg' },
    favicon: '/favicon.svg',
    social: [{ icon: 'github', label: 'GitHub', href: 'https://github.com/joelromanpr/jetpack-charts' }],
    editLink: { baseUrl: 'https://github.com/joelromanpr/jetpack-charts/edit/main/docs/site/' },
    customCss: ['./src/styles/custom.css'],
    expressiveCode: { themes: ['github-dark', 'github-light'] },
    sidebar: [
      { label: 'Start here', items: ['getting-started', 'charts', 'examples'] },
      { label: 'Build with charts', items: ['guides/trading', 'guides/live-data', 'guides/theming'] },
      { label: 'Reference', items: ['reference/api'] },
      { label: 'Project', items: ['community/contributing', 'community/releases'] },
    ],
  })],
});
