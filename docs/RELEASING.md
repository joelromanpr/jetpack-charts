# Releases

The initial release is **2.0.0**. Use semantic versioning: patches fix behavior, minors add compatible APIs, majors change contracts. Published versions and annotated `v*` tags are immutable.

1. Set `VERSION_NAME` in `gradle.properties` and add a concise changelog entry. Review `apiDump`, then run the contribution checks and device tests on API 23, 36, and 37.
2. Merge the release commit to `main`. Create and push an annotated tag matching the version, for example: `git tag -a v2.1.0 -m "Jetpack Charts 2.1.0"`.
3. Keep the `maven-central` GitHub environment restricted to `v*` tags. Store four secrets: `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`, `SIGNING_KEY` (ASCII-armored private key), and `SIGNING_PASSWORD`. Use an expiring Central Portal token and distribute the signing public key to a [supported keyserver](https://central.sonatype.org/publish/requirements/gpg/). Never commit credentials.
4. Dispatch **Stage release** from the tag: `gh workflow run release.yml --ref v2.1.0 -f tag=v2.1.0`. Both values must match the release; the publishing environment accepts release tags. It checks tag/version/ancestry, verifies both artifacts are absent from Central, runs CI/device gates, and uploads signed binaries, sources, documentation, POMs, and Gradle metadata for manual Central review.
5. Review the validated deployment in [Central Portal](https://central.sonatype.com/publishing/deployments), then publish it. Fetch both public POMs and build a separate consumer using only `mavenCentral()` for this library before publishing the GitHub release. Check that published artifact hashes match the reviewed bundle.

Maven group: `io.github.joelromanpr.charts`, under the account's verified namespace. Kotlin packages: `com.joelromanpr.charts`. Artifacts: `charts-core` and `charts-compose`.

For local review, run `./gradlew stageRelease`. This creates an unsigned bundle under ignored `outputs/releases/`. For a manual release, build and sign from the frozen tag, then upload a [Maven-layout bundle](https://central.sonatype.org/publish/publish-portal-upload/) containing binaries, sources, documentation, metadata, signatures, and checksums. Central validation and a public consumer build are still required.

After cutting the release tag, advance `VERSION_NAME` to the next `-SNAPSHOT` on `main`; publication uses the frozen tag. Continue through short-lived branches and pull requests.
