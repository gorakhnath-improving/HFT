# Profiling

Phase 18 tooling for investigating hotspots before optimization.

## JDK Flight Recorder (JFR)

`finex-benchmarks` contains `ProfileRunner` which runs the default `LoadGenerator`
workload while capturing a JFR recording:

```bash
mvn -pl finex-benchmarks -DskipTests package exec:java \
  -Dexec.mainClass=com.finex.benchmarks.ProfileRunner \
  -Dexec.args="/tmp/finex-profile.jfr"
```

Open the resulting `.jfr` file with JDK Mission Control (JMC) or `jfr` command-line
tools:

```bash
jfr print --events "jdk.ExecutionSample" /tmp/finex-profile.jfr
```

## async-profiler

For wall-clock profiling with minimal safepoint bias, use async-profiler while the load
harness runs:

```bash
# Start the API or load generator in one terminal
mvn -pl finex-benchmarks exec:java -Dexec.mainClass=com.finex.benchmarks.ProfileRunner

# In another terminal, attach async-profiler (adjust PID)
./async-profiler/bin/asprof -d 30 -f /tmp/finex-flame.html <pid>
```

## JFR event types to watch

- `jdk.ExecutionSample` — hot methods and call stacks.
- `jdk.ObjectAllocationInNewTLAB` / `jdk.ObjectAllocationOutsideTLAB` — allocation pressure.
- `jdk.GCPhasePause` — GC pause times.
- `jdk.JavaMonitorEnter` / `jdk.ThreadPark` — contention (currently not expected since the
  baseline is single-threaded, but critical for future concurrency work).

## Next steps

Run `ProfileRunner`, open the recording in JMC, identify the top CPU consumers, then feed
those insights into Phase 19 optimizations. Record before/after numbers in
`BENCHMARKS.md` and the optimization rationale in `OPTIMIZATIONS.md`.
