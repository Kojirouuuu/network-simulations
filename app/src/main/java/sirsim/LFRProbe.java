package sirsim;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple parameter sweep for LFR to see which configurations succeed or fail.
 */
public class LFRProbe {
    record Params(int N, double zeta, double gamma, double mu,
                  int sMin, int sMax, int kMin, int kMax, long seed,
                  int maxRetriesPerCommunity, int maxRestarts, int maxSwapAttemptsPerBadPair) {}

    private static String classify(Throwable t) {
        String msg = t.getMessage();
        if (msg == null) return t.getClass().getSimpleName();
        if (msg.contains("Maximum community size is too small")) return "assign-size-feasibility";
        if (msg.contains("Failed to generate simple internal edges")) return "internal-edges-graphical-or-retry";
        if (msg.contains("Failed to generate external edges")) return "external-edges";
        if (msg.contains("Odd external stub sum")) return "external-stub-odd";
        if (msg.contains("kOut < 0")) return "kout-negative";
        if (msg.contains("Stub count is odd")) return "internal-stub-odd";
        return t.getClass().getSimpleName();
    }

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("mu-sweep")) {
            muSweep();
            return;
        }
        if (args.length > 0 && args[0].equals("size-sweep")) {
            sizeSweep();
            return;
        }
        if (args.length > 0 && args[0].equals("k-sweep")) {
            kSweep();
            return;
        }
        if (args.length > 0 && args[0].equals("write-sample")) {
            writeSample();
            return;
        }
        List<Params> runs = new ArrayList<>();

        int N = 500;
        int[] sMinVals = {15, 20};
        int[] sMaxVals = {50, 80};
        double[] zetas = {2.0, 2.5};
        double[] gammas = {2.3, 2.7};
        double[] mus = {0.1, 0.3, 0.5};
        int[][] kRanges = { {2, 10}, {3, 12}, {4, 20} };
        long[] seeds = {1L, 7L, 42L};

        for (double z : zetas) {
            for (double g : gammas) {
                for (double m : mus) {
                    for (int[] kr : kRanges) {
                        for (int sMin : sMinVals) {
                            for (int sMax : sMaxVals) {
                                for (long seed : seeds) {
                                    runs.add(new Params(N, z, g, m, sMin, sMax, kr[0], kr[1], seed,
                                            2000, 2000, 2000));
                                }
                            }
                        }
                    }
                }
            }
        }

        int ok = 0;
        int fail = 0;
        int printedOk = 0;
        int printedNg = 0;
        java.util.Map<String, Integer> failKinds = new java.util.HashMap<>();
        System.out.println("# LFR parameter sweep: total=" + runs.size());
        for (Params p : runs) {
            try {
                LFR graph = LFR.generate(p.N, p.zeta, p.gamma, p.mu,
                        p.sMin, p.sMax, p.kMin, p.kMax, p.seed,
                        p.maxRetriesPerCommunity, p.maxRestarts, p.maxSwapAttemptsPerBadPair);
                ok++;
                if (printedOk < 12) {
                    System.out.printf("OK  N=%d zeta=%.1f gamma=%.1f mu=%.1f s=[%d,%d] k=[%d,%d] seed=%d M=%d%n",
                            p.N, p.zeta, p.gamma, p.mu, p.sMin, p.sMax, p.kMin, p.kMax, p.seed, graph.getM());
                    printedOk++;
                }
            } catch (Throwable t) {
                String kind = classify(t);
                fail++;
                failKinds.put(kind, failKinds.getOrDefault(kind, 0) + 1);
                if (printedNg < 12) {
                    System.out.printf("NG  N=%d zeta=%.1f gamma=%.1f mu=%.1f s=[%d,%d] k=[%d,%d] seed=%d -> %s%n",
                            p.N, p.zeta, p.gamma, p.mu, p.sMin, p.sMax, p.kMin, p.kMax, p.seed, kind);
                    printedNg++;
                }
            }
        }
        System.out.printf("# Summary: ok=%d, fail=%d\n", ok, fail);
        if (!failKinds.isEmpty()) {
            System.out.println("# Fail kinds:");
            failKinds.entrySet().stream()
                    .sorted((a,b) -> Integer.compare(b.getValue(), a.getValue()))
                    .limit(6)
                    .forEach(e -> System.out.printf("  %s: %d\n", e.getKey(), e.getValue()));
        }
    }

    private static void muSweep() {
        int N = 500;
        double zeta = 2.0;
        double gamma = 2.5;
        int sMin = 20, sMax = 60;
        int kMin = 3, kMax = 12;
        long seed = 42L;
        int retries = 3000, restarts = 3000, swaps = 3000;
        System.out.printf("# mu-sweep N=%d zeta=%.1f gamma=%.1f s=[%d,%d] k=[%d,%d] seed=%d\n",
                N, zeta, gamma, sMin, sMax, kMin, kMax, seed);
        for (double mu = 0.05; mu <= 0.60; mu += 0.05) {
            try {
                LFR g = LFR.generate(N, zeta, gamma, mu, sMin, sMax, kMin, kMax, seed, retries, restarts, swaps);
                System.out.printf("mu=%.2f -> OK M=%d\n", mu, g.getM());
            } catch (Throwable t) {
                System.out.printf("mu=%.2f -> NG %s\n", mu, classify(t));
            }
        }
    }

    private static void sizeSweep() {
        int N = 1000;
        double zeta = 2.0;
        double gamma = 2.5;
        int kMin = 3, kMax = 15;
        double mu = 0.2;
        long seed = 7L;
        int retries = 4000, restarts = 4000, swaps = 4000;
        int[] sMins = {20, 30, 40, 60};
        int[] sMaxs = {60, 80, 100, 150};
        System.out.printf("# size-sweep N=%d zeta=%.1f gamma=%.1f mu=%.2f k=[%d,%d] seed=%d\n",
                N, zeta, gamma, mu, kMin, kMax, seed);
        for (int sMin : sMins) {
            for (int sMax : sMaxs) {
                if (sMax <= sMin) continue;
                try {
                    LFR g = LFR.generate(N, zeta, gamma, mu, sMin, sMax, kMin, kMax, seed, retries, restarts, swaps);
                    System.out.printf("s=[%d,%d] -> OK M=%d\n", sMin, sMax, g.getM());
                } catch (Throwable t) {
                    System.out.printf("s=[%d,%d] -> NG %s\n", sMin, sMax, classify(t));
                }
            }
        }
    }

    private static void kSweep() {
        int N = 500;
        double zeta = 2.0;
        double gamma = 2.5;
        int sMin = 20, sMax = 60;
        double mu = 0.4; // try to see threshold effects
        long seed = 42L;
        int retries = 4000, restarts = 4000, swaps = 4000;
        System.out.printf("# k-sweep N=%d zeta=%.1f gamma=%.1f mu=%.2f s=[%d,%d] seed=%d\n",
                N, zeta, gamma, mu, sMin, sMax, seed);
        for (int kMin = 2; kMin <= 4; kMin++) {
            for (int kMax = 6; kMax <= 20; kMax += 2) {
                if (kMax <= kMin) continue;
                try {
                    LFR g = LFR.generate(N, zeta, gamma, mu, sMin, sMax, kMin, kMax, seed, retries, restarts, swaps);
                    System.out.printf("k=[%d,%d] -> OK M=%d\n", kMin, kMax, g.getM());
                } catch (Throwable t) {
                    System.out.printf("k=[%d,%d] -> NG %s\n", kMin, kMax, classify(t));
                }
            }
        }
    }

    private static void writeSample() {
        try {
            int N = 100;
            double zeta = 2.0, gamma = 2.5, mu = 0.3;
            int sMin = 10, sMax = 30, kMin = 2, kMax = 8;
            long seed = 42L;
            int retries = 4000, restarts = 4000, swaps = 4000;
            java.nio.file.Files.createDirectories(java.nio.file.Path.of("out"));
            LFR g = LFR.generate(N, zeta, gamma, mu, sMin, sMax, kMin, kMax, seed, retries, restarts, swaps);
            java.nio.file.Path out = java.nio.file.Path.of("out/lfr-sample.txt");
            g.writeEdgeList(out);
            System.out.println("# wrote: " + out.toAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
