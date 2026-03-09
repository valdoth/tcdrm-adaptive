package org.tcdrm.adaptive.benchmark;

import org.tcdrm.adaptive.core.TcdrmConstants;

/**
 * Quick verification that simulation output matches Paper TCDRM V1 expected ranges.
 * 
 * Expected values from paper figures:
 * - NoRepLc Simple: ~200ms response time, cumul BW ~$18 at 1000q
 * - NoRepLc Complex: ~420ms response time, cumul BW ~$38 at 1000q
 * - TCDRM Simple: drops to ~100ms after P_SLA, cumul BW ~$13 at 1000q
 * - TCDRM Complex: drops to ~200ms after P_SLA, cumul BW ~$25 at 1000q
 * - Replica Factor: simple 0→6, complex 0→12 starting at query 200
 */
public class PaperAlignmentTest {

    public static void main(String[] args) {
        System.out.println("=== Paper Alignment Verification ===\n");

        // Simple queries
        NoRepBenchmarkPerQuery norepSimple = new NoRepBenchmarkPerQuery(42L, false);
        TcdrmBenchmarkPerQuery tcdrmSimple = new TcdrmBenchmarkPerQuery(42L, false);
        BenchmarkDataPerQuery norepS = norepSimple.computeBenchmark("Simple_NoRepLc");
        BenchmarkDataPerQuery tcdrmS = tcdrmSimple.computeBenchmark("Simple_TCDRM");

        // Complex queries
        NoRepBenchmarkPerQuery norepComplex = new NoRepBenchmarkPerQuery(42L, true);
        TcdrmBenchmarkPerQuery tcdrmComplex = new TcdrmBenchmarkPerQuery(42L, true);
        BenchmarkDataPerQuery norepC = norepComplex.computeBenchmark("Complex_NoRepLc");
        BenchmarkDataPerQuery tcdrmC = tcdrmComplex.computeBenchmark("Complex_TCDRM");

        int last = TcdrmConstants.MAX_QUERIES - 1;

        System.out.println("--- NoRepLc Simple ---");
        double norepSimpleAvgTime = avg(norepS.timePerQueryMs());
        double norepSimpleCumCost = norepS.cumulativeCost().get(last);
        System.out.printf("  Avg response time: %.1f ms (expected ~200ms)%n", norepSimpleAvgTime);
        System.out.printf("  Cumul cost at %d: $%.2f (expected ~$18)%n", last, norepSimpleCumCost);
        check("NoRepLc Simple time", norepSimpleAvgTime, 150, 300);
        check("NoRepLc Simple cumCost", norepSimpleCumCost, 10, 25);

        System.out.println("\n--- NoRepLc Complex ---");
        double norepComplexAvgTime = avg(norepC.timePerQueryMs());
        double norepComplexCumCost = norepC.cumulativeCost().get(last);
        System.out.printf("  Avg response time: %.1f ms (expected ~420ms)%n", norepComplexAvgTime);
        System.out.printf("  Cumul cost at %d: $%.2f (expected ~$38)%n", last, norepComplexCumCost);
        check("NoRepLc Complex time", norepComplexAvgTime, 300, 600);
        check("NoRepLc Complex cumCost", norepComplexCumCost, 25, 55);

        System.out.println("\n--- TCDRM Simple ---");
        double tcdrmSimpleEarlyTime = avg(tcdrmS.timePerQueryMs().subList(0, 200));
        double tcdrmSimpleLateTime = avg(tcdrmS.timePerQueryMs().subList(500, 1000));
        double tcdrmSimpleCumCost = tcdrmS.cumulativeCost().get(last);
        int tcdrmSimpleMaxReplicas = tcdrmS.replicaCount().get(last);
        System.out.printf("  Avg time before P_SLA (0-200): %.1f ms (expected ~200ms)%n", tcdrmSimpleEarlyTime);
        System.out.printf("  Avg time after replicas (500-1000): %.1f ms (expected ~100ms)%n", tcdrmSimpleLateTime);
        System.out.printf("  Cumul cost at %d: $%.2f (expected ~$13)%n", last, tcdrmSimpleCumCost);
        System.out.printf("  Max replicas: %d (expected 6)%n", tcdrmSimpleMaxReplicas);
        check("TCDRM Simple early time", tcdrmSimpleEarlyTime, 150, 300);
        check("TCDRM Simple late time", tcdrmSimpleLateTime, 50, 180);
        check("TCDRM Simple cumCost", tcdrmSimpleCumCost, 8, 20);
        check("TCDRM Simple maxReplicas", tcdrmSimpleMaxReplicas, 6, 6);

        System.out.println("\n--- TCDRM Complex ---");
        double tcdrmComplexEarlyTime = avg(tcdrmC.timePerQueryMs().subList(0, 200));
        double tcdrmComplexLateTime = avg(tcdrmC.timePerQueryMs().subList(500, 1000));
        double tcdrmComplexCumCost = tcdrmC.cumulativeCost().get(last);
        int tcdrmComplexMaxReplicas = tcdrmC.replicaCount().get(last);
        System.out.printf("  Avg time before P_SLA (0-200): %.1f ms (expected ~420ms)%n", tcdrmComplexEarlyTime);
        System.out.printf("  Avg time after replicas (500-1000): %.1f ms (expected ~200ms)%n", tcdrmComplexLateTime);
        System.out.printf("  Cumul cost at %d: $%.2f (expected ~$25)%n", last, tcdrmComplexCumCost);
        System.out.printf("  Max replicas: %d (expected 12)%n", tcdrmComplexMaxReplicas);
        check("TCDRM Complex early time", tcdrmComplexEarlyTime, 300, 600);
        check("TCDRM Complex late time", tcdrmComplexLateTime, 100, 350);
        check("TCDRM Complex cumCost", tcdrmComplexCumCost, 15, 40);
        check("TCDRM Complex maxReplicas", tcdrmComplexMaxReplicas, 12, 12);

        System.out.println("\n--- Replica creation timing ---");
        System.out.printf("  Simple replicas at q=199: %d (expected 0)%n", tcdrmS.replicaCount().get(199));
        System.out.printf("  Simple replicas at q=200: %d (expected 1)%n", tcdrmS.replicaCount().get(200));
        System.out.printf("  Simple replicas at q=205: %d (expected 6)%n", tcdrmS.replicaCount().get(205));
        System.out.printf("  Complex replicas at q=211: %d (expected 12)%n", tcdrmC.replicaCount().get(211));

        System.out.println("\n=== Done ===");
    }

    private static double avg(java.util.List<Double> list) {
        return list.stream().mapToDouble(d -> d).average().orElse(0);
    }

    private static void check(String name, double value, double min, double max) {
        if (value >= min && value <= max) {
            System.out.printf("  ✅ %s = %.2f (in range [%.0f, %.0f])%n", name, value, min, max);
        } else {
            System.out.printf("  ❌ %s = %.2f (OUT OF RANGE [%.0f, %.0f])%n", name, value, min, max);
        }
    }
}
