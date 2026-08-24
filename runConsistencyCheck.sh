#!/usr/bin/env bash
set -uo pipefail

if [[ $# -lt 1 || $# -gt 2 ]]; then
    echo "usage: $(basename "$0") PROPS_FILE [INTERVAL_SECONDS]" >&2
    exit 2
fi

WORKSPACE=$(cd "$(dirname "$0")" && pwd)
if [[ "$1" = /* ]]; then
    PROPS_FILE=$1
else
    PROPS_FILE=$(cd "$(dirname "$1")" && pwd)/$(basename "$1")
fi
INTERVAL_SECONDS=${2:-0}

cd "$WORKSPACE" || exit 1
# shellcheck source=funcs.sh
source "$WORKSPACE/funcs.sh" "$PROPS_FILE"
setCP || exit 1

CHECKER_SOURCE="$WORKSPACE/src/main/java/io/mo/ConsistencyCheck.java"
CHECKER_CLASSES="$WORKSPACE/target/consistency-checker"
CHECKER_IDENTITY="$WORKSPACE/consistency-checker-identity.json"
mkdir -p "$WORKSPACE/target"
CHECKER_CLASSES_TMP=$(mktemp -d "$WORKSPACE/target/consistency-checker.tmp.XXXXXX") || exit 1
cleanup() {
    if [[ -n "$CHECKER_CLASSES_TMP" ]]; then
        rm -rf -- "$CHECKER_CLASSES_TMP"
    fi
}
trap cleanup EXIT

# Compile the checked-out source into a dedicated first classpath entry.  This
# prevents the committed historical JAR from shadowing a newer checker source.
if ! javac -source 8 -target 8 -cp "$myCP" -d "$CHECKER_CLASSES_TMP" "$CHECKER_SOURCE"; then
    echo "failed to compile ConsistencyCheck from checked-out source" >&2
    exit 1
fi
rm -rf -- "$CHECKER_CLASSES"
mv "$CHECKER_CLASSES_TMP" "$CHECKER_CLASSES"
CHECKER_CLASSES_TMP=""

SOURCE_REVISION=$(git -C "$WORKSPACE" rev-parse HEAD) || exit 1
SOURCE_DIGEST=$(sha256sum "$CHECKER_SOURCE" | awk '{print $1}') || exit 1
CLASS_COUNT=$(find "$CHECKER_CLASSES" -type f -name '*.class' | wc -l | awk '{print $1}') || exit 1
if [[ "$CLASS_COUNT" -eq 0 ]]; then
    echo "checker compilation produced no class files" >&2
    exit 1
fi
CLASS_TREE_DIGEST=$(
    cd "$CHECKER_CLASSES" || exit 1
    find . -type f -name '*.class' -print0 \
        | LC_ALL=C sort -z \
        | xargs -0 sha256sum \
        | sha256sum \
        | awk '{print $1}'
) || exit 1
IDENTITY_TMP="${CHECKER_IDENTITY}.tmp"
printf '{"format":"consistency-checker-identity/v1","source_revision":"%s","source_path":"src/main/java/io/mo/ConsistencyCheck.java","source_digest":"sha256:%s","deployed_artifact_path":"target/consistency-checker","deployed_class_count":%s,"deployed_artifact_digest":"sha256:%s"}\n' \
    "$SOURCE_REVISION" "$SOURCE_DIGEST" "$CLASS_COUNT" "$CLASS_TREE_DIGEST" > "$IDENTITY_TMP"
mv "$IDENTITY_TMP" "$CHECKER_IDENTITY"
echo "checker identity: $CHECKER_IDENTITY"

exec java \
    -cp "$CHECKER_CLASSES:$myCP" \
    -Dprop="$PROPS_FILE" \
    io.mo.ConsistencyCheck \
    "$INTERVAL_SECONDS"
