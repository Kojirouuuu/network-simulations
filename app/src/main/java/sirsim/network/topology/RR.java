package sirsim.network.topology;

import sirsim.network.Graph;
import sirsim.utils.Array;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class RR {

    /**
     * ランダム d-正則グラフを生成する。
     *
     * アルゴリズム（configuration model ベース）:
     *   1. 各頂点 v について d 本の「半辺（stub）」を用意する（合計 N*d 本）。
     *   2. それらを一様ランダムにシャッフルする。
     *   3. 先頭から 2 本ずつペアにして辺とみなす。
     *   4. 自己ループや多重辺ができたらやり直し（一定回数まで）。
     *
     * @param N    頂点数
     * @param d    次数（全頂点で同じ）
     * @param seed 乱数シード
     * @return ランダム d-正則グラフ
     */
    public static Graph generateRR(int N, int d, long seed) {
        if (N <= 0) {
            throw new IllegalArgumentException("ノード数Nは正の整数である必要があります");
        }
        if (d <= 0 || d >= N) {
            throw new IllegalArgumentException("次数dは 1〜N-1 の範囲で指定してください");
        }
        // d-正則グラフでは Nd は必ず偶数でないといけない
        if ((N * d) % 2 != 0) {
            throw new IllegalArgumentException("N*d が奇数なので d-正則グラフは存在しません");
        }

        final int M = N * d / 2;            // 辺数
        final int stubCount = N * d;        // 半辺の本数
        final int maxTries = 1000;          // 自己ループ・多重辺が出たときのリトライ回数

        Random random = new Random(seed);

        // 各頂点 v を d 回ずつ並べた配列を作る（v の半辺を表す）
        int[] baseStubs = new int[stubCount];
        int idx = 0;
        for (int v = 0; v < N; v++) {
            for (int k = 0; k < d; k++) {
                baseStubs[idx++] = v;
            }
        }

        // 自己ループや多重辺が出来てしまった場合は、シャッフルからやり直す
        for (int attempt = 0; attempt < maxTries; attempt++) {
            // 毎回もとの配列をコピーしてシャッフル
            int[] stubs = baseStubs.clone();
            Array.shuffle(stubs, random.nextLong());

            int[] startIndices = new int[M];
            int[] destIndices  = new int[M];

            boolean ok = true;
            Set<Long> usedEdges = new HashSet<>();

            int e = 0;
            for (int i = 0; i < stubCount; i += 2) {
                int u = stubs[i];
                int v = stubs[i + 1];

                // 自己ループはNG
                if (u == v) {
                    ok = false;
                    break;
                }

                // 無向辺 (min,max) をキーにして多重辺を検出
                int a = Math.min(u, v);
                int b = Math.max(u, v);
                long key = (((long) a) << 32) | (b & 0xffffffffL);

                if (!usedEdges.add(key)) {
                    ok = false;
                    break;
                }

                startIndices[e] = u;
                destIndices[e]  = v;
                e++;
            }

            if (ok && e == M) {
                // 条件を満たしたら Graph を生成して返す
                return Graph.fromUndirectedEdgeList("RR", N, startIndices, destIndices);
            }
        }

        throw new RuntimeException("ランダム d-正則グラフの生成に失敗しました（試行回数オーバー）");
    }

    /**
     * シード省略版
     */
    public static Graph generateRR(int N, int d) {
        return generateRR(N, d, System.currentTimeMillis());
    }
}
