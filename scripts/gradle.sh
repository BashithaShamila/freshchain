#!/usr/bin/env bash
# Runs Gradle inside a container. No local JDK or Gradle install required.
#
# The Docker socket is mounted so Testcontainers can start sibling containers;
# TESTCONTAINERS_HOST_OVERRIDE tells the test JVM how to reach the ports those
# siblings publish, since "localhost" inside this container is not the host.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IMAGE="gradle:8.14.3-jdk21"

docker volume create freshchain-gradle-cache >/dev/null

exec docker run --rm -i \
  -u root \
  -v "$ROOT":/work -w /work \
  -v freshchain-gradle-cache:/home/gradle/.gradle \
  -v /var/run/docker.sock:/var/run/docker.sock \
  --add-host host.docker.internal:host-gateway \
  -e GRADLE_USER_HOME=/home/gradle/.gradle \
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
  "$IMAGE" gradle --no-daemon "$@"
