#!/usr/bin/env bash
# Release script for use in Jenkins
set -x
set -e
set -u

if [[ $CI != "true" ]]; then
  echo "Error: This script is intended to be run in Jenkins/Linux environment."
  exit 1
fi

SCRIPT_DIR=$( dirname -- "$0"; )
cd "$SCRIPT_DIR/../"

sudo chmod 666 /var/run/docker.sock

DOCKER_NAME="${JOB_NAME}-${BUILD_NUMBER}"
docker run -t --rm --name "$DOCKER_NAME" -u 1000:1000 \
  -v "$WORKSPACE:/usr/src/workspace" -w /usr/src/workspace \
  -v /etc/passwd:/etc/passwd:ro \
  -v "$WORKSPACE_TMP/.m2:/var/maven/.m2" -e MAVEN_CONFIG=/var/maven/.m2 \
  maven:3.6.3-openjdk-17 mvn -Duser.home=/var/maven -f wavefront-spring-boot/pom.xml \
  	--no-transfer-progress \
    clean \
    javadoc:javadoc \
    install
