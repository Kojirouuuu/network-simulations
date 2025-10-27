package sirsim.percolation;

import sirsim.network.Graph;
import sirsim.network.topology.ER;
import sirsim.utils.Logger;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.SplittableRandom;

/**
 * K-core percolation simulation: random node removal (site percolation)
 * followed by k-core pruning. Sweeps occupancy probability p and averages over trials.
 */
public final class KCorePercolation {
    private static final Logger log = new Logger(KCorePercolation.class);

    public record ResultRow(double p, double meanFrac, double stdFrac, double meanSize, double stdSize) {}

    /**
     * Run a sweep over p in [pMin, pMax] with given steps (inclusive),
     * generating an ER(N, z) graph for each trial unless a graph is supplied.
     */
    public static void sweepER_Z(int n, double z, int k, double pMin, double pMax, int steps, int trials, long seed, Path outCsv) throws IOException {
        double dp = steps <= 1 ? 0.0 : (pMax - pMin) / (steps - 1);
        if (outCsv != null) Files.createDirectories(outCsv.getParent());
        try (BufferedWriter bw = outCsv == null ? null : Files.newBufferedWriter(outCsv)) {
            if (bw != null) bw.write("p,frac_kcore,frac_std,size_mean,size_std\n");
            for (int i = 0; i < steps; i++) {
                double p = pMin + dp * i;
                double[] vals = runManyER_Z(n, z, k, p, trials, seed + i * 1337L);
                // vals: [meanFrac, stdFrac, meanSize, stdSize]
                if (bw != null) bw.write(String.format(Locale.US, "%.8f,%.8f,%.8f,%.3f,%.3f\n", p, vals[0], vals[1], vals[2], vals[3]));
                log.info("p=%.4f -> k-core frac=%.4f ± %.4f", p, vals[0], vals[1]);
            }
        }
    }

    /**
     * Run many trials on ER(N, z) for a fixed node-occupancy probability p.
     * Returns [meanFrac, stdFrac, meanSize, stdSize].
     */
    public static double[] runManyER_Z(int n, double z, int k, double pOcc, int trials, long seed) {
        double[] frac = new double[trials];
        double[] size = new double[trials];
        SplittableRandom master = new SplittableRandom(seed);
        for (int t = 0; t < trials; t++) {
            long s = master.split().nextLong();
            Graph g = ER.generateERFromP(n, Math.max(0.0, Math.min(1.0, z / Math.max(1, n - 1))), s);
            int kc = runOnce(g, k, pOcc, master.split());
            frac[t] = kc / (double) n;
            size[t] = kc;
        }
        return new double[]{ mean(frac), std(frac), mean(size), std(size) };
    }

    /**
     * One realization: apply site percolation with occupancy probability pOcc,
     * then compute the size of the resulting k-core.
     */
    public static int runOnce(Graph g, int k, double pOcc, SplittableRandom rng) {
        final int n = g.n;
        boolean[] alive = new boolean[n];
        for (int u = 0; u < n; u++) alive[u] = rng.nextDouble() < pOcc;
        return KCore.size(g, alive, k);
    }

    private static double mean(double[] a) {
        double s = 0; for (double v : a) s += v; return s / Math.max(1, a.length);
    }
    private static double std(double[] a) {
        if (a.length <= 1) return 0.0;
        double m = mean(a);
        double s2 = 0; for (double v : a) { double d = v - m; s2 += d * d; }
        return Math.sqrt(s2 / (a.length - 1));
    }
}

