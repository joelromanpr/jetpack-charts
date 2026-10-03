# Contributing

Use Java 17 and Android SDK 36. Open an issue for a new chart family or breaking API change before implementation.

Create a short-lived `feat/`, `fix/`, or `docs/` branch from `main`; submit a focused pull request. Maintainer branches use the `joelromanpr/` prefix. Use Conventional Commits, such as `feat: add a price marker`. Maintainers squash merge after checks pass. There is no permanent development branch.

```sh
./scripts/check-surfaces.sh
./gradlew :charts-core:test :charts-compose:lintRelease :sample:lintDebug :sample:assembleDebug apiCheck
./gradlew :charts-compose:connectedDebugAndroidTest
```

Keep Android rendering in `charts-compose` and numeric algorithms in `charts-core`. New behavior needs a meaningful regression check; visual or interaction changes need a sample. Public API changes require `./gradlew apiDump` and review of the diff. Preserve accessibility, dark mode, API 23 support, and bounded work when rendering live data.

By contributing, you agree to license your contribution under Apache-2.0.
