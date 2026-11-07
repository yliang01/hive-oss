#!/bin/sh
set -eu
cd "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
command -v java >/dev/null 2>&1 || { echo 'ERROR: Java 21 is required on PATH.' >&2; exit 1; }
exec java -jar hive-oss.jar "$@"
