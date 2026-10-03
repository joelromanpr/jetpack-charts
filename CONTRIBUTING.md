# Contributing

Use Java 17, Android SDK 37, and Python 3 for verification helpers. Open an issue for a new chart family or breaking API change before implementation.

Create a short-lived `feat/`, `fix/`, or `docs/` branch from `main`; submit a focused pull request. Maintainer branches use the `joelromanpr/` prefix. Use Conventional Commits, such as `feat: add a price marker`. Maintainers squash merge after checks pass. There is no permanent development branch.

```sh
./scripts/check-surfaces.sh
./gradlew :charts-core:test :charts-compose:testDebugUnitTest :charts-compose:lintRelease :sample:lintDebug :sample:assembleDebug apiCheck
./gradlew :charts-compose:connectedDebugAndroidTest
python3 scripts/check-device-results.py
```

Keep Android rendering in `charts-compose` and numeric algorithms in `charts-core`. New behavior needs a meaningful regression check; visual or interaction changes need a sample. Public API changes require `./gradlew apiDump` and review of the diff. Preserve accessibility, dark mode, API 23 support, and bounded work when rendering live data.

By contributing, you agree to license your contribution under Apache-2.0.

For documentation, use Node 22.12+: `cd docs/site && npm ci && npm run check && npm run build`. `npm run dev` starts the local site. Markdown guides live in `src/content/docs`; builds validate local links and generate search. Main deployments use GitHub Pages. See the [contribution guide](https://joelromanpr.github.io/jetpack-charts/community/contributing/).
