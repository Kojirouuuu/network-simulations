package sirsim.simulation;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** SIRシミュレーション結果（可視化/再利用しやすいCSV出力つき） */
public final class SirResult {
    public final int n;
    public final List<Double> times;
    public final List<Integer> S;
    public final List<Integer> I;
    public final List<Integer> R;
    public final double[] tInfect;   // 各ノードの感染成立時刻（未感染はNaN）
    public final double[] tRecover;  // 各ノードの回復成立時刻（未回復はNaN）

    SirResult(int n,
              List<Double> times, List<Integer> S, List<Integer> I, List<Integer> R,
              double[] tInfect, double[] tRecover) {
        this.n = n; this.times = times; this.S = S; this.I = I; this.R = R;
        this.tInfect = tInfect; this.tRecover = tRecover;
    }

    /** 集計時系列CSV（time,S,I,R） */
    public void writeTimeSeriesCsv(Path path) throws IOException {
        path = sirsim.utils.PathsEx.resolveIndexed(path);
        Files.createDirectories(path.getParent());
        try (BufferedWriter bw = Files.newBufferedWriter(path);
             PrintWriter out = new PrintWriter(bw)) {
            out.println("time,S,I,R");
            for (int i = 0; i < times.size(); i++) {
                out.printf(Locale.ROOT, "%.9f,%d,%d,%d%n",
                        times.get(i), S.get(i), I.get(i), R.get(i));
            }
        }
    }

    /** 集計時系列CSV（time,S,I,R,itr）を追記モードで出力 */
    public void writeTimeSeriesCsv(Path path, int itr, boolean append) throws IOException {
        if (!append) path = sirsim.utils.PathsEx.resolveIndexed(path);
        Files.createDirectories(path.getParent());
        boolean writeHeader = true;
        if (Files.exists(path)) {
            try {
                writeHeader = Files.size(path) == 0L;
            } catch (IOException ignored) { /* fallback to writing header */ }
        }
        try (BufferedWriter bw = Files.newBufferedWriter(path,
                java.nio.file.StandardOpenOption.CREATE,
                append ? java.nio.file.StandardOpenOption.APPEND : java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
             PrintWriter out = new PrintWriter(bw)) {
            if (writeHeader) {
                out.println("time,S,I,R,itr");
            }
            for (int i = 0; i < times.size(); i++) {
                out.printf(Locale.ROOT, "%.9f,%d,%d,%d,%d%n",
                        times.get(i), S.get(i), I.get(i), R.get(i), itr);
            }
        }
    }

    /** ノード別時刻CSV（node,infected_at,recovered_at；NaNは空欄） */
    public void writeNodeTimesCsv(Path path) throws IOException {
        path = sirsim.utils.PathsEx.resolveIndexed(path);
        Files.createDirectories(path.getParent());
        try (BufferedWriter bw = Files.newBufferedWriter(path);
             PrintWriter out = new PrintWriter(bw)) {
            out.println("node,infected_at,recovered_at");
            for (int u = 0; u < n; u++) {
                String ti = Double.isNaN(tInfect[u]) ? "" : String.format(Locale.ROOT, "%.9f", tInfect[u]);
                String tr = Double.isNaN(tRecover[u]) ? "" : String.format(Locale.ROOT, "%.9f", tRecover[u]);
                out.println(u + "," + ti + "," + tr);
            }
        }
    }

    /** ノード別時刻CSV（node,infected_at,recovered_at,itr；NaNは空欄）を追記モードで出力 */
    public void writeNodeTimesCsv(Path path, int itr, boolean append) throws IOException {
        if (!append) path = sirsim.utils.PathsEx.resolveIndexed(path);
        Files.createDirectories(path.getParent());
        boolean writeHeader = true;
        if (Files.exists(path)) {
            try {
                writeHeader = Files.size(path) == 0L;
            } catch (IOException ignored) { /* fallback to writing header */ }
        }
        try (BufferedWriter bw = Files.newBufferedWriter(path,
                java.nio.file.StandardOpenOption.CREATE,
                append ? java.nio.file.StandardOpenOption.APPEND : java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
             PrintWriter out = new PrintWriter(bw)) {
            if (writeHeader) {
                out.println("node,itr,infected_at,recovered_at");
            }
            for (int u = 0; u < n; u++) {
                String ti = Double.isNaN(tInfect[u]) ? "" : String.format(Locale.ROOT, "%.9f", tInfect[u]);
                String tr = Double.isNaN(tRecover[u]) ? "" : String.format(Locale.ROOT, "%.9f", tRecover[u]);
                out.println(u + "," + itr + "," + ti + "," + tr);
            }
        }
    }

    /**
     * 集計時系列CSV（itr,alpha,beta,lambda,time,I,R）を追記モードで出力。
     * 解析時にパラメータも横に展開したいケース向け。
     */
    public void writeTimeSeriesCsv(Path path, int itr, double alpha, double beta, double lambda, boolean append) throws IOException {
        if (!append) path = sirsim.utils.PathsEx.resolveIndexed(path);
        Files.createDirectories(path.getParent());
        boolean writeHeader = true;
        if (Files.exists(path)) {
            try {
                writeHeader = Files.size(path) == 0L;
            } catch (IOException ignored) { /* fallback to writing header */ }
        }
        try (BufferedWriter bw = Files.newBufferedWriter(path,
                java.nio.file.StandardOpenOption.CREATE,
                append ? java.nio.file.StandardOpenOption.APPEND : java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
             PrintWriter out = new PrintWriter(bw)) {
            if (writeHeader) {
                out.println("itr,alpha,beta,lambda,time,I,R");
            }
            for (int i = 0; i < times.size(); i++) {
                out.printf(Locale.ROOT, "%d,%.9f,%.9f,%.9f,%.9f,%d,%d%n",
                        itr, alpha, beta, lambda, times.get(i), I.get(i), R.get(i));
            }
        }
    }

    /**
     * 集計時系列CSV（itr,alpha,beta,lambda,time,I,R）を追記モードで出力。
     * 解析時にパラメータも横に展開したいケース向け。
     */
    public void writeFinalStateCsv(Path path, int itr, double alpha, double beta, double lambda, boolean append) throws IOException {
        if (!append) path = sirsim.utils.PathsEx.resolveIndexed(path);
        Files.createDirectories(path.getParent());
        boolean writeHeader = true;
        if (Files.exists(path)) {
            try {
                writeHeader = Files.size(path) == 0L;
            } catch (IOException ignored) { /* fallback to writing header */ }
        }
        try (BufferedWriter bw = Files.newBufferedWriter(path,
                java.nio.file.StandardOpenOption.CREATE,
                append ? java.nio.file.StandardOpenOption.APPEND : java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
             PrintWriter out = new PrintWriter(bw)) {
            if (writeHeader) {
                out.println("itr,alpha,beta,lambda,time,I,R");
            }
            out.printf(Locale.ROOT, "%d,%.9f,%.9f,%.9f,%.9f,%d,%d%n",
                        itr, alpha, beta, lambda, times.get(times.size() - 1), I.get(I.size() - 1), R.get(R.size() - 1));
        }
    }
}
