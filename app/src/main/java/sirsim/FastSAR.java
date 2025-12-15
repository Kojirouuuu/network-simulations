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
    private static final int PROGRESS_BAR_LENGTH = 20;
    private static final int PROGRESS_UPDATE_INTERVAL_MS = 100;
    private static final long PROGRESS_LOG_INTERVAL = 10_000;
    
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
        final long totalTasks = (long) config.itrs * alphaCount * lambdaCount;
        logger.info("Total tasks: %d", totalTasks);
        
        int parallelism = Runtime.getRuntime().availableProcessors();
        logger.info("Parallelism: %d (available processors)", parallelism);
        
        // 進捗表示の初期化
        int[] progressItr = new int[config.batchSize];
        AtomicLong done = new AtomicLong(0);
        AtomicBoolean running = new AtomicBoolean(true);
        
        // 進捗表示スレッドの開始
        Thread renderer = createProgressRenderer(progressItr, config.batchSize, config.itrs, running);
        renderer.start();
        
        // バッチ処理の実行
        try (ForkJoinPool pool = new ForkJoinPool(parallelism)) {
            Future<?> future = pool.submit(() -> 
                IntStream.range(0, config.batchSize).parallel().forEach(batchIndex -> 
                    processBatch(batchIndex, config, 
                                progressItr, done, totalTasks)
                )
            );
            
            future.get();
            running.set(false);
            renderer.join();
        }
        
        logger.info("All tasks completed");
    }
    
    /**
     * 進捗表示スレッドを作成
     */
    private static Thread createProgressRenderer(int[] progressItr, int batchSize, int itrs, AtomicBoolean running) {
        return new Thread(() -> {
            // 進捗表示用の空行を確保
            System.out.println();
            for (int i = 0; i < batchSize; i++) {
                System.out.println();
            }
            
            while (running.get()) {
                synchronized (System.out) {
                    // カーソルをbatchSize行だけ上に戻す（\033はESC）
                    System.out.print("\033[" + batchSize + "A");
                    
                    // 各バッチの進捗を表示
                    for (int b = 0; b < batchSize; b++) {
                        renderProgressBar(b, progressItr[b], itrs);
                    }
                    System.out.flush();
                }
                
                try {
                    Thread.sleep(PROGRESS_UPDATE_INTERVAL_MS);
                } catch (InterruptedException e) {
                    running.set(false);
                }
            }
        });
    }
    
    /**
     * 進捗バーを1行表示
     */
    private static void renderProgressBar(int batchIndex, int itrNow, int itrs) {
        int percent = itrNow * 100 / itrs;
        int filled = itrNow * PROGRESS_BAR_LENGTH / itrs;
        String bar = "#".repeat(filled) + " ".repeat(PROGRESS_BAR_LENGTH - filled);
        System.out.printf("batch=%02d [%s] %3d%%%n", batchIndex, bar, percent);
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
                    runSimulation(g, config, lambda, alpha, thresholdList, 
                                 batchIndex, itr, resultsPath);
                }
                
                // 進捗ログの更新
                updateProgressLog(done, totalTasks);
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
                                     double lambda, double alpha, int[] thresholdList,
                                     int batchIndex, int itr, Path resultsPath) {
        // 乱数生成器とシードの準備
        SplittableRandom rng = new SplittableRandom(
            RNG_BASE_SEED + (long) batchIndex * 10_000 + itr
        );
        int[] init = sampleUnique(rng, g.n, config.k0);
        long simSeed = SIM_BASE_SEED + (long) batchIndex * config.itrs + itr;
        
        // シミュレーション実行
        SarResult res = FastSARSimulator.simulate(
            g, lambda, config.gamma, config.tMax, thresholdList, 
            alpha, config.beta, init, simSeed
        );
        
        // CSV出力
        try {
            if (config.isFinal) {
                res.writeFinalStateCsv(resultsPath, itr, alpha, config.beta, lambda, true);
            } else {
                res.writeTimeSeriesCsv(resultsPath, itr, alpha, config.beta, lambda, true);
            }
        } catch (IOException e) {
            logger.error("CSV output error (batch %d, iteration %d, alpha %.1f, lambda %.2f): %s", 
                        batchIndex, itr, alpha, lambda, e.getMessage());
            throw new RuntimeException(e);
        }
    }
    
    /**
     * 進捗ログを更新
     */
    private static void updateProgressLog(AtomicLong done, long totalTasks) {
        long d = done.incrementAndGet();
        if (d % PROGRESS_LOG_INTERVAL == 0 || d == totalTasks) {
            double pct = 100.0 * d / totalTasks;
            logger.info("Progress: %d/%d (%.1f%%)", d, totalTasks, pct);
        }
    }
    
    /**
     * シミュレーション設定を保持する内部クラス
     */
    private static class SimulationConfig {
        final String networkType = "Config"; // "ER", "BA", "Config", "RR"
        final int N = 50_000;
        final int kAve = 10;
        final double powerLawGamma = 2.3;
        final int kMin = 5;
        final boolean isFinal = true;
        final int batchSize = 16;
        final int itrs = 50;
        final int k0 = 1;
        final double gamma = 1.0;
        final double tMax = 200.0;
        final double beta = 0.0;
        final double lambdaMin = 0.0;
        final double lambdaMax = 3.0;
        final double lambdaStep = 0.01;
        final double[] lambdaList = Array.arange(lambdaMin, lambdaMax, lambdaStep);
        final double[] alphaList = { -2.5, -2.0, -1.0, -0.5, 0.0 };
        final int threshold = 1;
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
