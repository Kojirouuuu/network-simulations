package sirsim.network.metrics;

import sirsim.network.Graph;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * 媒介中心性の計算で用いる要素（最短距離・最短路本数・前駆集合など）を提供するユーティリティ。
 *
 * {@link Graph} が保持する CSR 形式（無向・非重み）のグラフに対し、
 * BFS による単一始点最短路（SSSP）を計算します。
 *
 * Brandes アルゴリズムに必要な情報をそのまま返します:
 *  - dist[u]: 始点 s から u への最短距離（到達不可は -1）
 *  - sigma[u]: s→u の最短路本数（重複経路の合計）
 *  - P(u): u へ至る最短路上の前駆ノード集合（u の1層手前にあるノード）
 *  - stack: BFS での訪問順（距離非減少）。依存度の後退伝播では逆順に処理します。
 *
 * 設計メモ:
 *  - Graph は不変とし、アルゴリズムはここで静的メソッドとして提供します。
 *  - 前駆集合は predPtr/predList の CSR 形式に平坦化し、依存度の後退計算でボクシングを避けます。
 */
public final class Betweenness {

    private Betweenness() { /* no instances */ }

    /**
     * Brandes アルゴリズムで用いる単一始点最短路の結果コンテナ。
     *
     * パフォーマンスと簡潔さを優先し、フィールドは public（読み取り専用の想定）としています。
     */
    public static final class SsspResult {
        /** dist[u]: s から u への最短距離（到達不可は -1） */
        public final int[] dist;
        /** sigma[u]: s→u の最短路本数 */
        public final double[] sigma;
        /** BFS での訪問順（距離非減少）。依存度の後退計算では逆順に処理する */
        public final int[] stack;
        /** {@link #stack} の有効サイズ（先頭から stackSize 要素が有効） */
        public final int stackSize;
        /** 前駆集合の CSR ポインタ: u の前駆は predList[predPtr[u]..predPtr[u+1]-1] */
        public final int[] predPtr;
        /** 全頂点の前駆を平坦化した配列 */
        public final int[] predList;

        public SsspResult(int[] dist, double[] sigma, int[] stack, int stackSize, int[] predPtr, int[] predList) {
            this.dist = dist;
            this.sigma = sigma;
            this.stack = stack;
            this.stackSize = stackSize;
            this.predPtr = predPtr;
            this.predList = predList;
        }
    }

    /**
     * 無向・非重みグラフに対する BFS ベースの単一始点最短路を計算します。
     *
     * Brandes (2001) の前向き処理に相当し、以下を行います:
     *  - 始点 s から BFS を実行
     *  - 各頂点の最短路本数（sigma）と前駆集合（P）を記録
     *  - 距離非減少の訪問順を stack に格納（後退伝播は逆順で処理）
     *
     * 計算量: 最悪で O(n + m) 時間、補助メモリ O(n + m)。
     *
     * @param g CSR 形式の入力グラフ（無向・非重み）
     * @param s 始点頂点（0 <= s < g.n）
     * @return dist・sigma・前駆 CSR・BFS stack を含む SsspResult
     */
    public static SsspResult bfsShortestPaths(Graph g, int s) {
        if (g == null) throw new IllegalArgumentException("graph is null");
        if (s < 0 || s >= g.n) throw new IllegalArgumentException("source out of range: " + s);

        final int n = g.n;

        // --- 頂点ごとの配列を確保 ---
        final int[] dist = new int[n];
        Arrays.fill(dist, -1); // -1 は未到達（到達不可）を表す

        final double[] sigma = new double[n];
        // 始点 s の最短路本数は 1.0（自明な経路）
        sigma[s] = 1.0;

        // BFS キュー: 単純な配列ベース（qHead..qTail-1 が有効範囲）
        final int[] queue = new int[n];
        int qHead = 0, qTail = 0;

        // BFS の訪問順を積むスタック（距離非減少）。
        // Brandes の後退伝播ではこのスタックを逆順に処理する。
        final int[] stack = new int[n];
        int stackSize = 0;

        // 前駆集合 P[u] はまず List に集約してから、最後に CSR へ平坦化する。
        // これにより後段の処理でボクシングのオーバーヘッドを避ける。
        @SuppressWarnings("unchecked")
        final ArrayList<Integer>[] preds = new ArrayList[n];

        // --- BFS 初期化 ---
        dist[s] = 0;
        queue[qTail++] = s;

        // --- BFS 探索 ---
        while (qHead < qTail) {
            final int u = queue[qHead++];

            // 後退処理に備えて訪問順で push
            stack[stackSize++] = u;

            // CSR 直接走査（neighbors() の都度配列生成を避ける）
            final int eBegin = g.firstArc(u);
            final int eEnd = g.endArc(u);
            for (int e = eBegin; e < eEnd; e++) {
                final int v = g.colIdx[e];

                // v を初めて発見したら距離を設定しキューへ追加
                if (dist[v] == -1) {
                    dist[v] = dist[u] + 1;
                    queue[qTail++] = v;
                }

                // 次層への辺であれば、u は v の最短路上の前駆
                if (dist[v] == dist[u] + 1) {
                    // 最短路本数の伝播: u への最短路は v への最短路へと延長される
                    sigma[v] += sigma[u];

                    // 前駆関係 u -> v を記録（u ∈ P[v]）
                    ArrayList<Integer> Pv = preds[v];
                    if (Pv == null) {
                        Pv = (preds[v] = new ArrayList<>());
                    }
                    Pv.add(u);
                }
            }
        }

        // --- 前駆集合を CSR 形式（predPtr/predList）へ平坦化 ---
        int totalPred = 0;
        for (int v = 0; v < n; v++) {
            if (preds[v] != null) totalPred += preds[v].size();
        }

        final int[] predPtr = new int[n + 1];
        final int[] predList = new int[totalPred];

        int cursor = 0;
        for (int v = 0; v < n; v++) {
            predPtr[v] = cursor;
            final ArrayList<Integer> Pv = preds[v];
            if (Pv != null) {
                for (int i = 0, sz = Pv.size(); i < sz; i++) {
                    predList[cursor++] = Pv.get(i);
                }
            }
        }
        predPtr[n] = cursor; // 終端（番兵）

        return new SsspResult(dist, sigma, stack, stackSize, predPtr, predList);
    }

    /**
     * 辺(u, v)の媒介中心性を計算します。
     * Brandesアルゴリズムの後退伝播を使用して、すべての頂点対(s, t)について、
     * s→tの最短経路のうち辺(u, v)を通る経路の割合を計算します。
     * 
     * @param g グラフ
     * @param ssspResults 各頂点を始点としたSSSP結果（長さg.nの配列）
     * @param u 辺の一方の端点
     * @param v 辺のもう一方の端点
     * @return 辺(u, v)の媒介中心性
     */
    public static double computeEdgeBetweenness(Graph g, SsspResult[] ssspResults, int u, int v) {
        if (g == null) throw new IllegalArgumentException("graph is null");
        if (ssspResults == null) throw new IllegalArgumentException("ssspResults is null");
        if (ssspResults.length != g.n) throw new IllegalArgumentException("ssspResults length mismatch");
        if (u < 0 || u >= g.n || v < 0 || v >= g.n) {
            throw new IllegalArgumentException("vertex out of range: u=" + u + ", v=" + v);
        }

        final int n = g.n;
        double edgeBC = 0.0;
        
        // すべての頂点対(s, t)について、s→tの最短経路のうち辺(u, v)を通る経路の割合を計算
        for (int s = 0; s < n; s++) {
            SsspResult sssp = ssspResults[s];
            if (sssp == null) continue; // SSSP結果が未計算
            if (sssp.dist[u] == -1 || sssp.dist[v] == -1) continue; // 到達不可
            
            // 辺(u, v)がs→tの最短経路上にあるかチェック
            // 無向グラフなので、u→vまたはv→uのどちらかが最短経路上にあれば良い
            for (int t = 0; t < n; t++) {
                if (s == t) continue;
                if (sssp.dist[t] == -1) continue;
                
                // 辺(u, v)がs→tの最短経路上にある条件:
                // dist[s][u] + 1 == dist[s][v] かつ dist[s][v] + dist[v][t] == dist[s][t]
                // または
                // dist[s][v] + 1 == dist[s][u] かつ dist[s][u] + dist[u][t] == dist[s][t]
                
                // u→v方向: s→u→v→t が最短経路かチェック
                if (sssp.dist[u] != -1 && sssp.dist[v] != -1 && 
                    sssp.dist[u] + 1 == sssp.dist[v]) {
                    // vからtへの最短距離を確認（vを始点としたSSSP結果を使用）
                    SsspResult vSssp = ssspResults[v];
                    if (vSssp == null) continue;
                    if (vSssp.dist[t] != -1 && sssp.dist[u] + 1 + vSssp.dist[t] == sssp.dist[t]) {
                        // 辺(u, v)を通るs→tの最短経路の割合
                        // s→u→v→t が最短経路の場合、経路数は sigma[s][u] * sigma[v][t]
                        // 全体の経路数は sigma[s][t]
                        if (sssp.sigma[t] > 0) {
                            double ratio = (sssp.sigma[u] * vSssp.sigma[t]) / sssp.sigma[t];
                            edgeBC += ratio;
                        }
                    }
                }
                
                // v→u方向: s→v→u→t が最短経路かチェック
                if (sssp.dist[v] != -1 && sssp.dist[u] != -1 && 
                    sssp.dist[v] + 1 == sssp.dist[u]) {
                    // uからtへの最短距離を確認（uを始点としたSSSP結果を使用）
                    SsspResult uSssp = ssspResults[u];
                    if (uSssp == null) continue;
                    if (uSssp.dist[t] != -1 && sssp.dist[v] + 1 + uSssp.dist[t] == sssp.dist[t]) {
                        // 辺(v, u)を通るs→tの最短経路の割合
                        // s→v→u→t が最短経路の場合、経路数は sigma[s][v] * sigma[u][t]
                        // 全体の経路数は sigma[s][t]
                        if (sssp.sigma[t] > 0) {
                            double ratio = (sssp.sigma[v] * uSssp.sigma[t]) / sssp.sigma[t];
                            edgeBC += ratio;
                        }
                    }
                }
            }
        }
        
        return edgeBC;
    }
}
