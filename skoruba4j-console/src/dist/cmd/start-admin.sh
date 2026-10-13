#!/bin/sh
# Packaged Admin launcher. Same IDSERVER_HOME as start-sts.sh so they share SQLite.
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
for f in "$HERE"/../lib/skoruba4j-admin-*.jar; do
  if [ ! -f "$f" ]; then
    continue
  fi
  case "$f" in
    *admin-api*) continue ;;
    *.original) continue ;;
  esac
  JAR=$f
done
if [ -z "$JAR" ]; then
  echo "Admin jar not found under $HERE/../lib" >&2
  exit 1
fi
exec "$JAVA" -Xmx512m -Dspring.profiles.active=oss,local -DIDSERVER_HOME="$IDSERVER_HOME" -jar "$JAR"
