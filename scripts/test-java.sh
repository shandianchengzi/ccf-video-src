#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/test-classes build/deps
# JVM-only JSON implementation for contract tests; not shipped in the Android JAR.
curl --fail --silent --show-error --retry 3 --max-time 60 \
  https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar \
  -o build/deps/json.jar
javac --release 8 -encoding UTF-8 -cp build/deps/json.jar -d build/test-classes \
  src/main/java/com/github/catvod/spider/ccf/Access.java \
  src/main/java/com/github/catvod/spider/ccf/SessionCookies.java \
  src/main/java/com/github/catvod/spider/ccf/Catalog.java \
  src/test/java/com/github/catvod/spider/ccf/ContractTest.java
java -cp build/test-classes:build/deps/json.jar com.github.catvod.spider.ccf.ContractTest
