# exploratory-dev

- Walking skeleton (ROADMAP 0.4): constant-rate loadgen -> stub engine (ack only, no book) -> loadgen.
- Arguments: outputDir=/home/ubuntu/jerome/low-latency-matching-engine/bench/skeleton/exploratory-dev-20261023-110453 profile=dev
- Exit status: 0 (0 completed, 1 configuration error, 2 truncated by the .hlog byte cap)
- Machine: aarch64, 4 CPUs. Label is 'exploratory-dev'; results from a development machine, VM or
  cloud host are exploratory and are not headline numbers (methodology §6).
- latency.hlog: values in ns, tags 'pipeline' (intended send -> ack observed) and 'engine'
  (inbound read -> ack written). *.hgrm: whole measurement window, values in µs.
- Redaction: env/ services.txt top.txt sysctl.txt interrupts.txt removed and hostname masked (REDACT_ENV=1, shared development host)
