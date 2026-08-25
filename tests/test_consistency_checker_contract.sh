#!/usr/bin/env bash
set -euo pipefail

SOURCE_WORKSPACE=$(cd "$(dirname "$0")/.." && pwd)
TEST_ROOT=$(mktemp -d)
cleanup() {
    rm -rf -- "$TEST_ROOT"
}
trap cleanup EXIT

# Run the production entrypoint in an isolated copy so the contract test never
# consumes or deletes artifacts belonging to a developer's checkout.
TEST_WORKSPACE="$TEST_ROOT/mo-tpcc"
cp -a "$SOURCE_WORKSPACE" "$TEST_WORKSPACE"

cat > "$TEST_ROOT/checker.properties" <<'EOF'
db=mo
driver=does.not.Exist
conn=jdbc:invalid
user=tester
password=not-printed
EOF

set +e
OUTPUT=$(cd "$TEST_ROOT" && "$TEST_WORKSPACE/runConsistencyCheck.sh" "$TEST_ROOT/checker.properties" 2>&1)
STATUS=$?
set -e
if [[ "$STATUS" -eq 0 ]]; then
    echo "checker unexpectedly returned success" >&2
    exit 1
fi
if [[ "$OUTPUT" == *"not-printed"* || "$OUTPUT" == *"jdbc:invalid"* ]]; then
    echo "checker leaked connection credentials" >&2
    exit 1
fi

IDENTITY="$TEST_WORKSPACE/consistency-checker-identity.json"
python3 -m json.tool "$IDENTITY" >/dev/null

SOURCE_DIGEST=$(sha256sum "$TEST_WORKSPACE/src/main/java/io/mo/ConsistencyCheck.java" | awk '{print $1}')
CLASS_COUNT=$(find "$TEST_WORKSPACE/target/consistency-checker" -type f -name '*.class' | wc -l | awk '{print $1}')
CLASS_TREE_DIGEST=$(
    cd "$TEST_WORKSPACE/target/consistency-checker"
    find . -type f -name '*.class' -print0 \
        | LC_ALL=C sort -z \
        | xargs -0 sha256sum \
        | sha256sum \
        | awk '{print $1}'
)

python3 - "$IDENTITY" "$SOURCE_DIGEST" "$CLASS_COUNT" "$CLASS_TREE_DIGEST" <<'PY'
import json
import sys

identity_path, source_digest, class_count, class_tree_digest = sys.argv[1:]
with open(identity_path, "r", encoding="utf-8") as handle:
    identity = json.load(handle)

assert identity == {
    "format": "consistency-checker-identity/v1",
    "source_revision": identity["source_revision"],
    "source_path": "src/main/java/io/mo/ConsistencyCheck.java",
    "source_digest": "sha256:" + source_digest,
    "deployed_artifact_path": "target/consistency-checker",
    "deployed_class_count": int(class_count),
    "deployed_artifact_digest": "sha256:" + class_tree_digest,
}
assert len(identity["source_revision"]) == 40
assert all(character in "0123456789abcdef" for character in identity["source_revision"])
PY
