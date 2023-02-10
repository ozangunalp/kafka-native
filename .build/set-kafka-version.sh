#!/usr/bin/env bash
set -e

echo Setting kafka version to $1
KAFKA_VERSION=$1

./mvnw -N versions:set-property -Dproperty=kafka.version -DnewVersion=${KAFKA_VERSION} -DgenerateBackupPoms=false
