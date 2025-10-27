package sirsim;

import sirsim.network.Graph;
import sirsim.network.topology.ER;
import sirsim.simulation.VacSIRSimulator;
import sirsim.simulation.VacSirResult;

import java.nio.file.Paths;
import java.util.SplittableRandom;

public class VacSIR {
    public static void main(String[] args) throws Exception {
        // 例: 無向ERネットワーク（CSR）
        int N = 10_000;
        int kAve = 10;
        Graph g = ER.generateERFromKAve(N, kAve, 42L);

        // 繰り返し回数（引数指定なければ1回）
        int iters = 40;

        // 初期感染者（ランダムに1人）
        int k0 = 1;

        double omega = 0.45;
        double beta = 0.168;        // degree exponent for transmission rate
        int gamma = 3;       // recovery rate
        int tMax = 120;      // 打ち切り時刻
        double vacMax = 0.5; // 最大接種率


        // CSV 出力先（既存ファイルがあればインデックスを付与して新規作成）
        var tsPath = sirsim.utils.PathsEx.resolveIndexed(Paths.get(String.format("out/vacsir/%d/timeseries.csv", N)));

        // 進捗表示用カウント

        // iters 回シミュレーションし、CSVに itr カラム付きで追記（進捗を表示）
        for (int itr = 0; itr < iters; itr++) {
            System.out.printf("itr %d/%d%n", itr + 1, iters);
            System.out.flush();
            // 反復ごとに初期条件と乱数シードを変更
            int[] init = sampleUnique(new SplittableRandom(7L + itr), g.n, k0);
            long simSeed = 12345L + itr;

            VacSirResult res = VacSIRSimulator.simulate(g, omega, beta, gamma, tMax, vacMax, init, simSeed);

            // CSV 出力（パラメータ含む：itr,alpha,beta,lambda,time,I,R）
            res.writeTimeSeriesCsv(tsPath, itr, true);
        }
        System.out.println();
    }

    private static int[] sampleUnique(SplittableRandom rng, int n, int k) {
        if (k > n) throw new IllegalArgumentException("k>n");
        boolean[] used = new boolean[n];
        int[] r = new int[k];
        for (int c = 0; c < k; ) {
            int u = rng.nextInt(n);
            if (!used[u]) { used[u] = true; r[c++] = u; }
        }
        return r;
    }
}
