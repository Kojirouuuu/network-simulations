package sirsim.network.topology;

import sirsim.network.Graph;
import java.util.*;

public class Config {
    /**
     * コンフィグモデル（Configuration Model）を用いて、次数分布が k^{-gamma} に従う無向グラフを生成します。
     * ここでは自己ループ・多重辺を持たない「単純グラフ」を、ヒューリスティックに生成します。
     * （残スタブ数最大の頂点から貪欲にマッチングし、行き詰まり時は少数の辺を組み替え／バックトラックします）
     *
     * @param N     ノード数（>= 1）
     * @param gamma 冪指数（> 0）
     * @param seed  乱数シード
     * @return 生成されたGraphインスタンス
     */
    public static Graph generatePowerLawConfig(int N, double gamma, long seed) {
        if (N <= 0) throw new IllegalArgumentException("ノード数Nは正の整数である必要があります");
        if (gamma <= 0.0) throw new IllegalArgumentException("冪指数gammaは正である必要があります");

        Random random = new Random(seed);

        // 離散分布 p(k) ∝ k^{-gamma}, k = 1..N-1 を構築
        int kMax = Math.max(1, N - 1);
        double[] cdf = new double[kMax + 1]; // 1-indexed: cdf[k]
        double z = 0.0;
        for (int k = 1; k <= kMax; k++) z += Math.pow(k, -gamma);
        double acc = 0.0;
        for (int k = 1; k <= kMax; k++) {
            acc += Math.pow(k, -gamma) / z;
            cdf[k] = acc;
        }

        // 各頂点の次数をサンプリング（最低1、最大N-1）
        int[] deg = new int[N];
        long sumDeg = 0;
        for (int i = 0; i < N; i++) {
            double u = random.nextDouble();
            // 2分探索でkを取得
            int lo = 1, hi = kMax, picked = 1;
            while (lo <= hi) {
                int mid = (lo + hi) >>> 1;
                if (u <= cdf[mid]) { picked = mid; hi = mid - 1; }
                else { lo = mid + 1; }
            }
            deg[i] = picked;
            sumDeg += picked;
        }

        // 総次数を偶数に調整（必要であれば1だけ増減）
        if ((sumDeg & 1L) == 1L) {
            int idx = 0;
            if (deg[idx] < kMax) deg[idx]++;
            else if (deg[idx] > 1) deg[idx]--;
            else {
                // 探して調整
                boolean adjusted = false;
                for (int i = 1; i < N; i++) {
                    if (deg[i] < kMax) { deg[i]++; adjusted = true; break; }
                }
                if (!adjusted) {
                    for (int i = 1; i < N; i++) {
                        if (deg[i] > 1) { deg[i]--; adjusted = true; break; }
                    }
                }
                if (!adjusted) throw new IllegalStateException("次数調整に失敗しました");
            }
            sumDeg++; // 偶数にした
        }

        // これ以降は「スタブ列」を明示的に持たず、残り次数 remainingDeg で管理する

        final int MAX_RESTARTS = 10_000;
        final int m = (int) (sumDeg / 2);  // 辺の本数

        // 行き詰まり時に「組み換え」として許すバックトラック回数の上限
        // （小さいほど決定的・大きいほど成功しやすいが遅くなる）
        final int BACKTRACK_BUDGET_BASE = Math.max(100, N);

        for (int attempt = 0; attempt < MAX_RESTARTS; attempt++) {
            // 残りスタブ数（= 残り次数）
            int[] remainingDeg = Arrays.copyOf(deg, N);

            int[] s = new int[m];
            int[] d = new int[m];
            int e = 0;

            // 既に張られている辺（無向）を記録
            HashSet<Long> edges = new HashSet<>(m * 2);

            int backtrackBudget = BACKTRACK_BUDGET_BASE;
            boolean okAttempt = true;

            while (e < m) {
                // 残スタブが最大の頂点 u を選ぶ（tie は最初のもの）
                int u = -1;
                int maxRem = 0;
                for (int i = 0; i < N; i++) {
                    if (remainingDeg[i] > maxRem) {
                        maxRem = remainingDeg[i];
                        u = i;
                    }
                }

                // もうスタブが残っていないのに e < m → 矛盾（理論上ほぼ起こらない）
                if (u == -1 || maxRem == 0) {
                    okAttempt = false;
                    break;
                }

                // u と結べる候補 v を列挙（自己ループ＆多重辺禁止）
                List<Integer> candidates = new ArrayList<>();
                for (int v = 0; v < N; v++) {
                    if (v == u) continue;
                    if (remainingDeg[v] <= 0) continue;
                    long key = edgeKey(u, v);
                    if (edges.contains(key)) continue; // 既に辺あり → 多重辺になるので不可
                    candidates.add(v);
                }

                if (candidates.isEmpty()) {
                    // u から誰とも合法に結べない = 局所的に詰んでいる
                    // → 少数本の辺を巻き戻して「組み換え」してみる
                    if (backtrackBudget > 0 && e > 0) {
                        // 最後に張った辺を1本消す
                        e--;
                        int uu = s[e];
                        int vv = d[e];
                        long key = edgeKey(uu, vv);
                        edges.remove(key);
                        remainingDeg[uu]++;
                        remainingDeg[vv]++;
                        backtrackBudget--;
                        // ここで while の先頭に戻って、別の構成を試す
                        continue;
                    } else {
                        // これ以上組み換えしない → この attempt は失敗としてリスタート
                        okAttempt = false;
                        break;
                    }
                }

                // 候補の中からランダムに v を選んでエッジ (u, v) を張る
                int v = candidates.get(random.nextInt(candidates.size()));
                long key = edgeKey(u, v);
                edges.add(key);

                s[e] = u;
                d[e] = v;
                remainingDeg[u]--;
                remainingDeg[v]--;
                e++;
            }

            if (okAttempt && e == m) {
                // うまく全部のスタブを消費できた
                return Graph.fromUndirectedEdgeList(N, s, d);
            }
            // 失敗したら次の attempt へ（度数列は同じだが、乱数の流れやバックトラックで構成が変わる）
        }

        throw new IllegalStateException(
                "単純グラフとしてのコンフィグ生成に失敗しました（試行回数上限）: N=" + N + ", gamma=" + gamma
        );
    }

    /** シード省略版 */
    public static Graph generatePowerLawConfig(int N, double gamma) {
        return generatePowerLawConfig(N, gamma, System.currentTimeMillis());
    }

    /** 無向辺 (u, v) を一意に表すキー（u < v に正規化） */
    private static long edgeKey(int u, int v) {
        int a = Math.min(u, v);
        int b = Math.max(u, v);
        return (((long) a) << 32) ^ (b & 0xffffffffL);
    }
}
