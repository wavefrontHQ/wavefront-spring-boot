#!/usr/bin/env bash
# Release script for use in Jenkins
set -x
set -e
set -u

if [[ $CI != "true" ]]; then
  echo "Error: This script is intended to be run in Jenkins/Linux environment."
  exit 1
fi

REPO_DIR=$(git rev-parse --show-toplevel)
cd "$REPO_DIR"

sudo chmod 666 /var/run/docker.sock
rm -rf "$WORKSPACE_TMP/.m2"
mkdir -p "$WORKSPACE_TMP/.m2"

DOCKER_NAME="${JOB_NAME}-${BUILD_NUMBER}"
docker run -t --rm --name "$DOCKER_NAME" -u 1000:1000 \
  -v /etc/passwd:/etc/passwd:ro \
  -v "$REPO_DIR:/usr/src" -w /usr/src \
  -v "$WORKSPACE_TMP/.m2:/var/maven/.m2" -e MAVEN_CONFIG=/var/maven/.m2 \
  maven:3.6.3-openjdk-17 mvn -Duser.home=/var/maven --file pom.xml \
  	--no-transfer-progress \
    clean \
    javadoc:javadoc \
    package
