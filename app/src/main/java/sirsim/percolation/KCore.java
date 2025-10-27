package sirsim.percolation;

import sirsim.network.Graph;

import java.util.ArrayDeque;

/**
 * k-core extraction utilities on an undirected graph.
 */
public final class KCore {
    private KCore() {}

    /**
     * Compute the size of the k-core after pruning nodes with degree < k,
     * restricted to the nodes marked as initially alive.
     *
     * @param g     Undirected graph (CSR style)
     * @param alive Nodes that survive initial percolation (true = present)
     * @param k     Minimum required degree in the core
     * @return number of nodes remaining in the k-core (over original index space)
     */
    public static int size(Graph g, boolean[] alive, int k) {
        final int n = g.n;
        if (alive.length != n) throw new IllegalArgumentException("alive size mismatch");
        if (k <= 0) {
            int cnt = 0; for (boolean b : alive) if (b) cnt++; return cnt;
        }

        int[] deg = new int[n];
        ArrayDeque<Integer> dq = new ArrayDeque<>();

        for (int u = 0; u < n; u++) {
            if (!alive[u]) { deg[u] = 0; continue; }
            int d = 0;
            for (int e = g.firstArc(u); e < g.endArc(u); e++) {
                int v = g.colIdx[e];
                if (alive[v]) d++;
            }
            deg[u] = d;
            if (d < k) dq.add(u);
        }

        boolean[] in = alive.clone();
        while (!dq.isEmpty()) {
            int u = dq.poll();
            if (!in[u]) continue;
            if (deg[u] >= k) continue; // might have been increased by neighbors' updates
            in[u] = false;
            for (int e = g.firstArc(u); e < g.endArc(u); e++) {
                int v = g.colIdx[e];
                if (!in[v]) continue;
                if (deg[v] > 0) {
                    deg[v]--;
                    if (deg[v] < k) dq.add(v);
                }
            }
        }

        int left = 0;
        for (int u = 0; u < n; u++) if (in[u]) left++;
        return left;
    }
}

