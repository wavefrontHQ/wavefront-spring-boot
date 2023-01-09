#!/usr/bin/env bash
# Jenkins script to deploy SNAPSHOT releases to repo.spring.io
set -eux

if [[ $CI != "true" ]]; then
  echo "Error: This script is intended to be run in Jenkins/Linux environment."
  exit 1
fi

REPO_DIR=$(git rev-parse --show-toplevel)
cd "$REPO_DIR"

sudo chmod 666 /var/run/docker.sock
mkdir -p "$WORKSPACE_TMP/.m2" && chown -R "$(whoami)" "$WORKSPACE_TMP/.m2"

# Credentials for publishing to repo.spring.io
M2_SETTINGS_PATH="${M2_SETTINGS_SPRINGIO_XML}"

DOCKER_NAME="${JOB_NAME}-${BUILD_NUMBER}"

docker run -t --rm --name "$DOCKER_NAME" -u 1000:1000 \
  -v /etc/passwd:/etc/passwd:ro \
  -v "$REPO_DIR:/usr/src" -w /usr/src \
  -v "$WORKSPACE_TMP/.m2:/var/maven/.m2" -e MAVEN_CONFIG=/var/maven/.m2 \
  -v "$M2_SETTINGS_PATH:$M2_SETTINGS_PATH:ro" \
  maven:3.6.3-openjdk-17 mvn -Duser.home=/var/maven --file pom.xml \
    --settings "$M2_SETTINGS_PATH" \
    --batch-mode \
    clean javadoc:javadoc deploy
