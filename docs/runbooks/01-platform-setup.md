# Runbook 01 — Benchmark machine setup and platform baseline

Covers ROADMAP Phase 1. Machine: Redmi G (2020), i5-10300H (4C/8T, 2.5 GHz base), 16 GB, internal NVMe.

Goal: a committed, reproducible **A (untuned) vs B (tuned)** comparison of platform noise. This tells us the noise floor every later engine number sits on.

Time needed: about half a day, most of it waiting for runs.

---

## Step 1 — Hardware check (before wiping Windows)

1. Back up anything you need from Windows.
2. Power off, unplug the charger, open the bottom cover.
3. **Disconnect the battery connector from the motherboard first**, before touching anything else.
4. Inspect the battery. If it is swollen, do not press or pry it with metal tools; remove it and take it to a rechargeable-battery recycling point. A swollen battery stays out for good — the machine runs on AC only.
5. Clean dust from fans and heatsink; replace thermal paste.
6. If the battery is removed, confirm the machine boots on AC alone. Throttling without a battery is checked in Step 4.

## Step 2 — Install the OS

1. Install **Ubuntu Server 24.04 LTS** on the internal NVMe (the journal will live on this disk; do not use USB storage).
   - Minimal install, OpenSSH server enabled, no desktop, no proprietary GPU driver.
2. Connect via **Ethernet**; from then on work over SSH from another machine.
3. Packages:

   ```bash
   sudo apt update && sudo apt full-upgrade -y
   sudo apt install -y git build-essential rt-tests stress-ng \
        linux-tools-common linux-tools-$(uname -r) python3-matplotlib
   ```
4. JDK 25: install Eclipse Temurin 25 (via the Adoptium apt repository or SDKMAN). Verify `java -version`.
5. Measurement tools (put them in `~/tools/`):
   - **jHiccup** — download the release jar from the project's GitHub releases (`giltene/jHiccup`).
   - **HdrHistogram** — download the jar from Maven Central (`org.hdrhistogram:HdrHistogram`).

   ```bash
   export JHICCUP_JAR=~/tools/jHiccup.jar
   export HDR_JAR=~/tools/HdrHistogram.jar
   ```
6. Clone this repository and commit the environment snapshot:

   ```bash
   ./scripts/platform/collect-env.sh bench/platform/env-fresh-install
   ```

> **First-run check:** `run-baseline.sh` assumes jHiccup provides `org.jhiccup.Idle` (an idle workload with `-t <ms>`) and HdrHistogram provides `org.HdrHistogram.HistogramLogProcessor`. If either class name differs in the version you downloaded, fix the script before recording baseline A, then never change it between A and B.

## Step 3 — Thermal check, untuned

```bash
sudo ./scripts/platform/thermal-check.sh 30
```

Record whether `busy_mhz_min` drops during the run. At this stage turbo is still on, so frequency varies — what matters is whether temperature caps it hard (sustained drop well below base clock = cooling problem; fix before going further).

## Step 4 — Baseline A (untuned)

Do not change anything in the OS yet.

```bash
sudo -E ./scripts/platform/run-baseline.sh A-untuned
```

Defaults: CPU 3, 10 minutes per tool (≈ 30 min total). Keep the machine otherwise idle: no SSH sessions doing work, no other processes.

Commit `bench/platform/A-untuned-*/`.

## Step 5 — Apply tuning

```bash
sudo ./scripts/platform/setup-tuning.sh apply
sudo reboot
# after reboot
sudo ./scripts/platform/setup-tuning.sh status
```

Verify in the status output:

- `/proc/cmdline` contains `nosmt isolcpus=1-3 nohz_full=1-3 rcu_nocbs=1-3 ...`
- online CPUs: `0-3`; isolated: `1-3`
- turbo disabled = `1`; all cores at ~2500 MHz
- runtime service `active`; noisy services `inactive`
- no IRQs listed outside CPU 0 (a few non-movable per-CPU interrupts are normal — note them)

Optional, only if SSH is over Ethernet: `sudo BLOCK_WIRELESS=1 ./scripts/platform/setup-tuning.sh apply` to turn off Wi-Fi/Bluetooth radios.

Then repeat the thermal check — now `busy_mhz_min` must equal the locked 2500 MHz for the full 30 minutes (ROADMAP 1.1 acceptance).

## Step 6 — Baseline B (tuned)

Exactly the same command and parameters as Step 4:

```bash
sudo -E ./scripts/platform/run-baseline.sh B-tuned
```

Repeat A and B at least 3 times each over time if you can (see methodology §8). For A runs after tuning, use `setup-tuning.sh revert` + reboot, then re-apply.

## Step 7 — Compare and write up

```bash
python3 scripts/platform/plot_baseline.py \
    bench/platform/A-untuned-<ts> bench/platform/B-tuned-<ts> \
    --out bench/platform
```

This writes `bench/platform/comparison.md` and `comparison.png`. Use them to write `bench/platform/README.md` (ROADMAP 1.6):

- the A vs B table (p99, p99.9, max)
- the plot
- **hwlatdetect findings stated honestly**: if SMIs appear, they are the hardware noise floor this laptop cannot remove, and every later max-latency number inherits it
- what each tuning step is expected to remove and whether the data agrees

## Reverting

```bash
sudo ./scripts/platform/setup-tuning.sh revert && sudo reboot
```

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| `cpupower: command not found` | `linux-tools-$(uname -r)` not installed (re-install after kernel upgrades) |
| Frequency not locked after reboot | `ll-runtime-tuning.service` failed: `journalctl -u ll-runtime-tuning` |
| cyclictest max in the hundreds of µs even after tuning | check hwlatdetect (SMIs), a remaining periodic service, or thermal throttling |
| jHiccup step fails immediately | class name / jar path — see the first-run check in Step 2 |
| Machine shuts down under stress without battery | firmware power limits on AC-only; check `turbostat` PkgWatt and consider keeping a healthy battery |
