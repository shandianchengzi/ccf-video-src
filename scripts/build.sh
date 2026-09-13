#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -z "$sdk_root" ]]; then
  echo 'Android SDK required (ANDROID_SDK_ROOT or ANDROID_HOME)' >&2
  exit 1
fi
platform="$sdk_root/platforms/android-35/android.jar"
d8="$sdk_root/build-tools/35.0.0/d8"
[[ -f "$platform" && -x "$d8" ]] || { echo 'Install platforms;android-35 and build-tools;35.0.0' >&2; exit 1; }
mkdir -p build/stubs build/classes build/dex
find src/stubs/java -name '*.java' > build/stubs.list
find src/main/java -name '*.java' > build/sources.list
javac --release 8 -encoding UTF-8 -cp "$platform" -d build/stubs @build/stubs.list
javac --release 8 -encoding UTF-8 -cp "$platform:build/stubs" -d build/classes @build/sources.list
jar cf build/ccf-classes.jar -C build/classes .
"$d8" --release --min-api 21 --lib "$platform" --classpath build/stubs --output build/dex build/ccf-classes.jar
jar cf build/ccf.jar -C build/dex classes.dex
python - <<'PY'
from zipfile import ZipFile
with ZipFile('build/ccf.jar') as z:
    d = z.read('classes.dex')
    assert d.startswith(b'dex\n')
    assert b'Lcom/github/catvod/spider/CCF;' in d
print('Dex JAR built; compile-time Spider stub excluded')
PY
