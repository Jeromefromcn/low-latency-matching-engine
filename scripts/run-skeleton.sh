#!/usr/bin/env bash
# Walking-skeleton run (ROADMAP 0.4), producing the result directory layout of
# docs/benchmark-methodology.md §7 under bench/skeleton/.
#
# Usage: [LABEL=exploratory-dev] scripts/run-skeleton.sh [key=value ...]
#   key=value arguments go to SkeletonMain (e.g. profile=dev rate=20000 durationSeconds=60).
#   Without profile=bench the label must contain "exploratory" (methodology §6).
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LABEL="${LABEL:-exploratory-dev}"
JVM_OPTS_EXTRA="${JVM_OPTS_EXTRA:--Xms1g -Xmx1g -XX:+AlwaysPreTouch}"

if [[ " $* " != *" profile=bench "* && "$LABEL" != *exploratory* ]]; then
  echo "label '$LABEL' must contain 'exploratory' unless profile=bench" >&2
  exit 1
fi

OUT="$REPO_ROOT/bench/skeleton/${LABEL}-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$OUT"

"$REPO_ROOT/gradlew" -q -p "$REPO_ROOT" :engine-app:installDist
BIN="$REPO_ROOT/engine-app/build/install/engine-app/bin/engine-app"

"$REPO_ROOT/scripts/platform/collect-env.sh" "$OUT/env" >/dev/null

export JAVA_OPTS="-Xlog:gc*:file=$OUT/gc.log $JVM_OPTS_EXTRA"
ARGS=("outputDir=$OUT" "$@")
{
  echo "# git commit"
  git -C "$REPO_ROOT" rev-parse HEAD
  echo "# uncommitted changes (empty = clean tree)"
  git -C "$REPO_ROOT" status --porcelain
  echo "# command"
  echo "JAVA_OPTS=\"$JAVA_OPTS\" $BIN ${ARGS[*]}"
} > "$OUT/command.txt"

set +e
"$BIN" "${ARGS[@]}" 2>&1 | tee "$OUT/stdout.txt"
STATUS=${PIPESTATUS[0]}
set -e

cat > "$OUT/notes.md" <<NOTES
# ${LABEL}

- Walking skeleton (ROADMAP 0.4): constant-rate loadgen -> stub engine (ack only, no book) -> loadgen.
- Arguments: ${ARGS[*]}
- Exit status: ${STATUS} (0 completed, 1 configuration error, 2 truncated by the .hlog byte cap)
- Machine: $(uname -m), $(nproc) CPUs. Label is '${LABEL}'; results from a development machine, VM or
  cloud host are exploratory and are not headline numbers (methodology §6).
- latency.hlog: values in ns, tags 'pipeline' (intended send -> ack observed) and 'engine'
  (inbound read -> ack written). *.hgrm: whole measurement window, values in µs.
NOTES

echo "result directory: $OUT (exit $STATUS)"
exit "$STATUS"
