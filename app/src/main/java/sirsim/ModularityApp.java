package sirsim;

import sirsim.network.Graph;
import sirsim.network.topology.BA;
import sirsim.network.topology.RR;
import sirsim.network.metrics.GreedyModularityOptimizerWithBetweenness;
import sirsim.network.metrics.Betweenness.SsspResult;
import sirsim.network.metrics.Betweenness;
import sirsim.utils.Array;
import sirsim.utils.PathsEx;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.io.BufferedWriter;
import java.io.PrintWriter;
import java.io.IOException;
import java.util.Locale;
import sirsim.utils.PrintProgressBar;

public class ModularityApp {
    public static void main(String[] args) throws IOException {
        Long seed = 12345L;
        // Graph g = BA.generateBA(100, 2, 2, seed);
        // Graph g = Graph.fromFile(Paths.get("app/src/main/java/sirsim/network/files/exampleNetworkN24.txt"));
        // Graph g = Graph.fromFile(Paths.get("app/src/main/java/sirsim/network/files/karate_club.txt"));
        Graph g = Graph.fromCsvFile(Paths.get("app/src/main/java/sirsim/network/files/S1.csv/edges.csv"));
        System.out.println(g.name);

        double alphaMin = 0.0;
        double alphaMax = 0.5;
        double alphaStep = 0.05;
        // double[] alphaList = Array.arange(alphaMin, alphaMax, alphaStep);
        double[] alphaList = {0.0, 0.05, 0.1, 0.5, 1.0, 1.5, 2.0};
        g.printInfo();
        
        // 貪欲法でモジュラリティを最大化
        int numAlpha = alphaList.length;
        double[] results = new double[numAlpha];

        // すべてのノードについてSSSP結果を事前計算
        SsspResult[] ssspResults = new SsspResult[g.n];
        System.out.println("Calculating SSSP results... ");
        for (int i = 0; i < g.n; i++) {
            PrintProgressBar.printProgressBar(i, g.n);
            ssspResults[i] = Betweenness.bfsShortestPaths(g, i);
        }
        System.out.println("done");

        System.out.println("Calculating modularity... ");
        for (int aIdx = 0; aIdx < numAlpha; aIdx++) {
            PrintProgressBar.printProgressBar(aIdx, numAlpha);
            double a = alphaList[aIdx];
            GreedyModularityOptimizerWithBetweenness.Result result = GreedyModularityOptimizerWithBetweenness.optimize(g, ssspResults, a, seed);
            results[aIdx] = result.modularity;
        }
        System.out.println("done");

        for (int i = 0; i < numAlpha; i++) {
            System.out.printf("alpha: %f, modularity: %f%n", alphaList[i], results[i]);
        }
        
        // 結果をCSVファイルに書き出し
        writeModularityResults(g.name, alphaList, results, seed);
    }
    
    /**
     * モジュラリティ計算結果をCSVファイルに書き出します。
     * 
     * @param graphName グラフ名
     * @param alphaList alpha値の配列
     * @param results モジュラリティ値の配列
     * @param seed 乱数シード
     * @throws IOException ファイル書き込みエラー
     */
    private static void writeModularityResults(String graphName, double[] alphaList, double[] results, Long seed) throws IOException {
        if (alphaList.length != results.length) {
            throw new IllegalArgumentException("alphaList and results must have the same length");
        }
        
        // 出力パスを生成（out/modularity/グラフ名_seed=シード.csv）
        String seedStr = seed != null ? String.valueOf(seed) : "random";
        Path outputPath = Paths.get(String.format("out/modularity/%s_seed=%s.csv", graphName.replace(".txt", ""), seedStr));
        outputPath = PathsEx.resolveIndexed(outputPath);
        
        // ディレクトリを作成
        Files.createDirectories(outputPath.getParent());
        
        // CSVファイルに書き出し
        try (BufferedWriter bw = Files.newBufferedWriter(outputPath);
             PrintWriter out = new PrintWriter(bw)) {
            // ヘッダー行
            out.println("alpha,modularity");
            
            // データ行
            for (int i = 0; i < alphaList.length; i++) {
                out.printf(Locale.ROOT, "%.9f,%.9f%n", alphaList[i], results[i]);
            }
        }
        
        System.out.printf("Results written to: %s%n", outputPath);
    }
}
