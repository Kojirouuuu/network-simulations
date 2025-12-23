package sirsim;

import sirsim.network.Graph;
import sirsim.network.topology.RR;
import sirsim.network.topology.ER;
import sirsim.network.topology.BA;
import sirsim.network.topology.Config;
import sirsim.simulation.FastSARSimulator;
import sirsim.simulation.SarResult;
import sirsim.utils.Array;
import sirsim.utils.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.SplittableRandom;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

public class FastSAR {
    
    private static final Logger logger = new Logger(FastSAR.class);
    
    // 進捗表示設定
    private static final int PROGRESS_BAR_LENGTH = 100;
    private static final int PROGRESS_UPDATE_INTERVAL_MS = 100;
    
    // 乱数シードのベース値
    private static final long RNG_BASE_SEED = 7L;
    private static final long SIM_BASE_SEED = 12345L;
    private static final long GRAPH_BASE_SEED = 42L;
    
    public static void main(String[] args) throws Exception {
        // シミュレーション設定
        SimulationConfig config = new SimulationConfig();
        
        // タスク数計算
        final int lambdaCount = config.lambdaList.length;
        final int alphaCount = config.alphaList.length;
        final int rho0Count = config.rho0List.length;
        final long totalTasks = (long) config.batchSize * config.itrs * alphaCount * lambdaCount * rho0Count;
        logger.info("Total tasks: %d", totalTasks);
        logger.info("%s: N=%d, itrs=%d", config.networkType, config.N, config.itrs);
        
        int parallelism = Runtime.getRuntime().availableProcessors();
        logger.info("Parallelism: %d (available processors)", parallelism);
        
        // 進捗表示の初期化
        int[] progressItr = new int[config.batchSize];
        AtomicLong done = new AtomicLong(0);
        AtomicBoolean running = new AtomicBoolean(true);

        // 進捗表示スレッド開始（全体1行）
        Thread renderer = createTotalProgressRenderer(done, totalTasks, running);
        renderer.start();

        try (ForkJoinPool pool = new ForkJoinPool(parallelism)) {
            Future<?> future = pool.submit(() ->
                IntStream.range(0, config.batchSize).parallel().forEach(batchIndex ->
                    processBatch(batchIndex, config, progressItr, done, totalTasks)
                )
            );

            future.get();
        } finally {
            running.set(false);
            renderer.join();
        }

        logger.info("All tasks completed");

    }
    
    /**
     * 1つのバッチを処理
     */
    private static void processBatch(int batchIndex, SimulationConfig config,
                                    int[] progressItr, AtomicLong done, long totalTasks) {
        // グラフ生成
        Graph g = switch (config.networkType) {
            case "RR" -> RR.generateRR(config.N, config.kAve, GRAPH_BASE_SEED + batchIndex);
            case "ER" -> ER.generateERFromKAve(config.N, config.kAve, GRAPH_BASE_SEED + batchIndex);
            case "BA" -> BA.generateBA(config.N, config.kAve / 2, config.kAve / 2, GRAPH_BASE_SEED + batchIndex);
            case "Config" -> Config.generatePowerLawConfig(config.N, config.powerLawGamma, config.kMin, GRAPH_BASE_SEED + batchIndex);
            default -> throw new IllegalArgumentException("Unknown network type: " + config.networkType);
        };
        
        // 出力パスの準備
        Path resultsPath = prepareOutputPath(g, batchIndex, config);
        
        final int lambdaCount = config.lambdaList.length;
        final int alphaCount = config.alphaList.length;
        
        // 各反復を処理
        for (int itr = 0; itr < config.itrs; itr++) {
            progressItr[batchIndex] = itr;
            
            for (int ri = 0; ri < config.rho0List.length; ri++) {
                double rho0 = config.rho0List[ri];
                
                for (int ai = 0; ai < alphaCount; ai++) {
                    double alpha = config.alphaList[ai];
                    
                    for (int li = 0; li < lambdaCount; li++) {
                        double lambda = config.lambdaList[li];

                        // パラメータ設定
                        int[] thresholdList = new int[config.N];
                        Arrays.fill(thresholdList, config.threshold);
                        int numActivist = (int) (config.N * config.p);
                        for (int i = 0; i < numActivist; i++) {
                            thresholdList[i] = 1;
                        }
                        thresholdList = Array.shuffle(thresholdList, 
                            RNG_BASE_SEED + (long) batchIndex * 1_000 + itr);
                        
                        // シミュレーション実行
                        runSimulation(g, config, lambda, alpha, rho0, thresholdList, 
                                    batchIndex, itr, resultsPath);

                        done.incrementAndGet();
                    }
                }
            }
        }
        
        // ループ完了後に100%を表示
        progressItr[batchIndex] = config.itrs;
    }
    
    /**
     * 出力パスを準備
     */
    private static Path prepareOutputPath(Graph g, int batchIndex, SimulationConfig config) {
        String idx = String.format("%02d", batchIndex);
        String networkPath = g.name;
        
        if ("Config".equals(networkPath)) {
            networkPath = String.format("config/gamma=%.2f/kmin=%d", 
                                       config.powerLawGamma, config.kMin);
        }
        
        Path basePath = Paths.get(String.format("out/fastsar/%s/threshold=%d/p=%.2f/N=%d", 
                                                networkPath, config.threshold, config.p, config.N));
        return sirsim.utils.PathsEx.resolveIndexed(
            basePath.resolve(String.format("results_%s.csv", idx))
        );
    }
    
    /**
     * 1回のシミュレーションを実行
     */
    private static void runSimulation(Graph g, SimulationConfig config, 
                                     double lambda, double alpha, double rho0, int[] thresholdList,
                                     int batchIndex, int itr, Path resultsPath) {
        // 乱数生成器とシードの準備
        SplittableRandom rng = new SplittableRandom(
            RNG_BASE_SEED + (long) batchIndex * 10_000 + itr
        );

        int initialInfectedNum = (int) (g.n * rho0);
        
        int[] init = sampleUnique(rng, g.n, initialInfectedNum);
        long simSeed = SIM_BASE_SEED + (long) batchIndex * config.itrs + itr;
        
        // シミュレーション実行
        SarResult res = FastSARSimulator.simulate(
            g, lambda, config.mu, config.tMax, thresholdList, 
            alpha, config.beta, init, simSeed
        );
        
        // CSV出力
        try {
            if (config.isFinal) {
                res.writeFinalStateCsv(resultsPath, itr, alpha, config.beta, lambda, rho0, true);
            } else {
                res.writeTimeSeriesCsv(resultsPath, itr, alpha, config.beta, lambda, rho0, true);
            }
        } catch (IOException e) {
            logger.error("CSV output error (batch %d, iteration %d, alpha %.1f, lambda %.2f, rho0 %.2f): %s", 
                        batchIndex, itr, alpha, lambda, rho0, e.getMessage());
            throw new RuntimeException(e);
        }
    }
    
    private static Thread createTotalProgressRenderer(AtomicLong done, long totalTasks, AtomicBoolean running) {
        return new Thread(() -> {
            long lastPrintedDone = 0;

            while (running.get()) {
                long d = done.get();
                if (d != lastPrintedDone) {
                    synchronized (System.out) {
                        renderTotalProgressBar(d, totalTasks);
                    }
                    lastPrintedDone = d;
                }

                if (d >= totalTasks) break;

                try {
                    Thread.sleep(PROGRESS_UPDATE_INTERVAL_MS);
                } catch (InterruptedException e) {
                    running.set(false);
                    break;
                }
            }

            synchronized (System.out) {
                renderTotalProgressBar(totalTasks, totalTasks);
                System.out.println();
            }
        }, "progress-renderer");
    }

    private static void renderTotalProgressBar(long done, long total) {
        int filled = (total == 0) ? PROGRESS_BAR_LENGTH
                : (int) Math.min(PROGRESS_BAR_LENGTH, (done * PROGRESS_BAR_LENGTH) / total);
    
        int percent = (total == 0) ? 100 : (int) Math.min(100, (done * 100) / total);
    
        String bar = "#".repeat(filled) + "-".repeat(PROGRESS_BAR_LENGTH - filled);
    
        // \r で同じ行を上書き
        System.out.printf("\rProgress [%s] %3d%% (%d/%d)", bar, percent, done, total);
        System.out.flush();
    }
    
    /**
     * シミュレーション設定を保持する内部クラス
     */
    private static class SimulationConfig {
        final String networkType = "ER"; // "ER", "BA", "Config", "RR"
        final int N = 100_000;
        final int kAve = 10;
        final double powerLawGamma = 2.4;
        final int kMin = 5;
        final boolean isFinal = true;
        final int batchSize = 16;
        final int itrs = 20;
        final double mu = 1.0;
        final double tMax = 200.0;
        final double beta = 0.0;
        final double lambdaMin = 0.0;
        final double lambdaMax = 2.0;
        final double lambdaStep = 0.02;
        final double[] lambdaList = Array.arange(lambdaMin, lambdaMax, lambdaStep);
        final double rho0Min = 0;
        final double rho0Max = 0.2;
        final double rho0Step = 0.002;
        final double[] rho0List = Array.arange(rho0Min, rho0Max, rho0Step);
        // final double[] rho0List = { 0.05, 0.1 };
        final double[] alphaList = { 0.0 };
        final int threshold = 3;
        final double p = 0.0; // fraction of activists
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
