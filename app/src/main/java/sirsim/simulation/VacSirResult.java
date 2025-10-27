package sirsim.simulation;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** SIRシミュレーション結果（可視化/再利用しやすいCSV出力つき） */
public final class VacSirResult {
    public final int[] S;  // length tMax+1
    public final int[] I;  // length tMax+1
    public final int[] V;  // length tMax+1
    public final int[] R;  // length tMax+1

    VacSirResult(int[] S, int[] I, int[] V, int[] R) {
        this.S = S; this.I = I; this.V = V; this.R = R;
    }

    /** 集計時系列CSV（itr,t,S,I,V,R）を追記モードで出力 */
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
                out.println("itr,t,S,I,V,R");
            }
            int T = I.length; // t = 0..T-1
            for (int t = 0; t < T; t++) {
                out.printf(Locale.ROOT, "%d,%d,%d,%d,%d,%d%n",
                        itr, t, S[t], I[t], V[t], R[t]);
            }
        }
    }
}
