package sirsim;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.TreeMap;

/**
 * LFR (Lancichinetti-Fortunato-Radicchi) ベンチマークネットワーク生成器
 * 
 * コミュニティ構造を持つスケールフリーネットワークを生成します。
 * - コミュニティサイズはべき分布（指数ζ）に従う
 * - 次数分布はべき分布（指数γ）に従う
 * - 各頂点の辺のうち、μの割合がコミュニティ外に接続される
 */
public class LFR {
    private final int N;
    private final double zeta;
    private final double gamma;
    private final double mu;

    private final int M;
    private final int[] rowPtr;
    private final int[] colIdx;
    private final int[] rev;
    private final int[] src;
    private final int[] communityOfNode;

    private LFR(int N, double zeta, double gamma, double mu, int M, int[] rowPtr, int[] colIdx, int[] rev, int[] src, int[] communityOfNode) {
        this.N = N;
        this.zeta = zeta;
        this.gamma = gamma;
        this.mu = mu;
        this.M = M;
        this.rowPtr = rowPtr;
        this.colIdx = colIdx;
        this.rev = rev;
        this.src = src;
        this.communityOfNode = communityOfNode;
    }

    // ============================================================================
    // パブリックAPI
    // ============================================================================

    /**
     * LFRグラフを生成する
     * 
     * @param N 頂点数
     * @param zeta コミュニティサイズ分布のべき指数（P(s) ∝ s^(-ζ)）
     * @param gamma 次数分布のべき指数（P(k) ∝ k^(-γ)）
     * @param mu コミュニティ間の辺の割合（0 < mu < 1）
     * @param sMin 最小コミュニティサイズ
     * @param sMax 最大コミュニティサイズ
     * @param kMin 最小次数
     * @param kMax 最大次数
     * @param seed 乱数シード
     * @return 生成されたLFRグラフ
     */
    public static LFR generate(int N, double zeta, double gamma, double mu,
                                int sMin, int sMax, int kMin, int kMax, long seed) {
        return generate(N, zeta, gamma, mu, sMin, sMax, kMin, kMax, seed, 100, 100, 100);
    }

    /**
     * LFRグラフを生成する（詳細パラメータ付き）
     * 
     * @param N 頂点数
     * @param zeta コミュニティサイズ分布のべき指数
     * @param gamma 次数分布のべき指数
     * @param mu コミュニティ間の辺の割合
     * @param sMin 最小コミュニティサイズ
     * @param sMax 最大コミュニティサイズ
     * @param kMin 最小次数
     * @param kMax 最大次数
     * @param seed 乱数シード
     * @param maxRetriesPerCommunity コミュニティごとの内部辺生成の最大リトライ回数
     * @param maxRestarts 外部辺生成の最大リスタート回数
     * @param maxSwapAttemptsPerBadPair 不正ペア修正の最大試行回数
     * @return 生成されたLFRグラフ
     */
    public static LFR generate(int N, double zeta, double gamma, double mu,
                                int sMin, int sMax, int kMin, int kMax, long seed,
                                int maxRetriesPerCommunity, int maxRestarts, int maxSwapAttemptsPerBadPair) {
        // 入力パラメータ検証
        if (!(mu > 0.0 && mu < 1.0)) {
            throw new IllegalArgumentException("mu must satisfy 0 < mu < 1");
        }
        if (zeta == 1.0 || gamma == 1.0) {
            throw new IllegalArgumentException("power-law exponent must not be 1 (sampler is singular at 1)");
        }
        if (N <= 0) throw new IllegalArgumentException("N must be positive");
        if (sMin < 2 || sMax < sMin) throw new IllegalArgumentException("community sizes must satisfy 2 <= sMin <= sMax");
        if (kMin < 1 || kMax < kMin) throw new IllegalArgumentException("degrees must satisfy 1 <= kMin <= kMax");
        if (kMax > Math.max(1, N - 1)) throw new IllegalArgumentException("kMax must be <= N-1");
        // 1. コミュニティサイズを生成
        int[] commSizes = generateCommunitySizes(N, zeta, sMin, sMax, seed);
        
        // 2. 次数列を生成
        int[] degrees = generateDegreeSequence(N, gamma, kMin, kMax, seed + 1);
        
        // 3. 頂点をコミュニティに割り当て
        CommunityAssignment asg = assignNodesToCommunities(commSizes, degrees, mu, seed + 2);
        
        // 4. コミュニティ内次数を構築
        int[] kIn = buildInternalDegrees(asg, degrees, mu, seed + 3);
        
        // 5. 内部辺を生成
        IntPairEdgeList internalEdges = generateInternalEdges(asg, kIn, seed + 4, maxRetriesPerCommunity);
        
        // 6. 外部辺を生成
        int[] kOut = buildExternalDegrees(degrees, kIn);
        // 外部ユニーク隣接数上限チェック（kOut[v] <= N - size(comm_v)）
        for (int c = 0; c < asg.C; c++) {
            int start = asg.commStart[c];
            int end = asg.commStart[c + 1];
            int size = asg.commSizes[c];
            int extCap = N - size;
            for (int i = start; i < end; i++) {
                int v = asg.nodesByComm[i];
                if (kOut[v] > extCap) {
                    throw new IllegalStateException("External degree exceeds capacity: node=" + v +
                            ", kOut=" + kOut[v] + ", capacity=" + extCap + ", communitySize=" + size);
                }
            }
        }

        IntPairEdgeList externalEdges = generateExternalEdges(asg, kOut, internalEdges, seed + 5, maxRestarts, maxSwapAttemptsPerBadPair);
        // 達成μの参考値を出力
        int intE = internalEdges.m;
        int extE = externalEdges.m;
        int totE = intE + extE;
        if (totE > 0) {
            double muHat = (double) extE / (double) totE;
            System.out.println(String.format("[LFR] mu target=%.4f, achieved=%.4f (E_int=%d, E_ext=%d)", mu, muHat, intE, extE));
        }
        
        // 7. CSR形式のグラフを構築
        return buildCSRGraph(N, zeta, gamma, mu, internalEdges, externalEdges, asg.commOfNode);
    }

    // ============================================================================
    // ゲッター
    // ============================================================================

    public int getN() { return N; }
    public double getZeta() { return zeta; }
    public double getGamma() { return gamma; }
    public double getMu() { return mu; }
    public int getM() { return M; }
    public int[] getRowPtr() { return rowPtr; }
    public int[] getColIdx() { return colIdx; }
    public int[] getRev() { return rev; }
    public int[] getSrc() { return src; }
    public int[] getCommunityOfNode() { return communityOfNode; }

    /**
     * 辺リストをテキストに書き出す（無向・重複なし）。
     * 形式: 先頭行に "%N Vertices, E Edges"、以降に "u v"（0始まり、u < v のみ）。
     */
    public void writeEdgeList(Path path) throws IOException {
        // 辺数をカウント（u < v のみ）
        int E = 0;
        for (int u = 0; u < N; u++) {
            for (int i = rowPtr[u]; i < rowPtr[u + 1]; i++) {
                int v = colIdx[i];
                if (u < v) E++;
            }
        }

        try (var w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            w.write("%" + N + " Vertices, " + E + " Edges\n");
            for (int u = 0; u < N; u++) {
                for (int i = rowPtr[u]; i < rowPtr[u + 1]; i++) {
                    int v = colIdx[i];
                    if (u < v) {
                        w.write(u + " " + v + "\n");
                    }
                }
            }
        }
    }

    public void writeEdgeList(String path) throws IOException { writeEdgeList(Path.of(path)); }

    /**
     * CSV形式でエッジリストを書き出す（Gephiで読み込み可能）
     * 形式: ヘッダー行 "Source,Target"、以降に "u,v"（0始まり、u < v のみ）
     */
    public void writeEdgeListCSV(Path path) throws IOException {
        try (var w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            w.write("Source,Target\n");
            for (int u = 0; u < N; u++) {
                for (int i = rowPtr[u]; i < rowPtr[u + 1]; i++) {
                    int v = colIdx[i];
                    if (u < v) {
                        w.write(u + "," + v + "\n");
                    }
                }
            }
        }
    }

    public void writeEdgeListCSV(String path) throws IOException {
        writeEdgeListCSV(Path.of(path));
    }

    /**
     * GEXF形式でグラフを書き出す（Gephiで読み込み可能）
     * コミュニティ情報も含める
     */
    public void writeGEXF(Path path) throws IOException {
        // 辺数をカウント（u < v のみ）
        int E = 0;
        for (int u = 0; u < N; u++) {
            for (int i = rowPtr[u]; i < rowPtr[u + 1]; i++) {
                int v = colIdx[i];
                if (u < v) E++;
            }
        }

        try (var w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            w.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            w.write("<gexf xmlns=\"http://www.gexf.net/1.3\" version=\"1.3\" xmlns:viz=\"http://www.gexf.net/1.3/viz\">\n");
            w.write("  <meta lastmodifieddate=\"" + java.time.LocalDate.now() + "\">\n");
            w.write("    <creator>LFR Generator</creator>\n");
            w.write("    <description>LFR Benchmark Network</description>\n");
            w.write("  </meta>\n");
            w.write("  <graph mode=\"static\" defaultedgetype=\"undirected\">\n");
            
            // 属性定義（コミュニティID）
            if (communityOfNode != null) {
                w.write("    <attributes class=\"node\">\n");
                w.write("      <attribute id=\"0\" title=\"Community\" type=\"integer\"/>\n");
                w.write("    </attributes>\n");
            }
            
            // ノード
            w.write("    <nodes>\n");
            for (int u = 0; u < N; u++) {
                int deg = rowPtr[u + 1] - rowPtr[u];
                w.write("      <node id=\"" + u + "\" label=\"Node " + u + "\">\n");
                if (communityOfNode != null && u < communityOfNode.length) {
                    w.write("        <attvalues>\n");
                    w.write("          <attvalue for=\"0\" value=\"" + communityOfNode[u] + "\"/>\n");
                    w.write("        </attvalues>\n");
                }
                // サイズ属性（次数に比例）
                w.write("        <viz:size value=\"" + (5.0 + deg * 0.5) + "\"/>\n");
                w.write("      </node>\n");
            }
            w.write("    </nodes>\n");
            
            // エッジ
            w.write("    <edges>\n");
            int edgeId = 0;
            for (int u = 0; u < N; u++) {
                for (int i = rowPtr[u]; i < rowPtr[u + 1]; i++) {
                    int v = colIdx[i];
                    if (u < v) {
                        w.write("      <edge id=\"" + edgeId + "\" source=\"" + u + "\" target=\"" + v + "\"/>\n");
                        edgeId++;
                    }
                }
            }
            w.write("    </edges>\n");
            
            w.write("  </graph>\n");
            w.write("</gexf>\n");
        }
    }

    public void writeGEXF(String path) throws IOException {
        writeGEXF(Path.of(path));
    }

    // ============================================================================
    // べき分布サンプリング
    // ============================================================================

    /**
     * べき分布 P(x) ∝ x^(-power) に従う整数をサンプリング
     */
    private static int samplePowerLawInt(double power, int min, int max, Random rnd) {
        double u = rnd.nextDouble();
        double a = Math.pow(min, 1.0 - power);
        double b = Math.pow(max, 1.0 - power);
        double x = Math.pow(u * (b - a) + a, 1.0 / (1.0 - power));

        int s = (int) Math.floor(x + 1e-12);
        if (s < min) s = min;
        if (s > max) s = max;
        return s;
    }

    // ============================================================================
    // コミュニティサイズ生成
    // ============================================================================

    /**
     * コミュニティサイズ列を生成（P(s) ∝ s^(-ζ)）
     */
    private static int[] generateCommunitySizes(int N, double zeta, int sMin, int sMax, long seed) {
        Random rnd = new Random(seed);
        List<Integer> sizes = new ArrayList<>();
        int remaining = N;

        while (remaining > 0) {
            if (remaining <= sMax && remaining >= sMin) {
                sizes.add(remaining);
                remaining = 0;
                break;
            }

            // 残りがsMinより小さい場合、既存コミュニティから頂点を移動
            if (remaining < sMin) {
                int need = sMin - remaining;
                for (int i = sizes.size() - 1; i >= 0 && need > 0; i--) {
                    int si = sizes.get(i);
                    int canGive = si - sMin;
                    if (canGive <= 0) continue;
                    int give = Math.min(canGive, need);
                    sizes.set(i, si - give);
                    need -= give;
                    remaining += give;
                }
                if (remaining < sMin) {
                    throw new IllegalStateException(
                        "Cannot satisfy constraints: try smaller sMin or larger N / different parameters");
                }
                continue;
            }

            // べき分布に従ってコミュニティサイズをサンプリング
            int s = samplePowerLawInt(zeta, sMin, sMax, rnd);
            int tail = remaining - s;
            
            // 残りがsMinより小さくなる場合は調整
            if (tail > 0 && tail < sMin) {
                int maxSAllowed = remaining - sMin;
                s = Math.min(s, maxSAllowed);
                if (s < sMin) {
                    s = sMin;
                }
            }

            if (s > remaining) s = remaining;
            sizes.add(s);
            remaining -= s;
        }

        // 検証
        int sum = 0;
        for (int s : sizes) {
            if (s < sMin || s > sMax) {
                throw new IllegalStateException("Generated size out of bounds: " + s);
            }
            sum += s;
        }
        if (sum != N) {
            throw new IllegalStateException("Generated sizes do not sum up to N: sum=" + sum + ", N=" + N);
        }

        int[] arr = new int[sizes.size()];
        for (int i = 0; i < sizes.size(); i++) arr[i] = sizes.get(i);
        return arr;
    }

    // ============================================================================
    // 次数列生成
    // ============================================================================

    /**
     * 次数列を生成（P(k) ∝ k^(-γ)）
     */
    private static int[] generateDegreeSequence(int N, double gamma, int kMin, int kMax, long seed) {
        Random rnd = new Random(seed);
        int[] degrees = new int[N];

        long sum = 0;
        for (int i = 0; i < N; i++) {
            degrees[i] = samplePowerLawInt(gamma, kMin, kMax, rnd);
            sum += degrees[i];
        }

        // 次数の合計が奇数の場合、偶数化
        if ((sum % 2) != 0) {
            boolean fixed = false;
            for (int tries = 0; tries < 10_000 && !fixed; tries++) {
                int i = rnd.nextInt(N);
                if (degrees[i] < kMax) {
                    degrees[i]++;
                    sum++;
                    fixed = true;
                    break;
                }
            }
            if (!fixed) {
                for (int tries = 0; tries < 10_000 && !fixed; tries++) {
                    int i = rnd.nextInt(N);
                    if (degrees[i] > kMin) {
                        degrees[i]--;
                        sum--;
                        fixed = true;
                        break;
                    }
                }
            }
            if (!fixed) {
                throw new IllegalStateException(
                    "Failed to fix degree sequence: try smaller kMin or larger N / different parameters");
            }

            // 検証
            long checkSum = 0;
            int minDeg = Integer.MAX_VALUE;
            int maxDeg = Integer.MIN_VALUE;
            for (int d : degrees) {
                if (d < kMin || d > kMax) {
                    throw new IllegalStateException("Generated degree out of bounds: " + d);
                }
                checkSum += d;
                minDeg = Math.min(minDeg, d);
                maxDeg = Math.max(maxDeg, d);
            }
            if (checkSum != sum) {
                throw new IllegalStateException(
                    "Generated degree sequence does not match the target sum: sum=" + checkSum + ", target=" + sum);
            }
        }

        return degrees;
    }

    // ============================================================================
    // コミュニティ割り当て
    // ============================================================================

    /**
     * コミュニティ割り当て情報
     */
    private static final class CommunityAssignment {
        final int C;
        final int[] commSizes;
        final int[] commStart;
        final int[] nodesByComm;
        final int[] commOfNode;

        CommunityAssignment(int C, int[] commSizes, int[] commStart, int[] nodesByComm, int[] commOfNode) {
            this.C = C;
            this.commSizes = commSizes;
            this.commStart = commStart;
            this.nodesByComm = nodesByComm;
            this.commOfNode = commOfNode;
        }
    }

    /**
     * コミュニティ内次数を計算
     */
    private static int internalDegree(int k, double mu) {
        double x = (1.0 - mu) * k;
        int kin = (int) Math.floor(x + 0.5);
        if (kin < 0) kin = 0;
        if (kin > k) kin = k;
        return kin;
    }

    /**
     * 頂点をコミュニティに割り当て
     */
    private static CommunityAssignment assignNodesToCommunities(int[] commSizes, int[] degrees, double mu, long seed) {
        final int N = degrees.length;
        int sum = 0;
        for (int s : commSizes) sum += s;
        if (sum != N) {
            throw new IllegalStateException("Generated community sizes do not sum up to N: sum=" + sum + ", N=" + N);
        }

        // 最大必要サイズを事前チェック
        int maxNeed = 0;
        for (int k : degrees) {
            int kin = internalDegree(k, mu);
            int need = kin + 1;
            if (need > maxNeed) maxNeed = need;
        }

        int maxComm = 0;
        int minComm = Integer.MAX_VALUE;
        for (int s : commSizes) {
            maxComm = Math.max(maxComm, s);
            minComm = Math.min(minComm, s);
        }

        if (maxNeed > maxComm) {
            throw new IllegalStateException(
                "Maximum community size is too small to fit the required internal degrees: maxNeed=" + maxNeed
                    + ", maxComm=" + maxComm);
        }

        // ログ出力: コミュニティ数やサイズレンジ、必要サイズなど
        int C = commSizes.length;
        System.out.println(String.format("[LFR] Communities: C=%d, size[min=%d, max=%d], required(min=%d, maxNeed=%d)",
                C, minComm, maxComm, 1, maxNeed));

        // コミュニティの開始オフセットを計算
        int[] commStart = new int[C + 1];
        for (int c = 0; c < C; c++) commStart[c + 1] = commStart[c] + commSizes[c];

        int[] nodesByComm = new int[N];
        int[] writePos = Arrays.copyOf(commStart, C);
        int[] commOfNode = new int[N];

        // サイズ別に未充填コミュニティを管理（TreeMapで効率的に検索）
        TreeMap<Integer, ArrayList<Integer>> bySize = new TreeMap<>();
        int[] remainingSlots = new int[C];
        for (int c = 0; c < C; c++) {
            bySize.computeIfAbsent(commSizes[c], __ -> new ArrayList<>()).addLast(c);
            remainingSlots[c] = commSizes[c];
        }

        // ノードを次数降順に並べる（次数の大きい頂点から割り当て）
        Integer[] order = new Integer[N];
        for (int i = 0; i < N; i++) order[i] = i;
        Arrays.sort(order, (a, b) -> Integer.compare(degrees[b], degrees[a]));

        for (int idx = 0; idx < N; idx++) {
            int v = order[idx];
            int k = degrees[v];
            int kin = internalDegree(k, mu);
            int needSize = kin + 1;

            // needSize以上の最小サイズのコミュニティを検索
            Integer key = bySize.ceilingKey(needSize);
            
            // 見つからない場合、残りの最大サイズのコミュニティを使用（needSizeを調整）
            if (key == null) {
                if (bySize.isEmpty()) {
                    int totalRemaining = 0;
                    for (int c = 0; c < C; c++) totalRemaining += remainingSlots[c];
                    int unassigned = N - idx;
                    throw new IllegalStateException(
                        String.format("No communities available for node %d: needSize=%d, k=%d, kin=%d, " +
                                     "unassigned=%d, totalRemainingSlots=%d",
                                     v, needSize, k, kin, unassigned, totalRemaining));
                }
                // 残りの最大サイズのコミュニティを使用
                key = bySize.lastKey();
                // needSizeを実際の残りスロットに合わせて調整（制約を緩和）
                if (key < needSize) {
                    // この場合、コミュニティ内次数の制約を満たせない可能性があるが、
                    // 後でbuildInternalDegreesで調整される
                }
            }

            ArrayList<Integer> q = bySize.get(key);
            int c = q.removeFirst();

            if (q.isEmpty()) bySize.remove(key);

            // 割り当て
            commOfNode[v] = c;
            nodesByComm[writePos[c]++] = v;

            // スロット更新（まだ空きがあれば新しいサイズのキューへ戻す）
            remainingSlots[c]--;
            if (remainingSlots[c] > 0) {
                int newKey = remainingSlots[c];
                bySize.computeIfAbsent(newKey, __ -> new ArrayList<>()).addLast(c);
            }
        }

        // 検証
        for (int c = 0; c < C; c++) {
            if (writePos[c] != commStart[c] + commSizes[c]) {
                throw new IllegalStateException("Community " + c + " not filled correctly.");
            }
        }

        return new CommunityAssignment(C, commSizes, commStart, nodesByComm, commOfNode);
    }

    // ============================================================================
    // 次数構築
    // ============================================================================

    /**
     * コミュニティ内次数を柔軟に構築：
     * - 目標値 internalDegree(k, mu) を初期値に採用
     * - 各コミュニティごとに [0, min(k, size-1)] の範囲で調整
     * - 偶数性を保ちながら Havel–Hakimi によるグラフ実現可能性を満たすまで最大次数側から減算
     * （必要なら全ゼロ列まで縮退するため、常に成功する）
     */
    private static int[] buildInternalDegrees(CommunityAssignment asg, int[] degrees, double mu, long seed) {
        final int N = degrees.length;
        int[] kIn = new int[N];
        for (int v = 0; v < N; v++) kIn[v] = internalDegree(degrees[v], mu);

        int globalMaxCap = 0;
        int globalMaxKinInit = 0;
        for (int c = 0; c < asg.C; c++) {
            int start = asg.commStart[c];
            int end = asg.commStart[c + 1];
            int size = asg.commSizes[c];

            // コミュニティ内のノードと境界
            int m = end - start;
            int[] vs = new int[m];
            int[] cap = new int[m];
            int[] low = new int[m];
            int[] d = new int[m];
            for (int i = 0; i < m; i++) {
                int v = asg.nodesByComm[start + i];
                vs[i] = v;
                cap[i] = Math.min(degrees[v], size - 1);
                // 外部ユニーク隣接の上限制約から導かれる下限
                int lower = Math.max(0, degrees[v] - (N - size));
                if (lower > cap[i]) lower = cap[i];
                low[i] = lower;
                int x = kIn[v];
                if (x < lower) x = lower;
                if (x > cap[i]) x = cap[i];
                d[i] = x;
                if (cap[i] > globalMaxCap) globalMaxCap = cap[i];
                if (x > globalMaxKinInit) globalMaxKinInit = x;
            }

            // 偶数性確保：和が奇数なら最大要素を1減算（正のものが無ければ0のまま）
            int sum = 0;
            for (int x : d) sum += x;
            if ((sum & 1) == 1) {
                int idxMax = argmaxAbove(d, low);
                if (idxMax >= 0) {
                    d[idxMax] -= 1;
                    sum -= 1;
                }
            }

            // Havel–Hakimiを満たすまで上位から度数を1ずつ減らす
            // （全ゼロまで減らせば必ず満たす）
            int guard = m * (size + 5);
            while (!isGraphicalHH(d)) {
                int idxMax = argmaxAbove(d, low);
                if (idxMax < 0) break; // これ以上減らせない
                d[idxMax] -= 1;
                sum -= 1;
                // 偶数性維持のため、和が奇数になったら別の要素を1減算（下限を割らない）
                if ((sum & 1) == 1) {
                    int idx2 = argmaxAboveExcept(d, low, idxMax);
                    if (idx2 >= 0) {
                        d[idx2] -= 1;
                        sum -= 1;
                    }
                }
                guard--;
                if (guard <= 0) break; // セーフガード
            }

            // 書き戻し
            int localMax = 0;
            int localSum = 0;
            for (int i = 0; i < m; i++) {
                kIn[vs[i]] = d[i];
                if (d[i] > localMax) localMax = d[i];
                localSum += d[i];
            }
            System.out.println(String.format("[LFR] Community %d: size=%d, k_in[max=%d, sum=%d]", c, size, localMax, localSum));
        }

        // グローバルの参考値
        int globalMaxKin = 0;
        long sumKin = 0;
        for (int v = 0; v < N; v++) { globalMaxKin = Math.max(globalMaxKin, kIn[v]); sumKin += kIn[v]; }
        System.out.println(String.format("[LFR] k_in summary: maxInit=%d, maxCap=%d, maxFinal=%d, sum=%d",
                globalMaxKinInit, globalMaxCap, globalMaxKin, sumKin));
        return kIn;
    }

    private static int argmaxAbove(int[] a, int[] low) {
        int idx = -1, best = Integer.MIN_VALUE;
        for (int i = 0; i < a.length; i++) {
            if (a[i] > low[i] && a[i] > best) { best = a[i]; idx = i; }
        }
        return idx;
    }

    private static int argmaxAboveExcept(int[] a, int[] low, int except) {
        int idx = -1, best = Integer.MIN_VALUE;
        for (int i = 0; i < a.length; i++) {
            if (i == except) continue;
            if (a[i] > low[i] && a[i] > best) { best = a[i]; idx = i; }
        }
        return idx;
    }

    /**
     * Havel–Hakimi 判定（入力は破壊しない）
     */
    private static boolean isGraphicalHH(int[] degrees) {
        int n = degrees.length;
        int[] d = Arrays.copyOf(degrees, n);
        // 負や上限超えは上位側で弾いているが、ここでも防御
        for (int x : d) if (x < 0) return false;
        // 反復：降順ソート→先頭hを1ずつ減らす
        while (true) {
            Arrays.sort(d);
            // 昇順なので末尾が最大
            int h = d[n - 1];
            if (h == 0) return true; // 全て0
            d[n - 1] = 0;
            if (h < 0 || h >= n) return false;
            // 末尾からh個に対し-1（自身を除くので n-2 まで）
            for (int i = 0; i < h; i++) {
                int idx = n - 2 - i;
                if (idx < 0) return false;
                d[idx] -= 1;
                if (d[idx] < 0) return false;
            }
        }
    }

    /**
     * コミュニティ外次数を構築
     */
    private static int[] buildExternalDegrees(int[] deg, int[] kIn) {
        int N = deg.length;
        int[] kOut = new int[N];
        long sum = 0;
        for (int v = 0; v < N; v++) {
            int kout = deg[v] - kIn[v];
            if (kout < 0) throw new IllegalStateException("kOut < 0 at node " + v);
            kOut[v] = kout;
            sum += kout;
        }
        if ((sum & 1L) == 1L) {
            throw new IllegalStateException("Sum of external degrees is odd (should not happen). sum=" + sum);
        }
        return kOut;
    }

    // ============================================================================
    // 辺生成
    // ============================================================================

    /**
     * 辺リスト（動的配列）
     */
    private static final class IntPairEdgeList {
        int[] u;
        int[] v;
        int m;

        IntPairEdgeList(int cap) {
            u = new int[Math.max(1, cap)];
            v = new int[Math.max(1, cap)];
            m = 0;
        }

        void add(int a, int b) {
            if (m == u.length) {
                int newCap = u.length * 2;
                u = Arrays.copyOf(u, newCap);
                v = Arrays.copyOf(v, newCap);
            }
            u[m] = a;
            v[m] = b;
            m++;
        }
    }

    /**
     * 辺をlong値にパック（重複検出用）
     */
    private static long packEdge(int a, int b) {
        int x = Math.min(a, b);
        int y = Math.max(a, b);
        return (((long) x) << 32) | (y & 0xffffffffL);
    }

    /**
     * コミュニティ内辺を生成
     */
    private static IntPairEdgeList generateInternalEdges(CommunityAssignment asg, int[] kIn, long seed,
                                                          int maxRetriesPerCommunity) {
        long sumKin = 0;
        for (int x : kIn) sumKin += x;
        IntPairEdgeList edges = new IntPairEdgeList((int) Math.min(Integer.MAX_VALUE, Math.max(16, sumKin / 2)));

        // コミュニティ単位で Havel–Hakimi 構成で確実に単純グラフを構築
        for (int c = 0; c < asg.C; c++) {
            int start = asg.commStart[c];
            int end = asg.commStart[c + 1];
            int size = asg.commSizes[c];

            int m = end - start;
            if (m <= 1) continue;

            int[] vs = new int[m];
            int[] d = new int[m];
            int stubCount = 0;
            for (int i = 0; i < m; i++) {
                int v = asg.nodesByComm[start + i];
                vs[i] = v;
                int kin = kIn[v];
                if (kin < 0 || kin > size - 1) {
                    throw new IllegalStateException("Internal degree out of bounds: kin=" + kin + ", size=" + size);
                }
                d[i] = kin;
                stubCount += kin;
            }

            if (stubCount == 0) continue;
            if ((stubCount & 1) == 1) throw new IllegalStateException("Stub count is odd: stubCount=" + stubCount);

            // Havel–Hakimi 構成
            // 作業配列：インデックス0..m-1
            // エッジは (vs[u], vs[v]) で追加
            int[] rem = Arrays.copyOf(d, m);
            // 反復：残度が0になるまで
            while (true) {
                // 降順にインデックスを並べ替え（安価に単純ソート）
                Integer[] order = new Integer[m];
                for (int i = 0; i < m; i++) order[i] = i;
                Arrays.sort(order, (a, b) -> Integer.compare(rem[b], rem[a]));

                int u = order[0];
                int h = rem[u];
                if (h == 0) break; // 完了
                if (h < 0 || h >= m) {
                    throw new IllegalStateException("Failed to generate simple internal edges for community " + c);
                }
                rem[u] = 0;
                for (int i = 1; i <= h; i++) {
                    int v = order[i];
                    rem[v] -= 1;
                    if (rem[v] < 0) {
                        throw new IllegalStateException("Failed to generate simple internal edges for community " + c);
                    }
                    edges.add(vs[u], vs[v]);
                }
            }
        }

        return edges;
    }

    /**
     * コミュニティ外辺を生成
     */
    private static IntPairEdgeList generateExternalEdges(CommunityAssignment asg, int[] kOut,
                                                          IntPairEdgeList internalEdges, long seed, int maxRestarts,
                                                          int maxSwapAttemptsPerBadPair) {

        final int N = kOut.length;

        // 既存（内部）も含めた重複禁止セット
        HashSet<Long> used = new HashSet<>();
        for (int i = 0; i < internalEdges.m; i++) {
            used.add(packEdge(internalEdges.u[i], internalEdges.v[i]));
        }

        // 外部スタブ総数
        long stubSumL = 0;
        for (int x : kOut) stubSumL += x;
        if ((stubSumL & 1L) == 1L) throw new IllegalStateException("Odd external stub sum");
        if (stubSumL > Integer.MAX_VALUE) throw new IllegalStateException("Too many stubs for int[]");

        int stubSum = (int) stubSumL;
        int targetEdges = stubSum / 2;

        Random rnd = new Random(seed);

        // リスタート方式
        for (int restart = 0; restart < maxRestarts; restart++) {

            // スタブ配列を構築
            int[] stubs = new int[stubSum];
            int idx = 0;
            for (int v = 0; v < N; v++) {
                int t = kOut[v];
                for (int j = 0; j < t; j++) stubs[idx++] = v;
            }

            // シャッフル
            for (int i = stubs.length - 1; i > 0; i--) {
                int j = rnd.nextInt(i + 1);
                int tmp = stubs[i];
                stubs[i] = stubs[j];
                stubs[j] = tmp;
            }

            // 外部辺を一時的に構築
            int[] eu = new int[targetEdges];
            int[] ev = new int[targetEdges];
            int m = 0;

            // リスタート時に内部辺のみの状態に戻すため、外部で追加したキーを記録
            LongArrayList addedKeys = new LongArrayList(targetEdges);

            boolean failed = false;

            for (int p = 0; p < stubs.length; p += 2) {
                int a = stubs[p];
                int b = stubs[p + 1];

                if (isValidExternalEdge(a, b, asg, used)) {
                    eu[m] = a;
                    ev[m] = b;
                    long key = packEdge(a, b);
                    used.add(key);
                    addedKeys.add(key);
                    m++;
                    continue;
                }

                // 不正ペアをswapで修正
                boolean fixed = false;

                if (m > 0) {
                    for (int attempt = 0; attempt < maxSwapAttemptsPerBadPair && !fixed; attempt++) {
                        int q = rnd.nextInt(m);
                        int x = eu[q];
                        int y = ev[q];

                        // swap案1: (a,y) と (x,b)
                        if (isValidExternalEdge(a, y, asg, used) && isValidExternalEdge(x, b, asg, used)) {
                            long oldKey = packEdge(x, y);
                            used.remove(oldKey);
                            addedKeys.removeOne(oldKey);

                            eu[q] = a;
                            ev[q] = y;
                            long k1 = packEdge(a, y);
                            used.add(k1);
                            addedKeys.add(k1);

                            eu[m] = x;
                            ev[m] = b;
                            long k2 = packEdge(x, b);
                            used.add(k2);
                            addedKeys.add(k2);

                            m++;
                            fixed = true;
                            break;
                        }

                        // swap案2: (a,x) と (y,b)
                        if (isValidExternalEdge(a, x, asg, used) && isValidExternalEdge(y, b, asg, used)) {
                            long oldKey = packEdge(x, y);
                            used.remove(oldKey);
                            addedKeys.removeOne(oldKey);

                            eu[q] = a;
                            ev[q] = x;
                            long k1 = packEdge(a, x);
                            used.add(k1);
                            addedKeys.add(k1);

                            eu[m] = y;
                            ev[m] = b;
                            long k2 = packEdge(y, b);
                            used.add(k2);
                            addedKeys.add(k2);

                            m++;
                            fixed = true;
                            break;
                        }
                    }
                }

                if (!fixed) {
                    failed = true;
                    break;
                }
            }

            if (!failed && m == targetEdges) {
                IntPairEdgeList external = new IntPairEdgeList(targetEdges);
                for (int i = 0; i < m; i++) external.add(eu[i], ev[i]);
                return external;
            }

            // リスタート：外部で追加したusedを巻き戻す
            for (int i = 0; i < addedKeys.size; i++) used.remove(addedKeys.data[i]);
        }

        throw new IllegalStateException("Failed to generate external edges after maxRestarts=" + maxRestarts);
    }

    /**
     * 外部辺が有効かチェック
     */
    private static boolean isValidExternalEdge(int a, int b, CommunityAssignment asg, HashSet<Long> used) {
        if (a == b) return false;
        if (asg.commOfNode[a] == asg.commOfNode[b]) return false;
        long key = packEdge(a, b);
        return !used.contains(key);
    }

    /**
     * long動的配列（boxing回避）
     */
    private static final class LongArrayList {
        long[] data;
        int size;

        LongArrayList(int cap) {
            data = new long[Math.max(1, cap)];
            size = 0;
        }

        void add(long x) {
            if (size == data.length) data = Arrays.copyOf(data, data.length * 2);
            data[size++] = x;
        }

        void removeOne(long x) {
            for (int i = 0; i < size; i++) {
                if (data[i] == x) {
                    data[i] = data[size - 1];
                    size--;
                    return;
                }
            }
        }
    }

    // ============================================================================
    // CSRグラフ構築
    // ============================================================================

    /**
     * 辺リストを結合
     */
    private static IntPairEdgeList concatEdges(IntPairEdgeList a, IntPairEdgeList b) {
        IntPairEdgeList all = new IntPairEdgeList(a.m + b.m);
        for (int i = 0; i < a.m; i++) all.add(a.u[i], a.v[i]);
        for (int i = 0; i < b.m; i++) all.add(b.u[i], b.v[i]);
        return all;
    }

    /**
     * CSR形式のグラフを構築
     */
    private static LFR buildCSRGraph(int N, double zeta, double gamma, double mu, IntPairEdgeList internal,
                                      IntPairEdgeList external, int[] communityOfNode) {
        IntPairEdgeList edges = concatEdges(internal, external);

        final int E = edges.m;
        final int M = E;
        final int twoE = 2 * E;

        // 各頂点の次数を数える
        int[] deg = new int[N];
        for (int i = 0; i < E; i++) {
            int a = edges.u[i];
            int b = edges.v[i];
            if (a < 0 || a >= N || b < 0 || b >= N) {
                throw new IllegalStateException("Edge endpoint out of range: (" + a + "," + b + ")");
            }
            if (a == b) {
                throw new IllegalStateException("Self-loop found in edge list: (" + a + "," + b + ")");
            }
            deg[a]++;
            deg[b]++;
        }

        // rowPtr（prefix sum）
        int[] rowPtr = new int[N + 1];
        rowPtr[0] = 0;
        for (int v = 0; v < N; v++) {
            rowPtr[v + 1] = rowPtr[v] + deg[v];
        }
        if (rowPtr[N] != twoE) {
            throw new IllegalStateException("rowPtr[N] mismatch: " + rowPtr[N] + " != " + twoE);
        }

        // colIdx/src/revを確保
        int[] colIdx = new int[twoE];
        int[] src = new int[twoE];
        int[] rev = new int[twoE];

        // 書き込み位置
        int[] next = Arrays.copyOf(rowPtr, N);

        // 辺を流し込み：(a->b)と(b->a)を同時に追加し、revを相互参照
        for (int i = 0; i < E; i++) {
            int a = edges.u[i];
            int b = edges.v[i];

            int idxAB = next[a]++;
            int idxBA = next[b]++;

            colIdx[idxAB] = b;
            src[idxAB] = a;
            rev[idxAB] = idxBA;

            colIdx[idxBA] = a;
            src[idxBA] = b;
            rev[idxBA] = idxAB;
        }

        // 検証
        for (int v = 0; v < N; v++) {
            if (next[v] != rowPtr[v + 1]) {
                throw new IllegalStateException("Adjacency fill mismatch at v=" + v);
            }
        }

        return new LFR(N, zeta, gamma, mu, M, rowPtr, colIdx, rev, src, communityOfNode);
    }

    // ============================================================================
    // メインメソッド
    // ============================================================================

    public static void main(String[] args) {
        int N = 500;
        double zeta = 2.0;
        double gamma = 2.5;
        double mu = 0.1;
        int sMin = 10;
        int sMax = 100;
        int kMin = 4;
        int kMax = 50;
        long seed = 42L;

        try {
            LFR graph = generate(N, zeta, gamma, mu, sMin, sMax, kMin, kMax, seed, 5000, 5000, 5000);
            
            // 平均次数を計算
            long sumDeg = 0;
            int minDeg = Integer.MAX_VALUE;
            int maxDeg = 0;
            for (int u = 0; u < graph.N; u++) {
                int deg = graph.rowPtr[u + 1] - graph.rowPtr[u];
                sumDeg += deg;
                minDeg = Math.min(minDeg, deg);
                maxDeg = Math.max(maxDeg, deg);
            }
            double avgDeg = (double) sumDeg / graph.N;
            
            System.out.println("Generated LFR graph:");
            System.out.println("  N: " + graph.N);
            System.out.println("  M: " + graph.M + " (undirected edges)");
            System.out.println("  zeta: " + graph.zeta);
            System.out.println("  gamma: " + graph.gamma);
            System.out.println("  mu: " + graph.mu);
            System.out.println("  Degree statistics:");
            System.out.println("    min: " + minDeg);
            System.out.println("    max: " + maxDeg);
            System.out.println("    average: " + String.format("%.2f", avgDeg));

            // Windowsでも動作するパス構築
            Path baseDir = Path.of("out", "lfr",
                String.format("N=%d", N),
                String.format("gamma=%.2f", gamma),
                String.format("kmin=%d", kMin),
                String.format("kmax=%d", kMax));
            Files.createDirectories(baseDir);
            
            // 辺リスト（テキスト）を出力
            Path samplePath = baseDir.resolve(String.format("sample_%02d.txt", seed));
            graph.writeEdgeList(samplePath);
            System.out.println("辺リストを書き出しました: " + samplePath.toAbsolutePath());
            
            // CSV形式を出力
            Path csvPath = baseDir.resolve(String.format("sample_%02d.csv", seed));
            graph.writeEdgeListCSV(csvPath);
            System.out.println("CSVファイルを書き出しました: " + csvPath.toAbsolutePath());
            
            // GEXFファイルを出力（Gephi用）
            Path gexfPath = baseDir.resolve(String.format("sample_%02d.gexf", seed));
            graph.writeGEXF(gexfPath);
            System.out.println("GEXFファイルを書き出しました: " + gexfPath.toAbsolutePath());
        } catch (Exception e) {
            System.err.println("Error generating graph: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
