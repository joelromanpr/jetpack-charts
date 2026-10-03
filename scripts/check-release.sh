#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
version="$(sed -n 's/^VERSION_NAME=//p' gradle.properties)"
tag="${1:?Pass an annotated release tag}"
[[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ && "$tag" == "v$version" ]] || { echo 'Tag must match a stable VERSION_NAME.' >&2; exit 1; }
git fetch --force origin "refs/tags/$tag:refs/tags/$tag"
[[ "$(git cat-file -t "refs/tags/$tag")" == tag ]] || { echo 'Release tags must be annotated.' >&2; exit 1; }
[[ "$(git rev-parse "refs/tags/$tag^{}")" == "$(git rev-parse HEAD)" ]] || { echo 'Tag must point to HEAD.' >&2; exit 1; }
git fetch origin main
git merge-base --is-ancestor HEAD origin/main || { echo 'Release commit must belong to main.' >&2; exit 1; }
group="$(sed -n 's/^GROUP=//p' gradle.properties | tr . /)"
for artifact in charts-core charts-compose; do
    status="$(curl --silent --show-error --location --head --retry 3 --output /dev/null --write-out '%{http_code}' "https://repo.maven.apache.org/maven2/$group/$artifact/$version/$artifact-$version.pom")"
    [[ "$status" == 404 ]] || { echo "Expected unpublished $artifact; Central returned HTTP $status." >&2; exit 1; }
done
echo "Release $tag validated."
