#!/usr/bin/env bash
# Walking-skeleton run (ROADMAP 0.4). Two modes (docs/benchmark-methodology.md §7):
#
#   Day-to-day (default): output goes to build/runs/skeleton/ (git-ignored), replacing the previous
#   run. No env snapshot. Nothing accumulates and nothing is committed.
#
#   Evidence (EVIDENCE=1): output goes to a new bench/skeleton/<label>-<timestamp>/ with the full
#   result layout (env snapshot, notes), ready to commit. Requires a clean working tree so the
#   recorded commit is exactly the code that ran. Generate evidence deliberately, not on every run.
#
# Usage: [EVIDENCE=1] [LABEL=exploratory-dev] scripts/run-skeleton.sh [key=value ...]
#   key=value arguments go to SkeletonMain (e.g. profile=dev rate=20000 durationSeconds=60).
#   Evidence mode: without profile=bench the label must contain "exploratory" (methodology §6), and
#   REDACT_ENV=1 (default for exploratory labels) drops env files that describe other workloads and
#   infrastructure on a shared development host and masks its hostname; notes.md records this.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
EVIDENCE="${EVIDENCE:-0}"
LABEL="${LABEL:-exploratory-dev}"
JVM_OPTS_EXTRA="${JVM_OPTS_EXTRA:--Xms1g -Xmx1g -XX:+AlwaysPreTouch}"
REDACTED_FILES=(services.txt top.txt sysctl.txt interrupts.txt)

# Recorded before any output exists, so the run's own output is never reported as a change.
GIT_COMMIT="$(git -C "$REPO_ROOT" rev-parse HEAD)"
GIT_STATUS="$(git -C "$REPO_ROOT" status --porcelain)"

if [[ "$EVIDENCE" == 1 ]]; then
  if [[ -n "$GIT_STATUS" ]]; then
    echo "evidence runs need a clean working tree; commit or stash first:" >&2
    echo "$GIT_STATUS" >&2
    exit 1
  fi
  if [[ " $* " != *" profile=bench "* && "$LABEL" != *exploratory* ]]; then
    echo "label '$LABEL' must contain 'exploratory' unless profile=bench" >&2
    exit 1
  fi
  if [[ "$LABEL" == *exploratory* ]]; then REDACT_ENV="${REDACT_ENV:-1}"; else REDACT_ENV="${REDACT_ENV:-0}"; fi
fi

"$REPO_ROOT/gradlew" -q -p "$REPO_ROOT" :engine-app:installDist
BIN="$REPO_ROOT/engine-app/build/install/engine-app/bin/engine-app"

if [[ "$EVIDENCE" == 1 ]]; then
  # Collected outside the repository: collect-env.sh records whether the tree is dirty.
  ENV_TMP="$(mktemp -d)"
  bash "$REPO_ROOT/scripts/platform/collect-env.sh" "$ENV_TMP/env" >/dev/null
  OUT="$REPO_ROOT/bench/skeleton/${LABEL}-$(date +%Y%m%d-%H%M%S)"
  mkdir -p "$OUT"
  mv "$ENV_TMP/env" "$OUT/env" && rmdir "$ENV_TMP"
  REDACTION_NOTE="none"
  if [[ "$REDACT_ENV" == 1 ]]; then
    for f in "${REDACTED_FILES[@]}"; do rm -f "$OUT/env/$f"; done
    HOST="$(hostname)"
    grep -rlF -- "$HOST" "$OUT/env" | xargs -r sed -i "s/$HOST/<redacted-host>/g"
    REDACTION_NOTE="env/ ${REDACTED_FILES[*]} removed and hostname masked (REDACT_ENV=1, shared development host)"
  fi
else
  OUT="$REPO_ROOT/build/runs/skeleton"
  rm -rf "$OUT"
  mkdir -p "$OUT"
fi

export JAVA_OPTS="-Xlog:gc*:file=$OUT/gc.log $JVM_OPTS_EXTRA"
ARGS=("outputDir=$OUT" "$@")
{
  echo "# git commit"
  echo "$GIT_COMMIT"
  echo "# uncommitted changes (empty = clean tree)"
  echo "$GIT_STATUS"
  echo "# command"
  echo "JAVA_OPTS=\"$JAVA_OPTS\" $BIN ${ARGS[*]}"
} > "$OUT/command.txt"

set +e
"$BIN" "${ARGS[@]}" 2>&1 | tee "$OUT/stdout.txt"
STATUS=${PIPESTATUS[0]}
set -e

if [[ "$EVIDENCE" == 1 ]]; then
  cat > "$OUT/notes.md" <<NOTES
# ${LABEL}

- Walking skeleton (ROADMAP 0.4): constant-rate loadgen -> stub engine (ack only, no book) -> loadgen.
- Arguments: ${ARGS[*]}
- Exit status: ${STATUS} (0 completed, 1 configuration error, 2 truncated by the .hlog byte cap)
- Machine: $(uname -m), $(nproc) CPUs. Label is '${LABEL}'; results from a development machine, VM or
  cloud host are exploratory and are not headline numbers (methodology §6).
- latency.hlog: values in ns, tags 'pipeline' (intended send -> ack observed) and 'engine'
  (inbound read -> ack written). *.hgrm: whole measurement window, values in µs.
- Redaction: ${REDACTION_NOTE}
NOTES
fi

echo "result directory: $OUT (exit $STATUS)"
exit "$STATUS"
