#!/usr/bin/env bash
set -e

export BRANCH="HEAD"
export GPG_CONFIG=""

if [[ ${DRY_RUN} == "true" ]]; then
  echo "[DRY RUN] - Dry Run Enabled - Git push will be skipped."
fi

if [[ ! -z "${GPG_KEYNAME}" ]]; then
  echo "[GPG] - Seting gpg.keyname to '${GPG_KEYNAME}'"
  GPG_CONFIG="-Dgpg.keyname=${GPG_KEYNAME}"
fi

echo "Cutting release ${RELEASE_VERSION}"
./mvnw -s .github/ci-maven-settings.xml -B -fn clean
git checkout ${BRANCH}
HASH=$(git rev-parse --verify $BRANCH)
echo "Last commit is ${HASH} - creating detached branch"
git checkout -b "r${RELEASE_VERSION}" "${HASH}"

echo "Update version to ${RELEASE_VERSION}"
./mvnw -B versions:set -DnewVersion="${RELEASE_VERSION}" -DgenerateBackupPoms=false -s .github/ci-maven-settings.xml

if [[ ${SKIP_TESTS} == "true" ]]; then
  ./mvnw -B clean verify -Prelease ${GPG_CONFIG} -DskipTests -s .github/ci-maven-settings.xml
else
  ./mvnw -B clean verify -Prelease ${GPG_CONFIG} -s .github/ci-maven-settings.xml
fi

git commit -am "[RELEASE] - Bump version to ${RELEASE_VERSION}"
git tag "${RELEASE_VERSION}"
echo "Pushing tag to origin"
if [[ ${DRY_RUN} == "true" ]]; then
  echo "[DRY RUN] - Skipping push: git push origin ${RELEASE_VERSION}"
else
  git push origin "${RELEASE_VERSION}"
fi
