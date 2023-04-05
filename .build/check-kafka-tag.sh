#!/usr/bin/env bash
set -e

echo "Checking branch ${RELEASE_VERSION} against ${KAFKA_VERSION}"

if [[ ! "${RELEASE_VERSION}" =~ ^.*-kafka-${KAFKA_VERSION} ]]; then
  echo "::error Branch name does not contain the kafka version"
  exit 1
fi