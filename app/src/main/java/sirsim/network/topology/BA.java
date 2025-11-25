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

        Random random = new Random(seed);

        int[] deg = new int[N];
        List<Integer> srcList = new ArrayList<>();
        List<Integer> dstList = new ArrayList<>();

        // 初期完全グラフのエッジを設定
        for (int i = 0; i < m0; i++) {
            for (int j = i + 1; j < m0; j++) {
                srcList.add(i);
                dstList.add(j);
                deg[i]++;
                deg[j]++;
            }
        }

        // 新規ノードの追加
        for (int i = m0; i < N; i++) {
            // 既存のノードのリストを作成（重複を許可）
            List<Integer> existingNodes = new ArrayList<>();
            for (int j = 0; j < i; j++) {
                for (int k = 0; k < deg[j]; k++) {
                    existingNodes.add(j);
                }
            }
            
            // 既に接続したノードを記録（重複接続を避けるため）
            Set<Integer> connected = new HashSet<>();
            
            // m個のエッジを接続
            for (int j = 0; j < m; j++) {
                if (existingNodes.isEmpty()) {
                    break;
                }
                
                // 優先度付き選択（次数に比例）
                // 既に接続したノードはスキップ
                int target = -1;
                int attempts = 0;
                while (attempts < existingNodes.size() * 10) { // 無限ループ防止
                    int r = random.nextInt(existingNodes.size());
                    int candidate = existingNodes.get(r);
                    if (!connected.contains(candidate)) {
                        target = candidate;
                        break;
                    }
                    attempts++;
                }
                
                if (target == -1) {
                    // 接続可能なノードが見つからない場合は終了
                    break;
                }
                
                // エッジを追加
                final int finalTarget = target; // final変数として宣言
                srcList.add(i);
                dstList.add(finalTarget);
                deg[i]++;
                deg[finalTarget]++;
                connected.add(finalTarget);
                
                // 選択されたノードをリストからすべて削除（重複接続を避けるため）
                existingNodes.removeIf(node -> node == finalTarget);
            }
        }

        // 配列に変換
        int numEdges = srcList.size();
        int[] s = new int[numEdges];
        int[] d = new int[numEdges];
        for (int i = 0; i < numEdges; i++) {
            s[i] = srcList.get(i);
            d[i] = dstList.get(i);
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