package sirsim.network.metrics;

import sirsim.network.Graph;
import sirsim.network.metrics.Betweenness.SsspResult;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Random;

/**
 * 貪欲法によるモジュラリティ最大化アルゴリズム。
 * 
 * 各ステップで最大のΔQ（モジュラリティ変化量）を持つコミュニティ対をマージし、
 * モジュラリティが最大になる分割を見つけます。
 */
public final class GreedyModularityOptimizerWithBetweenness {
    
    private GreedyModularityOptimizerWithBetweenness() { /* no instances */ }
    
    /**
     * 貪欲法でモジュラリティを最大化するコミュニティ分割を見つけます。
     * 
     * @param g 無向グラフ
     * @param seed ランダムシード（最大ΔQが複数ある場合の選択用、nullの場合は現在時刻を使用）
     * @return 最適化結果
     */
    public static Result optimize(Graph g, SsspResult[] ssspResults, double alpha, Long seed) {
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
            
            // 各マージ候補について、統合後にコミュニティ内に含まれるようになる辺の媒介中心性の合計を計算
            double[] edgeBetweennessSums = new double[deltas.length];
            double[] scores = new double[deltas.length];
            
            // まず、すべての辺媒介中心性の合計を計算
            for (int dIdx = 0; dIdx < deltas.length; dIdx++) {
                int c1 = deltas[dIdx].c1;
                int c2 = deltas[dIdx].c2;
                
                // コミュニティc1とc2の間を接続するすべての辺の媒介中心性の合計を計算
                // これらは統合後にコミュニティ内に含まれるようになる辺
                double edgeBetweenness = 0.0;
                for (int u = 0; u < n; u++) {
                    if (labels[u] != c1) continue;
                    for (int e = g.firstArc(u); e < g.endArc(u); e++) {
                        int v = g.colIdx[e];
                        if (labels[v] == c2) {
                            // 辺(u, v)の媒介中心性を計算
                            double edgeBC = Betweenness.computeEdgeBetweenness(g, ssspResults, u, v);
                            edgeBetweenness += edgeBC;
                        }
                    }
                }
                edgeBetweennessSums[dIdx] = edgeBetweenness;
            }
            
            // 辺媒介中心性の範囲を計算（正規化用）
            double minEB = Double.POSITIVE_INFINITY;
            double maxEB = Double.NEGATIVE_INFINITY;
            for (int dIdx = 0; dIdx < deltas.length; dIdx++) {
                if (edgeBetweennessSums[dIdx] < minEB) minEB = edgeBetweennessSums[dIdx];
                if (edgeBetweennessSums[dIdx] > maxEB) maxEB = edgeBetweennessSums[dIdx];
            }
            double ebRange = maxEB - minEB;
            if (ebRange < 1e-10) {
                // すべての値が同じ場合は正規化不要
                ebRange = 1.0;
            }
            
            // deltaQの範囲も計算（正規化用）
            double minDeltaQ = Double.POSITIVE_INFINITY;
            double maxDeltaQ = Double.NEGATIVE_INFINITY;
            for (int dIdx = 0; dIdx < deltas.length; dIdx++) {
                if (deltas[dIdx].deltaQ < minDeltaQ) minDeltaQ = deltas[dIdx].deltaQ;
                if (deltas[dIdx].deltaQ > maxDeltaQ) maxDeltaQ = deltas[dIdx].deltaQ;
            }
            double deltaQRange = maxDeltaQ - minDeltaQ;
            if (deltaQRange < 1e-10) {
                // すべての値が同じ場合は正規化不要
                deltaQRange = 1.0;
            }
            
            // 評価関数を計算（両方を正規化してから組み合わせ）

            // WARNING: normalizeなのかチェック!!
            deltaQRange = 1.0;
            ebRange = 1.0;

            for (int dIdx = 0; dIdx < deltas.length; dIdx++) {
                // 正規化された値（0-1の範囲）
                double normalizedDeltaQ = (deltas[dIdx].deltaQ - minDeltaQ) / deltaQRange;
                double normalizedEB = (edgeBetweennessSums[dIdx] - minEB) / ebRange / (g.n * (g.n - 1) / 2);
                // 評価関数: normalizedDeltaQ - alpha * normalizedEB
                // 辺媒介中心性が大きいほど評価を下げる（統合後にコミュニティ内に含まれる辺の媒介中心性が大きい = その辺が重要 = マージを避けるべき）
                scores[dIdx] = normalizedDeltaQ - alpha * normalizedEB;
            }
            
            // 評価関数でソート（昇順）
            Integer[] indices = new Integer[deltas.length];
            for (int i = 0; i < deltas.length; i++) {
                indices[i] = i;
            }
            Arrays.sort(indices, Comparator.comparingDouble(i -> scores[i]));
            
            // 最大の評価値を取得
            int maxIdx = indices[indices.length - 1];
            double maxScore = scores[maxIdx];
            
            // 最大評価値を持つコミュニティ対をすべて収集
            int maxCount = 0;
            Modularity.MergeDelta[] maxDeltas = new Modularity.MergeDelta[deltas.length];
            for (int i = indices.length - 1; i >= 0; i--) {
                int idx = indices[i];
                if (Math.abs(scores[idx] - maxScore) < 1e-10) {
                    maxDeltas[maxCount++] = deltas[idx];
                } else {
                    break;
                }
            }
            
            // ランダムに1つ選択（GreedyRandomと同じ動作）
            // 注意：最大評価値が負でもマージを続ける（GreedyRandomと同じ動作）
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
            record[step] = selected.deltaQ + record[step - 1];
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
