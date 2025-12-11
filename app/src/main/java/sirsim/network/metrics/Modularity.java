package sirsim.network.metrics;

import sirsim.network.Graph;

import java.util.HashMap;
import java.util.Map;

/**
 * モジュラリティ（Newman-Girvan）の計算ユーティリティ。
 *
 * 無向・非重みグラフ（{@link Graph} の CSR 形式）と、各頂点のコミュニティラベルを受け取り、
 * 次の定義でモジュラリティ Q を計算します。
 *   Q = Σ_c ( l_c / m - (d_c / (2m))^2 )
 * ここで、m は無向辺数、l_c はコミュニティ c 内部の辺数、d_c はコミュニティ c に属する頂点の次数総和です。
 *
 * 注意:
 * - ラベルは 0..k-1 の連番でなくても構いません（内部で集計）。
 * - 辺数 m = g.m2 / 2 を用います（g.m2 は両方向辺の本数）。
 */
public final class Modularity {

    private Modularity() { /* no instances */ }

    /**
     * グラフとコミュニティラベルからモジュラリティ Q を計算します。
     *
     * @param g CSR 形式の無向・非重みグラフ
     * @param labels 頂点ごとのコミュニティラベル（長さ g.n）
     * @param alpha 辺媒介中心性の係数
     * @return モジュラリティ Q（[-1, 1] 程度の値域）
     */
    public static double compute(Graph g, int[] labels) {
        if (g == null) throw new IllegalArgumentException("graph is null");
        if (labels == null) throw new IllegalArgumentException("labels is null");
        if (labels.length != g.n) throw new IllegalArgumentException("labels length mismatch: " + labels.length + " != " + g.n);

        final int n = g.n;
        final int m = g.m2 / 2; // 無向辺数
        if (m == 0) return 0.0; // 辺が無い場合は 0 とする（定義次第だが無害）

        // ラベル -> コミュニティ内部辺数 l_c
        final Map<Integer, Integer> internalEdges = new HashMap<>();
        // ラベル -> 次数総和 d_c
        final Map<Integer, Long> degreeSums = new HashMap<>();

        // 各頂点の次数をコミュニティへ加算
        for (int u = 0; u < n; u++) {
            final int c = labels[u];
            final int deg = g.degree(u);
            degreeSums.merge(c, (long) deg, Long::sum);
        }

        // 各無向辺を一度だけ数える（u < v のみ）
        for (int u = 0; u < n; u++) {
            final int cU = labels[u];
            final int eBegin = g.firstArc(u);
            final int eEnd = g.endArc(u);
            for (int e = eBegin; e < eEnd; e++) {
                final int v = g.colIdx[e];
                if (u < v && cU == labels[v]) {
                    // コミュニティ内部の無向辺
                    internalEdges.merge(cU, 1, Integer::sum);
                }
            }
        }

        // Q = Σ_c ( l_c / m - (d_c / (2m))^2 )
        final double twoM = 2.0 * m;
        double Q = 0.0;
        // すべての出現ラベルについて加算（内部辺数が 0 のコミュニティも考慮）
        for (Map.Entry<Integer, Long> entry : degreeSums.entrySet()) {
            final int c = entry.getKey();
            final long dC = entry.getValue();
            final int lC = internalEdges.getOrDefault(c, 0);
            Q += (double) lC / (double) m - Math.pow(dC / twoM, 2.0);
        }

        return Q;
    }

    /**
     * 2つのコミュニティをマージしたときのモジュラリティ変化量 ΔQ を、
     * 現在の分割から到達可能な（辺で接続されている）コミュニティ対すべてについて計算して返します。
     *
     * Louvain/Greedy 系で用いられる定式化に従い、
     *   ΔQ(c1, c2) = e_{c1,c2} / m - (d_{c1} d_{c2}) / (2 m^2)
     * を使用します（m は無向辺数、e_{c1,c2} はコミュニティ間の無向辺数、d_c は次数総和）。
     *
     * @param g CSR 形式の無向・非重みグラフ
     * @param labels 頂点ごとのコミュニティラベル（長さ g.n）
     * @return ΔQ 候補の配列（各要素はコミュニティ対とΔQ）
     */
    public static MergeDelta[] computeMergeDeltas(Graph g, int[] labels) {
        if (g == null) throw new IllegalArgumentException("graph is null");
        if (labels == null) throw new IllegalArgumentException("labels is null");
        if (labels.length != g.n) throw new IllegalArgumentException("labels length mismatch: " + labels.length + " != " + g.n);

        final int n = g.n;
        final int m = g.m2 / 2;
        if (m == 0) return new MergeDelta[0];

        // ラベル -> 次数総和 d_c
        final Map<Integer, Long> degreeSums = new HashMap<>();
        for (int u = 0; u < n; u++) {
            degreeSums.merge(labels[u], (long) g.degree(u), Long::sum);
        }

        // コミュニティ間の無向辺数 e_{c1,c2} を集計（c1 < c2 の正規化で1回だけカウント）
        final Map<Pair, Integer> interEdges = new HashMap<>();
        for (int u = 0; u < n; u++) {
            final int cu = labels[u];
            for (int e = g.firstArc(u); e < g.endArc(u); e++) {
                final int v = g.colIdx[e];
                if (u < v) { // 無向辺を一度だけ
                    final int cv = labels[v];
                    if (cu != cv) {
                        final Pair p = Pair.of(cu, cv);
                        interEdges.merge(p, 1, Integer::sum);
                    }
                }
            }
        }

        // ΔQ を算出
        final double twoM = 2.0 * m;
        final MergeDelta[] result = new MergeDelta[interEdges.size()];
        int idx = 0;
        for (Map.Entry<Pair, Integer> entry : interEdges.entrySet()) {
            final Pair p = entry.getKey();
            final int e12 = entry.getValue();
            final long d1 = degreeSums.getOrDefault(p.a, 0L);
            final long d2 = degreeSums.getOrDefault(p.b, 0L);
            final double delta = ((double) e12) / (double) m - ((double) d1 * (double) d2) / (twoM * (double) m);
            result[idx++] = new MergeDelta(p.a, p.b, delta);
        }
        return result;
    }

    /** コミュニティ対（順序無し：a <= b） */
    private static final class Pair {
        final int a, b;
        private Pair(int a, int b) { this.a = a; this.b = b; }
        static Pair of(int x, int y) { return (x <= y) ? new Pair(x, y) : new Pair(y, x); }
        @Override public boolean equals(Object o) { if (this == o) return true; if (!(o instanceof Pair p)) return false; return a == p.a && b == p.b; }
        @Override public int hashCode() { return 31 * a + b; }
    }

    /** ΔQ の結果コンテナ */
    public static final class MergeDelta {
        public final int c1;
        public final int c2;
        public final double deltaQ;
        public MergeDelta(int c1, int c2, double deltaQ) { this.c1 = c1; this.c2 = c2; this.deltaQ = deltaQ; }
    }
}
