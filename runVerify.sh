#!/usr/bin/env bash
set -uo pipefail

if [[ $# -ne 1 ]]; then
    echo "usage: $(basename "$0") PROPS_FILE" >&2
    exit 2
fi

WORKSPACE=$(cd "$(dirname "$0")" && pwd)
if [[ "$1" = /* ]]; then
    PROPS_FILE=$1
else
    PROPS_FILE=$(cd "$(dirname "$1")" && pwd)/$(basename "$1")
fi

function checkError() {
  local wareNum
  local termNum
  local result
  wareNum=$(grep '^warehouses=' "$PROPS_FILE" | awk -F '=' '{print $2}')
  termNum=$(grep '^terminals=' "$PROPS_FILE" | awk -F '=' '{print $2}')
  
  if [[ ! -f "$WORKSPACE/benchmarksql-error.log" ]]; then
    echo "There is no benchmarksql-error.log."
    return 0
  else
    result=$(grep "UNEXPECTED" "$WORKSPACE/benchmarksql-error.log" || true)
    if [[ -n "$result" ]]; then
      echo "There are some unexpected error in benchmarksql-error.log."
      echo "$result"
      mv "$WORKSPACE/benchmarksql-error.log" "$WORKSPACE/benchmarksql-error-${wareNum}-${termNum}.log"
      return 1
    fi
  fi
}

if ! "$WORKSPACE/runConsistencyCheck.sh" "$PROPS_FILE"; then
  exit 1
fi

checkError
