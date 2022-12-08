#!/usr/bin/env bash
set -e

mvn -B clean package -Dnative \
  -Dquarkus.native.container-build=true \
  -Dquarkus.container-image.build=true \
  -Dquarkus.container-image.additional-tags=latest

mvn -B clean verify -Dtest-container
