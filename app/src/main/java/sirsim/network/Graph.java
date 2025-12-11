package sirsim.network;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Graph {
    public final String name;
    public final int n;
    public final int[] rowPtr;
    public final int[] colIdx;
    public final int[] rev;
    public final int[] src;
    public final int m2;

    private Graph(String name, int n, int[] rowPtr, int[] colIdx, int[] rev, int[] src, int m2) {
        this.name = name;
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

    public static Graph fromUndirectedEdgeList(String name, int n, int[] srcs, int[] dsts) {
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
        return new Graph(name, n, rowPtr, colIdx, rev, src, m2);
    }

    /**
     * CSV形式のエッジリストファイルからGraphオブジェクトを作成します。
     * ファイル形式: 1行目はヘッダー行（#で始まるコメント行、または"source,target"形式）。
     * 2行目以降はカンマ区切りの2つの整数でエッジを表す（source,target）。
     * 
     * @param path 読み込むファイルのパス
     * @return 作成されたGraphオブジェクト
     * @throws IOException ファイル読み込みエラー
     */
    public static Graph fromCsvFile(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path);
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("Empty file: " + path);
        }

        List<int[]> edges = new ArrayList<>();
        boolean headerSkipped = false;

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            // ヘッダー行をスキップ（#で始まる、または"source"を含む）
            if (!headerSkipped && (line.startsWith("#") || line.toLowerCase().contains("source"))) {
                headerSkipped = true;
                continue;
            }

            // エッジ行を解析（カンマ区切り）
            String[] parts = line.split(",");
            if (parts.length >= 2) {
                try {
                    int u = Integer.parseInt(parts[0].trim());
                    int v = Integer.parseInt(parts[1].trim());
                    edges.add(new int[]{u, v});
                } catch (NumberFormatException e) {
                    // 数値でない行はスキップ
                    continue;
                }
            }
        }

        if (edges.isEmpty()) {
            throw new IllegalArgumentException("No edges found in file: " + path);
        }

        // エッジリストから最大の頂点番号+1を計算
        int maxVertex = 0;
        for (int[] edge : edges) {
            maxVertex = Math.max(maxVertex, Math.max(edge[0], edge[1]));
        }
        int n = maxVertex + 1;

        // エッジリストを配列に変換
        int m = edges.size();
        int[] srcs = new int[m];
        int[] dsts = new int[m];
        for (int i = 0; i < m; i++) {
            srcs[i] = edges.get(i)[0];
            dsts[i] = edges.get(i)[1];
        }

        String name = path.getParent() != null ? path.getParent().getFileName().toString() : "";
        if (name.endsWith(".csv")) {
            name = name.substring(0, name.length() - 4);
        }
        System.out.println("network name: " + name);
        return fromUndirectedEdgeList(name, n, srcs, dsts);
    }

    /**
     * ファイルからGraphオブジェクトを作成します。
     * ファイル形式: 1行目はコメント行（%で始まる）で頂点数とエッジ数の情報を含む。
     * 2行目以降はスペース区切りの2つの整数でエッジを表す。
     * 
     * @param path 読み込むファイルのパス
     * @return 作成されたGraphオブジェクト
     * @throws IOException ファイル読み込みエラー
     */
    public static Graph fromFile(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path);
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("Empty file: " + path);
        }

        int n = -1;
        List<int[]> edges = new ArrayList<>();
        Pattern vertexPattern = Pattern.compile("(\\d+)\\s+Vertices", Pattern.CASE_INSENSITIVE);

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            // コメント行から頂点数を取得
            if (line.startsWith("%")) {
                Matcher matcher = vertexPattern.matcher(line);
                if (matcher.find()) {
                    n = Integer.parseInt(matcher.group(1));
                }
                continue;
            }

            // エッジ行を解析
            String[] parts = line.split("\\s+");
            if (parts.length >= 2) {
                try {
                    int u = Integer.parseInt(parts[0]);
                    int v = Integer.parseInt(parts[1]);
                    edges.add(new int[]{u, v});
                } catch (NumberFormatException e) {
                    // 数値でない行はスキップ
                    continue;
                }
            }
        }

        if (edges.isEmpty()) {
            throw new IllegalArgumentException("No edges found in file: " + path);
        }

        // 頂点数が取得できなかった場合は、エッジリストから最大の頂点番号+1を計算
        if (n < 0) {
            int maxVertex = 0;
            for (int[] edge : edges) {
                maxVertex = Math.max(maxVertex, Math.max(edge[0], edge[1]));
            }
            n = maxVertex + 1;
        }

        // エッジリストを配列に変換
        int m = edges.size();
        int[] srcs = new int[m];
        int[] dsts = new int[m];
        for (int i = 0; i < m; i++) {
            srcs[i] = edges.get(i)[0];
            dsts[i] = edges.get(i)[1];
        }

        String name = path.getFileName().toString();
        return fromUndirectedEdgeList(name, n, srcs, dsts);
    }

    public int[] neighbors(int u) {
        int[] neighbors = new int[degree(u)];
        for (int e = firstArc(u); e < endArc(u); e++) {
            neighbors[e - firstArc(u)] = colIdx[e];
        }
        return neighbors;
    }

    public double averageDegree() {
        return (double) m2 / n;
    }

    public int maxDegree() {
        int maxDeg = 0;
        for (int u = 0; u < n; u++) {
            int deg = degree(u);
            if (deg > maxDeg) {
                maxDeg = deg;
            }
        }
        return maxDeg;
    }

    public int minDegree() {
        int minDeg = Integer.MAX_VALUE;
        for (int u = 0; u < n; u++) {
            int deg = degree(u);
            if (deg < minDeg) {
                minDeg = deg;
            }
        }
        return minDeg;
    }

    public void printInfo() {
        System.out.printf("Graph: %s%n", name);
        System.out.printf("  Nodes: %d%n", n);
        System.out.printf("  Edges: %d%n", m2 / 2);
        System.out.printf("  Avg. degree: %.4f%n", averageDegree());
        System.out.printf("  Min. degree: %d%n", minDegree());
        System.out.printf("  Max. degree: %d%n", maxDegree());
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
