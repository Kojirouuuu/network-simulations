package sirsim.network.topology;

import sirsim.network.Graph;
import java.util.*;

public class BA {
    /**
     * BAモデル（Barabási–Albert型スケールフリーネットワーク）を生成
     * @param N ノード数
     * @param m0 初期完全グラフの頂点数
     * @param m 各新規ノードが接続するエッジ数
     * @param seed 乱数シード（省略可）
     * @return 生成されたGraphインスタンス
     */
    public static Graph generateBA(int N, int m0, int m, long seed) {
        if (N <= 0) throw new IllegalArgumentException("ノード数Nは正の整数である必要があります");
        if (m0 <= 0 || m0 > N) throw new IllegalArgumentException("初期完全グラフの頂点数m0は1〜Nの範囲で指定してください");
        if (m < 0 || m > m0) throw new IllegalArgumentException("各新規ノードが接続するエッジ数mは0以上m0以下である必要があります");
    
        // BAとして扱うなら推奨制約（必要なら有効化）
        // if (m == 0) throw new IllegalArgumentException("BAモデルでは通常 m >= 1 を想定します");
        // if (m0 < 2 && m > 0) throw new IllegalArgumentException("m>0 の場合、通常 m0>=2 を推奨します");
    
        Random random = new Random(seed);
    
        // 総エッジ数（undirected）
        long totalEdgesLong = (long) m0 * (m0 - 1) / 2 + (long) (N - m0) * m;
        if (totalEdgesLong > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("エッジ数が大きすぎます: " + totalEdgesLong);
        }
        int totalEdges = (int) totalEdgesLong;
    
        // stubList は 2*総エッジ数（両端点）
        int[] stubList = new int[2 * totalEdges];
        int[] s = new int[totalEdges];
        int[] d = new int[totalEdges];
    
        int e = 0;       // 現在のエッジ本数
        int stubLen = 0; // 現在のスタブ数 (=2*e)
    
        // 初期完全グラフ
        for (int i = 0; i < m0; i++) {
            for (int j = i + 1; j < m0; j++) {
                s[e] = i;
                d[e] = j;
                stubList[stubLen++] = i;
                stubList[stubLen++] = j;
                e++;
            }
        }
    
        // 新規ノード追加
        for (int i = m0; i < N; i++) {
            if (m == 0) continue;
    
            if (stubLen == 0) {
                // m>0 なのにスタブが無い（初期辺ゼロ等）。仕様としてどう扱うか決める。
                // ここでは 0 に繋ぐ等のフォールバックにせず、明示的に例外にするのが安全。
                throw new IllegalStateException("優先的選択のためのスタブが存在しません（初期辺が0本です）");
            }
    
            Set<Integer> connected = new HashSet<>(Math.max(16, m * 2));
            while (connected.size() < m) {
                int target = stubList[random.nextInt(stubLen)]; // 有効範囲のみ
                if (target != i) { // 念のため（通常 i は stubList にいない）
                    connected.add(target);
                }
            }
    
            for (int target : connected) {
                s[e] = i;
                d[e] = target;
                stubList[stubLen++] = i;
                stubList[stubLen++] = target;
                e++;
            }
        }
    
        return Graph.fromUndirectedEdgeList("BA", N, s, d);
    }
    

    /**
     * シード省略版
     */
    public static Graph generateBA(int N, int m0, int m) {
        return generateBA(N, m0, m, System.currentTimeMillis());
    }
}