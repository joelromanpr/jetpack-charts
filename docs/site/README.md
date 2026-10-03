# Documentation site

Use Node 22.12+ and `npm ci`. `npm run dev` starts the site; `npm run check && npm run build` validates types, pages, assets, and local links. Build output is static HTML with a Pagefind search index. Main deploys to GitHub Pages through `docs.yml`.

Guides live in `src/content/docs`. Native sample screenshots live in `public/images`; the browser explorer is illustrative and uses synthetic data. Keep installation examples on the published version while `main` advances to its next snapshot.

Dependencies are pinned in `package-lock.json` and checked monthly by Dependabot. TypeScript 6.0.3 is the latest supported by `astro check`; TypeScript 7 is currently unsupported.

`npm audit` reports [GHSA-ch52-4w7c-c8xp](https://github.com/advisories/GHSA-ch52-4w7c-c8xp) in Astro's build dependency `http-cache-semantics`; no patched release is available. Its cross-user server-cache scenario does not apply to the deployed static files. Do not expose the development server publicly or downgrade Astro to suppress the advisory. Recheck when upgrading the site.
