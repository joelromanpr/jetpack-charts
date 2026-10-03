# Releases

The first release is **2.0.0**. Use semantic versioning afterwards: patches fix behavior, minors add compatible APIs, majors change contracts. Published versions and `v*` tags are immutable.

1. Set `VERSION_NAME` in `gradle.properties` and add a concise changelog entry. Review `apiDump`, then run the contribution checks and device tests on API 23 and 36.
2. Merge the release commit to `main`. Create and push an annotated tag matching the version: `git tag -a v2.0.0 -m "Jetpack Charts 2.0.0"`.
3. Configure the `maven-central` GitHub environment with required maintainer review and four secrets: `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`, `SIGNING_KEY` (ASCII-armored private key), and `SIGNING_PASSWORD`. Use a Central Portal token and a signing public key distributed to a supported keyserver. Never commit credentials.
4. Dispatch **Stage release** from the tag: `gh workflow run release.yml --ref v2.0.0 -f tag=v2.0.0`. Both values must match the release; the publishing environment accepts release tags. It checks tag/version/ancestry, verifies both artifacts are absent from Central, runs CI/device gates, and uploads signed binaries, sources, documentation, POMs, and Gradle metadata for manual Central review.
5. Inspect the deployment in [Central Portal](https://central.sonatype.com/publishing/deployments), then publish it. Verify both POMs and build a separate consumer against `mavenCentral()` before creating the GitHub release.

Maven group: `io.github.joelromanpr.charts`, under the account's verified namespace. Kotlin packages: `com.joelromanpr.charts`. Artifacts: `charts-core` and `charts-compose`.

For local packaging without credentials, run `./gradlew stageRelease`. The review bundle is created under ignored `outputs/releases/`; it is unsigned and cannot be uploaded as a completed Central release. Signing and Portal access are verified only by a successful staging run.

After release, advance `VERSION_NAME` to the next `-SNAPSHOT` on `main`; continue through short-lived branches and pull requests.
