package com.finex.benchmarks.stress;

/**
 * Manual driver for large-scale OPT-009 stress runs, following the same convention as
 * {@code com.finex.benchmarks.SustainedSharedServiceDriver}: it is not part of {@code mvn
 * test} (which uses the small/medium scenarios in {@code StressHarnessTest}) and is invoked
 * directly via {@code java -cp}.
 *
 * <p>Usage:
 * <pre>
 * mvn -q -DskipTests install
 * mvn -q -pl finex-benchmarks dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt
 * java -cp "finex-benchmarks/target/classes:$(cat /tmp/cp.txt)" \
 *   com.finex.benchmarks.stress.StressDriver [seed] [profile] [commandCount]
 * </pre>
 *
 * <p>Defaults to seed {@code 7}, profile {@code BALANCED}, {@code commandCount=100000} if no
 * arguments are given. Pass {@code ALL} as the profile to run every {@link WorkloadProfile}
 * at the given seed/count in sequence.
 */
public final class StressDriver {

    private StressDriver() {
    }

    public static void main(String[] args) {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 7L;
        String profileArg = args.length > 1 ? args[1] : "BALANCED";
        int commandCount = args.length > 2 ? Integer.parseInt(args[2]) : 100_000;

        if ("ALL".equalsIgnoreCase(profileArg)) {
            boolean allPassed = true;
            for (WorkloadProfile profile : WorkloadProfile.values()) {
                allPassed &= runOne(seed, profile, commandCount);
            }
            System.out.println(allPassed ? "ALL PROFILES PASSED" : "AT LEAST ONE PROFILE FAILED");
            if (!allPassed) {
                System.exit(1);
            }
            return;
        }

        WorkloadProfile profile = WorkloadProfile.valueOf(profileArg.toUpperCase());
        boolean passed = runOne(seed, profile, commandCount);
        if (!passed) {
            System.exit(1);
        }
    }

    private static boolean runOne(long seed, WorkloadProfile profile, int commandCount) {
        StressHarnessResult result = StressHarness.run(seed, profile, commandCount);
        System.out.println(result.describe());
        return result.passed();
    }
}
