#!/usr/bin/env bash
# Platform noise baseline (ROADMAP 1.3 / 1.5).
# Runs, one after another, with identical parameters every time:
#   1. hwlatdetect  — hardware/firmware latency (SMIs) the OS cannot see or control
#   2. cyclictest   — OS scheduling jitter on one isolated CPU
#   3. jHiccup      — stalls seen by an idle JVM pinned to the same CPU
#
# Usage: sudo -E ./run-baseline.sh <label>        e.g.  A-untuned   or   B-tuned
#
# Environment:
#   CPU=3                 CPU to measure on (an isolated CPU after tuning)
#   DURATION_MIN=10       minutes per tool (total ≈ 3 × DURATION_MIN)
#   JHICCUP_JAR=...       path to jHiccup.jar           (required unless SKIP_JHICCUP=1)
#   HDR_JAR=...           path to HdrHistogram-*.jar    (required unless SKIP_JHICCUP=1)
#   SKIP_HWLAT=0 SKIP_CYCLIC=0 SKIP_JHICCUP=0
#
# Output: bench/platform/<label>-<timestamp>/
# Do NOT change these parameters between the A and B runs.
set -euo pipefail

LABEL="${1:?usage: run-baseline.sh <label>}"
CPU="${CPU:-3}"
DURATION_MIN="${DURATION_MIN:-10}"
SKIP_HWLAT="${SKIP_HWLAT:-0}"
SKIP_CYCLIC="${SKIP_CYCLIC:-0}"
SKIP_JHICCUP="${SKIP_JHICCUP:-0}"

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$REPO_ROOT/bench/platform/${LABEL}-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$OUT"

[[ $EUID -eq 0 ]] || { echo "Run with sudo -E (hwlatdetect and cyclictest need root)." >&2; exit 1; }

log() { echo "[$(date +%H:%M:%S)] $*" | tee -a "$OUT/run.log"; }

"$REPO_ROOT/scripts/platform/collect-env.sh" "$OUT/env" >/dev/null
{
  echo "label=$LABEL cpu=$CPU duration_min=$DURATION_MIN"
  echo "git_commit=$(git -C "$REPO_ROOT" rev-parse HEAD 2>/dev/null || echo n/a)"
} > "$OUT/command.txt"

# 1. hwlatdetect -----------------------------------------------------------
if [[ "$SKIP_HWLAT" != "1" ]]; then
  CMD="hwlatdetect --duration=${DURATION_MIN}m --threshold=10"
  echo "$CMD" >> "$OUT/command.txt"
  log "hwlatdetect for ${DURATION_MIN} min"
  $CMD > "$OUT/hwlatdetect.txt" 2>&1 || log "hwlatdetect exited non-zero (see hwlatdetect.txt)"
fi

# 2. cyclictest ------------------------------------------------------------
if [[ "$SKIP_CYCLIC" != "1" ]]; then
  # -m lock memory, -p95 SCHED_FIFO prio 95, -t1 one thread, -a pin to CPU,
  # -i200 wake every 200 us, -h1000 histogram buckets 0..999 us (+overflow), -q summary only
  CMD="cyclictest -m -p95 -t1 -a${CPU} -i200 -D ${DURATION_MIN}m -h1000 -q"
  echo "$CMD" >> "$OUT/command.txt"
  log "cyclictest on CPU $CPU for ${DURATION_MIN} min"
  $CMD > "$OUT/cyclictest.txt" 2>&1
fi

# 3. jHiccup on an idle JVM -------------------------------------------------
if [[ "$SKIP_JHICCUP" != "1" ]]; then
  : "${JHICCUP_JAR:?set JHICCUP_JAR to the jHiccup.jar path (or SKIP_JHICCUP=1)}"
  : "${HDR_JAR:?set HDR_JAR to the HdrHistogram jar path (or SKIP_JHICCUP=1)}"
  RUN_MS=$(( DURATION_MIN * 60 * 1000 ))
  CMD="taskset -c ${CPU} java -Xlog:gc*:file=$OUT/jhiccup-gc.log -javaagent:${JHICCUP_JAR}=\"-d 0 -i 1000 -l $OUT/jhiccup.hlog\" -cp ${JHICCUP_JAR} org.jhiccup.Idle -t ${RUN_MS}"
  echo "$CMD" >> "$OUT/command.txt"
  log "jHiccup (idle JVM) on CPU $CPU for ${DURATION_MIN} min"
  taskset -c "$CPU" java -Xlog:gc*:file="$OUT/jhiccup-gc.log" \
    -javaagent:"${JHICCUP_JAR}"="-d 0 -i 1000 -l $OUT/jhiccup.hlog" \
    -cp "${JHICCUP_JAR}" org.jhiccup.Idle -t "${RUN_MS}" > "$OUT/jhiccup-stdout.txt" 2>&1

  # Full-run percentile distribution (.hgrm), values in milliseconds
  java -cp "${HDR_JAR}" org.HdrHistogram.HistogramLogProcessor \
    -i "$OUT/jhiccup.hlog" -o "$OUT/jhiccup" >> "$OUT/run.log" 2>&1 \
    || log "HistogramLogProcessor failed — check HDR_JAR"
fi

# Make results readable by the normal user who will commit them
if [[ -n "${SUDO_USER:-}" ]]; then chown -R "$SUDO_USER":"$SUDO_USER" "$OUT"; fi

log "done → $OUT"
