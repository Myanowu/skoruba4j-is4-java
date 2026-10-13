#!/bin/sh
# Packaged STS launcher. IDSERVER_HOME defaults to the dist directory (parent of cmd).
# Uses dist/jdk when mvn package copied one (Windows), otherwise java on PATH.
set -eu
cd "$(dirname "$0")"
HERE=$(pwd)
if [ -z "${IDSERVER_HOME:-}" ]; then
  IDSERVER_HOME=$(CDPATH= cd "$HERE/.." && pwd)
  export IDSERVER_HOME
fi
mkdir -p "$IDSERVER_HOME/data" "$IDSERVER_HOME/secrets" "$IDSERVER_HOME/logs"
if [ -x "$IDSERVER_HOME/jdk/bin/java" ]; then
  JAVA=$IDSERVER_HOME/jdk/bin/java
elif command -v java >/dev/null 2>&1; then
  JAVA=$(command -v java)
else
  echo "Java not found. Install JDK 21, or package on Windows so dist/jdk exists." >&2
  exit 1
fi
JAR=
for f in "$HERE"/../lib/skoruba4j-sts-*.jar; do
  if [ ! -f "$f" ]; then
    continue
  fi
  case "$f" in
    *.original) continue ;;
  esac
  JAR=$f
done
if [ -z "$JAR" ]; then
  echo "STS jar not found under $HERE/../lib" >&2
  exit 1
fi
exec "$JAVA" -Xmx512m -Dspring.profiles.active=oss,local -DIDSERVER_HOME="$IDSERVER_HOME" -jar "$JAR"
