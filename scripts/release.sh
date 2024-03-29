#!/usr/bin/env bash
# Jenkins script to publish M(ilestone) and RC releases to repo.spring.io,
# or final releases to Sonatype OSSRH
set -eux

if [[ $CI != "true" ]]; then
  echo "Error: This script is intended to be run in Jenkins/Linux environment."
  exit 1
fi

REPO_DIR=$(git rev-parse --show-toplevel)
cd "$REPO_DIR"

sudo chmod 666 /var/run/docker.sock
mkdir -p "$WORKSPACE_TMP/.m2" && chown -R "$(whoami)" "$WORKSPACE_TMP/.m2"

if [ "${RELEASE_TYPE}" = "milestone" ]; then
  # Credentials for publishing Milestones/RCs to repo.spring.io
	M2_SETTINGS_PATH="${M2_SETTINGS_SPRINGIO_XML}"
else
  # Credentials for publishing final releases to Sonatype OSSRH
	M2_SETTINGS_PATH="${M2_SETTINGS_SONATYPE_GPG_XML}"
fi

DOCKER_NAME="${JOB_NAME}-${BUILD_NUMBER}"

# mvn help:effective-settings is not required; it can be useful for debugging.
docker run -t --rm --name "$DOCKER_NAME" -u 1000:1000 \
  -v /etc/passwd:/etc/passwd:ro \
  -v "$REPO_DIR:/usr/src" -w /usr/src \
  -v "$GH_SSH_KEY_PATH:$HOME/.ssh/id_rsa:ro" \
  -v "$HOME/.ssh/known_hosts:$HOME/.ssh/known_hosts" \
  -v "$HOME/.gnupg:/var/gnupg" -e GNUPGHOME=/var/gnupg -e GPG_TTY=/dev/console \
  -v "$WORKSPACE_TMP/.m2:/var/maven/.m2" -e MAVEN_CONFIG=/var/maven/.m2 \
  -v "$M2_SETTINGS_PATH:$M2_SETTINGS_PATH:ro" \
  -e GIT_AUTHOR_EMAIL -e GIT_AUTHOR_NAME -e GIT_COMMITTER_EMAIL -e GIT_COMMITTER_NAME \
  maven:3.6.3-openjdk-17 mvn -Duser.home=/var/maven --file pom.xml \
    --batch-mode \
    --settings "$M2_SETTINGS_PATH" \
    help:effective-settings

CURRENT_PROJECT_VERSION=$(
  docker run -t --rm --name "$DOCKER_NAME" -u 1000:1000 \
    -v /etc/passwd:/etc/passwd:ro \
    -v "$REPO_DIR:/usr/src" -w /usr/src \
    -v "$GH_SSH_KEY_PATH:$HOME/.ssh/id_rsa:ro" \
    -v "$HOME/.ssh/known_hosts:$HOME/.ssh/known_hosts" \
    -v "$HOME/.gnupg:/var/gnupg" -e GNUPGHOME=/var/gnupg -e GPG_TTY=/dev/console \
    -v "$WORKSPACE_TMP/.m2:/var/maven/.m2" -e MAVEN_CONFIG=/var/maven/.m2 \
    -v "$M2_SETTINGS_PATH:$M2_SETTINGS_PATH:ro" \
    -e GIT_AUTHOR_EMAIL -e GIT_AUTHOR_NAME -e GIT_COMMITTER_EMAIL -e GIT_COMMITTER_NAME \
    maven:3.6.3-openjdk-17 mvn -Duser.home=/var/maven --file pom.xml \
      --settings "$M2_SETTINGS_PATH" \
      --batch-mode \
      --quiet \
      -Dexec.executable=echo \
      -Dexec.args='${project.version}' \
      --non-recursive \
      exec:exec
)

RELEASE_VERSION_POM=$(echo "$CURRENT_PROJECT_VERSION" | grep -Po '\d.*-SNAP' | sed 's/-SNAP//g')
if [[ ${RELEASE_VERSION} = ${RELEASE_VERSION_POM}* ]]; then
	echo "Major version from input: ${RELEASE_VERSION}, matches version in pom: ${RELEASE_VERSION_POM}"
else
	echo "Version mismatch, please check the version in project head and enter again."
  exit 1
fi


# mvn release:prepare
docker run -t --rm --name "$DOCKER_NAME" -u 1000:1000 \
  -v /etc/passwd:/etc/passwd:ro \
  -v "$REPO_DIR:/usr/src" -w /usr/src \
  -v "$GH_SSH_KEY_PATH:$HOME/.ssh/id_rsa:ro" \
  -v "$HOME/.ssh/known_hosts:$HOME/.ssh/known_hosts" \
  -v "$HOME/.gnupg:/var/gnupg" -e GNUPGHOME=/var/gnupg -e GPG_TTY=/dev/console \
  -v "$WORKSPACE_TMP/.m2:/var/maven/.m2" -e MAVEN_CONFIG=/var/maven/.m2 \
  -v "$M2_SETTINGS_PATH:$M2_SETTINGS_PATH:ro" \
  -e GIT_AUTHOR_EMAIL -e GIT_AUTHOR_NAME -e GIT_COMMITTER_EMAIL -e GIT_COMMITTER_NAME \
  maven:3.6.3-openjdk-17 mvn -Duser.home=/var/maven --file pom.xml \
    --settings "$M2_SETTINGS_PATH" \
    --batch-mode \
    clean release:prepare \
    -Darguments="-DskipTests -DreleaseType=${RELEASE_TYPE} -Drelease" \
    -DautoVersionSubmodules=true \
    -DreleaseVersion="${RELEASE_VERSION}" \
    -Dtag=v"${RELEASE_VERSION}" \
    -DdevelopmentVersion="${NEXT_VERSION}" \
    --activate-profiles release

# mvn release:perform
docker run -t --rm --name "$DOCKER_NAME" -u 1000:1000 \
  -v /etc/passwd:/etc/passwd:ro \
  -v "$REPO_DIR:/usr/src" -w /usr/src \
  -v "$GH_SSH_KEY_PATH:$HOME/.ssh/id_rsa:ro" \
  -v "$HOME/.ssh/known_hosts:$HOME/.ssh/known_hosts" \
  -v "$HOME/.gnupg:/var/gnupg" -e GNUPGHOME=/var/gnupg -e GPG_TTY=/dev/console \
  -v "$WORKSPACE_TMP/.m2:/var/maven/.m2" -e MAVEN_CONFIG=/var/maven/.m2 \
  -v "$M2_SETTINGS_PATH:$M2_SETTINGS_PATH:ro" \
  -e GIT_AUTHOR_EMAIL -e GIT_AUTHOR_NAME -e GIT_COMMITTER_EMAIL -e GIT_COMMITTER_NAME \
  maven:3.6.3-openjdk-17 mvn -Duser.home=/var/maven --file pom.xml \
    --settings "$M2_SETTINGS_PATH" \
    --batch-mode \
    release:perform \
    -Darguments="-DskipTests -DreleaseType=${RELEASE_TYPE} -Drelease" \
    --activate-profiles release
