#!/usr/bin/env bash
# Thermal / frequency stability check (ROADMAP 1.1).
# Loads all CPUs with stress-ng and logs package temperature and busy frequency with turbostat.
#
# Usage: sudo ./thermal-check.sh [minutes]      (default 30)
# Output: bench/platform/thermal-<timestamp>/
#
# Pass criterion: Bzy_MHz stays at the locked frequency for the whole run
# (after tuning) and never drops because of temperature.
set -euo pipefail

MINUTES="${1:-30}"
REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$REPO_ROOT/bench/platform/thermal-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$OUT"

[[ $EUID -eq 0 ]] || { echo "Run with sudo (turbostat needs root)." >&2; exit 1; }
for t in stress-ng turbostat; do command -v "$t" >/dev/null || { echo "missing: $t" >&2; exit 1; }; done

"$REPO_ROOT/scripts/platform/collect-env.sh" "$OUT/env" >/dev/null
echo "stress-ng --cpu 0 --timeout ${MINUTES}m ; turbostat --quiet --Summary --show Busy%,Bzy_MHz,PkgTmp,PkgWatt --interval 5" > "$OUT/command.txt"

turbostat --quiet --Summary --show Busy%,Bzy_MHz,PkgTmp,PkgWatt --interval 5 > "$OUT/turbostat.txt" 2>&1 &
TS_PID=$!
trap 'kill $TS_PID 2>/dev/null || true' EXIT

echo "Running stress-ng on all CPUs for ${MINUTES} min..."
stress-ng --cpu 0 --timeout "${MINUTES}m" --metrics-brief > "$OUT/stress-ng.txt" 2>&1
kill $TS_PID 2>/dev/null || true
wait $TS_PID 2>/dev/null || true

# Summarise: skip header lines, columns are Busy% Bzy_MHz PkgTmp PkgWatt
awk '$1 ~ /^[0-9.]+$/ {
       n++; if (min=="" || $2<min) min=$2; if ($2>max) max=$2;
       if ($3>tmax) tmax=$3; sum+=$2
     }
     END {
       if (n==0) { print "no samples parsed — inspect turbostat.txt"; exit }
       printf "samples=%d  busy_mhz_min=%d  busy_mhz_max=%d  busy_mhz_avg=%.0f  pkg_temp_max=%dC\n", n, min, max, sum/n, tmax
     }' "$OUT/turbostat.txt" | tee "$OUT/summary.txt"

if [[ -n "${SUDO_USER:-}" ]]; then chown -R "$SUDO_USER":"$SUDO_USER" "$OUT"; fi
echo "Results: $OUT"
