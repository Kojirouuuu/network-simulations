package sirsim;

public class LFRParamProbe {
    private static void runCase(String name, int N, double zeta, double gamma, double mu,
                                int sMin, int sMax, int kMin, int kMax, long seed) {
        System.out.println("\n== Case: " + name + " ==");
        System.out.println(String.format("N=%d, zeta=%.2f, gamma=%.2f, mu=%.2f, s=[%d,%d], k=[%d,%d]",
                N, zeta, gamma, mu, sMin, sMax, kMin, kMax));
        try {
            LFR g = LFR.generate(N, zeta, gamma, mu, sMin, sMax, kMin, kMax, seed, 2000, 2000, 2000);
            // 簡易サマリ
            long sumDeg = 0;
            int minDeg = Integer.MAX_VALUE;
            int maxDeg = Integer.MIN_VALUE;
            for (int u = 0; u < g.getN(); u++) {
                int deg = g.getRowPtr()[u + 1] - g.getRowPtr()[u];
                sumDeg += deg;
                minDeg = Math.min(minDeg, deg);
                maxDeg = Math.max(maxDeg, deg);
            }
            double avgDeg = (double) sumDeg / g.getN();
            System.out.println("Result: SUCCESS");
            System.out.println(String.format("  M=%d, deg[min=%d, max=%d, avg=%.2f]", g.getM(), minDeg, maxDeg, avgDeg));
        } catch (Throwable t) {
            System.out.println("Result: FAILURE");
            System.out.println("  " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    public static void main(String[] args) {
        // ベースライン（成立する想定）
        runCase("baseline-ok", 1000, 2.0, 2.5, 0.6, 10, 100, 5, 30, 1L);

        // sMax が小さすぎ、(1-mu)kMin + 1 を満たせず割当失敗が期待されるケース
        // 例: kMin=30, mu=0.1 => kin≈27, needSize≈28 なのに sMax=20
        runCase("sMax-too-small", 500, 2.0, 2.5, 0.1, 10, 20, 30, 40, 2L);

        // kMin が大きすぎてコミュニティ容量を超えるケース
        // 例: kMin=60, mu=0.2 => kin≈48, needSize≈49 なのに sMax=30
        runCase("kMin-too-large", 800, 2.0, 2.5, 0.2, 10, 30, 60, 80, 3L);

        // mu が小さく、内部要求が大きくなるが sMax を厳しくしたケース
        // 例: mu=0.05, kMin=25 => kin≈24, needSize≈25 で sMax=24 はギリギリ不可
        runCase("mu-small-tight-sMax", 600, 2.0, 2.5, 0.05, 8, 24, 25, 60, 4L);

        // 度数上限の境界（kMaxがN-1に近い）でも成立するかの軽いチェック
        runCase("high-kmax-ok", 400, 2.0, 2.5, 0.5, 6, 80, 3, 120, 5L);
    }
}

