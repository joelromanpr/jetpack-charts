# Engineering contract

- Keep `charts-core` independent of Android and Compose; keep Material in the sample.
- Prefer immutable snapshots, finite numeric inputs, visible-range rendering, and bounded sampling. Never hide expensive work inside recomposition.
- Preserve public API compatibility, light/dark support, accessibility, and Android API 23 compatibility.
- Add focused regression tests for meaningful behavior. Keep docs brief and examples compilable.
- Run `scripts/check-surfaces.sh`, relevant Gradle tests/lint, and `apiCheck`; update `apiDump` only for intentional API changes.
- Use Conventional Commits and short-lived branches from `main`. Keep credentials and generated outputs out of Git.
- Report local, device, CI, and published-artifact evidence separately. Follow `docs/RELEASING.md` for releases.
