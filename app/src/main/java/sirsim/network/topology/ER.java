package sirsim.network.topology;

import sirsim.network.Graph;
import java.util.*;

public class ER {
    /**
     * ERモデル（Erdős–Rényi型ランダムグラフ）を生成
     * @param N ノード数
     * @param p エッジ生成確率（0.0〜1.0）
     * @param seed 乱数シード（省略可）
     * @return 生成されたGraphインスタンス
     */
    public static Graph generateERFromP(int N, double p, long seed) {
        if (N <= 0) throw new IllegalArgumentException("ノード数Nは正の整数である必要があります");
        if (p < 0.0 || p > 1.0) throw new IllegalArgumentException("確率pは0.0〜1.0の範囲で指定してください");

        Random random = new Random(seed);

        List<Integer> src = new ArrayList<>();
        List<Integer> dst = new ArrayList<>();

        // エッジ生成
        for (int i = 0; i < N; i++) {
            for (int j = i + 1; j < N; j++) {
                if (random.nextDouble() < p) {
                    src.add(i);
                    dst.add(j);
                }
            }
        }

        int m = src.size();
        int[] s = new int[m];
        int[] d = new int[m];
        for (int k = 0; k < m; k++) {
            s[k] = src.get(k);
            d[k] = dst.get(k);
        }
        return Graph.fromUndirectedEdgeList(N, s, d);
    }
    /**
     * シード省略版
     */
    public static Graph generateERFromP(int N, double p) {
        return generateERFromP(N, p, System.currentTimeMillis());
    }

    public static Graph generateERFromKAve(int N, double kAve, long seed) {
        Random random = new Random(seed);
        int maxEdges = (int) Math.floor(N * kAve);
        int[] rawEdgeList = new int[2 * maxEdges];
        Arrays.fill(rawEdgeList, -1);
        int edgeCount = 0;

        // 重複エッジと自己ループを避けながらエッジをランダムに選択
        Set<String> selectedEdges = new HashSet<>();
        int m = maxEdges / 2;
        int[] s = new int[m];
        int[] d = new int[m];

        while (selectedEdges.size() < m) {
            int u = random.nextInt(N);
            int v = random.nextInt(N);
            if (u == v) continue;  // 自己ループ除外

            String edgeKey = (u < v) ? u + "-" + v : v + "-" + u;
            if (!selectedEdges.contains(edgeKey)) {
                selectedEdges.add(edgeKey);
                s[edgeCount] = u;
                d[edgeCount] = v;
                edgeCount++;
            }
        }

        return Graph.fromUndirectedEdgeList(N, s, d);
    }

    public static Graph generateERFromKAve(int N, double kAve) {
        return generateERFromKAve(N, kAve, System.currentTimeMillis());
    }
}
