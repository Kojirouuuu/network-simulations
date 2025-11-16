package sirsim.network;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class Graph {
    public final int n;
    public final int[] rowPtr;
    public final int[] colIdx;
    public final int[] rev;
    public final int[] src;
    public final int m2;

    private Graph(int n, int[] rowPtr, int[] colIdx, int[] rev, int[] src, int m2) {
        this.n = n;
        this.rowPtr = rowPtr;
        this.colIdx = colIdx;
        this.rev = rev;
        this.src = src;
        this.m2 = m2;
    }

    public int degree(int u) { return rowPtr[u + 1] - rowPtr[u]; }
    public int firstArc(int u) { return rowPtr[u]; }
    public int endArc(int u) { return rowPtr[u + 1]; }

    public static Graph fromUndirectedEdgeList(int n, int[] srcs, int[] dsts) {
        if (srcs.length != dsts.length) throw new IllegalArgumentException("srcs/dsts length mismatch");
        final int m = srcs.length;
        int[] deg = new int[n];
        for (int i = 0; i < m; i++) {
            int u = srcs[i], v = dsts[i];
            if (u < 0 || u >= n || v < 0 || v >= n) throw new IllegalArgumentException("invalid edge: " + u + " " + v);
            deg[u]++;
            deg[v]++;
        }

        int[] rowPtr = new int[n + 1];
        for (int u = 0; u < n; u++) rowPtr[u + 1] = rowPtr[u] + deg[u];
        int m2 = rowPtr[n];
        int[] colIdx = new int[m2];
        int[] rev = new int[m2];
        int[] src = new int[m2];
        int[] cur = Arrays.copyOf(rowPtr, rowPtr.length);

        Arrays.fill(rev, -1);
        int[] tmpPos = new int[m];
        for (int i = 0; i < m; i++) {
            int u = srcs[i], v = dsts[i];
            int eUV = cur[u]++; colIdx[eUV] = v; src[eUV] = u;
            int eVU = cur[v]++; colIdx[eVU] = u; src[eVU] = v;
            rev[eUV] = eVU; rev[eVU] = eUV;
            tmpPos[i] = eUV;
        }
        return new Graph(n, rowPtr, colIdx, rev, src, m2);
    }

    public int[] neighbors(int u) {
        int[] neighbors = new int[degree(u)];
        for (int e = firstArc(u); e < endArc(u); e++) {
            neighbors[e - firstArc(u)] = colIdx[e];
        }
        return neighbors;
    }

    /**
     * エッジリストをファイルに書き出します。
     * Pythonのnetworkxで読み込める形式（スペース区切りの2列）で出力します。
     * 
     * @param path 出力先のファイルパス
     * @throws IOException ファイル書き込みエラー
     */
    public void writeEdgelist(Path path) throws IOException {
        // 親ディレクトリが存在しない場合は作成
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(path))) {
            // 無向グラフなので、u < vの条件で各エッジを1回だけ書き出す
            for (int u = 0; u < n; u++) {
                for (int e = firstArc(u); e < endArc(u); e++) {
                    int v = colIdx[e];
                    if (u < v) {  // 重複を避けるため、u < vの条件で書き出す
                        writer.printf("%d %d%n", u, v);
                    }
                }
            }
        }
    }
}
