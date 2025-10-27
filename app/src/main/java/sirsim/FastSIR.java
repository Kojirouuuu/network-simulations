package sirsim;

import sirsim.network.Graph;
import sirsim.network.topology.ER;
import sirsim.simulation.FastSIRSimulator;
import sirsim.simulation.SirResult;
import sirsim.utils.Array;
import sirsim.utils.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.SplittableRandom;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

public class FastSIR {
    
    // ロガーインスタンス
    private static final Logger logger = new Logger(FastSIR.class);
    
    public static void main(String[] args) throws Exception {
        // 例: 無向ERネットワーク（CSR）
        int N = 100_000;
        int kAve = 25;
        // double p = (double)kAve / (N - 1);

        // 書き出し設定
        boolean isFinal = true;
        int batchSize = 12;
        int iters = 10;

        // 初期感染者（ランダムに1人）
        int k0 = 1;

        double gamma = 1.0;       // recovery rate
        double tMax = 200.0;      // 打ち切り時刻
        double beta = 0.0;        // degree exponent for transmission rate

        double lambdaMin = 0.0;
        double lambdaMax = 1.5;
        double lambdaStep = 0.01;
        double[] lambdaList = Array.arange(lambdaMin, lambdaMax, lambdaStep);

        double[] alphaList = { -2.0, -1.0, 0.0, 1.0 };

        // CSV 出力先（既存ファイルがあればインデックスを付与して新規作成）
        

        // 進捗表示用カウント
        final int lambdaCount = lambdaList.length;
        final int alphaCount = alphaList.length;
        final long totalTasks = (long) iters * alphaCount * lambdaCount;
        logger.info("Total tasks: %d", totalTasks);
        AtomicLong done = new AtomicLong(0);

        int parallelism = Runtime.getRuntime().availableProcessors();
        logger.info("Parallelism: %d (available processors)", parallelism);
        
        try (ForkJoinPool pool = new ForkJoinPool(parallelism)) {
            Future<?> future = pool.submit(() -> IntStream.range(0, batchSize).parallel().forEach(batchIndex -> {
                Graph g = ER.generateERFromKAve(N, kAve, 42L + batchIndex);
                String idx = String.format("%02d", batchIndex);
                Path basePath = Paths.get(String.format("out/fastsir/%d", N));
                Path resultsPath = sirsim.utils.PathsEx.resolveIndexed(basePath.resolve(String.format("results_%s.csv", idx)));

                logger.info("Batch %d started", batchIndex);
                for (int itr = 0; itr < iters; itr++) {
                    for (int ai = 0; ai < alphaCount; ai++) {
                        double alpha = alphaList[ai];
                        for (int li = 0; li < lambdaCount; li++) {
                            double lambda = lambdaList[li];

                            // 反復ごとに初期条件と乱数シードを変更
                            SplittableRandom rng = new SplittableRandom(7L + (long) batchIndex * 10_000 + itr);
                            int[] init = sampleUnique(rng, g.n, k0);
                            long simSeed = 12345L + batchIndex * iters + itr;

                            SirResult res = FastSIRSimulator.simulate(g, lambda, gamma, tMax, alpha, beta, init, simSeed);

                            // CSV 出力（パラメータ含む：itr,alpha,beta,lambda,time,I,R）
                            try {
                                if (isFinal) res.writeFinalStateCsv(resultsPath, itr, alpha, beta, lambda, true);
                                else res.writeTimeSeriesCsv(resultsPath, itr, alpha, beta, lambda, true);
                            } catch (IOException e) {
                                logger.error("CSV output error (batch %d, iteration %d, alpha %.1f, lambda %.2f): %s", 
                                    batchIndex, itr, alpha, lambda, e.getMessage());
                                throw new RuntimeException(e);
                            }
                        }
                        
                        long d = done.incrementAndGet();
                        if (d % 10_000 == 0 || d == totalTasks) {
                            double pct = 100.0 * d / totalTasks;
                            logger.info("Progress: %d/%d (%.1f%%)", d, totalTasks, pct);
                        }
                    }
                }
            }));

            future.get();
        }
        logger.info("All tasks completed");
    }

    private static int[] sampleUnique(SplittableRandom rng, int n, int k) {
        if (k > n) {
            logger.error("sampleUnique: k(%d) > n(%d)", k, n);
            throw new IllegalArgumentException("k>n");
        }
        logger.debug("sampleUnique: n=%d, k=%d", n, k);
        boolean[] used = new boolean[n];
        int[] r = new int[k];
        for (int c = 0; c < k; ) {
            int u = rng.nextInt(n);
            if (!used[u]) { used[u] = true; r[c++] = u; }
        }
        return r;
    }
}
