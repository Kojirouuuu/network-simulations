package sirsim.network.metrics;

import sirsim.network.Graph;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Random;

/**
 * 貪欲法によるモジュラリティ最大化アルゴリズム。
 * 
 * 各ステップで最大のΔQ（モジュラリティ変化量）を持つコミュニティ対をマージし、
 * モジュラリティが最大になる分割を見つけます。
 */
public final class GreedyModularityOptimizer {
    
    private GreedyModularityOptimizer() { /* no instances */ }
    
    /**
     * 貪欲法でモジュラリティを最大化するコミュニティ分割を見つけます。
     * 
     * @param g 無向グラフ
     * @param seed ランダムシード（最大ΔQが複数ある場合の選択用、nullの場合は現在時刻を使用）
     * @return 最適化結果
     */
    public static Result optimize(Graph g, Long seed) {
        if (g == null) throw new IllegalArgumentException("graph is null");
        
        final int n = g.n;
        final int m = g.m2 / 2;
        if (m == 0) {
            // 辺が無い場合は各頂点が独立したコミュニティ
            int[] labels = new int[n];
            for (int i = 0; i < n; i++) labels[i] = i;
            return new Result(labels, 0.0, 0);
        }
        
        // 初期状態：各頂点が独立したコミュニティ
        int[] labels = new int[n];
        for (int i = 0; i < n; i++) {
            labels[i] = i;
        }
        
        // 初期モジュラリティを計算
        double initialQ = Modularity.compute(g, labels);
        
        // 各ステップのモジュラリティとラベルを記録
        double[] record = new double[n];
        record[0] = initialQ;
        int[][] labelHistory = new int[n][n];
        labelHistory[0] = Arrays.copyOf(labels, n);
        
        Random rand = seed != null ? new Random(seed) : new Random();
        
        // 貪欲法でコミュニティをマージ
        int step = 1;
        while (step < n) {
            // マージ可能なコミュニティ対のΔQを計算
            Modularity.MergeDelta[] deltas = Modularity.computeMergeDeltas(g, labels);
            
            if (deltas.length == 0) {
                // マージ可能なコミュニティ対が無い場合は終了
                break;
            }
            
            // ΔQでソート（昇順）
            Arrays.sort(deltas, Comparator.comparingDouble(d -> d.deltaQ));
            
            // 最大のΔQを取得
            double maxDeltaQ = deltas[deltas.length - 1].deltaQ;
            
            // 最大ΔQを持つコミュニティ対をすべて収集
            int maxCount = 0;
            Modularity.MergeDelta[] maxDeltas = new Modularity.MergeDelta[deltas.length];
            for (int i = deltas.length - 1; i >= 0; i--) {
                if (Math.abs(deltas[i].deltaQ - maxDeltaQ) < 1e-10) {
                    maxDeltas[maxCount++] = deltas[i];
                } else {
                    break;
                }
            }
            
            // ランダムに1つ選択（GreedyRandomと同じ動作）
            // 注意：最大ΔQが負でもマージを続ける（GreedyRandomと同じ動作）
            int selectedIdx = rand.nextInt(maxCount);
            Modularity.MergeDelta selected = maxDeltas[selectedIdx];
            
            // コミュニティをマージ（c2をc1に統合）
            int c1 = selected.c1;
            int c2 = selected.c2;
            for (int i = 0; i < n; i++) {
                if (labels[i] == c2) {
                    labels[i] = c1;
                }
            }
            
            // モジュラリティを累積的に更新
            record[step] = maxDeltaQ + record[step - 1];
            labelHistory[step] = Arrays.copyOf(labels, n);
            
            step++;
        }
        
        // 最大モジュラリティを見つける
        int maxIdx = 0;
        double maxQ = record[0];
        for (int i = 1; i < step; i++) {
            if (record[i] > maxQ) {
                maxQ = record[i];
                maxIdx = i;
            }
        }
        
        return new Result(labelHistory[maxIdx], maxQ, maxIdx);
    }
    
    /**
     * 最適化結果を保持するクラス。
     */
    public static final class Result {
        /** 最適なコミュニティラベル（各頂点の所属コミュニティ） */
        public final int[] labels;
        /** 最大モジュラリティ */
        public final double modularity;
        /** 最大モジュラリティに到達したステップ数 */
        public final int step;
        
        Result(int[] labels, double modularity, int step) {
            this.labels = labels;
            this.modularity = modularity;
            this.step = step;
        }
    }
}
