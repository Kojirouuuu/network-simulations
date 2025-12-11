package sirsim.network.metrics;

import org.junit.jupiter.api.Test;
import sirsim.network.Graph;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Modularity の基本検証用テスト。
 */
public class ModularityTest {

    @Test
    public void triangleGraph_modularityAndDeltas() {
        // 三角形グラフ（0-1, 1-2, 0-2）
        int n = 3;
        int[] src = {0, 1, 0};
        int[] dst = {1, 2, 2};
        Graph g = Graph.fromUndirectedEdgeList("triangle", n, src, dst);

        // すべて同一コミュニティ
        int[] labelsAllOne = {0, 0, 0};
        double qAllOne = Modularity.compute(g, labelsAllOne);
        // 三角形を1コミュニティにまとめると Q=0
        assertEquals(0.0, qAllOne, 1e-12);

        // 各頂点が別コミュニティ
        int[] labelsSeparate = {0, 1, 2};
        double qSeparate = Modularity.compute(g, labelsSeparate);
        // Q = -1/3 ≈ -0.333...
        assertEquals(-1.0 / 3.0, qSeparate, 1e-12);

        // ΔQ 候補: 各辺に対応するコミュニティ対が存在し、かつ正の ΔQ（1/9）
        Modularity.MergeDelta[] deltas = Modularity.computeMergeDeltas(g, labelsSeparate);
        assertEquals(3, deltas.length);
        for (Modularity.MergeDelta d : deltas) {
            assertTrue(d.deltaQ > 0);
            assertEquals(1.0 / 9.0, d.deltaQ, 1e-12);
        }
    }

    @Test
    public void chain3_modularityAndDeltas() {
        // 直線 0-1-2
        int n = 3;
        int[] src = {0, 1};
        int[] dst = {1, 2};
        Graph g = Graph.fromUndirectedEdgeList("chain3", n, src, dst);

        int[] labelsSeparate = {0, 1, 2};
        double q = Modularity.compute(g, labelsSeparate);
        // 手計算: Q = Σ_c (0 - (d_c/(2m))^2) = - (1/4)^2 - (2/4)^2 - (1/4)^2 = -0.375
        assertEquals(-0.375, q, 1e-12);

        Modularity.MergeDelta[] deltas = Modularity.computeMergeDeltas(g, labelsSeparate);
        // 辺が2本なので (0,1) と (1,2) の2組のみ
        assertEquals(2, deltas.length);
        for (Modularity.MergeDelta d : deltas) {
            assertEquals(0.25, d.deltaQ, 1e-12);
        }
    }
}

