#!/bin/sh
# Start one public-demo process against a local SQLite file.
# Usage: ./demo/run.sh [sts|admin|admin-api]
# JDK 21 is required. The DEMO user is created on first STS (or Admin) start.
set -eu

ROOT=$(CDPATH= cd "$(dirname "$0")/.." && pwd)
ROLE=${1:-sts}

case "$ROLE" in
  sts) MODULE=skoruba4j-sts; PORT=5050 ;;
  admin) MODULE=skoruba4j-admin; PORT=6060 ;;
  admin-api) MODULE=skoruba4j-admin-api; PORT=44302 ;;
  *)
    echo "Usage: $0 [sts|admin|admin-api]" >&2
    exit 1
    ;;
esac

if ! command -v java >/dev/null 2>&1; then
  echo "JDK 21 is required. java was not found on PATH." >&2
  exit 1
fi
MAJOR=$(java -version 2>&1 | sed -n 's/.* version "\([0-9][0-9]*\).*/\1/p' | head -n 1)
if [ "$MAJOR" != "21" ]; then
  echo "JDK 21 is required. This java reports major version ${MAJOR:-unknown}." >&2
  exit 1
fi

if [ -z "${IDSERVER_HOME:-}" ]; then
  IDSERVER_HOME=$ROOT/demo/runtime
fi
export IDSERVER_HOME
mkdir -p "$IDSERVER_HOME/data" "$IDSERVER_HOME/secrets" "$IDSERVER_HOME/logs"

cd "$ROOT"
# com.myano:jsqlite is a public 0.1.0-SNAPSHOT, not a Maven Central release.
JSQLITE_REV=90f7ede35801a60cd3ea9e206a03d5cf68b35c14
JSQLITE_JAR="${HOME}/.m2/repository/com/myano/jsqlite/0.1.0-SNAPSHOT/jsqlite-0.1.0-SNAPSHOT.jar"
if [ ! -f "$JSQLITE_JAR" ]; then
  if [ -f "$ROOT/../JSQLite/pom.xml" ]; then
    JSQLITE_SRC="$ROOT/../JSQLite"
  elif [ -f "$ROOT/../jsqlite/pom.xml" ]; then
    JSQLITE_SRC="$ROOT/../jsqlite"
  else
    JSQLITE_SRC="$IDSERVER_HOME/jsqlite-src"
    if [ ! -f "$JSQLITE_SRC/pom.xml" ]; then
      echo "Cloning public JSQLite $JSQLITE_REV (SQLite driver, no secrets)."
      rm -rf "$JSQLITE_SRC"
      git init "$JSQLITE_SRC"
      git -C "$JSQLITE_SRC" remote add origin https://github.com/Myanowu/jsqlite.git
      git -C "$JSQLITE_SRC" fetch --depth 1 origin "$JSQLITE_REV"
      git -C "$JSQLITE_SRC" checkout --detach FETCH_HEAD
    fi
  fi
  echo "Installing com.myano:jsqlite from $JSQLITE_SRC"
  ./mvnw -f "$JSQLITE_SRC/pom.xml" -B -DskipTests install
fi
./mvnw -pl "$MODULE" -am -DskipTests package

JAR=
for f in "$ROOT/$MODULE/target/$MODULE-"*.jar; do
  if [ ! -f "$f" ]; then
    continue
  fi
  case "$f" in
    *.original) continue ;;
  esac
  JAR=$f
done
if [ -z "$JAR" ]; then
  echo "Packaged jar not found under $ROOT/$MODULE/target" >&2
  exit 1
fi

echo "IDSERVER_HOME=$IDSERVER_HOME"
echo "Starting $ROLE from $JAR"
echo "When the process is up: http://127.0.0.1:$PORT/health"
exec java \
  -Xmx512m \
  -Dspring.profiles.active=oss \
  -Dserver.ssl.enabled=false \
  -DIDSERVER_HOME="$IDSERVER_HOME" \
  -Dspring.config.additional-location="file:$ROOT/demo/idserver-demo.yml" \
  -jar "$JAR"
