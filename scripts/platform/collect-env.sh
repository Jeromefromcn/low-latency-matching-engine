#!/usr/bin/env bash
# Capture everything about the machine state that can affect latency results.
# Usage: collect-env.sh <output-dir>
# Run immediately before every benchmark. Safe to run without root
# (some sections are richer with sudo).
set -uo pipefail

OUT="${1:?usage: collect-env.sh <output-dir>}"
mkdir -p "$OUT"

section() {  # section <file> <command...>
  local file="$1"; shift
  {
    echo "\$ $*"
    "$@" 2>&1 || echo "(command failed or unavailable: exit $?)"
  } > "$OUT/$file"
}

section uname.txt          uname -a
section os-release.txt     cat /etc/os-release
section cmdline.txt        cat /proc/cmdline
section lscpu.txt          lscpu
section lscpu-e.txt        lscpu -e
section cpu-online.txt     cat /sys/devices/system/cpu/online
section cpu-isolated.txt   cat /sys/devices/system/cpu/isolated
section nohz-full.txt      cat /sys/devices/system/cpu/nohz_full
section smt.txt            cat /sys/devices/system/cpu/smt/active
section no-turbo.txt       cat /sys/devices/system/cpu/intel_pstate/no_turbo
section cpufreq.txt        cpupower frequency-info
section cpuidle.txt        cpupower idle-info
section meminfo.txt        cat /proc/meminfo
section hugepages.txt      bash -c 'grep -i huge /proc/meminfo; cat /sys/kernel/mm/transparent_hugepage/enabled'
section interrupts.txt     cat /proc/interrupts
section irqbalance.txt     systemctl is-active irqbalance
section sysctl.txt         sysctl -a
section java.txt           java -XshowSettings:properties -version
section bios.txt           bash -c 'cat /sys/class/dmi/id/bios_vendor /sys/class/dmi/id/bios_version /sys/class/dmi/id/bios_date /sys/class/dmi/id/product_name 2>/dev/null'
section power.txt          bash -c 'for f in /sys/class/power_supply/*/online /sys/class/power_supply/*/status; do echo "$f: $(cat "$f")"; done'
section thermal.txt        bash -c 'for z in /sys/class/thermal/thermal_zone*; do echo "$(cat "$z/type"): $(cat "$z/temp")"; done'
section top.txt            top -b -n 1
section services.txt       systemctl list-units --type=service --state=running --no-pager

{
  echo "collected_at=$(date -Iseconds)"
  echo "hostname=$(hostname)"
  if git -C "$(dirname "$0")" rev-parse HEAD >/dev/null 2>&1; then
    echo "git_commit=$(git -C "$(dirname "$0")" rev-parse HEAD)"
    echo "git_dirty=$(git -C "$(dirname "$0")" status --porcelain | wc -l)"
  fi
} > "$OUT/summary.txt"

echo "Environment captured in $OUT"
