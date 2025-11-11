package sirsim.simulation;

import sirsim.network.Graph;

import java.util.*;

public final class VacSIRSimulator {
    public enum Status { S, I, V, R }

    private final Graph g;
    private final double omega;   // vaccination probability on S exposed by I
    private final double beta;    // infection probability on S exposed by I
    private final int gamma;      // recovery delay in steps
    private final int tMax;       // total steps
    private final double vacMax;  // max fraction vaccinated
    private final int r; // number of neighbors to vaccinate
    private final SplittableRandom rng;

    private final Status[] status;
    private final int[] infStep;  // steps since infected (only valid for I)

    public VacSIRSimulator(Graph g, double omega, double beta, double gamma, double tMax, double vacMax, int r, long seed) {
        if (r > 2) throw new IllegalArgumentException("r must be less than or equal to 2");
        if (g == null) throw new IllegalArgumentException("Graph is null");
        if (omega < 0 || omega > 1 || beta < 0 || beta > 1) throw new IllegalArgumentException("omega and beta must be probabilities in [0,1]");
        if (gamma < 0) throw new IllegalArgumentException("gamma must be non-negative");
        if (tMax <= 0) throw new IllegalArgumentException("tMax must be positive");
        if (vacMax < 0 || vacMax > 1) throw new IllegalArgumentException("vacMax must be in [0,1]");
        this.g = g;
        this.omega = omega;
        this.beta = beta;
        this.gamma = (int)Math.round(gamma);
        this.tMax = (int)Math.round(tMax);
        this.vacMax = vacMax;
        this.r = r;
        this.rng = new SplittableRandom(seed);

        int n = g.n;
        this.status = new Status[n];
        this.infStep = new int[n];
    }

    public VacSirResult run(int[] initialInfecteds) {
        final int n = g.n;
        // initialize all susceptible
        for (int u = 0; u < n; u++){
            status[u] = Status.S;
            infStep[u] = 0;
        }

        // set initial infected unique
        boolean[] seen = new boolean[n];
        int curInfectedNum = 0;
        for (int u : initialInfecteds) {
            if (u < 0 || u >= n) throw new IllegalArgumentException("invalid initial infected: " + u);
            if (seen[u]) continue;
            seen[u] = true;
            status[u] = Status.I;
            infStep[u] = 0;
            curInfectedNum++;
        }

        // vaccination cap
        int maxVaccinations = Math.min(n, (int)Math.floor(vacMax * n));
        int curVaccinatedNum = 0;

        // time series arrays (0..tMax)
        int[] Sseries = new int[tMax + 1];
        int[] Iseries = new int[tMax + 1];
        int[] Vseries = new int[tMax + 1];
        int[] Rseries = new int[tMax + 1];

        // record counts at t=0
        countStates(Sseries, Iseries, Vseries, Rseries, 0);

        // working buffers
        boolean[] toVaccinate = new boolean[n];
        boolean[] toInfect = new boolean[n];
        boolean[] toRecover = new boolean[n];

        for (int t = 0; t < tMax; t++) {
            Arrays.fill(toVaccinate, false);
            Arrays.fill(toInfect, false);
            Arrays.fill(toRecover, false);

            // exposure and recovery scheduling based on state at time t
            for (int u = 0; u < n; u++) {
                if (status[u] == Status.I) {
                    // if recovery is due now, recover and skip exposure this step
                    if (infStep[u] >= gamma) {
                        toRecover[u] = true;
                    }

                    // otherwise expose neighbors
                    int[] neighbors = g.neighbors(u);
                    for (int v : neighbors) {
                        if (status[v] != Status.S) continue;
                        // vaccination attempt first (cap enforced at apply stage)
                        if (!toVaccinate[v] && !toInfect[v]) {
                            if (rng.nextDouble() < omega && curVaccinatedNum <= maxVaccinations) {
                                toVaccinate[v] = true;
                                curVaccinatedNum++;
                            } else if (rng.nextDouble() < beta) {
                                toInfect[v] = true;
                            }
                        }
                    }

                    if (r == 2) {
                        ArrayList<Integer> neighbors2 = new ArrayList<>();
                        for (int v : neighbors) {
                            for (int w : g.neighbors(v)) {
                                if (status[w] == Status.S && !toVaccinate[w] && !toInfect[w]) {
                                    neighbors2.add(w);
                                }
                            }
                        }
                        for (int w : neighbors2) {
                            if (rng.nextDouble() < omega && curVaccinatedNum <= maxVaccinations) {
                                toVaccinate[w] = true;
                                curVaccinatedNum++;
                            }
                        }
                    }
                }
            }

            // apply infections
            for (int u = 0; u < n; u++) {
                if (toInfect[u]) {
                    if (status[u] != Status.S) {
                        throw new IllegalArgumentException("invalid status: " + status[u]);
                    }
                    status[u] = Status.I;
                    curInfectedNum++;
                }
            }

            // apply recoveries
            for (int u = 0; u < n; u++) {
                if (toRecover[u]) {
                    if (status[u] != Status.I) {
                        throw new IllegalArgumentException("invalid status: " + status[u]);
                    }
                    status[u] = Status.R;
                    curInfectedNum--;
                }
            }

            // apply vaccinations up to cap (randomize starting point to reduce bias)
            for (int u = 0; u < n; u++) {
                if (toVaccinate[u]) {
                    if (status[u] != Status.S) {
                        throw new IllegalArgumentException("invalid status: " + status[u]);
                    }
                    status[u] = Status.V;
                }
            }



            // advance infection timers for those still infected
            for (int u = 0; u < n; u++) {
                if (status[u] == Status.I) infStep[u]++;
            }

            // record counts at t+1
            countStates(Sseries, Iseries, Vseries, Rseries, t + 1);

            if (curInfectedNum == 0) {
                // no more infections, but keep filling remaining with steady counts
                for (int tt = t + 1; tt < tMax; tt++) {
                    Sseries[tt + 1] = Sseries[tt];
                    Iseries[tt + 1] = Iseries[tt];
                    Vseries[tt + 1] = Vseries[tt];
                    Rseries[tt + 1] = Rseries[tt];
                }
                break;
            }
        }

        return new VacSirResult(Sseries, Iseries, Vseries, Rseries);
    }

    private void countStates(int[] S, int[] I, int[] V, int[] R, int idx) {
        int s = 0, i = 0, v = 0, r = 0;
        for (Status st : status) {
            switch (st) {
                case S -> s++;
                case I -> i++;
                case V -> v++;
                case R -> r++;
            }
        }
        S[idx] = s; I[idx] = i; V[idx] = v; R[idx] = r;
    }

    public static VacSirResult simulate(Graph g, double omega, double beta, double gamma, int tMax, double vacMax, int[] initialInfecteds, int r, long seed) {
        return new VacSIRSimulator(g, omega, beta, gamma, tMax, vacMax, r, seed).run(initialInfecteds);
    }
}
